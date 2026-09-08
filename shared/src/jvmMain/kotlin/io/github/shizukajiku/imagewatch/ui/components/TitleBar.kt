package io.github.shizukajiku.imagewatch.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowScope
import io.github.shizukajiku.imagewatch.ui.theme.IconSize
import io.github.shizukajiku.imagewatch.ui.theme.TypeScale

// Ancho de la zona de clic del boton de cerrar: no es un paso de la escala, es el tamaño minimo
// comodo para acertar el clic sin invadir el area que arrastra la ventana.
private val CLOSE_HIT_WIDTH = 46.dp

/**
 * Barra de título propia. La ventana va sin decoración del sistema y con un único botón, cerrar,
 * que la esconde en la bandeja.
 *
 * No hay minimizar ni maximizar a propósito. Una aplicación residente que se minimiza acaba
 * duplicando su sitio —en la barra de tareas y en la bandeja— y deja a Windows decidiendo cuándo
 * se puede volver a poner delante, cosa que no concede a un proceso que no está en primer plano.
 * Con un solo estado, «visible» o «en la bandeja», reaparecer siempre funciona.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WindowScope.TitleBar(title: String, onClose: () -> Unit) {
    // La barra entera arrastra la ventana, que es lo que el usuario espera de una barra de título.
    WindowDraggableArea {
        Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                // El start = 14.dp no esta en la escala de Space (12 o 16): es el margen que ya
                // tenia la barra frente al borde de la ventana.
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
                IconButton(onClose, modifier = Modifier.width(CLOSE_HIT_WIDTH)) {
                    SvgIcon(
                        AppSvg.CLOSE,
                        MaterialTheme.colorScheme.onSurfaceVariant,
                        Modifier.size(IconSize.sm),
                    )
                }
            }
        }
    }
}
