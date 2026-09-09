package io.github.shizukajiku.imagewatch.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowScope
import io.github.shizukajiku.imagewatch.ui.theme.IconSize
import io.github.shizukajiku.imagewatch.ui.theme.Layout
import io.github.shizukajiku.imagewatch.ui.theme.TypeScale

// Ancho de la zona de clic del boton de cerrar: no es un paso de la escala, es el tamaño minimo
// comodo para acertar el clic sin invadir el area que arrastra la ventana.
private val CLOSE_HIT_WIDTH = 46.dp

// Igual que la campana de la cabecera (ImagesScreen.Header): zona de clic redonda de 24 dp, mas
// angosta que CLOSE_HIT_WIDTH porque aqui no compite con el area de arrastre de la ventana.
private val BELL_HIT_WIDTH = 24.dp

/**
 * Barra de título propia. La ventana va sin decoración del sistema y con dos botones: silenciar
 * todos los avisos y cerrar (a la bandeja).
 *
 * No hay minimizar ni maximizar a propósito. Una aplicación residente que se minimiza acaba
 * duplicando su sitio —en la barra de tareas y en la bandeja— y deja a Windows decidiendo cuándo
 * se puede volver a poner delante, cosa que no concede a un proceso que no está en primer plano.
 * Con un solo estado, «visible» o «en la bandeja», reaparecer siempre funciona.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WindowScope.TitleBar(title: String, mutedAll: Boolean, onToggleMuteAll: () -> Unit, onClose: () -> Unit) {
    // La barra entera arrastra la ventana, que es lo que el usuario espera de una barra de título.
    WindowDraggableArea {
        Column {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth().height(Layout.titleBarHeight),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    // El start = 14.dp no esta en la escala de Space (12 o 16): es el margen que
                    // ya tenia la barra frente al borde de la ventana.
                    modifier = Modifier.fillMaxWidth().padding(start = 14.dp),
                ) {
                    SvgIcon(AppSvg.LOGO, MaterialTheme.colorScheme.primary, Modifier.size(IconSize.md))
                    Text(
                        "  $title",
                        fontSize = TypeScale.meta,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    // Mismo interruptor que la campana de la cabecera: silenciar todos los avisos.
                    Surface(
                        color = if (mutedAll) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
                        shape = CircleShape,
                    ) {
                        IconButton(onToggleMuteAll, modifier = Modifier.width(BELL_HIT_WIDTH)) {
                            val tint = if (mutedAll) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            SvgIcon(if (mutedAll) AppSvg.BELL_OFF else AppSvg.BELL, tint, Modifier.size(IconSize.md))
                        }
                    }
                    IconButton(onClose, modifier = Modifier.width(CLOSE_HIT_WIDTH)) {
                        SvgIcon(
                            AppSvg.CLOSE,
                            MaterialTheme.colorScheme.onSurfaceVariant,
                            Modifier.size(IconSize.sm),
                        )
                    }
                }
            }
            Box(
                Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.surfaceVariant),
            )
        }
    }
}
