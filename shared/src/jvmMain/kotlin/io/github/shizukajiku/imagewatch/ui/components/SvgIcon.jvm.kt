package io.github.shizukajiku.imagewatch.ui.components

import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.loadSvgPainter
import androidx.compose.ui.unit.Density

actual fun loadAppSvg(svg: AppSvg, density: Density): Painter {
    val stream =
        requireNotNull(AppSvg::class.java.getResourceAsStream(svg.resourcePath)) {
            "Falta el icono ${svg.resourcePath} en los recursos"
        }
    return stream.use { loadSvgPainter(it, density) }
}
