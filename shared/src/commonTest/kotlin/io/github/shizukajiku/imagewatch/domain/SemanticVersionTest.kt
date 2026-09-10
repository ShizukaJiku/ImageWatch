package io.github.shizukajiku.imagewatch.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SemanticVersionTest {

    @Test
    fun `parsea con y sin prefijo v`() {
        assertEquals(SemanticVersion(1, 2, 3), SemanticVersion.parseOrNull("1.2.3"))
        assertEquals(SemanticVersion(1, 2, 3), SemanticVersion.parseOrNull("v1.2.3"))
        assertEquals(SemanticVersion(1, 2, 3), SemanticVersion.parseOrNull("  v1.2.3  "))
    }

    @Test
    fun `ordena por campos, no por texto`() {
        assertTrue(SemanticVersion(1, 0, 10) > SemanticVersion(1, 0, 9))
        assertTrue(SemanticVersion(2, 0, 0) > SemanticVersion(1, 9, 9))
        assertTrue(SemanticVersion(1, 2, 3).compareTo(SemanticVersion(1, 2, 3)) == 0)
    }

    @Test
    fun `la basura da null, no excepcion`() {
        assertNull(SemanticVersion.parseOrNull("latest"))
        assertNull(SemanticVersion.parseOrNull("1.2"))
        assertNull(SemanticVersion.parseOrNull(""))
        assertNull(SemanticVersion.parseOrNull("1.2.3.4"))
        assertNull(SemanticVersion.parseOrNull("1.2.x"))
    }

    @Test
    fun `toString reconstruye la forma canonica`() {
        assertEquals("1.2.3", SemanticVersion(1, 2, 3).toString())
    }
}
