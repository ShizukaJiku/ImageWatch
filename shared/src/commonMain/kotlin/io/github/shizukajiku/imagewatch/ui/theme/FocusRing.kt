package io.github.shizukajiku.imagewatch.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val RING_WIDTH = 2.dp
private val RING_OFFSET = 2.dp

/**
 * Anillo de foco de teclado (Blueprint 1h, «Foco de teclado»): 2 dp del color de acento, separado
 * 2 dp por fuera del borde del componente, sin cambiar su tamaño.
 *
 * No añade un segundo objetivo de foco: `onFocusChanged` aquí observa el foco del nodo que ya es
 * foco real más adelante en la misma cadena de modificadores -el `clickable`/`Surface(onClick=…)`/
 * `IconButton`/`Switch` al que se le pasa este modifier-. Añadir un `.focusable()` propio
 * duplicaría la parada de `Tab` sobre el mismo control visual.
 *
 * `cornerRadius` no tiene por qué coincidir en tipo con el radio real de la forma: un valor mayor
 * que la mitad del lado corto -como `Radius.pill`, 999 dp- se clampa al dibujar al mínimo de las
 * dos dimensiones del anillo, así que sirve igual para una tarjeta con esquinas y para una píldora
 * o un círculo. El clamp es manual y no de `drawRoundRect` -que, a diferencia de
 * `RoundedCornerShape`, clampa el radio de forma independiente por eje-: sin él, un radio de
 * píldora sobre una caja no cuadrada (un chip de 88×30) dibuja dos arcos elípticos en vez de una
 * píldora real.
 */
fun Modifier.focusRing(cornerRadius: Dp = Radius.md): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    val color = MaterialTheme.colorScheme.primary
    this
        .onFocusChanged { focused = it.isFocused }
        .drawWithContent {
            drawContent()
            if (focused) {
                val strokeWidthPx = RING_WIDTH.toPx()
                val offsetPx = RING_OFFSET.toPx()
                val ringWidth = size.width + offsetPx * 2
                val ringHeight = size.height + offsetPx * 2
                val effectiveRadius = minOf(cornerRadius.toPx() + offsetPx, ringWidth / 2f, ringHeight / 2f)
                drawRoundRect(
                    color = color,
                    topLeft = Offset(-offsetPx, -offsetPx),
                    size = Size(ringWidth, ringHeight),
                    cornerRadius = CornerRadius(effectiveRadius),
                    style = Stroke(strokeWidthPx),
                )
            }
        }
}
