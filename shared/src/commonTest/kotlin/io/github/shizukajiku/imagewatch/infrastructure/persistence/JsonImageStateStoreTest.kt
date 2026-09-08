package io.github.shizukajiku.imagewatch.infrastructure.persistence

import io.github.shizukajiku.imagewatch.domain.ImageRelease
import kotlinx.datetime.LocalDateTime
import okio.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

internal class JsonImageStateStoreTest {
    @Test
    fun findsNothingWhenTheFileDoesNotExist() = withTempDir { dir ->
        assertNull(store(dir).find("alpha"))
    }

    @Test
    fun savedReleasesSurviveARoundTrip() = withTempDir { dir ->
        val store = store(dir)

        store.save(listOf(release("alpha", "1.0.0")))

        assertEquals("registry.local/alpha:1.0.0", store.find("alpha")?.reference)
    }

    @Test
    fun savingOneImageDoesNotEraseTheOthers() = withTempDir { dir ->
        val store = store(dir)
        store.save(listOf(release("alpha", "1.0.0"), release("beta", "2.0.0")))

        // Es lo que hace acknowledge(): guardar una sola imagen.
        store.save(listOf(release("gamma", "3.0.0")))

        assertNotNull(store.find("alpha"))
        assertNotNull(store.find("beta"))
        assertNotNull(store.find("gamma"))
    }

    @Test
    fun savingAnImageAgainReplacesItsPreviousVersion() = withTempDir { dir ->
        val store = store(dir)
        store.save(listOf(release("alpha", "1.0.0")))

        store.save(listOf(release("alpha", "2.0.0")))

        assertEquals("registry.local/alpha:2.0.0", store.find("alpha")?.reference)
    }

    @Test
    fun survivesBeingReopened() = withTempDir { dir ->
        store(dir).save(listOf(release("alpha", "1.0.0")))

        assertNotNull(store(dir).find("alpha"))
    }

    private fun store(dir: Path) = JsonImageStateStore(dir.resolve("images.json"))

    private companion object {
        private val WHEN = LocalDateTime.parse("2026-09-03T10:00:00")

        private fun release(name: String, version: String) = ImageRelease(name, "registry.local/$name:$version", WHEN)
    }
}
