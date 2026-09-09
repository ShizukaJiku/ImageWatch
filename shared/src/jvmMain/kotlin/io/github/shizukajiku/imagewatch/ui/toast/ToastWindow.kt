package io.github.shizukajiku.imagewatch.ui.toast

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import io.github.shizukajiku.imagewatch.domain.ImageStatus
import io.github.shizukajiku.imagewatch.ui.components.AppSvg
import io.github.shizukajiku.imagewatch.ui.components.SvgIcon
import io.github.shizukajiku.imagewatch.ui.theme.Elevation
import io.github.shizukajiku.imagewatch.ui.theme.IconSize
import io.github.shizukajiku.imagewatch.ui.theme.Layout
import io.github.shizukajiku.imagewatch.ui.theme.Motion
import io.github.shizukajiku.imagewatch.ui.theme.Radius
import io.github.shizukajiku.imagewatch.ui.theme.Space
import io.github.shizukajiku.imagewatch.ui.theme.StatusColors
import io.github.shizukajiku.imagewatch.ui.theme.TypeScale
import io.github.shizukajiku.imagewatch.ui.theme.statusColors
import kotlinx.coroutines.delay
import java.awt.GraphicsEnvironment
import kotlin.math.roundToInt

// Alto de la capa de toasts. Compensacion de composicion que fija el tamaño de la ventana sin
// decoracion; desaparece cuando la ventana deje de tener una altura fija. El ancho y el margen
// de pantalla viven ahora en `Layout` (`toastWidth`, `toastScreenMargin`).
private const val LAYER_HEIGHT = 420

/**
 * Capa de toasts: una única ventana sin decoración anclada abajo a la derecha.
 *
 * Tres banderas no son negociables. `transparent` **exige** `undecorated`, o Compose lanza. Sin
 * `focusable = false` la ventana roba el foco mientras el usuario escribe en otra aplicación. Y
 * `alwaysOnTop` es lo único que hace que un aviso sirva de algo cuando la ventana principal está
 * cerrada y hay otra encima.
 *
 * La posición se calcula con `getMaximumWindowBounds`, que descuenta la barra de tareas. El tamaño
 * de pantalla crudo no lo hace y colocaría los toasts por debajo de ella.
 *
 * Sin receptor `ApplicationScope`: lo único que ese scope aporta es `exitApplication()`, y aquí
 * nadie cierra la aplicación. El `Window` de dentro es una función de nivel superior, no una
 * extensión suya.
 */
@Composable
fun ToastLayer(
    toasts: List<Toast>,
    onPause: (Long) -> Unit,
    onResume: (Long) -> Unit,
    onExitFinished: (Long) -> Unit,
    onAction: (String?, ToastKind) -> Unit,
) {
    if (toasts.isEmpty()) {
        return
    }
    val bounds = remember { GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds }
    val position = remember(bounds) {
        WindowPosition(
            x = (bounds.x + bounds.width).dp - Layout.toastWidth - Layout.toastScreenMargin,
            y = (bounds.y + bounds.height - LAYER_HEIGHT).dp - Layout.toastScreenMargin,
        )
    }

    Window(
        onCloseRequest = {},
        state = rememberWindowState(position = position, width = Layout.toastWidth, height = LAYER_HEIGHT.dp),
        undecorated = true,
        transparent = true,
        alwaysOnTop = true,
        focusable = false,
        resizable = false,
        title = "ImageWatch — avisos",
    ) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(Space.sm, Alignment.Bottom),
            modifier = Modifier.fillMaxSize(),
        ) {
            // Solo los que caben: el resto de la cola espera turno, no se descarta. ToastState
            // solo arranca reloj para los que caben aqui, asi que uno que no se pinta tampoco
            // consume su tiempo, y entra en cuanto se libera hueco.
            items(toasts.take(TOASTS_VISIBLES), key = { it.id }) { toast ->
                // animateItem cierra el hueco cuando uno desaparece; sin el, los de abajo
                // saltarian de golpe.
                ToastCard(
                    toast = toast,
                    onPause = { onPause(toast.id) },
                    onResume = { onResume(toast.id) },
                    onExitFinished = { onExitFinished(toast.id) },
                    onAction = { onAction(toast.imageName, toast.kind) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ToastCard(
    toast: Toast,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onExitFinished: () -> Unit,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var visible by remember { mutableStateOf(false) }
    // Salida por accion del usuario -boton de accion o la cruz-: mismo camino de salida que el
    // descarte automatico de ToastState, asi que ambos deslizan igual hacia fuera y no se esfuman.
    var saliendoManual by remember { mutableStateOf(false) }
    val saliendo = toast.leaving || saliendoManual
    // El puntero encima tambien lo sabe ToastState -por onPause/onResume-, pero la tarjeta
    // necesita su propia copia: es la clave que arranca y detiene la animacion de la barra.
    var puntero by remember { mutableStateOf(false) }
    // La barra la anima la tarjeta, contra el reloj de fotogramas de Compose y no contra un bucle
    // de tics: asi llega a cero en el mismo instante en que ToastState marca `leaving`, sin la
    // deriva que acumulaba sumar delays de 16 ms. ToastState solo le da la condicion inicial.
    val progreso = remember { Animatable(toast.progress) }

    LaunchedEffect(toast.id) { visible = true }

    // Cancelar este efecto -al posar el puntero o al empezar a salir- detiene la animacion donde
    // este; al reanudarla se anima lo que quede, no el total.
    LaunchedEffect(puntero, saliendo) {
        if (!puntero && !saliendo) {
            progreso.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = (progreso.value * toast.durationMillis).toInt().coerceAtLeast(0),
                    easing = LinearEasing,
                ),
            )
        }
    }

    // Cuando toca irse -por reloj de ToastState o por accion del usuario- se anima la salida
    // antes de avisar. Avisando directamente, la tarjeta desaparecia con el fundido por defecto
    // de la lista y el deslizamiento inverso que pide el diseno no llegaba a verse nunca.
    LaunchedEffect(saliendo) {
        if (saliendo) {
            visible = false
            // Unica `delay` fuera de ToastState/ImagesViewModel, y se queda: no mide un tiempo de
            // negocio -cuanto vive el aviso lo decide ToastState-, solo espera a que termine SU
            // PROPIA animacion de salida, con la misma duracion (Motion.QUICK) que el `exit` de
            // abajo. Subirla al view model obligaria a propagar hacia arriba un evento "animacion
            // terminada" y a bajar de vuelta la orden de retirar, para acabar esperando lo mismo:
            // mas cableado, cero cambio observable.
            delay(Motion.QUICK.toLong())
            onExitFinished()
        }
    }

    val dark = isSystemInDarkTheme()
    val tono = tonoDe(toast.kind, dark)

    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally { it } + fadeIn(),
        exit = slideOutHorizontally(tween(Motion.QUICK)) { it } + fadeOut(tween(Motion.QUICK)),
        modifier = modifier,
    ) {
        Surface(
            shape = RoundedCornerShape(Radius.md),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = Elevation.toast,
            border = BorderStroke(1.dp, tono.background),
            modifier = Modifier
                // 6.dp de margen en cada lado de la tarjeta: junto con el ancho de abajo
                // (Layout.toastWidth - 12.dp = 2 x 6.dp) mantiene la tarjeta centrada en la capa.
                .padding(6.dp)
                .width(Layout.toastWidth - 12.dp)
                .height(Layout.toastHeight)
                .onPointerEvent(PointerEventType.Enter) {
                    puntero = true
                    onPause()
                }
                .onPointerEvent(PointerEventType.Exit) {
                    puntero = false
                    onResume()
                },
        ) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Space.sm),
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier.fillMaxWidth().padding(start = Space.md, top = 10.dp, end = Space.md),
                ) {
                    Box(
                        Modifier.size(Layout.toastIcon).clip(CircleShape).background(tono.background),
                        contentAlignment = Alignment.Center,
                    ) {
                        SvgIcon(iconoDe(toast.kind), tono.foreground, Modifier.size(IconSize.sm))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            toast.title,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = TypeScale.body,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (toast.sub.isNotEmpty()) {
                            Text(
                                toast.sub,
                                fontSize = TypeScale.caption,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (toast.action.isNotEmpty()) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(Radius.pill),
                            onClick = {
                                onAction()
                                saliendoManual = true
                            },
                            modifier = Modifier.width(Layout.toastAction),
                        ) {
                            Text(
                                toast.action,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = TypeScale.caption,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(vertical = Space.xs),
                            )
                        }
                    }
                    IconButton({ saliendoManual = true }, Modifier.size(Layout.toastClose)) {
                        SvgIcon(AppSvg.CLOSE, MaterialTheme.colorScheme.onSurfaceVariant, Modifier.size(IconSize.sm))
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = Space.md, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        toast.meta,
                        fontFamily = FontFamily.Monospace,
                        fontSize = TypeScale.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).widthIn(max = Layout.toastMetaMax),
                    )
                    Text(
                        "${(progreso.value * toast.durationMillis / 1000f).roundToInt().coerceAtLeast(0)} s",
                        fontFamily = FontFamily.Monospace,
                        fontSize = TypeScale.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(Layout.toastTimer),
                    )
                }
                // Cuando expira el aviso lo decide ToastState; cuanta barra queda dibujada, esta
                // animacion. Leerla con lambda mantiene el repintado en el paso de dibujo: no
                // recompone la tarjeta, y mucho menos la lista que lee el resto de la aplicacion.
                LinearProgressIndicator(
                    progress = { progreso.value },
                    color = tono.background,
                    trackColor = Color.Transparent,
                    modifier = Modifier.fillMaxWidth().height(Layout.toastBar),
                )
            }
        }
    }
}

private fun iconoDe(kind: ToastKind): AppSvg = when (kind) {
    ToastKind.NUEVA, ToastKind.SALTADAS -> AppSvg.BELL
    ToastKind.ERROR -> AppSvg.WARNING
    ToastKind.RESUMEN -> AppSvg.CHECK_ALL
}

/**
 * Tono por tipo. `NUEVA`/`SALTADAS` reutilizan la paleta `PENDING` -misma gama que la insignia de
 * la fila, es el mismo mensaje-, `ERROR` la suya, y `RESUMEN` usa el acento porque no describe un
 * estado de imagen sino una acción de la interfaz ("ver todas").
 */
private fun tonoDe(kind: ToastKind, dark: Boolean): StatusColors = when (kind) {
    ToastKind.NUEVA, ToastKind.SALTADAS -> statusColors(ImageStatus.PENDING, dark)

    ToastKind.ERROR -> statusColors(ImageStatus.ERROR, dark)

    ToastKind.RESUMEN -> if (dark) {
        StatusColors(Color(0xFF2B3557), Color(0xFFD9E0FF), "")
    } else {
        StatusColors(Color(0xFFDDE3FF), Color(0xFF1B2A5C), "")
    }
}
