package io.github.shizukajiku.imagewatch.ui.theme

import io.github.shizukajiku.imagewatch.domain.ImageStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ColorsTest {
    @Test
    fun `cada estado tiene su propia etiqueta`() {
        val labels = ImageStatus.entries.map { statusColors(it, dark = true).label }

        assertEquals(labels.size, labels.toSet().size, "Dos estados comparten etiqueta: $labels")
    }

    @Test
    fun `el estado de error no se confunde con el de sin verificar`() {
        assertNotEquals(
            statusColors(ImageStatus.ERROR, dark = true).label,
            statusColors(ImageStatus.UNKNOWN, dark = true).label,
        )
    }

    @Test
    fun `la paleta responde en claro y en oscuro`() {
        ImageStatus.entries.forEach { status ->
            assertNotEquals(
                statusColors(status, dark = true).background,
                statusColors(status, dark = false).background,
                "El estado $status usa el mismo fondo en claro y en oscuro",
            )
        }
    }

    @Test
    fun `el fondo fantasma y el texto apagado tambien responden al tema`() {
        assertNotEquals(ghostBackground(dark = true), ghostBackground(dark = false))
        assertNotEquals(mutedText(dark = true), mutedText(dark = false))
    }
}
