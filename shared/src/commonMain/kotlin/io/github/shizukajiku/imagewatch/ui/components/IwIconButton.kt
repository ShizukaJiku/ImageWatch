package io.github.shizukajiku.imagewatch.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.shizukajiku.imagewatch.ui.theme.IconSize
import io.github.shizukajiku.imagewatch.ui.theme.focusRing

/**
 * Botón de icono circular: buscar/campana/ajustes de la cabecera, kebab de fila, volver de
 * Ajustes. `toggledOn` es para el único caso con dos estados visuales -la campana de
 * silenciar-todo-: fondo `toggledBackground` recortado en círculo + tinte `toggledTint` cuando
 * está activo.
 *
 * El fondo y el anillo de foco van en la MISMA cadena de modificadores que el propio `IconButton`
 * -no un `Surface` padre separado-: si el círculo de fondo fuera un `Surface` envolvente del mismo
 * tamaño, su `shape = CircleShape` recortaría el anillo de foco, que se dibuja 2 dp por fuera del
 * borde del botón.
 *
 * El anillo siempre usa radio de píldora (círculo): todo botón de icono del diseño es circular,
 * cualquiera que sea su tamaño (24 en la barra de título, 30 en el kebab, 32 en cabecera/Ajustes).
 */
@Composable
fun IwIconButton(
    icon: AppSvg,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    boxSize: Dp = 32.dp,
    iconSize: Dp = IconSize.lg,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    toggledOn: Boolean = false,
    toggledTint: Color = MaterialTheme.colorScheme.onSurface,
    toggledBackground: Color = MaterialTheme.colorScheme.surfaceVariant,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(boxSize)
            .then(if (toggledOn) Modifier.background(toggledBackground, CircleShape) else Modifier)
            .focusRing(999.dp),
    ) {
        SvgIcon(icon, if (toggledOn) toggledTint else tint, Modifier.size(iconSize))
    }
}
