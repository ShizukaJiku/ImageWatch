package io.github.shizukajiku.imagewatch.infrastructure.persistence

import kotlin.test.Test
import kotlin.test.assertEquals

internal class JsonTrackedImageStoreTest {
    @Test
    fun seedsDefaultsOnFirstRead() = withTempDir { dir ->
        val store = JsonTrackedImageStore(dir.resolve("tracked-images.json"), listOf("alpha"))

        assertEquals(listOf("alpha"), store.findAll())
    }

    @Test
    fun savedNamesSurviveARoundTrip() = withTempDir { dir ->
        val store = JsonTrackedImageStore(dir.resolve("tracked-images.json"), emptyList())

        store.save(listOf("beta", "gamma"))

        assertEquals(listOf("beta", "gamma"), store.findAll())
    }

    @Test
    fun savingReplacesThePreviousList() = withTempDir { dir ->
        val store = JsonTrackedImageStore(dir.resolve("tracked-images.json"), listOf("old-image"))
        store.findAll()

        store.save(listOf("new-image"))

        assertEquals(listOf("new-image"), store.findAll())
    }
}
