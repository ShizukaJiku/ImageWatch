package io.github.shizukajiku.imagewatch.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class PollSnapshotTest {
    @Test
    fun findsAnImageByName() {
        val snapshot = PollSnapshot(listOf(ok("alpha"), ok("beta")), NOW)

        assertEquals("beta", snapshot.find("beta")?.name)
        assertNull(snapshot.find("ausente"))
    }

    @Test
    fun pendingReturnsOnlyImagesWithANewerRemoteVersion() {
        val snapshot = PollSnapshot(listOf(ok("alpha"), pending("beta"), ok("gamma")), NOW)

        assertEquals(listOf("beta"), snapshot.pending().map { it.name })
    }

    /**
     * La segunda mitad del test original —que `images.add(...)` lanzase
     * `UnsupportedOperationException`— desapareció porque ya no puede escribirse: `images` es una
     * `List` de Kotlin y el compilador rechaza la llamada. La garantía sigue ahí, comprobada antes
     * de ejecutar en vez de después.
     */
    @Test
    fun theImageListIsDefensivelyCopied() {
        val mutable = mutableListOf(ok("alpha"))
        val snapshot = PollSnapshot(mutable, NOW)

        mutable.clear()

        assertEquals(1, snapshot.images.size)
    }

    @Test
    fun anErrorStateCarriesItsMessageAndNoVersions() {
        val state = ImageState.failed(name = "alpha", message = "HTTP 503", at = NOW)

        assertEquals(ImageStatus.ERROR, state.status)
        assertEquals("HTTP 503", state.error)
        assertNull(state.remote)
        assertEquals(NOW, state.lastCheckedAt)
    }

    @Test
    fun anUnknownStateHasNeitherVersionsNorError() {
        val state = ImageState.unknown("alpha", NOW)

        assertEquals(ImageStatus.UNKNOWN, state.status)
        assertNull(state.error)
        assertNull(state.local)
        assertNull(state.remote)
    }

    @Test
    fun theEmptySnapshotFindsNothing() {
        assertNull(PollSnapshot.EMPTY.find("alpha"))
        assertTrue(PollSnapshot.EMPTY.pending().isEmpty())
    }

    private companion object {
        private val NOW = Instant.parse("2026-09-03T10:00:00Z")

        private fun ok(name: String) = ImageState(
            name = name,
            local = Version("1.0.0"),
            remote = Version("1.0.0"),
            registry = "registry.local/$name",
            status = ImageStatus.OK,
            error = null,
            lastCheckedAt = NOW,
        )

        private fun pending(name: String) = ImageState(
            name = name,
            local = Version("1.0.0"),
            remote = Version("2.0.0"),
            registry = "registry.local/$name",
            status = ImageStatus.PENDING,
            error = null,
            lastCheckedAt = NOW,
        )
    }
}
