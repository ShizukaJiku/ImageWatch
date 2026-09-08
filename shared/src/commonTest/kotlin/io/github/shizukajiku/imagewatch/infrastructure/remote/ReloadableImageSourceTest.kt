package io.github.shizukajiku.imagewatch.infrastructure.remote

import io.github.shizukajiku.imagewatch.application.ImageResult
import io.github.shizukajiku.imagewatch.application.ImageSource
import io.github.shizukajiku.imagewatch.domain.ImageRelease
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * El test `rechaza_un_reemplazo_nulo` desapareció: `swap` recibe un `ImageSource` no nulo y el
 * compilador rechaza la llamada. La garantía que protegía —no dejar la aplicación sin origen— sigue
 * en pie, comprobada antes de ejecutar.
 */
internal class ReloadableImageSourceTest {
    @Test
    fun delega_en_el_origen_inicial() = runTest {
        val source = ReloadableImageSource(sourceNamed("antes"))

        val results = source.findByNames(listOf("alpha"))

        assertTrue(assertNotNull(results.single().release).reference.startsWith("antes/"))
    }

    @Test
    fun tras_el_cambio_delega_en_el_nuevo() = runTest {
        val source = ReloadableImageSource(sourceNamed("antes"))

        source.swap(sourceNamed("despues"))
        val results = source.findByNames(listOf("alpha"))

        assertTrue(assertNotNull(results.single().release).reference.startsWith("despues/"))
    }

    private companion object {
        private val WHEN = LocalDateTime.parse("2026-09-03T10:00:00")

        private fun sourceNamed(tag: String) = object : ImageSource {
            override suspend fun findByNames(names: List<String>): List<ImageResult> =
                names.map { ImageResult.found(ImageRelease(it, "$tag/$it:1.0.0", WHEN)) }
        }
    }
}
