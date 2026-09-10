package io.github.shizukajiku.imagewatch.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import io.github.shizukajiku.imagewatch.ui.theme.IconSize
import io.github.shizukajiku.imagewatch.ui.theme.Radius
import io.github.shizukajiku.imagewatch.ui.theme.Space
import io.github.shizukajiku.imagewatch.ui.theme.TypeScale
import io.github.shizukajiku.imagewatch.ui.theme.focusRing

/**
 * La píldora de la app: texto centrado, icono inicial opcional, ancho fijo o al contenido. Es la
 * misma forma detrás de un chip de fila, un chip de cabecera, un botón del pie, "Agregar", la
 * acción de un aviso y los botones de un diálogo.
 *
 * Sin `onClick` es una etiqueta -la píldora de versión-: no lleva foco ni ripple. Con `onClick`
 * pasa por la rama que sí acepta gestos y `focusRing()`.
 *
 * `width == null` dibuja al contenido -`HeaderChip`, `FootPill`, botones de diálogo-; con `width`
 * fijo -chip de fila, píldora de versión, acción del aviso- el `Row` interior sí ocupa todo el
 * ancho para que `Arrangement.Center`/`spacedBy(..., CenterHorizontally)` centren de verdad: un
 * `Row` sin `fillMaxWidth` mide su propio contenido, no el ancho fijo del `Surface` que lo envuelve
 * -sin este detalle, una píldora de ancho fijo con icono queda pegada a la izquierda en vez de
 * centrada-.
 */
@Composable
fun Pill(
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    leadingIcon: AppSvg? = null,
    iconTint: Color = contentColor,
    width: Dp? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = Space.md, vertical = Space.xs + 1.dp),
    fontFamily: FontFamily? = null,
    fontWeight: FontWeight = FontWeight.SemiBold,
    fontSize: TextUnit = TypeScale.meta,
    maxLines: Int = 1,
) {
    val sizedModifier = if (width != null) modifier.width(width) else modifier
    val shape = RoundedCornerShape(Radius.pill)
    val body: @Composable () -> Unit = {
        Row(
            horizontalArrangement = if (leadingIcon != null) {
                Arrangement.spacedBy(Space.xs + 2.dp, Alignment.CenterHorizontally)
            } else {
                Arrangement.Center
            },
            verticalAlignment = Alignment.CenterVertically,
            modifier = (if (width != null) Modifier.fillMaxWidth() else Modifier).padding(contentPadding),
        ) {
            if (leadingIcon != null) {
                SvgIcon(leadingIcon, iconTint, Modifier.size(IconSize.sm))
            }
            Text(
                text,
                color = contentColor,
                fontFamily = fontFamily,
                fontWeight = fontWeight,
                fontSize = fontSize,
                textAlign = TextAlign.Center,
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    if (onClick != null) {
        Surface(
            onClick = onClick,
            color = containerColor,
            shape = shape,
            modifier = sizedModifier.focusRing(Radius.pill),
            content = body,
        )
    } else {
        Surface(color = containerColor, shape = shape, modifier = sizedModifier, content = body)
    }
}
