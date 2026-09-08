package io.github.shizukajiku.imagewatch.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.shizukajiku.imagewatch.ui.theme.LocalIsDark
import io.github.shizukajiku.imagewatch.ui.theme.Motion
import io.github.shizukajiku.imagewatch.ui.theme.Radius
import io.github.shizukajiku.imagewatch.ui.theme.Space
import io.github.shizukajiku.imagewatch.ui.theme.TypeScale
import io.github.shizukajiku.imagewatch.ui.theme.highlightPill

private const val DIMMED_ALPHA = 0.45f

/**
 * Animacion 2 de las siete: la version anterior sale hacia arriba y la nueva entra desde abajo,
 * de modo que el cambio se ve ocurrir en lugar de aparecer.
 */
@Composable
fun VersionPill(version: String, highlight: Boolean, modifier: Modifier = Modifier, dimmed: Boolean = false) {
    val palette = highlightPill(LocalIsDark.current)
    val background = if (highlight) palette.background else MaterialTheme.colorScheme.surfaceVariant
    val base = if (highlight) palette.foreground else MaterialTheme.colorScheme.onSurfaceVariant
    // Atenuar y no ocultar: el dato sigue siendo cierto, lo que ya no se sabe es si sigue vigente.
    val foreground = if (dimmed) base.copy(alpha = DIMMED_ALPHA) else base

    Surface(color = background, shape = RoundedCornerShape(Radius.pill), modifier = modifier) {
        AnimatedContent(
            targetState = version,
            transitionSpec = {
                slideInVertically(tween(Motion.NORMAL)) { it } togetherWith
                    slideOutVertically(tween(Motion.NORMAL)) { -it }
            },
            label = "version",
        ) { value ->
            Text(
                text = value,
                color = foreground,
                fontFamily = FontFamily.Monospace,
                fontSize = TypeScale.meta,
                // El vertical = 6.dp no esta en la escala de Space (4 u 8): es el padding que ya
                // tenia la pildora, cambiarlo alteraria su altura.
                modifier = Modifier.padding(horizontal = Space.md, vertical = 6.dp),
            )
        }
    }
}
