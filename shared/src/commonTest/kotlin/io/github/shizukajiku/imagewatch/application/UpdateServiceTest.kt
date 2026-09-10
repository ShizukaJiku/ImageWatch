package io.github.shizukajiku.imagewatch.application

import io.github.shizukajiku.imagewatch.domain.SemanticVersion
import io.github.shizukajiku.imagewatch.domain.UpdateManifest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateServiceTest {

    private val current = SemanticVersion(1, 0, 0)

    private val availableManifest = UpdateManifest(
        SemanticVersion(1, 1, 0),
        "notas",
        "https://d.test/a.msi",
        "https://d.test/a.msi.sha256",
    )

    /**
     * Checker de mentira: devuelve la cola en orden -y la última respuesta se repite- y cuenta
     * llamadas. `suspendMillis` deja modelar un chequeo que tarda, para probar el solape.
     */
    private class FakeChecker(private val answers: MutableList<UpdateStatus>, private val suspendMillis: Long = 0) :
        UpdateCheck {
        var calls = 0
            private set

        private var last: UpdateStatus = UpdateStatus.UpToDate

        override suspend fun check(current: SemanticVersion): UpdateStatus {
            calls++
            if (suspendMillis > 0) delay(suspendMillis)
            if (answers.isNotEmpty()) last = answers.removeAt(0)
            return last
        }
    }

    @Test
    fun `start dispara un chequeo y publica el resultado`() = runTest {
        val fake = FakeChecker(mutableListOf(UpdateStatus.Available(availableManifest)))
        val service = UpdateService(fake, current, backgroundScope, period = 24.hours)

        service.start()
        runCurrent()

        val phase = service.phase.value
        assertTrue(phase is UpdatePhase.Available)
        assertEquals("1.1.0", (phase as UpdatePhase.Available).version)
        assertEquals(availableManifest, service.pendingManifest)
    }

    @Test
    fun `un fallo periodico no borra un Available anterior`() = runTest {
        val fake = FakeChecker(
            mutableListOf(
                UpdateStatus.Available(availableManifest),
                UpdateStatus.CheckFailed("sin red"),
            ),
        )
        val service = UpdateService(fake, current, backgroundScope, period = 1.hours)

        service.start()
        runCurrent()
        assertTrue(service.phase.value is UpdatePhase.Available)

        advanceTimeBy((1.hours + 1.minutes).inWholeMilliseconds)
        runCurrent()

        assertTrue(service.phase.value is UpdatePhase.Available, "el Available se conserva")
        assertEquals(availableManifest, service.pendingManifest)
        assertEquals(2, fake.calls)
    }

    @Test
    fun `dos checkNow solapados hacen una sola consulta`() = runTest {
        val fake = FakeChecker(mutableListOf(UpdateStatus.UpToDate), suspendMillis = 100)
        val service = UpdateService(fake, current, backgroundScope, period = 24.hours)

        service.checkNow()
        service.checkNow()
        runCurrent()
        advanceTimeBy(200)
        runCurrent()

        assertEquals(1, fake.calls)
    }

    @Test
    fun `markReadyToApply usa la version del manifiesto pendiente`() = runTest {
        val fake = FakeChecker(mutableListOf(UpdateStatus.Available(availableManifest)))
        val service = UpdateService(fake, current, backgroundScope, period = 24.hours)
        service.start()
        runCurrent()

        service.markReadyToApply()

        val phase = service.phase.value
        assertTrue(phase is UpdatePhase.ReadyToApply)
        assertEquals("1.1.0", (phase as UpdatePhase.ReadyToApply).version)
    }

    @Test
    fun `UpToDate borra el manifiesto pendiente`() = runTest {
        val fake = FakeChecker(
            mutableListOf(
                UpdateStatus.Available(availableManifest),
                UpdateStatus.UpToDate,
            ),
        )
        val service = UpdateService(fake, current, backgroundScope, period = 1.hours)
        service.start()
        runCurrent()
        assertEquals(availableManifest, service.pendingManifest)

        advanceTimeBy((1.hours + 1.minutes).inWholeMilliseconds)
        runCurrent()

        assertEquals(null, service.pendingManifest)
        assertTrue(service.phase.value is UpdatePhase.UpToDate)
    }
}
