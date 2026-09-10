package io.github.shizukajiku.imagewatch.infrastructure.remote

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class EmptyImageSourceTest {
    @Test
    fun sin_imagenes_no_devuelve_nada() = runTest {
        assertEquals(emptyList(), EmptyImageSource.findByNames(emptyList()))
    }

    @Test
    fun con_imagenes_devuelve_un_fallo_por_cada_una() = runTest {
        val results = EmptyImageSource.findByNames(listOf("alpha", "beta"))

        assertEquals(listOf("alpha", "beta"), results.map { it.name })
        assertTrue(results.all { it.release == null && it.error != null })
    }
}
