package io.github.shizukajiku.imagewatch.infrastructure.persistence

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class JsonSilencedImageStoreTest {
    @Test
    fun emptyWithNoFile() = withTempDir { dir ->
        val store = JsonSilencedImageStore(dir.resolve("silenced-images.json"))

        assertTrue(store.findAll().isEmpty())
    }

    @Test
    fun savedNamesSurviveARoundTrip() = withTempDir { dir ->
        val store = JsonSilencedImageStore(dir.resolve("silenced-images.json"))

        store.save(setOf("alpha", "beta"))

        assertEquals(setOf("alpha", "beta"), store.findAll())
    }

    @Test
    fun savingReplacesThePreviousSet() = withTempDir { dir ->
        val store = JsonSilencedImageStore(dir.resolve("silenced-images.json"))
        store.save(setOf("alpha"))

        store.save(setOf("beta"))

        assertEquals(setOf("beta"), store.findAll())
    }
}
