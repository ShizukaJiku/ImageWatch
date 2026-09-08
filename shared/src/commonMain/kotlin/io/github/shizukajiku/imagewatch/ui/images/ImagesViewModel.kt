package io.github.shizukajiku.imagewatch.ui.images

import io.github.shizukajiku.imagewatch.application.PollListener
import io.github.shizukajiku.imagewatch.application.PollingController
import io.github.shizukajiku.imagewatch.application.SilencedImageStore
import io.github.shizukajiku.imagewatch.application.TrackedImageStore
import io.github.shizukajiku.imagewatch.application.VersionPollingService
import io.github.shizukajiku.imagewatch.application.pendingNews
import io.github.shizukajiku.imagewatch.domain.ImageState
import io.github.shizukajiku.imagewatch.domain.ImageStatus
import io.github.shizukajiku.imagewatch.domain.PollSnapshot
import io.github.shizukajiku.imagewatch.ui.sound.Sound
import io.github.shizukajiku.imagewatch.ui.sound.Sounds
import io.github.shizukajiku.imagewatch.ui.theme.Dwell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlin.concurrent.Volatile
import kotlin.time.Clock
import kotlin.time.Instant

private val VALID_NAME = Regex("[A-Za-z0-9._-]+")
private const val ABSENT = "—"

/**
 * El formato «dd/MM/yyyy HH:mm» escrito a mano sobre los campos de la fecha local, que es lo
 * que hacia el `DateTimeFormatter` del JDK. La fase B lo sustituira por el formateador de
 * kotlinx-datetime.
 */
private fun formatWhen(at: Instant): String {
    val t = at.toLocalDateTime(TimeZone.currentSystemDefault())
    fun dos(v: Int) = v.toString().padStart(2, '0')
    return "${dos(t.day)}/${dos(t.month.number)}/${t.year} ${dos(t.hour)}:${dos(t.minute)}"
}

/**
 * Qué le pasó a la fila, no cómo se pinta: la capa visual elige si eso es un latido, un borde o
 * un icono, y cambiar esa eleccion no toca esta enumeracion.
 *
 * `SENALADA` gana sobre `NOVEDAD` cuando coinciden -lo calcula `derive()`-: el resaltado responde
 * a un clic del usuario, y eso pesa mas que una animacion que llego sola.
 */
enum class RowEmphasis { NINGUNO, SENALADA, NOVEDAD }

/** Una fila de la tabla, ya lista para pintar: sin `Optional`, sin fechas, sin lógica. */
data class ImageRowState(
    val name: String,
    val registry: String,
    val local: String,
    val remote: String,
    /** Se conserva como dato: `StatusBadge` lo pinta tal cual, sin decidir nada a partir de él. */
    val status: ImageStatus,
    val emphasis: RowEmphasis,
    val detail: String,
    /** Mensaje del fallo, si esta fila falló. Es lo que se enseña en el tooltip. */
    val error: String? = null,
    // Los cuatro campos de intención de abajo se derivan de `status` aquí mismo, con un valor por
    // defecto, y no uno a uno en `toRow`: son la misma regla -qué significa cada estado para la
    // fila- y partirla entre dos sitios es lo que dejaba a `canAcknowledge` viajando explícito
    // mientras sus tres hermanos se calculaban solos.
    /** Solo hay algo que reconocer cuando la fila está pendiente. */
    val canAcknowledge: Boolean = status == ImageStatus.PENDING,
    /** Nunca se ha podido verificar. Antes se leía como `status == ImageStatus.UNKNOWN` en `ImageRow`. */
    val unverified: Boolean = status == ImageStatus.UNKNOWN,
    /** La ultima consulta fallo. Gobierna a la vez el color del nombre y el atenuado de la version local. */
    val failed: Boolean = status == ImageStatus.ERROR,
    /** Hay una version remota nueva que resaltar. */
    val remoteHighlighted: Boolean = status == ImageStatus.PENDING,
    /** Avisos silenciados: la imagen sigue vigilada y al dia, solo no salta el aviso. */
    val muted: Boolean = false,
    /**
     * Se acaba de pedir «Visto»: la fila se sustituye por la linea de Deshacer durante
     * [io.github.shizukajiku.imagewatch.ui.theme.Dwell.UNDO_MILLIS] y solo entonces se reconoce
     * de verdad. El estado de abajo no cambia mientras tanto: `status` sigue siendo PENDING.
     */
    val pendingUndo: Boolean = false,
    /** Se pidio comprobar solo esta fila y la respuesta todavia no ha llegado. */
    val checking: Boolean = false,
    /** Hace cuánto se comprobó esta fila, en texto. Vacío si nunca se ha comprobado. */
    val age: String = "",
)

data class ImagesUiState(
    val rows: List<ImageRowState> = emptyList(),
    val total: Int = 0,
    val pending: Int = 0,
    /** Cuántas filas están en ERROR ahora mismo. Decide si el aviso «Nada que atender» aplica. */
    val errorCount: Int = 0,
    /** Hay al menos una pendiente que reconocer de una vez. */
    val canAcknowledgeAll: Boolean = false,
    val polling: Boolean = false,
    /** Cada cuántos segundos sondea. Va en el subtítulo de la cabecera. */
    val pollIntervalSeconds: Long = 0,
    val search: String = "",
    val verifying: Boolean = false,
    /** Todas las imágenes vigiladas fallaron en el último ciclo: el origen no responde. */
    val allFailing: Boolean = false,
    /** Cuándo terminó el último ciclo en el que algo se verificó. Nulo si nunca ocurrió. */
    val lastSuccessAt: Instant? = null,
    /**
     * Lo que dice el aviso de sin conexión sobre [lastSuccessAt], ya en texto. Se recalcula en
     * cada ciclo de sondeo, igual que el `detail` de cada fila: leer el reloj y repartir en
     * umbrales es una decisión, y el banner que lo enseña no es sitio para tomarla.
     */
    val lastSuccessLabel: String = "",
    /**
     * Novedades que acaban de llegar y esperan tras la banda «Ponerlas arriba»: ya cuentan en
     * [pending], todavia no se ven en [rows]. Es la mecanica que evita que el orden salte cuando
     * el operador ya esta mirando la lista.
     */
    val queuedCount: Int = 0,
    /**
     * Nombre que se acaba de mover a «Al dia» tras confirmarse un «Visto»: deja un rastro breve
     * en la linea plegada de esa seccion. Nulo cuando no hay rastro que enseñar.
     */
    val trace: String? = null,
)

/**
 * Hace cuánto se comprobó una fila, en el texto breve que cabe en la columna de antigüedad: «hace
 * 40 min», no la fecha completa que ya lleva `detail`. Funcion pura por la misma razon que
 * [lastSuccessLabel]: se puede probar con un reloj fijo sin montar Compose.
 */
internal fun relativeAge(at: Instant, now: Instant): String {
    val minutes = (now - at).inWholeMinutes
    return when {
        minutes < 1 -> "hace unos segundos"
        minutes < 60 -> "hace $minutes min"
        minutes < 24 * 60 -> "hace ${minutes / 60} h"
        else -> "hace ${minutes / (24 * 60)} d"
    }
}

/**
 * Cuánto hace de la última verificación correcta, en las tres franjas que el usuario distingue:
 * «acaba de pasar», «hace un rato» y «hace horas». Función pura con el reloj como parámetro —el
 * view model le pasa `Clock.System.now()`— para poder probar los tres umbrales sin tocar el reloj real.
 */
internal fun lastSuccessLabel(at: Instant?, now: Instant): String {
    if (at == null) {
        return "No se ha verificado nada desde que arrancó."
    }
    val minutes = (now - at).inWholeMinutes
    return when {
        minutes < 1 -> "Última verificación correcta: hace menos de un minuto."
        minutes < 60 -> "Última verificación correcta: hace $minutes min."
        else -> "Última verificación correcta: hace ${minutes / 60} h."
    }
}

/**
 * Adapta el núcleo a un `StateFlow` que Compose puede observar.
 *
 * No es un ViewModel de AndroidX: es una clase plana que recibe sus dependencias por constructor,
 * de modo que se puede probar sin arrancar la aplicación ni pintar nada.
 *
 * Los métodos que validan devuelven `null` cuando todo fue bien, o el mensaje a mostrar. Así la
 * validación se prueba sin interfaz, que es justo lo que no se podía hacer cuando esa lógica
 * vivía dentro de los diálogos de Swing.
 */
class ImagesViewModel(
    private val service: VersionPollingService,
    private val trackedImages: TrackedImageStore,
    private val silencedImages: SilencedImageStore,
    private val controller: PollingController,
    private val sounds: Sounds,
    /**
     * Donde corren los temporizadores del resaltado y de las novedades. Antes vivian como
     * `LaunchedEffect(...) { delay(...) }` dentro de `ImagesScreen`; medir tiempo es logica, no
     * pintura, y aqui se puede probar con `runTest`/`advanceTimeBy` sin arrancar Compose.
     */
    private val scope: CoroutineScope,
    /**
     * Retira los avisos en pantalla de una imagen. Llega como función y no como la capa de avisos
     * entera para que el view model de la lista no dependa de ella: aquí solo se sabe que
     * reconocer una imagen deja sin sentido lo que se estuviera diciendo de ella.
     */
    private val dismissToastsFor: (String) -> Unit = {},
) : PollListener {
    // Lo escriben dos hilos: el del planificador en onPollStarted y onSnapshot, y el de Compose
    // en todo lo demas. Volatile para que recompute() no lea una publicacion rancia.
    @Volatile private var snapshot = service.lastSnapshot()

    /**
     * Fila senalada desde un toast, y novedades a la espera de que expire su latido. Viven fuera
     * de ImagesUiState -no las ve la pantalla, que ya no decide cuando se apagan- pero **no**
     * como `@Volatile var` sueltos: `onSnapshot` (hilo del planificador) hace `bumped + nuevas`, y
     * el job de `scheduleBumpClear` (hilo de `Dispatchers.Default`) hace `bumped - nuevas` cuando
     * expira. Ambas son lecturas-y-escrituras compuestas sobre el mismo `Set`, y `@Volatile` solo
     * garantiza que cada escritura se vea, no que la combinacion sea atomica: dos hilos pueden
     * leer el mismo valor base y perder una de las dos actualizaciones. Es exactamente el mismo
     * patrón que la fase 6 quitó del resto del estado -ver el comentario junto a `mutableState`
     * mas abajo-, así que aquí se resuelve igual: un `StateFlow` propio editado con `.update {}`.
     */
    private data class TimerState(
        val highlighted: String? = null,
        val bumped: Set<String> = emptySet(),
        /** Nombres que acaban de pasar a pendientes y esperan tras «Ponerlas arriba». */
        val queued: Set<String> = emptySet(),
        /** Nombres con la fila sustituida por la linea de Deshacer, camino de reconocerse. */
        val pendingUndo: Set<String> = emptySet(),
        /** Nombres cuya comprobacion individual esta en vuelo. */
        val checking: Set<String> = emptySet(),
        /** Se acaba de mover a «Al dia» y deja un rastro breve; un solo dueño a la vez. */
        val trace: String? = null,
    )

    private val timers = MutableStateFlow(TimerState())

    /**
     * El resaltado tiene un solo dueño a la vez: uno nuevo cancela el anterior.
     *
     * `var` pelado y no `CopyOnWriteArrayList` como [bumpJobs]: sus tres únicos escritores
     * -`highlight()`, `clearHighlight()` y `close()`- corren en el hilo de Compose, así que no hay
     * dos hilos compitiendo por este campo. El job expirando en `Dispatchers.Default` no lo toca:
     * solo escribe en `timers`, que sí es un `StateFlow`.
     */
    private var highlightJob: Job? = null

    /**
     * Un job por tanda de novedades: cada uno retira solo las suyas al cumplirse su tiempo.
     * `CopyOnWriteArrayList` porque lo mutan tres hilos distintos -el del planificador en
     * `scheduleBumpClear`, los de `Dispatchers.Default` cuando un job expira o se cancela, y el
     * de Compose en `close()`-, el mismo motivo por el que `VersionPollingService` usa la misma
     * estructura para sus oyentes.
     */
    private val bumpJobs = MutableStateFlow<List<Job>>(emptyList())

    /**
     * Un job de deshacer por imagen: uno nuevo para el mismo nombre cancela al anterior, igual
     * que [highlightJob]. Con clave, porque a diferencia del resaltado puede haber varias filas
     * en su ventana de Deshacer a la vez.
     */
    private val undoJobs = MutableStateFlow<Map<String, Job>>(emptyMap())

    /** El rastro de «se ha movido aqui» tiene un solo dueño a la vez, igual que el resaltado. */
    private var traceJob: Job? = null

    // El resto del estado vive dentro del propio ImagesUiState y se edita con update {}, que hace
    // el read-modify-write con CAS. Cuando vivia en campos paralelos, dos hilos que llegaban a la
    // vez se pisaban: la ultima tecla del buscador desaparecia de la pantalla si un ciclo entraba
    // justo al teclear.
    private val mutableState = MutableStateFlow(ImagesUiState())
    val state: StateFlow<ImagesUiState> = mutableState.asStateFlow()

    // Llegan desde el hilo del planificador. MutableStateFlow es seguro entre hilos y Compose
    // recolecta desde el suyo, así que no hace falta saltar de hilo aquí.
    override fun onPollStarted() {
        recompute { it.copy(verifying = true) }
    }

    override fun onSnapshot(received: PollSnapshot) {
        // Se compara antes de mover `snapshot`: la referencia es la publicacion anterior.
        val nuevas = novedadesSobrePendientes(snapshot, received)
        val nuevasPendientes = newlyPending(snapshot, received)
        snapshot = received
        // Se acumulan, no se reemplazan: con un intervalo corto, un ciclo sin novedades no puede
        // apagar el latido de una tanda anterior antes de que cumpla sus tres segundos. Solo el
        // temporizador de abajo -uno por tanda- retira las suyas. `.update {}` hace el
        // read-modify-write con CAS: sin esto, este `+` compuesto perdería una tanda si coincide
        // con el `-` de un temporizador expirando en otro hilo.
        if (nuevas.isNotEmpty()) {
            timers.update { it.copy(bumped = it.bumped + nuevas) }
            scheduleBumpClear(nuevas)
        }
        // Las que acaban de pasar a pendientes no entran solas en la lista: esperan tras la banda
        // «Ponerlas arriba» hasta que el usuario las pide (D1, mecanica 2). Solo la promueve
        // promoteQueued(); aqui no hay temporizador que las retire solas.
        if (nuevasPendientes.isNotEmpty()) {
            timers.update { it.copy(queued = it.queued + nuevasPendientes) }
        }
        // Cualquier snapshot que llega resuelve toda comprobacion individual en vuelo, sea o no
        // la que la origino: si llego un snapshot, el dato de esa fila ya esta fresco.
        timers.update { it.copy(checking = emptySet()) }
        // La marca solo avanza si algo se verificó. Si avanzara en cada ciclo, el aviso diría
        // "hace 10 segundos" mientras el origen lleva una hora caído.
        val verifiedAt =
            if (received.images.any { it.status != ImageStatus.ERROR }) received.at else null
        // Solo en la transicion: repetir el sonido de error en cada ciclo mientras el origen
        // sigue caido es exactamente lo que hace que el usuario apague todos los sonidos. Se lee
        // el aviso ya publicado antes de recomputar -y no un campo aparte- porque un segundo
        // calculo de "todo falla" diverge del de recompute() en cuanto cambian las imagenes
        // vigiladas entre un ciclo y el siguiente.
        val antesFallabaTodo = mutableState.value.allFailing
        recompute { current ->
            current.copy(
                verifying = false,
                lastSuccessAt = verifiedAt ?: current.lastSuccessAt,
            )
        }
        if (mutableState.value.allFailing && !antesFallabaTodo) {
            sounds.play(Sound.ERROR)
        }
    }

    init {
        service.addListener(this)
        recompute()
    }

    fun onSearchChange(text: String) {
        recompute { it.copy(search = text) }
    }

    /** Consulta ya una imagen, o todas si [name] es nulo, sin esperar al siguiente ciclo. */
    fun refreshNow(name: String? = null) {
        // Solo una fila concreta se marca "comprobando": una comprobacion de todas ya tiene su
        // propio indicador en `verifying`, y marcarlas todas aqui duplicaria esa señal.
        if (name != null) {
            timers.update { it.copy(checking = it.checking + name) }
            recompute()
        }
        controller.refreshNow(name)
    }

    /**
     * «Ponerlas arriba»: las novedades que esperaban tras la banda pasan a verse en la lista. No
     * hace falta nada mas -ya estaban contadas en el titular-, solo dejar de esconderlas.
     */
    fun promoteQueued() {
        if (timers.value.queued.isEmpty()) return
        timers.update { it.copy(queued = emptySet()) }
        recompute()
    }

    /** Silencia o des-silencia los avisos de una imagen: sigue vigilada y al dia, solo no salta el aviso. */
    fun toggleSilence(name: String) {
        val actuales = silencedImages.findAll()
        silencedImages.save(if (name in actuales) actuales - name else actuales + name)
        sounds.play(Sound.TOGGLE)
        recompute()
    }

    /**
     * Pide dar una pendiente por vista: la fila se sustituye por la linea de Deshacer durante
     * [Dwell.UNDO_MILLIS] y solo entonces se reconoce de verdad (D1, mecanica 1). No-op si la
     * imagen no esta pendiente -no hay nada que deshacer de una accion que no debia ofrecerse-.
     */
    fun requestAcknowledge(name: String) {
        val fila = snapshot.find(name)
        if (fila == null || fila.status != ImageStatus.PENDING) {
            return
        }
        // Se captura la version que el usuario vio al pulsar «Visto», no la que haya en
        // `latestReleases` cuando el temporizador despierte: si llega una version mas nueva
        // durante la ventana de deshacer, acknowledge() leeria esa version sin que nadie la
        // hubiera revisado y la daria por vista en silencio.
        val vista = fila.remote?.value
        undoJobs.value[name]?.cancel()
        timers.update { it.copy(pendingUndo = it.pendingUndo + name) }
        recompute()
        val job = scope.launch {
            delay(Dwell.UNDO_MILLIS)
            timers.update { it.copy(pendingUndo = it.pendingUndo - name) }
            val actual = snapshot.find(name)?.remote?.value
            if (actual == vista) {
                acknowledge(name)
                leaveTrace(name)
            } else {
                // La version cambio mientras se esperaba: lo que se iba a reconocer ya no es lo
                // ultimo. No se reconoce nada solo -la fila vuelve a pendiente con la version
                // nueva, y el usuario decide otra vez-.
                recompute()
            }
        }
        undoJobs.update { it + (name to job) }
        job.invokeOnCompletion { undoJobs.update { actual -> if (actual[name] === job) actual - name else actual } }
    }

    /** Deshace un «Visto» pedido hace menos de [Dwell.UNDO_MILLIS]: la fila vuelve a como estaba. */
    fun undoAcknowledge(name: String) {
        undoJobs.getAndUpdate { it - name }[name]?.cancel()
        timers.update { it.copy(pendingUndo = it.pendingUndo - name) }
        recompute()
    }

    /**
     * Rastro de «X se ha movido aquí» en la línea plegada de «Al día» (D1, mecánica 3). Un solo
     * dueño a la vez, igual que [highlightJob]: el ultimo movimiento es el que se enseña.
     */
    private fun leaveTrace(name: String) {
        traceJob?.cancel()
        timers.update { it.copy(trace = name) }
        recompute()
        traceJob = scope.launch {
            delay(Dwell.TRACE_MILLIS)
            timers.update { if (it.trace == name) it.copy(trace = null) else it }
            recompute()
        }
    }

    /** Da por vistas todas las que tienen version pendiente, de una vez. */
    fun acknowledgeAll() {
        if (mutableState.value.pending == 0) {
            return
        }
        // Los nombres se leen antes de reconocer: despues no queda ninguna pendiente que
        // consultar, y los avisos de todas ellas se quedarian en pantalla.
        val pendientes = snapshot.pending().map { it.name }
        service.acknowledgeAll()
        snapshot = service.lastSnapshot()
        pendientes.forEach(dismissToastsFor)
        sounds.play(Sound.SUCCESS)
        recompute()
    }

    fun acknowledge(name: String) {
        service.acknowledge(name)
        snapshot = service.lastSnapshot()
        dismissToastsFor(name)
        // Marcar como vista persiste una decision del usuario, igual que eliminar o renombrar:
        // se confirma igual.
        sounds.play(Sound.SUCCESS)
        recompute()
    }

    fun togglePolling() {
        if (controller.status() == PollingController.Status.RUNNING) {
            controller.stop()
        } else {
            controller.start()
        }
        sounds.play(Sound.TOGGLE)
        recompute()
    }

    fun addImage(name: String): String? = saveName(editing = null, candidate = name)

    fun renameImage(previous: String, candidate: String): String? = saveName(editing = previous, candidate = candidate)

    fun removeImage(name: String) {
        trackedImages.save(trackedImages.findAll() - name)
        sounds.play(Sound.SUCCESS)
        recompute()
    }

    /**
     * Se desengancha del servicio y para los temporizadores. Sin lo primero, el servicio
     * retendría una interfaz ya cerrada; sin lo segundo, un job de resaltado o de novedad
     * seguiría publicando estado -y llamando a `recompute()`- despues de que nadie fuera a leerlo.
     */
    fun close() {
        service.removeListener(this)
        highlightJob?.cancel()
        bumpJobs.value.forEach { it.cancel() }
        undoJobs.value.values.forEach { it.cancel() }
        traceJob?.cancel()
    }

    fun highlight(name: String) {
        highlightJob?.cancel()
        timers.update { it.copy(highlighted = name) }
        // Limpia el filtro: si la búsqueda excluyera la fila, "Ver" abriría la ventana sin
        // enseñar nada.
        recompute { it.copy(search = "") }
        highlightJob = scope.launch {
            delay(Dwell.HIGHLIGHT_MILLIS)
            // `cancel()` no interrumpe el código que ya pasó el punto de suspensión: si un
            // `highlight()` nuevo llega justo cuando este job ya despertó del `delay`, sin este
            // guard el job viejo borraría el nombre que el nuevo acaba de publicar. Solo limpia
            // si `highlighted` sigue siendo el que este job programó.
            timers.update { if (it.highlighted == name) it.copy(highlighted = null) else it }
            recompute()
        }
    }

    /**
     * Apaga el resaltado ya, sin esperar a sus [Dwell.HIGHLIGHT_MILLIS]. La llama la ventana al
     * cerrarse: el resaltado dice «mira esta fila», y cerrar la ventana es haber terminado de
     * mirarla. Sin esto, reabrir dentro de los cuatro segundos devuelve la fila resaltada y la
     * lista vuelve a desplazarse hasta ella (H-76).
     */
    fun clearHighlight() {
        highlightJob?.cancel()
        timers.update { it.copy(highlighted = null) }
        recompute()
    }

    /**
     * Un job por tanda: espera lo que dura el latido y retira **solo** las novedades de esa
     * tanda, no `bumped` entero. Sin esto, una tanda que llega y expira rapido se llevaria por
     * delante el latido de otra que todavia no ha cumplido sus tres segundos.
     *
     * Vive separado del calculo de `nuevas` en `onSnapshot()` a proposito: la Tarea 7 va a
     * sustituir ese calculo por uno que vivira en el nucleo, y esta acumulacion tiene que
     * sobrevivir al cambio sin tocarse.
     */
    private fun scheduleBumpClear(nuevas: Set<String>) {
        lateinit var job: Job
        job = scope.launch {
            delay(Dwell.BUMP_MILLIS)
            timers.update { it.copy(bumped = it.bumped - nuevas) }
            recompute()
        }
        bumpJobs.update { it + job }
        job.invokeOnCompletion { bumpJobs.update { it - job } }
    }

    /**
     * Cuáles de las novedades del núcleo merecen el latido de «otra más».
     *
     * La comparación de versiones no se repite aquí: la hace [pendingNews], que es la misma regla
     * con la que el servicio decide qué genera aviso. Lo que sí es decisión de esta capa es el
     * filtro: **solo laten las que ya estaban pendientes**. Las que *acaban de* pasar a pendientes
     * quedan fuera porque ya se ven por su insignia y por su aviso, y señalarlas además con la
     * animación de «otra más» diría algo que no ocurrió.
     */
    private fun novedadesSobrePendientes(previous: PollSnapshot, current: PollSnapshot): Set<String> =
        pendingNews(previous, current)
            .filter { previous.find(it.name)?.status == ImageStatus.PENDING }
            .map { it.name }
            .toSet()

    private fun saveName(editing: String?, candidate: String): String? {
        val value = candidate.trim()
        if (value.isEmpty() || !VALID_NAME.matches(value)) {
            return "Usa solo letras, números, '.', '_' o '-'"
        }
        val current = trackedImages.findAll()
        if (current.any { it == value && it != editing }) {
            return "Ya existe una imagen con ese nombre"
        }
        val updated = when (editing) {
            null -> current + value
            else -> current.map { if (it == editing) value else it }
        }
        trackedImages.save(updated)
        // Renombrar no puede costarle al usuario su historial: sin esto, la imagen renombrada
        // nace sin version reconocida y vuelve a avisar de lo que ya habia dado por visto.
        if (editing != null && editing != value) {
            service.renameImage(editing, value)
            snapshot = service.lastSnapshot()
        }
        sounds.play(Sound.SUCCESS)
        recompute()
        return null
    }

    private fun recompute(change: (ImagesUiState) -> ImagesUiState = { it }) {
        mutableState.update { current -> derive(change(current)) }
    }

    /**
     * Recalcula lo que se deriva del snapshot y de las imágenes vigiladas, conservando tal cual
     * llega en [current] lo que el usuario tiene puesto: el filtro. El resaltado y las novedades
     * no viven en [current] -los lleva `timers`- pero sí entran en cada fila a través de
     * [emphasisFor].
     */
    private fun derive(current: ImagesUiState): ImagesUiState {
        val byName = snapshot.images.associateBy { it.name }
        val nombres = trackedImages.findAll()
        val silenciadas = silencedImages.findAll()
        // Una sola lectura para toda la lista: si se leyera `timers.value` fila a fila, una
        // actualizacion a mitad de la iteracion podria dejar algunas filas viendo el resaltado
        // viejo y otras el nuevo dentro del mismo `derive()`.
        val timerState = timers.value
        val all = nombres.map { toRow(it, byName[it], timerState, silenciadas) }
        val term = current.search.trim().lowercase()
        // "Todo falla" se decide entre las imagenes que el ultimo ciclo llego a consultar. Una
        // recien dada de alta todavia no esta en el snapshot: contandola, darla de alta durante
        // una caida retraia el aviso y devolvia la bandeja a su icono normal con el origen aun
        // caido, y al ciclo siguiente habia transicion otra vez y el error sonaba por segunda vez
        // para la misma caida. El criterio no es el estado UNKNOWN, que tambien lo tiene una
        // imagen consultada sin linea base con la que comparar, sino estar o no en el snapshot.
        val consultadas = nombres.mapNotNull { byName[it] }
        val pending = all.count { it.status == ImageStatus.PENDING }
        val errorCount = all.count { it.status == ImageStatus.ERROR }
        // Se filtra aqui y no al guardar en `timers`: una imagen encolada que se elimina, falla o
        // se reconoce por otra via deja de contar sola, sin que nada tenga que ir a limpiar el
        // conjunto. Solo cuentan las que siguen pendientes de verdad.
        val encoladas = timerState.queued.filter { byName[it]?.status == ImageStatus.PENDING }.toSet()
        val visibles = all.filterNot { it.name in encoladas }
        return current.copy(
            rows = if (term.isEmpty()) visibles else visibles.filter { it.name.lowercase().contains(term) },
            total = all.size,
            pending = pending,
            errorCount = errorCount,
            canAcknowledgeAll = pending > 0,
            polling = controller.status() == PollingController.Status.RUNNING,
            pollIntervalSeconds = controller.interval().inWholeSeconds,
            allFailing = consultadas.isNotEmpty() && consultadas.all { it.status == ImageStatus.ERROR },
            lastSuccessLabel = lastSuccessLabel(current.lastSuccessAt, Clock.System.now()),
            queuedCount = encoladas.size,
            trace = timerState.trace,
        )
    }

    /**
     * Qué le pasó a esta fila, no cómo se pinta. `SENALADA` gana sobre `NOVEDAD` cuando
     * coinciden: el resaltado responde a un clic del usuario, que pesa mas que una novedad que
     * llego sola.
     */
    private fun emphasisFor(name: String, timerState: TimerState): RowEmphasis = when {
        name == timerState.highlighted -> RowEmphasis.SENALADA
        name in timerState.bumped -> RowEmphasis.NOVEDAD
        else -> RowEmphasis.NINGUNO
    }

    private fun toRow(
        name: String,
        image: ImageState?,
        timerState: TimerState,
        silenciadas: Set<String>,
    ): ImageRowState {
        val muted = name in silenciadas
        val pendingUndo = name in timerState.pendingUndo
        val checking = name in timerState.checking
        if (image == null) {
            return ImageRowState(
                name = name,
                registry = ABSENT,
                local = ABSENT,
                remote = ABSENT,
                status = ImageStatus.UNKNOWN,
                emphasis = emphasisFor(name, timerState),
                detail = "Sin verificar todavía",
                muted = muted,
                pendingUndo = pendingUndo,
                checking = checking,
            )
        }
        val detail = image.error ?: ("Verificado " + formatWhen(image.lastCheckedAt))
        return ImageRowState(
            name = name,
            registry = image.registry,
            local = image.local?.value ?: ABSENT,
            remote = image.remote?.value ?: ABSENT,
            status = image.status,
            emphasis = emphasisFor(name, timerState),
            detail = detail,
            error = image.error,
            muted = muted,
            pendingUndo = pendingUndo,
            checking = checking,
            age = relativeAge(image.lastCheckedAt, Clock.System.now()),
        )
    }

    /**
     * Imágenes que acaban de pasar a pendientes: no lo estaban en el ciclo anterior, o no
     * existían. El primer ciclo de la sesión no encola nada -mismo criterio que
     * `VersionPollingService.notifyTransitions`-: abrir la aplicación debe enseñar el estado
     * pendiente que ya había, no encolarlo detrás de una banda que nadie pidió.
     */
    private fun newlyPending(previous: PollSnapshot, current: PollSnapshot): Set<String> {
        if (previous === PollSnapshot.EMPTY) {
            return emptySet()
        }
        return current.images
            .filter { it.status == ImageStatus.PENDING }
            .filter { image ->
                previous.find(image.name)?.status != ImageStatus.PENDING
            }
            .map { it.name }
            .toSet()
    }
}
