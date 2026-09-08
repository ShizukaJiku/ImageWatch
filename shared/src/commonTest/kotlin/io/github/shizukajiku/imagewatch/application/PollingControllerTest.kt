package io.github.shizukajiku.imagewatch.application

import io.github.shizukajiku.imagewatch.domain.ImageRelease
import io.github.shizukajiku.imagewatch.domain.ImageStatus
import io.github.shizukajiku.imagewatch.domain.PollSnapshot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Los tests de ciclo de vida construían el controlador con un servicio `null`, que Java permitía
 * porque nunca lo llegaban a usar. Aquí reciben un servicio real sobre almacenes vacíos: cuesta
 * una línea y deja de depender de que nadie toque esa referencia.
 *
 * Las esperas usan [withTimeout] sobre [Dispatchers.Default] en lugar de un `CountDownLatch`. Al
 * salir del despachador de prueba el tiempo vuelve a ser real, que es lo que hace falta para
 * esperar a un trabajo que corre en segundo plano.
 */
class PollingControllerTest {
    @Test
    fun startsStopped() {
        PollingController(idleService(), 30.seconds).use { controller ->
            assertEquals(PollingController.Status.STOPPED, controller.status())
            assertEquals(30.seconds, controller.interval())
        }
    }

    @Test
    fun rejectsIntervalsBelowOneSecond() {
        assertFailsWith<IllegalArgumentException> { PollingController(idleService(), 500.milliseconds) }
        assertFailsWith<IllegalArgumentException> { PollingController(idleService(), 0.seconds) }
        assertFailsWith<IllegalArgumentException> { PollingController(idleService(), (-1).seconds) }
    }

    @Test
    fun updatingTheIntervalWhileStoppedKeepsItStopped() {
        PollingController(idleService(), 30.seconds).use { controller ->
            controller.updateInterval(5.seconds)

            assertEquals(5.seconds, controller.interval())
            assertEquals(PollingController.Status.STOPPED, controller.status())
        }
    }

    @Test
    fun rejectsAnInvalidIntervalUpdateAndKeepsThePreviousOne() {
        PollingController(idleService(), 30.seconds).use { controller ->
            assertFailsWith<IllegalArgumentException> { controller.updateInterval(0.seconds) }

            assertEquals(30.seconds, controller.interval())
        }
    }

    @Test
    fun stopIsIdempotent() {
        PollingController(idleService(), 30.seconds).use { controller ->
            controller.stop()
            controller.stop()

            assertEquals(PollingController.Status.STOPPED, controller.status())
        }
    }

    @Test
    fun startAfterCloseIsIgnored() {
        val controller = PollingController(idleService(), 30.seconds)
        controller.close()

        controller.start()

        assertEquals(PollingController.Status.STOPPED, controller.status())
    }

    @Test
    fun closeIsIdempotent() {
        val controller = PollingController(idleService(), 30.seconds)

        controller.close()
        controller.close()

        assertEquals(PollingController.Status.STOPPED, controller.status())
    }

    @Test
    fun refreshNowWithANameQueriesOnlyThatImageAndKeepsTheOthersInPlace() = runTest {
        val names = FakeTrackedImageStore(listOf("alpha", "beta"))
        val state = FakeImageStateStore()
        state.seed("alpha", "registry.local/alpha:1.0.0")
        state.seed("beta", "registry.local/beta:1.0.0")
        val source =
            RecordingImageSource(
                mutableMapOf(
                    "alpha" to "registry.local/alpha:1.0.0",
                    "beta" to "registry.local/beta:1.0.0",
                ),
            )
        val service = VersionPollingService(source, state, emptyList(), names)
        // Línea base: las dos al día, antes de que empiece a contar refreshNow.
        service.poll()
        source.setReference("alpha", "registry.local/alpha:2.0.0")
        val published = CompletableDeferred<Unit>()
        service.addListener(
            object : PollListener {
                override fun onSnapshot(snapshot: PollSnapshot) {
                    published.complete(Unit)
                }
            },
        )

        PollingController(service, 30.seconds).use { controller ->
            // El sondeo funciona igual detenido o en marcha: refrescar una fila no depende de eso.
            controller.refreshNow("alpha")
            withContext(Dispatchers.Default) { withTimeout(2.seconds) { published.await() } }
        }

        // Solo se pidió "alpha": la consulta de una fila no arrastra a las demás.
        assertEquals(listOf(listOf("alpha", "beta"), listOf("alpha")), source.requestedNames)
        // El orden de la tabla no cambia: refrescar no manda la fila al final.
        assertEquals(listOf("alpha", "beta"), service.lastSnapshot().images.map { it.name })
        assertEquals(ImageStatus.PENDING, service.lastSnapshot().find("alpha")?.status)
    }

    @Test
    fun refreshNowWithoutANameQueriesEverythingImmediatelyWithoutTouchingTheSchedule() = runTest {
        val names = FakeTrackedImageStore(listOf("alpha", "beta"))
        val source =
            RecordingImageSource(
                mutableMapOf(
                    "alpha" to "registry.local/alpha:1.0.0",
                    "beta" to "registry.local/beta:1.0.0",
                ),
            )
        val service = VersionPollingService(source, FakeImageStateStore(), emptyList(), names)
        val published = CompletableDeferred<Unit>()
        service.addListener(
            object : PollListener {
                override fun onSnapshot(snapshot: PollSnapshot) {
                    published.complete(Unit)
                }
            },
        )

        PollingController(service, 30.seconds).use { controller ->
            // El sondeo sigue detenido: «comprobar todo ya» no necesita que el calendario esté en
            // marcha.
            controller.refreshNow(null)
            withContext(Dispatchers.Default) { withTimeout(2.seconds) { published.await() } }

            assertEquals(listOf(listOf("alpha", "beta")), source.requestedNames)
            assertEquals(PollingController.Status.STOPPED, controller.status())
        }
    }

    private fun idleService() = VersionPollingService(
        RecordingImageSource(mutableMapOf()),
        FakeImageStateStore(),
        emptyList(),
        FakeTrackedImageStore(emptyList()),
    )

    private class FakeTrackedImageStore(private var names: List<String>) : TrackedImageStore {
        override fun findAll(): List<String> = names

        override fun save(names: List<String>) {
            this.names = names
        }
    }

    private class FakeImageStateStore : ImageStateStore {
        private val byName = mutableMapOf<String, ImageRelease>()

        fun seed(name: String, reference: String) {
            byName[name] = ImageRelease(name, reference, now())
        }

        override fun find(name: String): ImageRelease? = byName[name]

        override fun save(releases: List<ImageRelease>) {
            releases.forEach { byName[it.name] = it }
        }

        override fun rename(previous: String, current: String) {
            val known = byName.remove(previous) ?: return
            byName[current] = known.copy(name = current)
        }
    }

    /** Registra, en orden, con qué nombres se llamó a `findByNames`. */
    private class RecordingImageSource(private val referenceByName: MutableMap<String, String>) : ImageSource {
        private val requested = mutableListOf<List<String>>()

        val requestedNames: List<List<String>> get() = requested

        fun setReference(name: String, reference: String) {
            referenceByName[name] = reference
        }

        override suspend fun findByNames(names: List<String>): List<ImageResult> {
            requested.add(names.toList())
            return names.map { name ->
                ImageResult.found(ImageRelease(name, referenceByName.getValue(name), now()))
            }
        }
    }

    private companion object {
        private fun now() = Clock.System.now().toLocalDateTime(TimeZone.UTC)
    }
}
