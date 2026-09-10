package io.github.shizukajiku.imagewatch.ui.components

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/** Los iconos vectoriales que la aplicacion empaqueta en `resources/icons/`. */
enum class AppSvg(private val file: String) {
    PLUS("plus"),
    PENCIL("pencil"),
    TRASH("trash"),
    SEARCH("search"),
    WARNING("warning"),
    GEAR("gear"),
    BACK("arrow-left"),
    CLOSE("close"),
    REFRESH("refresh"),
    SPINNER("spinner"),
    PAUSE("pause"),
    PLAY("play"),
    CHECK("check"),
    CHECK_ALL("check-all"),
    MINUS("minus"),
    LOGO("logo"),
    BELL("bell"),
    BELL_OFF("bell-off"),
    CHEVRON_DOWN("chevron-down"),
    KEBAB("kebab"),
    ARROW_UP("arrow-up"),
    COPY("copy"),
    DOWNLOAD("download"),
    ;

    val resourcePath: String get() = "/icons/$file.svg"
}

/**
 * Carga un icono desde el classpath. Lanza si el recurso falta: un icono ausente es un error de
 * empaquetado, y fallar pronto avisa mejor que un hueco en la pantalla.
 */
expect fun loadAppSvg(svg: AppSvg, density: Density): Painter

@Composable
fun SvgIcon(svg: AppSvg, tint: Color, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    // remember evita releer y reparsear el SVG en cada recomposicion.
    val painter = remember(svg, density) { loadAppSvg(svg, density) }
    Image(
        painter = painter,
        contentDescription = svg.name,
        modifier = modifier,
        colorFilter = ColorFilter.tint(tint),
    )
}
