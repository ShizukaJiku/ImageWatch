package io.github.shizukajiku.imagewatch.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.shizukajiku.imagewatch.ui.theme.LocalIsDark
import io.github.shizukajiku.imagewatch.ui.theme.Radius
import io.github.shizukajiku.imagewatch.ui.theme.hairline

/**
 * La tarjeta con borde de la app: fondo, borde de 1 dp y radio, envolviendo una `Column`. Es el
 * esqueleto detrás de `SettingsCard`, `RowCard`, `ConfirmDialog` y la tarjeta del aviso.
 */
@Composable
fun SurfaceCard(
    modifier: Modifier = Modifier,
    background: Color = MaterialTheme.colorScheme.surface,
    borderColor: Color = hairline(LocalIsDark.current),
    radius: Dp = Radius.md,
    tonalElevation: Dp = 0.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        color = background,
        border = BorderStroke(1.dp, borderColor),
        shape = RoundedCornerShape(radius),
        tonalElevation = tonalElevation,
        modifier = modifier,
    ) {
        Column(content = content)
    }
}
