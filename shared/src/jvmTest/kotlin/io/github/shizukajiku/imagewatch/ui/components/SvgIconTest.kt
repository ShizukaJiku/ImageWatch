package io.github.shizukajiku.imagewatch.ui.components

import androidx.compose.ui.unit.Density
import kotlin.test.Test
import kotlin.test.assertTrue

class SvgIconTest {
    @Test
    fun `todos los iconos del proyecto se cargan`() {
        val density = Density(1f)

        AppSvg.entries.forEach { svg ->
            val painter = loadAppSvg(svg, density)
            assertTrue(
                painter.intrinsicSize.width > 0f,
                "${svg.resourcePath} cargó con tamaño nulo",
            )
        }
    }

    @Test
    fun `cada icono apunta a un recurso existente`() {
        AppSvg.entries.forEach { svg ->
            val stream = SvgIconTest::class.java.getResourceAsStream(svg.resourcePath)
            assertTrue(stream != null, "No existe el recurso ${svg.resourcePath}")
            stream?.close()
        }
    }
}
