package io.github.shizukajiku.imagewatch.application

import io.github.shizukajiku.imagewatch.Log
import io.github.shizukajiku.imagewatch.domain.ImageRelease
import io.github.shizukajiku.imagewatch.domain.ImageState
import io.github.shizukajiku.imagewatch.domain.ImageStatus
import io.github.shizukajiku.imagewatch.domain.PollSnapshot
import io.github.shizukajiku.imagewatch.domain.Version
import kotlin.concurrent.Volatile
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Imágenes pendientes de las que hay algo nuevo que contar: o no estaban pendientes en el ciclo
 * anterior, o lo estaban con una versión remota distinta de la de ahora. Una imagen ausente del
 * ciclo anterior cuenta como novedad.
 *
 * Comparar la versión y no solo el estado es lo que hace que una segunda publicación sobre una
 * pendiente sin reconocer vuelva a avisar. Comparar *solo* el estado repetiría el aviso en cada
 * ciclo, que es justo lo que esto evita.
 *
 * Vive fuera de [VersionPollingService] porque tiene dos consumidores y la respuesta tiene que ser
 * la misma para ambos: el servicio decide con ella **qué genera aviso y qué se registra**, y la
 * lista de la interfaz **qué filas laten**. Mientras estuvo escrita dos veces, afinar una copia y
 * olvidar la otra dejaba un aviso sin su resaltado —o al revés— sin que ningún test dijera nada.
 */
fun pendingNews(previous: PollSnapshot, current: PollSnapshot): List<ImageState> = current.images
    .filter { it.status == ImageStatus.PENDING }
    .filter { image ->
        val before = previous.find(image.name)
        before == null || before.status != ImageStatus.PENDING || before.remote != image.remote
    }

class VersionPollingService(
    private val source: ImageSource,
    private val state: ImageStateStore,
    notifiers: List<NotificationPort>,
    private val trackedImages: TrackedImageStore,
    private val clock: Clock = Clock.System,
) {
    private val notifiers: List<NotificationPort> = notifiers.toList()

    @Volatile
    private var lastSnapshot: PollSnapshot = PollSnapshot.EMPTY

    @Volatile
    private var latestReleases: Map<String, ImageRelease> = emptyMap()

    // La lista se reemplaza entera en lugar de mutarse, y se lee a través de un campo volátil: los
    // oyentes se registran desde el hilo de la interfaz mientras el planificador itera sobre ellos.
    // Es la misma garantía que daba CopyOnWriteArrayList.
    @Volatile
    private var listeners: List<PollListener> = emptyList()

    suspend fun poll() {
        publishStart()
        val at = clock.now()
        val previous = lastSnapshot
        val results = source.findByNames(trackedImages.findAll())
        commit(results, results.map { toState(it, at) }, at, previous)
    }

    /**
     * Consulta una sola imagen, dejando intacto lo que se sabe de las demás. Es lo que hace el
     * botón de refrescar de una fila: esperar al siguiente ciclo para comprobar una imagen concreta
     * obliga a esperar por todas.
     */
    suspend fun pollOne(name: String) {
        publishStart()
        val at = clock.now()
        val previous = lastSnapshot
        val results = source.findByNames(listOf(name))
        val byName = LinkedHashMap<String, ImageState>()
        previous.images.forEach { byName[it.name] = it }
        results.map { toState(it, at) }.forEach { byName[it.name] = it }
        // Se reordena como la lista de vigiladas: sin esto, refrescar una fila la mandaría al final
        // de la tabla y el usuario perdería de vista la que acaba de pulsar.
        val states = trackedImages.findAll().mapNotNull { byName[it] }
        commit(results, states, at, previous)
    }

    /**
     * Publica el resultado de una consulta, sea de todas las imágenes o de una sola. Lo comparten
     * los dos caminos para que refrescar una fila avise, persista y registre igual que un ciclo
     * entero: duplicar esta secuencia significaría que un aviso llega por un camino y no por el
     * otro.
     */
    private fun commit(results: List<ImageResult>, states: List<ImageState>, at: Instant, previous: PollSnapshot) {
        val snapshot = PollSnapshot(states, at)
        lastSnapshot = snapshot
        // Lo que acknowledge() necesita guardar: la publicación completa, no solo la versión. Se
        // fusiona en lugar de reemplazar, porque una consulta de una sola imagen no puede borrar lo
        // que se sabe de las demás.
        latestReleases = latestReleases + results.mapNotNull { it.release }.associateBy { it.name }
        notifyTransitions(previous, snapshot)
        persist(results)
        logChanges(previous, snapshot)
        publish(snapshot)
    }

    /**
     * Da por vistas todas las imágenes que ahora mismo tienen una versión pendiente, en un solo
     * gesto y con una sola publicación. Reconocerlas una a una funcionaría, pero repintaría la
     * tabla y sonaría una vez por imagen.
     */
    fun acknowledgeAll() {
        val releases = lastSnapshot.pending().mapNotNull { latestReleases[it.name] }
        if (releases.isEmpty()) {
            return
        }
        state.save(releases)
        releases.forEach { log.info("${it.name} marcada como vista en ${it.reference}") }

        val at = clock.now()
        val reconocidas = releases.map { it.name }.toSet()
        val refreshed =
            lastSnapshot.images.map { image ->
                val release = latestReleases[image.name]
                if (image.name in reconocidas && release != null) {
                    toState(ImageResult.found(release), at)
                } else {
                    image
                }
            }
        lastSnapshot = PollSnapshot(refreshed, at)
        publish(lastSnapshot)
    }

    /**
     * Notifica las imágenes con una versión pendiente que el ciclo anterior no anunciaba: las que
     * acaban de pasar a pendientes y las que ya lo estaban pero han recibido una versión remota más
     * nueva todavía. Notificar todas las pendientes en cada ciclo produciría un aviso por
     * intervalo, indefinidamente, por la misma versión.
     *
     * El primer ciclo de la sesión no avisa. El estado pendiente persiste hasta que el usuario lo
     * reconoce y la ventana ya lo muestra al abrirse; avisar al arrancar repetiría cada día lo que
     * el usuario decidió posponer.
     */
    private fun notifyTransitions(previous: PollSnapshot, current: PollSnapshot) {
        if (previous === PollSnapshot.EMPTY) {
            return
        }
        val newlyPending = pendingNews(previous, current)
        if (newlyPending.isEmpty()) {
            return
        }
        // Igual que publish/publishStart: un notificador que lanza no puede tumbar el ciclo y dejar
        // a publish(snapshot) sin ejecutarse, o el pulso de la interfaz se quedaría encendido para
        // siempre.
        notifiers.forEach { notifier ->
            try {
                notifier.notifyUpdates(newlyPending)
            } catch (e: RuntimeException) {
                log.warn("Un notificador falló al recibir las transiciones", e)
            }
        }
    }

    /**
     * Imágenes que *acaban de* entrar en `status`: lo tienen ahora y no lo tenían en el ciclo
     * anterior. Una imagen ausente del anterior cuenta como transición.
     */
    private fun transitionedInto(
        previous: PollSnapshot,
        current: PollSnapshot,
        status: ImageStatus,
    ): List<ImageState> = current.images
        .filter { it.status == status }
        .filter { image ->
            val before = previous.find(image.name)
            before == null || before.status != status
        }

    /**
     * Deja en el registro lo que *cambió*, no que el ciclo ocurrió. Un log que escribe una línea
     * por ciclo se vuelve ilegible; uno que no escribe nada cuando una imagen empieza a fallar no
     * sirve para diagnosticar.
     */
    private fun logChanges(previous: PollSnapshot, current: PollSnapshot) {
        pendingNews(previous, current).forEach { image ->
            log.info(
                "Nueva versión de ${image.name}: " +
                    "${image.local?.value ?: "?"} -> ${image.remote?.value ?: "?"}",
            )
        }
        transitionedInto(previous, current, ImageStatus.ERROR).forEach { image ->
            log.warn("No se pudo verificar ${image.name}: ${image.error ?: "sin detalle"}")
        }
        // No se registra la transición a OK: en el segundo ciclo todas las imágenes pasan de
        // UNKNOWN a OK al establecerse su línea base, y eso llenaría el log en cada arranque sin
        // aportar nada. Lo que sí importa —que el usuario dio algo por visto— se registra en
        // acknowledge().
        log.debug(
            "Ciclo completado: ${current.images.size} imágenes, ${current.pending().size} pendientes",
        )
    }

    fun addListener(listener: PollListener) {
        listeners = listeners + listener
    }

    fun removeListener(listener: PollListener) {
        listeners = listeners - listener
    }

    /** Igual que [publish]: un oyente que lanza no puede dejar a los demás sin aviso. */
    private fun publishStart() {
        listeners.forEach { listener ->
            try {
                listener.onPollStarted()
            } catch (e: RuntimeException) {
                log.warn("Un oyente falló al recibir el comienzo del ciclo", e)
            }
        }
    }

    /**
     * Entrega el snapshot a cada oyente. Un oyente que lanza no impide que los demás reciban: la
     * interfaz va a ser uno de ellos y no puede dejar al núcleo sin publicar.
     */
    private fun publish(snapshot: PollSnapshot) {
        listeners.forEach { listener ->
            try {
                listener.onSnapshot(snapshot)
            } catch (e: RuntimeException) {
                log.warn("Un oyente falló al recibir el snapshot", e)
            }
        }
    }

    /** Estado de todas las imágenes vigiladas tras el último ciclo. */
    fun lastSnapshot(): PollSnapshot = lastSnapshot

    /**
     * Da por vista la publicación que el origen anuncia ahora mismo para `name`. Es un no-op si la
     * imagen no apareció en el último ciclo.
     */
    fun acknowledge(name: String) {
        val release = latestReleases[name] ?: return
        state.save(listOf(release))
        log.info("$name marcada como vista en ${release.reference}")

        // El snapshot vigente sigue diciendo PENDING. Sin recalcularlo aquí, la interfaz muestra el
        // estado antiguo hasta el siguiente ciclo —hasta cinco minutos con el intervalo por
        // defecto—, y el usuario vuelve a pulsar creyendo que no funcionó.
        val at = clock.now()
        val refreshed =
            lastSnapshot.images.map { image ->
                if (image.name == name) toState(ImageResult.found(release), at) else image
            }
        val snapshot = PollSnapshot(refreshed, at)
        lastSnapshot = snapshot
        publish(snapshot)
    }

    /**
     * Traslada a `current` lo que el usuario ya había dado por visto de `previous`, y refresca el
     * snapshot para que la fila no pierda su versión mientras llega el siguiente ciclo.
     */
    fun renameImage(previous: String, current: String) {
        state.rename(previous, current)
        val at = clock.now()
        val refreshed =
            lastSnapshot.images.map { image ->
                if (image.name == previous) image.copy(name = current) else image
            }
        lastSnapshot = PollSnapshot(refreshed, at)
        publish(lastSnapshot)
    }

    /**
     * Registra la línea base de las imágenes nunca vistas, y solo esas. Guardar también las ya
     * conocidas reconocería automáticamente la versión recién detectada, y el estado pendiente
     * duraría un único ciclo.
     *
     * La línea base evita el efecto contrario: sin ella, el primer arranque avisaría de cada imagen
     * vigilada.
     */
    private fun persist(results: List<ImageResult>) {
        val unseen = results.mapNotNull { it.release }.filter { state.find(it.name) == null }
        if (unseen.isNotEmpty()) {
            state.save(unseen)
        }
    }

    /**
     * Traduce el resultado de una imagen a su estado. Ni un fallo de consulta, ni una referencia
     * mal formada, ni una versión no parseable interrumpen el ciclo: cada uno produce el estado
     * `ERROR` de su propia imagen.
     */
    private fun toState(result: ImageResult, at: Instant): ImageState {
        if (result.error != null) {
            return failedKeepingKnownLocal(result.name, result.error, at)
        }
        val release =
            result.release
                ?: return failedKeepingKnownLocal(result.name, "Resultado sin publicación", at)
        val remote =
            parse(release.reference)
                ?: return failedKeepingKnownLocal(
                    result.name,
                    "Referencia no reconocida: ${release.reference}",
                    at,
                )
        val local = state.find(result.name)?.let { parse(it.reference) }
        return ImageState(
            name = result.name,
            local = local,
            remote = remote,
            registry = registryOf(release.reference),
            status = if (local == null) ImageStatus.UNKNOWN else statusOf(local, remote),
            error = null,
            lastCheckedAt = at,
        )
    }

    /**
     * Un fallo de consulta no borra lo último que se sabía de la imagen: la fila la sigue
     * enseñando, atenuada, en vez de quedarse en blanco.
     */
    private fun failedKeepingKnownLocal(name: String, message: String, at: Instant): ImageState {
        val known = state.find(name)
        return ImageState.failed(
            name = name,
            local = known?.let { parse(it.reference) },
            registry = known?.let { registryOf(it.reference) },
            message = message,
            at = at,
        )
    }

    private fun statusOf(local: Version, remote: Version): ImageStatus =
        if (remote > local) ImageStatus.PENDING else ImageStatus.OK

    private fun parse(reference: String): Version? {
        val match = REFERENCE.matchEntire(reference) ?: return null
        return try {
            Version(match.groupValues[2])
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    private fun registryOf(reference: String): String {
        val index = reference.lastIndexOf(':')
        return if (index > 0) reference.substring(0, index) else reference
    }

    private companion object {
        private val log = Log("io.github.shizukajiku.imagewatch.application.VersionPollingService")

        private val REFERENCE = Regex("([^/]+/[^:]+):(.+)")
    }
}
