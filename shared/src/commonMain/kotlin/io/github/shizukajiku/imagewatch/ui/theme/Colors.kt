package io.github.shizukajiku.imagewatch.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import io.github.shizukajiku.imagewatch.domain.ImageStatus

// El acento no es el color de marca de ningun proveedor: es un indigo neutro.
val Accent = Color(0xFF4C6EF5)
private val AccentDark = Color(0xFF3B5BDB)

// El fondo de la aplicacion, compartido con el icono de la bandeja para que la marca sea una
// sola. Vive aqui porque el diseno pide que el color se defina en un unico sitio.
val Ink = Color(0xFF1B1B1D)
val Alert = Color(0xFFE5484D)

val DarkColors =
    darkColorScheme(
        primary = Accent,
        onPrimary = Color.White,
        background = Ink,
        onBackground = Color(0xFFE7E7EA),
        surface = Color(0xFF232326),
        onSurface = Color(0xFFE7E7EA),
        surfaceVariant = Color(0xFF2A2A2E),
        onSurfaceVariant = Color(0xFF9A9AA2),
        // Sin estos tres, el resaltado que pinta la accion "Ver", la barra de sin conexion y el
        // tooltip de error caian al esquema por defecto de Material y salian morados.
        primaryContainer = Color(0xFF2B3557),
        onPrimaryContainer = Color(0xFFD9E0FF),
        errorContainer = Color(0xFF4A1D20),
        onErrorContainer = Color(0xFFFFB4B4),
        // «Recién movida»: el resaltado de 4 s de una fila que acaba de entrar en «Al día»
        // (Blueprint state colour «Contenedor de acento»). Alias explícito de primaryContainer:
        // dicen lo mismo -«mira esto»- y `bumpColor()` lo lee sin conocer `ImageStatus`. Si algún
        // día se separan, este deja de ser alias y toma color propio.
        tertiaryContainer = Color(0xFF2B3557),
        onTertiaryContainer = Color(0xFFD9E0FF),
    )

val LightColors =
    lightColorScheme(
        primary = AccentDark,
        onPrimary = Color.White,
        background = Color(0xFFF7F7F9),
        onBackground = Color(0xFF1B1B1D),
        surface = Color.White,
        onSurface = Color(0xFF1B1B1D),
        surfaceVariant = Color(0xFFECECF0),
        onSurfaceVariant = Color(0xFF5C5C66),
        primaryContainer = Color(0xFFDDE3FF),
        onPrimaryContainer = Color(0xFF1B2A5C),
        errorContainer = Color(0xFFFFE0E0),
        onErrorContainer = Color(0xFF7A1F22),
        tertiaryContainer = Color(0xFFDDE3FF),
        onTertiaryContainer = Color(0xFF1B2A5C),
    )

/** Colores y texto de la pildora de estado. */
data class StatusColors(val background: Color, val foreground: Color, val label: String)

/**
 * ERROR reutiliza la gama calida de PENDING: son los dos estados que piden atencion. Lo que no
 * puede repetirse es la etiqueta, porque en la fase 3 ERROR y UNKNOWN compartian la suya y una
 * imagen caida decia "Sin verificar".
 */
fun statusColors(status: ImageStatus, dark: Boolean): StatusColors = when (status) {
    ImageStatus.PENDING -> {
        if (dark) {
            StatusColors(Color(0xFF3A1414), Color(0xFFFF8F8F), "Versión nueva")
        } else {
            StatusColors(Color(0xFFFFE3E3), Color(0xFFC92A2A), "Versión nueva")
        }
    }

    ImageStatus.ERROR -> {
        if (dark) {
            StatusColors(Color(0xFF3A2414), Color(0xFFFFC078), "Error")
        } else {
            StatusColors(Color(0xFFFFF0E0), Color(0xFFD9480F), "Error")
        }
    }

    ImageStatus.OK -> {
        if (dark) {
            StatusColors(Color(0xFF14321A), Color(0xFF8FD792), "Al día")
        } else {
            StatusColors(Color(0xFFE6F4EA), Color(0xFF2B7A39), "Al día")
        }
    }

    ImageStatus.UNKNOWN -> {
        if (dark) {
            StatusColors(Color(0xFF313136), Color(0xFF9A9AA2), "Sin verificar")
        } else {
            StatusColors(Color(0xFFECECF0), Color(0xFF5C5C66), "Sin verificar")
        }
    }
}

/**
 * Paleta de la pildora resaltada (`VersionPill`). **Comparte a proposito la paleta de `PENDING`**,
 * y no por falta de una propia: la pildora resaltada y la insignia de pendiente dicen lo mismo
 * -«hay una version nueva que mirar»-, asi que verlas del mismo color es lo correcto, y darles
 * gamas distintas seria inventar dos vocabularios para un unico mensaje.
 *
 * La consecuencia esta asumida: **retocar la gama de `PENDING` mueve tambien esta pildora**. Si
 * algun dia se quiere separarlas, esto deja de ser un alias y pasa a tener sus propios colores;
 * mientras siga siendo un alias, no hay nada que sincronizar. El nombre propio existe para que
 * `VersionPill` no tenga que conocer `ImageStatus` solo para pedir prestado un color de atencion.
 *
 * La etiqueta no se usa: la pildora no la pinta.
 */
fun highlightPill(dark: Boolean): StatusColors = statusColors(ImageStatus.PENDING, dark)

/**
 * Fondo «fantasma»: mas apagado que `surface`, mas vivo que `background`. Lo usan la cabecera
 * plegada de «Al día» y la línea de «Deshacer», dos sitios que quieren leerse como parte de la
 * lista y no como una tarjeta mas.
 */
fun ghostBackground(dark: Boolean): Color = if (dark) Color(0xFF202023) else Color(0xFFF1F1F4)

/**
 * Un tercer tono de texto, mas apagado que `onSurfaceVariant`: la cifra de una columna que no
 * aplica, el contador de una sección, la nota de pie. `onSurfaceVariant` ya esta ocupado por el
 * texto secundario -origen de la fila, subtítulo-, y ese uso pesa mas que este.
 */
fun mutedText(dark: Boolean): Color = if (dark) Color(0xFF5C5C66) else Color(0xFF9A9AA2)

/**
 * Borde y divisor fino -`--hair` en el diseño-, distinto de `surfaceVariant` -`--surfv`, fondo de
 * píldora- en tema claro (en oscuro los dos valores coinciden, así que ahí no hay cambio visible).
 * Antes de este token, cada borde/divisor usaba `surfaceVariant` porque era el único disponible,
 * y en tema claro salía `#ECECF0` en vez de `#E4E4EA`.
 */
fun hairline(dark: Boolean): Color = if (dark) Color(0xFF2A2A2E) else Color(0xFFE4E4EA)
