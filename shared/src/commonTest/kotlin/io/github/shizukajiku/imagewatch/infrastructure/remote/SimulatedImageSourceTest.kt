package io.github.shizukajiku.imagewatch.infrastructure.remote

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

internal class SimulatedImageSourceTest {
    @Test
    fun theVersionAdvancesAsTimePasses() = runTest {
        val clock = MutableClock(START)
        val source = SimulatedImageSource(clock, BUMP_EVERY)

        val first = reference(source, "alpha")
        clock.advance(60.seconds)
        val later = reference(source, "alpha")

        assertNotEquals(first, later)
    }

    @Test
    fun theVersionIsStableWhileTimeDoesNotAdvance() = runTest {
        val source = SimulatedImageSource(MutableClock(START), BUMP_EVERY)

        assertEquals(reference(source, "alpha"), reference(source, "alpha"))
    }

    @Test
    fun differentImagesAdvanceOutOfStep() = runTest {
        // Con un intervalo de 20 s, el desfase derivado del nombre es 18 para "alpha" y 1 para
        // "omega". A los 5 s, alpha ya ha saltado de parche y omega todavía no. Los valores son
        // deterministas, así que el test no depende de que dos hashes cualesquiera difieran.
        val clock = MutableClock(START)
        val source = SimulatedImageSource(clock, BUMP_EVERY)

        clock.advance(5.seconds)

        assertEquals("registry.local/alpha:1.0.1", reference(source, "alpha"))
        assertEquals("registry.local/omega:1.0.0", reference(source, "omega"))
    }

    @Test
    fun anyNameContainingFailAlwaysReportsAnError() = runTest {
        val source = SimulatedImageSource(MutableClock(START), BUMP_EVERY)

        val results = source.findByNames(listOf("delta-fail"))

        assertNotNull(results.first().error)
        assertNull(results.first().release)
    }

    @Test
    fun aFailingImageDoesNotAffectTheOthersInTheSameBatch() = runTest {
        val source = SimulatedImageSource(MutableClock(START), BUMP_EVERY)

        val results = source.findByNames(listOf("alpha", "delta-fail", "omega"))

        assertEquals(3, results.size)
        assertNotNull(results[0].release)
        assertNotNull(results[1].error)
        assertNotNull(results[2].release)
    }

    private suspend fun reference(source: SimulatedImageSource, name: String): String = source
        .findByNames(listOf(name))
        .first()
        .release!!
        .reference

    /** Reloj controlado por el test: sin esperas reales y con resultados reproducibles. */
    private class MutableClock(private var current: Instant) : Clock {
        fun advance(amount: Duration) {
            current += amount
        }

        override fun now(): Instant = current
    }

    private companion object {
        private val START = Instant.parse("2026-09-03T10:00:00Z")
        private val BUMP_EVERY = 20.seconds
    }
}
