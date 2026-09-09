package io.github.shizukajiku.imagewatch.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.github.shizukajiku.imagewatch.ui.components.AppSvg
import io.github.shizukajiku.imagewatch.ui.components.SvgIcon
import io.github.shizukajiku.imagewatch.ui.theme.Elevation
import io.github.shizukajiku.imagewatch.ui.theme.IconSize
import io.github.shizukajiku.imagewatch.ui.theme.Layout
import io.github.shizukajiku.imagewatch.ui.theme.Radius
import io.github.shizukajiku.imagewatch.ui.theme.Space
import io.github.shizukajiku.imagewatch.ui.theme.TabularNums
import io.github.shizukajiku.imagewatch.ui.theme.TypeScale

/**
 * Diálogo de confirmación para las acciones que no se pueden deshacer (Blueprint «Diálogo de
 * confirmación»): quitar una imagen, restablecer los ajustes, borrar los datos locales. La lista
 * [lost] enumera lo que se pierde, un punto por línea. La línea «No se puede deshacer.» tiene
 * altura reservada. La confirmación va en contenedor de error, con ancho mínimo para que su texto
 * no cambie la geometría.
 */
@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    lost: List<String>,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(Radius.md),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = Elevation.dialog,
            modifier = Modifier.widthIn(max = Layout.dialogWidth),
        ) {
            Column(Modifier.padding(Space.xl), verticalArrangement = Arrangement.spacedBy(Space.md)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.md),
                ) {
                    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = CircleShape) {
                        Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                            SvgIcon(
                                AppSvg.WARNING,
                                MaterialTheme.colorScheme.onErrorContainer,
                                Modifier.size(IconSize.md),
                            )
                        }
                    }
                    Text(title, fontWeight = FontWeight.Bold, fontSize = TypeScale.body)
                }
                Text(body, fontSize = TypeScale.meta, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Surface(color = MaterialTheme.colorScheme.background, shape = RoundedCornerShape(Radius.sm)) {
                    Column(
                        Modifier.fillMaxWidth().padding(Space.md),
                        verticalArrangement = Arrangement.spacedBy(Space.xs),
                    ) {
                        lost.forEach { linea ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Space.sm),
                            ) {
                                Box(
                                    Modifier.size(4.dp)
                                        .background(MaterialTheme.colorScheme.onSurfaceVariant, CircleShape),
                                )
                                Text(
                                    linea,
                                    fontSize = TypeScale.meta,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = TabularNums,
                                )
                            }
                        }
                    }
                }
                Text(
                    "No se puede deshacer.",
                    fontSize = TypeScale.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.height(Layout.settingsHelpLine),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    Spacer(Modifier.weight(1f))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(Radius.pill),
                        onClick = onDismiss,
                    ) {
                        Text(
                            "Cancelar",
                            fontSize = TypeScale.meta,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = Space.md, vertical = Space.sm),
                        )
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(Radius.pill),
                        onClick = {
                            onConfirm()
                            onDismiss()
                        },
                    ) {
                        Text(
                            confirmLabel,
                            fontSize = TypeScale.meta,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.widthIn(min = Layout.dialogConfirmMin)
                                .padding(horizontal = Space.md, vertical = Space.sm),
                        )
                    }
                }
            }
        }
    }
}
