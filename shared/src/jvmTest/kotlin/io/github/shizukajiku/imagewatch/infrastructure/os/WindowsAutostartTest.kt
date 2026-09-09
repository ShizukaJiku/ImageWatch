package io.github.shizukajiku.imagewatch.infrastructure.os

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WindowsAutostartTest {

    private val label = "ImageWatch-test-" + (1..8).map { ('a'..'z').random() }.joinToString("")
    private val autostart = WindowsAutostart(label, listOf("C:\\ruta\\imagewatch.exe", "--minimized"))

    @AfterTest
    fun limpiar() {
        autostart.setEnabled(false)
    }

    @Test
    fun `arranca desactivado si la clave no existe`() {
        assertEquals(false, autostart.isEnabled())
    }

    @Test
    fun `activar escribe la clave Run y desactivar la borra`() {
        autostart.setEnabled(true)
        assertTrue(autostart.isEnabled(), "La clave Run debería existir tras activar")

        autostart.setEnabled(false)
        assertEquals(false, autostart.isEnabled(), "La clave Run debería haberse borrado")
    }

    @Test
    fun `activar dos veces no falla`() {
        autostart.setEnabled(true)
        autostart.setEnabled(true)
        assertTrue(autostart.isEnabled())
    }
}
