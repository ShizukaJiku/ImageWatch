package io.github.shizukajiku.imagewatch.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import io.github.shizukajiku.imagewatch.ui.theme.Accent
import io.github.shizukajiku.imagewatch.ui.theme.Alert
import io.github.shizukajiku.imagewatch.ui.theme.Ink

// La paleta se define una sola vez, en Colors.kt: repetir aqui los mismos hexadecimales era
// tener dos sitios que cambiar cada vez que se retoca la marca.
private val Background = Ink

private const val CORNER_FACTOR = 0.3f
private const val PADDING_FACTOR = 0.22f
private const val GAP_FACTOR = 0.12f
private const val CELL_CORNER_FACTOR = 0.4f
private const val DOT_FACTOR = 0.16f

/** La marca: una cuadrícula 2×2 sobre fondo redondeado. Compartida por bandeja y ventana. */
object AppIconPainter : Painter() {
    override val intrinsicSize = Size(64f, 64f)

    override fun DrawScope.onDraw() = drawIcon(Accent, alert = false)
}

/**
 * La misma marca en rojo y con un punto en la esquina. Es el único nivel de error visible cuando
 * la ventana está cerrada, así que se distingue de un vistazo y sin leer nada.
 */
object AlertIconPainter : Painter() {
    override val intrinsicSize = Size(64f, 64f)

    override fun DrawScope.onDraw() = drawIcon(Alert, alert = true)
}

private fun DrawScope.drawIcon(cellColor: Color, alert: Boolean) {
    val side = size.minDimension
    drawRoundRect(
        color = Background,
        size = Size(side, side),
        cornerRadius = CornerRadius(side * CORNER_FACTOR),
    )

    val padding = side * PADDING_FACTOR
    val gap = side * GAP_FACTOR
    val cell = (side - padding * 2 - gap) / 2
    val positions = listOf(0f to 0f, 1f to 0f, 0f to 1f, 1f to 1f)

    positions.forEach { (column, row) ->
        drawRoundRect(
            color = cellColor,
            topLeft = Offset(padding + column * (cell + gap), padding + row * (cell + gap)),
            size = Size(cell, cell),
            cornerRadius = CornerRadius(cell * CELL_CORNER_FACTOR),
        )
    }

    if (alert) {
        val radius = side * DOT_FACTOR
        // Sobre el fondo, no en el mismo rojo de las celdas: encima de una celda roja el punto se
        // fundia con ella y dejaba de distinguirse.
        drawCircle(color = Background, radius = radius * 1.35f, center = Offset(side - radius, radius))
        drawCircle(color = Alert, radius = radius, center = Offset(side - radius, radius))
    }
}
