package io.github.shizukajiku.imagewatch.ui.toast

import io.github.shizukajiku.imagewatch.domain.ImageState
import io.github.shizukajiku.imagewatch.infrastructure.persistence.Lock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.TimeSource

enum class ToastKind { NUEVA, SALTADAS, ERROR, RESUMEN }

/**
 * Un aviso en pantalla. Uno por imagen: los avisos no se agrupan (salvo el `RESUMEN`, que es una
 * derivación de la capa de UI y no un aviso real — ver [ToastState.toasts]).
 *
 * `leaving` es la señal de `ToastState` de que toca irse: la tarjeta la usa para arrancar su
 * animación de salida, y no para borrarse sola.
 *
 * `progress` y `durationMillis` son la **condición inicial** de la barrita, no su valor cuadro a
 * cuadro: con qué fracción arranca y a cuántos milisegundos equivale esa fracción entera. La
 * tarjeta anima de ahí a `0f` contra el reloj de fotogramas de Compose. Publicar la fracción viva
 * aquí obligaba a reemplazar esta lista ~60 veces por segundo y por aviso, y esta lista la lee el
 * composable raíz de la aplicación —bandeja y ventana incluidas—, que no tiene por qué repintarse
 * al ritmo de una barra de progreso.
 */
data class Toast(
    val id: Long,
    val kind: ToastKind,
    val title: String,
    val sub: String,
    val meta: String,
    val action: String,
    val imageName: String?,
    val leaving: Boolean = false,
    val progress: Float = 1f,
    val durationMillis: Long = 0L,
)

private const val ABSENT = "—"
private const val SIN_DETALLE = "sin detalle"

/**
 * Suelo de la duración de un aviso. `AppConfig.fromEnvironment()` no valida la duración
 * configurada: un 0 o negativo sembrado por variable de entorno no debe hacer que el aviso
 * desaparezca antes de poder leerse.
 *
 * **Cambió de sentido en la fase 7** y se deja así a propósito: antes vivía dentro de `ToastCard`
 * y se aplicaba en cada reanudación, de modo que quitar el puntero de un aviso casi agotado le
 * regalaba un segundo más de vida; ahora es un suelo sobre el total, una sola vez, en
 * [ToastState.show].
 *
 * Lo que se pierde, en lo observable: retirar el puntero de un aviso casi agotado puede hacerlo
 * desaparecer al instante, donde antes quedaba un segundo de lectura.
 *
 * Recuperarlo **sí cabría entero aquí arriba**, en [ToastState.resume], sin duplicar la regla en
 * ningún sitio: bastaría con subir `remaining` al suelo y republicar `progress`. Lo que costaría
 * es lo otro: la tarjeta tendría que tratar esa fracción publicada como valor de reinicio de su
 * `Animatable` —hoy es un `remember` sin claves, deliberadamente— y quedaría acoplada al ritmo al
 * que este estado publica fracciones. Eso es justo lo que la vía A vino a deshacer, y ese
 * acoplamiento cuesta más que el segundo de lectura que devuelve.
 */
private const val MIN_VISIBLE_MILLIS = 1000L

/**
 * Cuántos toasts se muestran a la vez, que son los que caben en la ventana.
 *
 * El resto **no se descarta: espera turno**. El reloj de descarte solo corre para los avisos
 * visibles; en cuanto uno se va, entra el siguiente de la cola. Ningún aviso se pierde por llegar
 * en mal momento.
 */
const val TOASTS_VISIBLES = 3

/**
 * Origen del reloj monótono por defecto. `System.nanoTime()` es del JDK; `TimeSource.Monotonic`
 * da la misma garantía —avanza siempre, no salta con la hora del sistema— en código común.
 */
private val ARRANQUE = TimeSource.Monotonic.markNow()

/**
 * Cola de toasts. Vive fuera de la ventana principal: los avisos tienen que aparecer justo cuando
 * la ventana está cerrada, que es cuando el usuario no está mirando la tabla.
 *
 * También es quien lleva el reloj de cada aviso visible —cuándo se va—; la tarjeta que lo pinta
 * solo decide cómo se va (la animación de salida y la barrita que la acompaña) y avisa con
 * [exitFinished] al terminar. La regla se queda arriba y el repintado baja: el instante de la
 * expiración lo fija este `delay`, y cuántos píxeles de barra quedan es cosa del reloj de
 * fotogramas de la tarjeta, que es donde no cuesta nada.
 *
 * `show()` corre en el hilo del planificador —`VersionPollingService.poll()` llama a
 * `notifyUpdates` de forma síncrona— y `dismiss()` en el de Compose, así que hay dos hilos reales
 * escribiendo. Todo el estado mutable vive en `StateFlow` y se edita con CAS —`update` o el
 * [mutar] de abajo—: una asignación directa a `.value` basada en el valor anterior perdería una de
 * las dos escrituras si coinciden.
 *
 * La duración llega como función y no como valor: se puede cambiar en Ajustes mientras la
 * aplicación corre, y capturarla aquí congelaría el ajuste en el que estuviera al construirse.
 */
class ToastState(
    private val scope: CoroutineScope,
    private val duration: () -> Duration,
    /**
     * Reloj monótono en milisegundos. Solo sirve para descontar lo ya consumido cuando el puntero
     * pausa un aviso: el `delay` que decide la expiración no necesita leerlo. Llega inyectable
     * porque en `runTest` el tiempo es virtual y el reloj monótono real no avanza con él, así que
     * la prueba pasa `{ currentTime }` y pausa y reanudación se comprueban igual que antes.
     */
    private val nowMillis: () -> Long = { ARRANQUE.elapsedNow().inWholeMilliseconds },
) {
    /**
     * El reloj de cada aviso, en una sola pieza inmutable.
     *
     * Los cuatro campos se editaban antes en cuatro `ConcurrentHashMap` sueltos, que no existen en
     * código común. Van juntos porque instalar un reloj toca dos a la vez —`jobs` y `startedAt`— y
     * partirlo en dos CAS deja una ventana en la que un `detener()` no encuentra el job que ya está
     * corriendo.
     */
    private data class Relojes(
        /** Un job por aviso visible. Los que esperan turno no tienen entrada aquí. */
        val jobs: Map<Long, Job> = emptyMap(),
        /** Cuánto le queda a cada aviso, en milisegundos. Se descuenta al pausarlo. */
        val remaining: Map<Long, Long> = emptyMap(),
        /** Cuándo arrancó el reloj de cada aviso visible, para saber qué descontarle si se pausa. */
        val startedAt: Map<Long, Long> = emptyMap(),
        /** Avisos con el puntero encima: `sincronizarRelojes` no les toca el reloj. */
        val paused: Set<Long> = emptySet(),
    )

    private val sequence = MutableStateFlow(0L)
    private val relojes = MutableStateFlow(Relojes())

    /**
     * Serializa cada mutación de la cola con su [publicar]. El CAS de cada `StateFlow` protege una
     * escritura, pero «mutar `mutableToasts` y derivar `visibleToasts`» son dos pasos: sin este
     * candado, `show()` en el hilo del planificador puede leer la cola, quedarse a medias, y que un
     * `dismiss()` del hilo de Compose publique una lista más nueva que el primero pisa luego con su
     * derivación vieja -reapareciendo un aviso ya descartado, o faltando uno recién añadido-. Es
     * reentrante: `pause()` llama a `publicarFraccion()`, `resume()` a `sincronizarRelojes()`.
     */
    private val lock = Lock()

    /** La cola completa, en orden de llegada. [toasts] es su recorte visible — ver [publicar]. */
    private val mutableToasts = MutableStateFlow<List<Toast>>(emptyList())
    private val visibleToasts = MutableStateFlow<List<Toast>>(emptyList())

    /**
     * La cola visible, en orden de llegada: los dos primeros reales más, si hay más de tres en
     * la cola completa, una tarjeta sintética `RESUMEN` en el tercer hueco.
     *
     * Se recalcula de forma síncrona en [publicar] justo después de cada escritura en
     * `mutableToasts` -no con un `.map().stateIn()` reactivo-, porque `show()`/`dismiss()` llegan
     * de hilos reales que leen `toasts.value` inmediatamente después de escribir, sin ceder el
     * hilo: una tubería reactiva publicaría el recorte en una corrutina aparte, y esa lectura
     * síncrona podría llegar antes de que corriera.
     *
     * El `RESUMEN` **no vive en `mutableToasts`**: `id = -1L` para que ningún `dismiss` real lo
     * alcance nunca (`sequence` arranca en 0 y solo sube), y para que `sincronizarRelojes` -que
     * itera sobre `mutableToasts`, no sobre este `StateFlow`- lo ignore por completo. Su reloj no
     * corre: cuando uno de los dos reales visibles se va, entra el siguiente real y el contador
     * del resumen baja solo, sin que nadie lo reprograme.
     */
    val toasts: StateFlow<List<Toast>> = visibleToasts.asStateFlow()

    private fun publicar() {
        val full = mutableToasts.value
        visibleToasts.value = if (full.size <= TOASTS_VISIBLES) {
            full
        } else {
            val visibles = TOASTS_VISIBLES - 1
            full.take(visibles) + resumenDe(full.drop(visibles))
        }
    }

    private fun resumenDe(ocultos: List<Toast>) = Toast(
        id = -1L,
        kind = ToastKind.RESUMEN,
        title = "y ${ocultos.size} novedades más",
        sub = "",
        meta = ocultos.mapNotNull { it.imageName }.joinToString(", "),
        action = "Ver todas",
        imageName = null,
    )

    fun show(updates: List<ImageState>) = lock.withLock {
        if (updates.isEmpty()) {
            return@withLock
        }
        val millis = duration().inWholeMilliseconds.coerceAtLeast(MIN_VISIBLE_MILLIS)
        val nuevos = updates.map { toastOf(it, millis) }
        relojes.update { s -> s.copy(remaining = s.remaining + nuevos.associate { it.id to millis }) }
        mutableToasts.update { it + nuevos }
        publicar()
        sincronizarRelojes()
    }

    /**
     * Aviso de imágenes que acaban de fallar la verificación. Mismo patrón que [show]: entra en
     * la misma cola, cuenta para el mismo `TOASTS_VISIBLES`, y su reloj lo lleva igual
     * [sincronizarRelojes]. La única diferencia es el contenido de la tarjeta ([failureOf]).
     */
    fun showFailures(failures: List<ImageState>) = lock.withLock {
        if (failures.isEmpty()) {
            return@withLock
        }
        val millis = duration().inWholeMilliseconds.coerceAtLeast(MIN_VISIBLE_MILLIS)
        val nuevos = failures.map { failureOf(it, millis) }
        relojes.update { s -> s.copy(remaining = s.remaining + nuevos.associate { it.id to millis }) }
        mutableToasts.update { it + nuevos }
        publicar()
        sincronizarRelojes()
    }

    fun dismiss(id: Long) = lock.withLock {
        olvidar(id)
        mutableToasts.update { current -> current.filterNot { it.id == id } }
        publicar()
        sincronizarRelojes()
    }

    /**
     * Retira los avisos de una imagen. Se llama al reconocerla: el aviso decía que hay una versión
     * nueva por ver, y el usuario acaba de decir que ya la vio. Dejarlo en pantalla —o peor, dejarlo
     * esperando turno en la cola para aparecer después— es enseñar algo que ya no es cierto.
     */
    fun dismissFor(imageName: String) = lock.withLock {
        mutableToasts.value.filter { it.imageName == imageName }.forEach { olvidar(it.id) }
        mutableToasts.update { current -> current.filterNot { it.imageName == imageName } }
        publicar()
        sincronizarRelojes()
    }

    /** El puntero encima: se detiene el reloj y se conserva lo que quedaba. */
    fun pause(id: Long) = lock.withLock {
        relojes.update { it.copy(paused = it.paused + id) }
        detener(id)
        publicarFraccion(id)
    }

    /** El puntero fuera: sigue con lo que quedaba, no con el total. */
    fun resume(id: Long) = lock.withLock {
        relojes.update { it.copy(paused = it.paused - id) }
        sincronizarRelojes()
    }

    /** La tarjeta terminó su animación de salida: ahora sí se saca de la cola. */
    fun exitFinished(id: Long) = dismiss(id)

    /**
     * Arranca un job para cada uno de los primeros [TOASTS_VISIBLES] que no tenga job ni esté
     * pausado ni saliente. No toca los demás: ni los que ya tienen reloj corriendo, ni los que
     * esperan turno más allá del hueco visible.
     */
    private fun sincronizarRelojes() {
        val estado = relojes.value
        mutableToasts.value.take(TOASTS_VISIBLES).forEach { toast ->
            if (!toast.leaving && toast.id !in estado.paused && toast.id !in estado.jobs) {
                arrancarReloj(toast.id)
            }
        }
    }

    /**
     * Un job por aviso visible, y una sola espera: el `delay(left)` decide **cuándo** expira. Ya
     * no hay ningún hijo repintando a cadencia de fotograma, así que tampoco hay dos relojes que
     * puedan separarse -uno midiendo el tiempo de pared y el otro sumando tics de 16 ms + ε-, ni
     * un tic superviviente que vuelva a escribir la fracción después de [marcarSaliente].
     *
     * `show()` corre en el hilo del planificador y `resume()`/`dismiss()` en el de Compose: dos
     * hilos reales pueden llegar aquí casi a la vez para el mismo aviso. El job nace `LAZY` y solo
     * arranca si este hilo gana el CAS que lo instala junto a su instante de arranque -es lo que
     * hacía `computeIfAbsent`: crear y publicar en un solo paso-. El perdedor cancela un job que
     * nunca llegó a correr, así que no hay dos relojes para el mismo aviso ni uno huérfano sin
     * entrada en `jobs` que nadie pueda cancelar.
     */
    private fun arrancarReloj(id: Long) {
        val left = relojes.value.remaining[id] ?: MIN_VISIBLE_MILLIS
        val job =
            scope.launch(start = CoroutineStart.LAZY) {
                delay(left)
                marcarSaliente(id)
            }
        val instalado =
            mutar { s ->
                if (id in s.jobs) {
                    s to false
                } else {
                    s.copy(
                        jobs = s.jobs + (id to job),
                        startedAt = s.startedAt + (id to nowMillis()),
                    ) to true
                }
            }
        if (instalado) job.start() else job.cancel()
    }

    // Sin comprobación explícita de isActive: el único punto de suspensión es delay(), que ya
    // lanza CancellationException por su cuenta al cancelarse el job -no hace falta duplicarlo-.

    /**
     * Publica la fracción que le queda al aviso. Solo se llama al pausar: así una tarjeta que se
     * componga de nuevo con el puntero encima arranca su barra donde estaba, y no en `1f`.
     */
    private fun publicarFraccion(id: Long) {
        val left = relojes.value.remaining[id] ?: return
        mutableToasts.update { lista ->
            lista.map {
                if (it.id != id || it.durationMillis <= 0) {
                    it
                } else {
                    it.copy(progress = (left.toFloat() / it.durationMillis).coerceIn(0f, 1f))
                }
            }
        }
        publicar()
    }

    private fun marcarSaliente(id: Long) = lock.withLock {
        relojes.update {
            it.copy(
                jobs = it.jobs - id,
                startedAt = it.startedAt - id,
                remaining = it.remaining + (id to 0L),
            )
        }
        mutableToasts.update { lista ->
            lista.map { if (it.id == id) it.copy(leaving = true, progress = 0f) else it }
        }
        publicar()
    }

    /**
     * Cancela el reloj de un aviso y le descuenta lo que llegó a consumir. El descuento va aquí
     * y no en `pause()` porque este es el único sitio por el que un reloj deja de correr: hacerlo
     * fuera dejaría a `remaining` mintiendo por cualquier otro camino que cancelara el job.
     */
    private fun detener(id: Long) {
        val job =
            mutar { s ->
                val desde = s.startedAt[id]
                val restante =
                    when {
                        desde == null -> {
                            s.remaining
                        }

                        else -> {
                            val consumido = nowMillis() - desde
                            val left = s.remaining[id]
                            if (left ==
                                null
                            ) {
                                s.remaining
                            } else {
                                s.remaining + (id to (left - consumido).coerceAtLeast(0L))
                            }
                        }
                    }
                s.copy(jobs = s.jobs - id, startedAt = s.startedAt - id, remaining = restante) to s.jobs[id]
            }
        job?.cancel()
    }

    /** Cancela el reloj y olvida todo rastro del aviso: se llama justo antes de retirarlo. */
    private fun olvidar(id: Long) {
        detener(id)
        relojes.update { it.copy(paused = it.paused - id, remaining = it.remaining - id) }
    }

    /**
     * Read-modify-write atómico sobre [relojes] que además devuelve un resultado. `update {}` no
     * basta aquí: [arrancarReloj] y [detener] necesitan saber qué encontraron —si ya había job, y
     * cuál era— para actuar fuera del CAS. Es el mismo bucle de `compareAndSet` con el que `update`
     * está escrito por dentro.
     */
    private inline fun <T> mutar(bloque: (Relojes) -> Pair<Relojes, T>): T {
        while (true) {
            val actual = relojes.value
            val (nuevo, resultado) = bloque(actual)
            if (relojes.compareAndSet(actual, nuevo)) {
                return resultado
            }
        }
    }

    private fun toastOf(image: ImageState, millis: Long) = Toast(
        id = sequence.updateAndGet { it + 1 },
        kind = ToastKind.NUEVA,
        title = "${image.name} · versión nueva",
        sub = "versión ${image.remote?.value ?: ABSENT}",
        meta = image.registry,
        // «Ver», no «Visto»: la acción abre la ventana y resalta la fila -navegar-, no reconoce
        // la versión. «Visto» es la acción de reconocer (H-92), que vive en la fila y su menú.
        action = "Ver",
        imageName = image.name,
        durationMillis = millis,
    )

    private fun failureOf(image: ImageState, millis: Long) = Toast(
        id = sequence.updateAndGet { it + 1 },
        kind = ToastKind.ERROR,
        title = "${image.name} · no se pudo verificar",
        sub = image.error ?: SIN_DETALLE,
        meta = image.registry,
        action = "Reintentar",
        imageName = image.name,
        durationMillis = millis,
    )
}
