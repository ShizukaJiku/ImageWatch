package io.github.shizukajiku.imagewatch.application

import io.github.shizukajiku.imagewatch.domain.ImageRelease
import io.github.shizukajiku.imagewatch.domain.ImageState
import io.github.shizukajiku.imagewatch.domain.ImageStatus
import io.github.shizukajiku.imagewatch.domain.PollSnapshot
import io.github.shizukajiku.imagewatch.domain.Version
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class VersionPollingServiceTest {
    @Test
    fun theSnapshotKeepsEveryTrackedImageEvenWhenUpToDate() = runTest {
        val state = FakeImageStateStore()
        state.seed("beta", "registry.local/beta:21.0.4")
        val source =
            FakeImageSource(
                mutableMapOf(
                    "alpha" to "registry.local/alpha:1.28.3",
                    "beta" to "registry.local/beta:21.0.4",
                ),
            )
        val service = service(source, state, names = listOf("alpha", "beta"))

        service.poll()

        assertEquals(
            setOf("alpha", "beta"),
            service
                .lastSnapshot()
                .images
                .map { it.name }
                .toSet(),
        )
    }

    @Test
    fun oneFailingImageDoesNotPreventTheOthersFromUpdating() = runTest {
        val state = FakeImageStateStore()
        state.seed("alpha", "registry.local/alpha:1.0.0")
        state.seed("beta", "registry.local/beta:1.0.0")
        val source = FakeImageSource(mutableMapOf("beta" to "registry.local/beta:2.0.0"))
        source.failOn("alpha", "HTTP 503")
        val service = service(source, state, names = listOf("alpha", "beta"))

        service.poll()

        val alpha = assertNotNull(service.lastSnapshot().find("alpha"))
        assertEquals(ImageStatus.ERROR, alpha.status)
        assertTrue("HTTP 503" in alpha.error!!)
        // Un fallo de consulta no borra lo que ya se sabía de la imagen.
        assertEquals(Version("1.0.0"), alpha.local)
        assertEquals("registry.local/alpha", alpha.registry)
        assertEquals(ImageStatus.PENDING, service.lastSnapshot().find("beta")?.status)
    }

    @Test
    fun anUnparseableReferenceBecomesAnErrorForThatImageOnly() = runTest {
        val state = FakeImageStateStore()
        state.seed("alpha", "registry.local/alpha:1.0.0")
        val source =
            FakeImageSource(
                mutableMapOf(
                    "alpha" to "esto-no-es-una-referencia",
                    "beta" to "registry.local/beta:1.0.0",
                ),
            )
        val service = service(source, state, names = listOf("alpha", "beta"))

        service.poll()

        val alpha = assertNotNull(service.lastSnapshot().find("alpha"))
        assertEquals(ImageStatus.ERROR, alpha.status)
        // Igual que un fallo de consulta: una referencia mal formada tampoco borra la versión ni el
        // registro que ya se conocían.
        assertEquals(Version("1.0.0"), alpha.local)
        assertEquals("registry.local/alpha", alpha.registry)
        assertNotEquals(ImageStatus.ERROR, service.lastSnapshot().find("beta")?.status)
    }

    @Test
    fun anImageWithoutAKnownVersionIsUnknownRatherThanPending() = runTest {
        val source = FakeImageSource(mutableMapOf("alpha" to "registry.local/alpha:1.0.0"))
        val service = service(source, FakeImageStateStore(), names = listOf("alpha"))

        service.poll()

        assertEquals(ImageStatus.UNKNOWN, service.lastSnapshot().find("alpha")?.status)
    }

    @Test
    fun notifiesOnlyImagesThatBecamePending() = runTest {
        val state = FakeImageStateStore()
        state.seed("alpha", "registry.local/alpha:1.0.0")
        state.seed("beta", "registry.local/beta:2.0.0")
        // Ambas arrancan al día: así el aviso del segundo ciclo es una transición real y no el
        // arrastre de un estado que ya venía pendiente.
        val source =
            FakeImageSource(
                mutableMapOf(
                    "alpha" to "registry.local/alpha:1.0.0",
                    "beta" to "registry.local/beta:2.0.0",
                ),
            )
        val notifications = RecordingNotificationPort()
        val service = service(source, state, listOf(notifications), listOf("alpha", "beta"))

        service.poll() // línea base de la sesión: no avisa
        source.setReference("alpha", "registry.local/alpha:2.0.0")

        service.poll()

        assertEquals(listOf("alpha"), notifications.received.map { it.name })
    }

    @Test
    fun unNotificadorQueLanzaNoImpideQueElCicloPubliqueElSnapshot() = runTest {
        val state = FakeImageStateStore()
        state.seed("alpha", "registry.local/alpha:1.0.0")
        val source = FakeImageSource(mutableMapOf("alpha" to "registry.local/alpha:1.0.0"))
        val brokenNotifier =
            object : NotificationPort {
                override fun notifyUpdates(updates: List<ImageState>) = throw IllegalStateException("notificador roto")
            }
        val service = service(source, state, listOf(brokenNotifier), listOf("alpha"))
        val received = RecordingListener()
        service.addListener(received)

        service.poll() // línea base: alpha al día, sin transición todavía
        source.setReference("alpha", "registry.local/alpha:2.0.0")
        service.poll() // alpha pasa a PENDING: el notificador roto no debe tumbar el ciclo

        assertEquals(2, received.snapshots.size)
        assertEquals(
            ImageStatus.PENDING,
            received.snapshots
                .last()
                .find("alpha")
                ?.status,
        )
    }

    @Test
    fun theFirstPollOfASessionDoesNotNotify() = runTest {
        val state = FakeImageStateStore()
        state.seed("alpha", "registry.local/alpha:1.0.0")
        val source = FakeImageSource(mutableMapOf("alpha" to "registry.local/alpha:2.0.0"))
        val notifications = RecordingNotificationPort()
        val service = service(source, state, listOf(notifications), listOf("alpha"))

        service.poll()

        // Las dos mitades importan: no avisa, pero el estado sí queda pendiente. Sin la segunda
        // aserción el test pasaría también si se rompiera la detección entera.
        assertTrue(notifications.received.isEmpty())
        assertEquals(ImageStatus.PENDING, service.lastSnapshot().find("alpha")?.status)
    }

    @Test
    fun doesNotNotifyTheSamePendingImageTwice() = runTest {
        val state = FakeImageStateStore()
        state.seed("alpha", "registry.local/alpha:1.0.0")
        val source = FakeImageSource(mutableMapOf("alpha" to "registry.local/alpha:2.0.0"))
        val notifications = RecordingNotificationPort()
        val service = service(source, state, listOf(notifications), listOf("alpha"))

        service.poll()
        notifications.received.clear()
        service.poll()

        assertTrue(notifications.received.isEmpty())
    }

    @Test
    fun notifiesAgainWhenAnImageBecomesPendingOnceMore() = runTest {
        val state = FakeImageStateStore()
        state.seed("alpha", "registry.local/alpha:1.0.0")
        val source = FakeImageSource(mutableMapOf("alpha" to "registry.local/alpha:2.0.0"))
        val notifications = RecordingNotificationPort()
        val service = service(source, state, listOf(notifications), listOf("alpha"))

        service.poll()
        notifications.received.clear()

        // La imagen se pone al día y después el origen publica una versión más nueva.
        state.seed("alpha", "registry.local/alpha:2.0.0")
        service.poll()
        source.setReference("alpha", "registry.local/alpha:3.0.0")
        service.poll()

        assertEquals(listOf("alpha"), notifications.received.map { it.name })
    }

    @Test
    fun avisaOtraVezCuandoLlegaUnaVersionMasNuevaSobreUnaPendienteSinReconocer() = runTest {
        val state = FakeImageStateStore()
        state.seed("alpha", "registry.local/alpha:1.0.0")
        val source = FakeImageSource(mutableMapOf("alpha" to "registry.local/alpha:2.0.0"))
        val notifications = RecordingNotificationPort()
        val service = service(source, state, listOf(notifications), listOf("alpha"))

        service.poll() // alpha pasa a pendiente en 2.0.0
        notifications.received.clear()
        // El usuario no la ha reconocido: sigue pendiente cuando el origen publica la siguiente.
        source.setReference("alpha", "registry.local/alpha:3.0.0")
        service.poll()

        assertEquals(1, notifications.received.size)
        val notified = notifications.received.single()
        assertEquals("alpha", notified.name)
        assertEquals(Version("3.0.0"), notified.remote)
    }

    @Test
    fun unaPendienteConLaMismaVersionRemotaNoVuelveAAvisar() = runTest {
        val state = FakeImageStateStore()
        state.seed("alpha", "registry.local/alpha:1.0.0")
        val source = FakeImageSource(mutableMapOf("alpha" to "registry.local/alpha:2.0.0"))
        val notifications = RecordingNotificationPort()
        val service = service(source, state, listOf(notifications), listOf("alpha"))

        service.poll()
        notifications.received.clear()
        service.poll()
        service.poll()

        assertTrue(notifications.received.isEmpty())
    }

    @Test
    fun anImageThatFailsIsNotNotifiedAsAnUpdate() = runTest {
        val source = FakeImageSource(mutableMapOf())
        source.failOn("alpha", "HTTP 503")
        val notifications = RecordingNotificationPort()
        val service = service(source, FakeImageStateStore(), listOf(notifications), listOf("alpha"))

        service.poll()

        assertTrue(notifications.received.isEmpty())
    }

    @Test
    fun aPendingImageStaysPendingAcrossPolls() = runTest {
        val state = FakeImageStateStore()
        state.seed("alpha", "registry.local/alpha:1.0.0")
        val source = FakeImageSource(mutableMapOf("alpha" to "registry.local/alpha:2.0.0"))
        val service = service(source, state, names = listOf("alpha"))

        service.poll()
        service.poll()
        service.poll()

        assertEquals(ImageStatus.PENDING, service.lastSnapshot().find("alpha")?.status)
    }

    @Test
    fun theFirstSightingOfAnImageIsRecordedAsAcknowledged() = runTest {
        val state = FakeImageStateStore()
        val source = FakeImageSource(mutableMapOf("alpha" to "registry.local/alpha:1.0.0"))
        val notifications = RecordingNotificationPort()
        val service = service(source, state, listOf(notifications), listOf("alpha"))

        service.poll()

        assertNotNull(state.find("alpha"))
        assertTrue(notifications.received.isEmpty())
    }

    @Test
    fun acknowledgingAnImageClearsItsPendingStatus() = runTest {
        val state = FakeImageStateStore()
        state.seed("alpha", "registry.local/alpha:1.0.0")
        val source = FakeImageSource(mutableMapOf("alpha" to "registry.local/alpha:2.0.0"))
        val service = service(source, state, names = listOf("alpha"))
        service.poll()

        service.acknowledge("alpha")
        service.poll()

        assertEquals(ImageStatus.OK, service.lastSnapshot().find("alpha")?.status)
    }

    @Test
    fun acknowledgingAnImageThatWasNeverSeenIsANoOp() {
        val state = FakeImageStateStore()
        val source = FakeImageSource(mutableMapOf("alpha" to "registry.local/alpha:1.0.0"))
        val service = service(source, state, names = listOf("alpha"))

        service.acknowledge("ausente")

        assertNull(state.find("ausente"))
    }

    @Test
    fun publishesTheSnapshotToRegisteredListeners() = runTest {
        val source = FakeImageSource(mutableMapOf("alpha" to "registry.local/alpha:1.0.0"))
        val service = service(source, FakeImageStateStore(), names = listOf("alpha"))
        val received = RecordingListener()
        service.addListener(received)

        service.poll()

        assertEquals(1, received.snapshots.size)
        assertNotNull(received.snapshots.first().find("alpha"))
    }

    @Test
    fun aRemovedListenerStopsReceivingSnapshots() = runTest {
        val source = FakeImageSource(mutableMapOf("alpha" to "registry.local/alpha:1.0.0"))
        val service = service(source, FakeImageStateStore(), names = listOf("alpha"))
        val received = RecordingListener()
        service.addListener(received)

        service.poll()
        service.removeListener(received)
        service.poll()

        assertEquals(1, received.snapshots.size)
    }

    @Test
    fun aFailingListenerDoesNotPreventTheOthersFromReceiving() = runTest {
        val source = FakeImageSource(mutableMapOf("alpha" to "registry.local/alpha:1.0.0"))
        val service = service(source, FakeImageStateStore(), names = listOf("alpha"))
        val received = RecordingListener()
        service.addListener(
            object : PollListener {
                override fun onSnapshot(snapshot: PollSnapshot) = throw IllegalStateException("oyente roto")
            },
        )
        service.addListener(received)

        service.poll()

        assertEquals(1, received.snapshots.size)
    }

    @Test
    fun acknowledgingPublishesTheUpdatedStateWithoutWaitingForTheNextPoll() = runTest {
        val state = FakeImageStateStore()
        state.seed("alpha", "registry.local/alpha:1.0.0")
        val source = FakeImageSource(mutableMapOf("alpha" to "registry.local/alpha:2.0.0"))
        val service = service(source, state, names = listOf("alpha"))
        val received = RecordingListener()
        service.poll()
        service.addListener(received)

        service.acknowledge("alpha")

        // Sin republicar, la interfaz mostraría PENDING hasta el siguiente ciclo.
        assertEquals(1, received.snapshots.size)
        assertEquals(
            ImageStatus.OK,
            received.snapshots
                .first()
                .find("alpha")
                ?.status,
        )
        assertEquals(ImageStatus.OK, service.lastSnapshot().find("alpha")?.status)
    }

    @Test
    fun elOyenteSeEnteraDeQueElCicloEmpiezaAntesDeRecibirElResultado() = runTest {
        val source = FakeImageSource(mutableMapOf("alpha" to "registry.local/alpha:1.0.0"))
        val service = service(source, FakeImageStateStore(), names = listOf("alpha"))
        val orden = mutableListOf<String>()
        service.addListener(
            object : PollListener {
                override fun onPollStarted() {
                    orden.add("inicio")
                }

                override fun onSnapshot(snapshot: PollSnapshot) {
                    orden.add("resultado")
                }
            },
        )

        service.poll()

        assertEquals(listOf("inicio", "resultado"), orden)
    }

    @Test
    fun acknowledgingOneImageLeavesTheOthersUntouched() = runTest {
        val state = FakeImageStateStore()
        state.seed("alpha", "registry.local/alpha:1.0.0")
        state.seed("beta", "registry.local/beta:1.0.0")
        val source =
            FakeImageSource(
                mutableMapOf(
                    "alpha" to "registry.local/alpha:2.0.0",
                    "beta" to "registry.local/beta:2.0.0",
                ),
            )
        val service = service(source, state, names = listOf("alpha", "beta"))
        service.poll()

        service.acknowledge("alpha")

        assertEquals(ImageStatus.OK, service.lastSnapshot().find("alpha")?.status)
        assertEquals(ImageStatus.PENDING, service.lastSnapshot().find("beta")?.status)
    }

    @Test
    fun pendingNewsDistingueLaPrimeraPendienteDeLaSegundaVersion() {
        // Es la regla que comparten el servicio -para decidir que avisa- y la lista -para decidir
        // que fila late-. Se prueba aparte porque, escrita dos veces, las dos copias podian
        // divergir sin que nada fallara.
        val primeraVez = pendingNews(instantanea(pendiente("alpha", "2.0.0")), instantanea(pendiente("alpha", "2.0.0")))
        val segundaVersion =
            pendingNews(instantanea(pendiente("alpha", "2.0.0")), instantanea(pendiente("alpha", "3.0.0")))
        val recienPendiente = pendingNews(instantanea(alDia("alpha")), instantanea(pendiente("alpha", "2.0.0")))

        assertTrue(primeraVez.isEmpty(), "Sin cambio de version no hay nada nuevo que contar")
        assertEquals(listOf("alpha"), segundaVersion.map { it.name })
        assertEquals(listOf("alpha"), recienPendiente.map { it.name })
    }

    private fun instantanea(vararg imagenes: ImageState) = PollSnapshot(imagenes.toList(), Instant.fromEpochSeconds(0))

    private fun pendiente(name: String, remota: String) = ImageState(
        name = name,
        local = Version("1.0.0"),
        remote = Version(remota),
        registry = "registry.local/$name",
        status = ImageStatus.PENDING,
        error = null,
        lastCheckedAt = Instant.fromEpochSeconds(0),
    )

    private fun alDia(name: String) = ImageState(
        name = name,
        local = Version("1.0.0"),
        remote = Version("1.0.0"),
        registry = "registry.local/$name",
        status = ImageStatus.OK,
        error = null,
        lastCheckedAt = Instant.fromEpochSeconds(0),
    )

    private fun service(
        source: ImageSource,
        state: ImageStateStore,
        notifiers: List<NotificationPort> = emptyList(),
        names: List<String>,
    ) = VersionPollingService(source, state, notifiers, FakeTrackedImageStore(names))

    private class FakeTrackedImageStore(private var names: List<String>) : TrackedImageStore {
        override fun findAll(): List<String> = names

        override fun save(names: List<String>) {
            this.names = names
        }
    }

    private class FakeImageSource(private val referenceByName: MutableMap<String, String>) : ImageSource {
        private val failures = mutableMapOf<String, String>()

        fun failOn(name: String, message: String) {
            failures[name] = message
        }

        fun setReference(name: String, reference: String) {
            referenceByName[name] = reference
        }

        override suspend fun findByNames(names: List<String>): List<ImageResult> = names.map { name ->
            val failure = failures[name]
            if (failure != null) {
                ImageResult.failed(name, failure)
            } else {
                ImageResult.found(ImageRelease(name, referenceByName.getValue(name), now()))
            }
        }
    }

    private class FakeImageStateStore : ImageStateStore {
        private val byName = mutableMapOf<String, ImageRelease>()

        fun seed(name: String, reference: String) {
            byName[name] = ImageRelease(name, reference, now())
        }

        override fun find(name: String): ImageRelease? = byName[name]

        // Upsert, igual que el contrato del puerto y que JsonImageStateStore. Cuando este doble
        // hacía upsert y el real reemplazaba el fichero entero, los tests pasaban mientras la
        // aplicación borraba datos: reconocer una imagen dejaba a las demás sin versión conocida.
        override fun save(releases: List<ImageRelease>) {
            releases.forEach { byName[it.name] = it }
        }

        override fun rename(previous: String, current: String) {
            val known = byName.remove(previous) ?: return
            byName[current] = known.copy(name = current)
        }
    }

    private class RecordingNotificationPort : NotificationPort {
        val received = mutableListOf<ImageState>()

        override fun notifyUpdates(updates: List<ImageState>) {
            received.addAll(updates)
        }
    }

    private class RecordingListener : PollListener {
        val snapshots = mutableListOf<PollSnapshot>()

        override fun onSnapshot(snapshot: PollSnapshot) {
            snapshots.add(snapshot)
        }
    }

    private companion object {
        private fun now() = Clock.System.now().toLocalDateTime(TimeZone.UTC)
    }
}
