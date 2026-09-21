package io.github.shizukajiku.imagewatch.infrastructure.persistence

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

internal class JsonTeamsNotifiedStoreTest {
    @Test
    fun nullWithNoFile() = withTempDir { dir ->
        val store = JsonTeamsNotifiedStore(dir.resolve("teams-seen.json"))

        assertNull(store.find("alpha"))
    }

    @Test
    fun savedVersionsSurviveARoundTrip() = withTempDir { dir ->
        val store = JsonTeamsNotifiedStore(dir.resolve("teams-seen.json"))

        store.save(mapOf("alpha" to "1.1.0"))

        assertEquals("1.1.0", JsonTeamsNotifiedStore(dir.resolve("teams-seen.json")).find("alpha"))
    }

    @Test
    fun savingUpsertsWithoutTouchingOtherImages() = withTempDir { dir ->
        val file = dir.resolve("teams-seen.json")
        val store = JsonTeamsNotifiedStore(file)
        store.save(mapOf("alpha" to "1.0.0", "beta" to "2.0.0"))

        store.save(mapOf("alpha" to "1.1.0"))

        val reloaded = JsonTeamsNotifiedStore(file)
        assertEquals("1.1.0", reloaded.find("alpha"))
        assertEquals("2.0.0", reloaded.find("beta"))
    }
}
