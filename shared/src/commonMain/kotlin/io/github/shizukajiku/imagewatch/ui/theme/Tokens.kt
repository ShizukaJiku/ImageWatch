package io.github.shizukajiku.imagewatch.ui.theme

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Las medidas y los tiempos de la interfaz, en un solo sitio, igual que el color vive en
 * `Colors.kt`. Cambiar el ritmo o la densidad no debe obligar a abrir cinco ficheros de lógica.
 *
 * Son `object` y no `CompositionLocal` a proposito: un local permitiria temas de densidad
 * distintos —compacto, comodo— y hoy no hay ninguno. Cuando exista un segundo, la migracion es
 * mecanica: `Space.md` pasa a `LocalSpace.current.md`.
 */
object Space {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
}

/** La pildora es la forma de marca: versiones, insignias, boton de agregar, buscador. */
object Radius {
    val sm = 6.dp
    val md = 10.dp
    val pill = 999.dp
}

/**
 * Tres tamanos de icono: en linea con el texto, de accion y de cabecera.
 *
 * Los seis tamanos que habia antes de esta escala -13, 14, 15, 16, 17 y 18 dp- se reparten en
 * pares consecutivos (13-14, 15-16, 17-18): el unico cambio visible que admite esta migracion es
 * el ajuste de 1 dp en los iconos que caian en el extremo bajo de cada par. Son cinco, y estos:
 *
 * - PLUS, el del boton «Agregar imagen» (`ImagesScreen`): 13 -> 14 (`sm`).
 * - CLOSE, la cruz de la tarjeta de aviso (`ToastWindow`): 13 -> 14 (`sm`).
 * - LOGO, el de la barra de titulo propia (`TitleBar`): 15 -> 16 (`md`).
 * - REFRESH, el de cada fila (`ImageRow`): 15 -> 16 (`md`).
 * - REFRESH, el de «comprobar todas» de la cabecera (`ImagesScreen`): 17 -> 18 (`lg`).
 *
 * Ningun otro icono cambio de tamano, y ninguno se movio mas de 1 dp.
 */
object IconSize {
    val sm = 14.dp
    val md = 16.dp
    val lg = 18.dp
}

/** Cuatro tamanos, cada uno con un oficio. Un quinto tamano es una decision nueva. */
object TypeScale {
    val title = 16.sp
    val body = 13.sp
    val meta = 12.sp
    val caption = 11.sp
}

/** Ritmo de las animaciones, en milisegundos. */
object Motion {
    const val QUICK = 220
    const val NORMAL = 320
    const val EMPHASIS = 400
    const val PULSE = 700
    const val BUMP = 600
}

/**
 * Cuanto vive un aviso visual antes de apagarse solo. Es UX y no animacion: dice cuanto dura el
 * mensaje, no como se mueve.
 */
object Dwell {
    const val HIGHLIGHT_MILLIS = 4000L
    const val BUMP_MILLIS = 3000L

    /** Cuanto se enseña la linea de «Deshacer» tras marcar «Visto» antes de reconocer de verdad. */
    const val UNDO_MILLIS = 4000L

    /** Cuanto dura el rastro de «X se ha movido aquí» en la línea plegada de «Al día». */
    const val TRACE_MILLIS = 4000L
}
