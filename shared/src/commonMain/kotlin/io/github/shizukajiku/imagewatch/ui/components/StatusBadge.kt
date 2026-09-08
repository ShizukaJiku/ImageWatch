package io.github.shizukajiku.imagewatch.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.shizukajiku.imagewatch.domain.ImageStatus
import io.github.shizukajiku.imagewatch.ui.theme.LocalIsDark
import io.github.shizukajiku.imagewatch.ui.theme.Motion
import io.github.shizukajiku.imagewatch.ui.theme.Radius
import io.github.shizukajiku.imagewatch.ui.theme.Space
import io.github.shizukajiku.imagewatch.ui.theme.TypeScale
import io.github.shizukajiku.imagewatch.ui.theme.statusColors

/**
 * Animacion 1 de las siete: el cambio de estado funde el color en lugar de saltar.
 *
 * Motion.EMPHASIS (400 ms) es deliberado: por debajo de unos 250 ms el ojo no registra la
 * transicion y el efecto es indistinguible de no animar.
 */
@Composable
fun StatusBadge(status: ImageStatus, modifier: Modifier = Modifier) {
    val palette = statusColors(status, LocalIsDark.current)
    val background by animateColorAsState(
        palette.background,
        tween(Motion.EMPHASIS),
        label = "badgeBackground",
    )
    val foreground by animateColorAsState(
        palette.foreground,
        tween(Motion.EMPHASIS),
        label = "badgeForeground",
    )

    Surface(color = background, shape = RoundedCornerShape(Radius.pill), modifier = modifier) {
        Text(
            text = palette.label,
            color = foreground,
            fontSize = TypeScale.meta,
            // El vertical = 6.dp no esta en la escala de Space (4 u 8): mismo padding de pildora
            // que VersionPill, cambiarlo alteraria la altura de la insignia.
            modifier = Modifier.padding(horizontal = Space.md, vertical = 6.dp),
        )
    }
}
