package io.github.shizukajiku.imagewatch.ui.images

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.shizukajiku.imagewatch.domain.ImageStatus
import io.github.shizukajiku.imagewatch.ui.components.AppSvg
import io.github.shizukajiku.imagewatch.ui.components.SvgIcon
import io.github.shizukajiku.imagewatch.ui.theme.Dwell
import io.github.shizukajiku.imagewatch.ui.theme.IconSize
import io.github.shizukajiku.imagewatch.ui.theme.LocalIsDark
import io.github.shizukajiku.imagewatch.ui.theme.Motion
import io.github.shizukajiku.imagewatch.ui.theme.Radius
import io.github.shizukajiku.imagewatch.ui.theme.Space
import io.github.shizukajiku.imagewatch.ui.theme.TypeScale
import io.github.shizukajiku.imagewatch.ui.theme.ghostBackground
import io.github.shizukajiku.imagewatch.ui.theme.mutedText
import io.github.shizukajiku.imagewatch.ui.theme.statusColors

// Los cinco anchos reservados de la fila -ninguno es un paso de la escala de Space, son los
// huecos que el diseño fija para que cambiar de estado nunca desplace a un vecino-.
private val AGE_WIDTH = 104.dp
private val SKIP_WIDTH = 44.dp
private val PILL_WIDTH = 104.dp
private val CHIP_WIDTH = 88.dp
private val KEBAB_WIDTH = 30.dp

// Relleno de la tarjeta de fila: no esta en la escala de Space, es el que ya trae el diseño.
private val CARD_PADDING_H = 14.dp
private val CARD_PADDING_V = 11.dp

/** Techo del latido de una fila UNKNOWN mientras se comprueba: cuanto se apaga en el punto mas bajo. */
private const val PULSE_MIN_ALPHA = 0.45f

/** Opacidad de una fila con una comprobacion individual en vuelo. */
private const val CHECKING_ALPHA = 0.45f

/**
 * La tarjeta compartida por las tres secciones: mismo borde, mismo relleno, misma rejilla de
 * columnas. Lo que cambia entre «Versión nueva», «No se pudo verificar» y «Al día» es el
 * contenido de cada celda, no la forma de la tarjeta.
 */
@Composable
private fun RowCard(
    background: Color,
    borderColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Surface(
        color = background,
        border = BorderStroke(1.dp, borderColor),
        shape = RoundedCornerShape(Radius.md),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.md),
            modifier = Modifier.padding(horizontal = CARD_PADDING_H, vertical = CARD_PADDING_V),
            content = content,
        )
    }
}

@Composable
private fun RowScope.NameCell(name: String, origin: String, nameColor: Color) {
    Column(Modifier.weight(1f)) {
        Text(
            name,
            fontWeight = FontWeight.SemiBold,
            fontSize = TypeScale.body,
            color = nameColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            origin,
            fontSize = TypeScale.caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RowScope.AgeCell(age: String, caption: String, color: Color) {
    Column(Modifier.width(AGE_WIDTH), horizontalAlignment = Alignment.End) {
        Text(
            age,
            fontSize = TypeScale.meta,
            color = color,
            textAlign = TextAlign.End,
            maxLines = 1,
        )
        Text(
            caption,
            fontSize = TypeScale.caption,
            color = mutedText(LocalIsDark.current),
            textAlign = TextAlign.End,
        )
    }
}

/** El contador de saltos («+2») a la izquierda de la píldora: hueco propio aunque este vacío. */
@Composable
private fun RowScope.SkipAndPillCell(skip: String, pillText: String, pillBackground: Color, pillForeground: Color) {
    Row(Modifier.width(SKIP_WIDTH + PILL_WIDTH), horizontalArrangement = Arrangement.End) {
        Text(
            skip,
            fontSize = TypeScale.caption,
            color = mutedText(LocalIsDark.current),
            textAlign = TextAlign.End,
            modifier = Modifier.width(SKIP_WIDTH).padding(end = Space.sm),
        )
        Surface(
            color = pillBackground,
            shape = RoundedCornerShape(Radius.pill),
            modifier = Modifier.width(PILL_WIDTH),
        ) {
            Text(
                pillText,
                color = pillForeground,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = TypeScale.meta,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun RowScope.ActionsCell(chip: @Composable () -> Unit, menu: @Composable () -> Unit) {
    Row(
        Modifier.width(CHIP_WIDTH + Space.xs + KEBAB_WIDTH),
        horizontalArrangement = Arrangement.spacedBy(Space.xs, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(CHIP_WIDTH), contentAlignment = Alignment.Center) { chip() }
        menu()
    }
}

@Composable
private fun ActionChip(text: String, background: Color, foreground: Color, onClick: () -> Unit) {
    Surface(
        color = background,
        shape = RoundedCornerShape(Radius.pill),
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Text(
            text,
            color = foreground,
            fontWeight = FontWeight.SemiBold,
            fontSize = TypeScale.meta,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.padding(vertical = Space.sm - 1.dp),
        )
    }
}

/**
 * Fila de «Versión nueva»: la píldora enseña la versión remota y el chip pide «Visto», que no
 * reconoce al momento -abre la ventana de Deshacer, ver [UndoRow]-.
 */
@Composable
fun PendingRow(
    row: ImageRowState,
    onAcknowledge: () -> Unit,
    onRefresh: () -> Unit,
    onEdit: () -> Unit,
    onToggleSilence: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = LocalIsDark.current
    val palette = statusColors(ImageStatus.PENDING, dark)
    if (row.checking) {
        CheckingRow(row, modifier)
        return
    }
    RowCard(
        background = MaterialTheme.colorScheme.surface,
        borderColor = palette.background,
        modifier = modifier.then(emphasisModifier(row)),
    ) {
        NameCell(row.name, row.registry, MaterialTheme.colorScheme.onSurface)
        AgeCell(row.age, "pendiente", palette.foreground)
        // El contador de versiones saltadas ("+2") queda vacío: el núcleo compara local contra
        // remota, no cuenta las publicaciones intermedias, así que no hay una cifra real que
        // enseñar aquí todavía. El hueco de SKIP_WIDTH se reserva igual para que la píldora no
        // se mueva el día que exista.
        SkipAndPillCell("", row.remote, palette.background, palette.foreground)
        ActionsCell(
            chip = {
                ActionChip(
                    "Visto",
                    MaterialTheme.colorScheme.primaryContainer,
                    MaterialTheme.colorScheme.onPrimaryContainer,
                    onAcknowledge,
                )
            },
            menu = { RowMenu(row, onAcknowledge, onRefresh, onEdit, onToggleSilence, onDelete) },
        )
    }
}

/** Fila de «No se pudo verificar»: la píldora dice «Error» y el chip ofrece reintentar. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ErrorRow(
    row: ImageRowState,
    onRefresh: () -> Unit,
    onEdit: () -> Unit,
    onToggleSilence: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = LocalIsDark.current
    val palette = statusColors(ImageStatus.ERROR, dark)
    if (row.checking) {
        CheckingRow(row, modifier)
        return
    }
    val content: @Composable () -> Unit = {
        RowCard(
            background = MaterialTheme.colorScheme.surface,
            borderColor = palette.background,
            modifier = modifier.then(emphasisModifier(row)),
        ) {
            NameCell(row.name, row.registry, palette.foreground)
            AgeCell(row.age, "falla", palette.foreground)
            SkipAndPillCell("", "Error", palette.background, palette.foreground)
            ActionsCell(
                chip = {
                    ActionChip(
                        "Reintentar",
                        MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.colorScheme.onSurfaceVariant,
                        onRefresh,
                    )
                },
                menu = { RowMenu(row, onAcknowledge = null, onRefresh, onEdit, onToggleSilence, onDelete) },
            )
        }
    }
    row.error?.let { message ->
        TooltipArea(tooltip = {
            Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(Radius.sm)) {
                Text(
                    message,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontSize = TypeScale.caption,
                    modifier = Modifier.padding(Space.sm),
                )
            }
        }) { content() }
    } ?: content()
}

/**
 * Fila de «Al día»: la píldora usa la paleta que le pase el llamador -verde al día, o gris «sin
 * verificar» cuando no hay conexión-, y el hueco de 88 px enseña «En silencio» si la imagen lo
 * está, o queda vacío.
 */
@Composable
fun OkRow(
    row: ImageRowState,
    ageCaption: String,
    pillBackground: Color,
    pillForeground: Color,
    verifying: Boolean,
    onAcknowledge: (() -> Unit)?,
    onRefresh: () -> Unit,
    onEdit: () -> Unit,
    onToggleSilence: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (row.checking) {
        CheckingRow(row, modifier)
        return
    }
    val pulsing = verifying && row.unverified
    val alpha = if (pulsing) unverifiedPulseAlpha() else 1f
    RowCard(
        background = MaterialTheme.colorScheme.surface,
        borderColor = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.alpha(alpha).then(emphasisModifier(row)),
    ) {
        NameCell(row.name, row.registry, MaterialTheme.colorScheme.onSurface)
        AgeCell(row.age.ifEmpty { "—" }, ageCaption, MaterialTheme.colorScheme.onSurfaceVariant)
        SkipAndPillCell("", if (row.remote != "—") row.remote else row.local, pillBackground, pillForeground)
        ActionsCell(
            chip = {
                if (row.muted) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SvgIcon(AppSvg.BELL_OFF, mutedText(LocalIsDark.current), Modifier.size(IconSize.sm))
                        Text("En silencio", fontSize = TypeScale.caption, color = mutedText(LocalIsDark.current))
                    }
                }
            },
            menu = { RowMenu(row, onAcknowledge, onRefresh, onEdit, onToggleSilence, onDelete) },
        )
    }
}

@Composable
private fun unverifiedPulseAlpha(): Float {
    val transition = rememberInfiniteTransition(label = "pulse")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = PULSE_MIN_ALPHA,
        animationSpec = infiniteRepeatable(tween(Motion.PULSE), RepeatMode.Reverse),
        label = "pulseAlpha",
    )
    return pulse
}

/**
 * Fila con una comprobación individual en vuelo: se atenúa y su mitad derecha se sustituye por un
 * giro y la palabra «comprobando», en vez de enseñar una versión que ya sabemos que va a cambiar.
 */
@Composable
private fun CheckingRow(row: ImageRowState, modifier: Modifier = Modifier) {
    val rotation by rememberInfiniteTransition(label = "spin")
        .animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(700, easing = LinearEasing)),
            label = "spinAngle",
        )
    RowCard(
        background = MaterialTheme.colorScheme.surface,
        borderColor = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.alpha(CHECKING_ALPHA),
    ) {
        NameCell(row.name, row.registry, MaterialTheme.colorScheme.onSurface)
        Row(
            Modifier.width(
                AGE_WIDTH + Space.md + SKIP_WIDTH + PILL_WIDTH + Space.md + CHIP_WIDTH + Space.xs + KEBAB_WIDTH,
            ),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SvgIcon(
                AppSvg.REFRESH,
                MaterialTheme.colorScheme.onSurfaceVariant,
                Modifier.size(IconSize.sm).rotate(rotation),
            )
            Spacer(Modifier.width(Space.sm))
            Text("comprobando", fontSize = TypeScale.meta, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Línea de «Deshacer»: sustituye a la fila durante [Dwell.UNDO_MILLIS] tras pedir «Visto». La
 * barra anima el mismo tiempo que corre el temporizador real del view model -ambas leen
 * `Dwell.UNDO_MILLIS`-, así que no hay dos duraciones que mantener sincronizadas a mano.
 */
@Composable
fun UndoRow(name: String, onUndo: () -> Unit, modifier: Modifier = Modifier) {
    val dark = LocalIsDark.current
    val progress = remember(name) { Animatable(1f) }
    LaunchedEffect(name) {
        progress.animateTo(0f, tween(Dwell.UNDO_MILLIS.toInt(), easing = LinearEasing))
    }
    Surface(
        color = ghostBackground(dark),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(Radius.md),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.md),
                modifier = Modifier.padding(horizontal = CARD_PADDING_H).height(41.dp),
            ) {
                SvgIcon(AppSvg.CHECK, statusColors(ImageStatus.OK, dark).foreground, Modifier.size(IconSize.sm))
                Text(
                    "Marcada como vista",
                    fontSize = TypeScale.meta,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    name,
                    fontSize = TypeScale.meta,
                    color = mutedText(dark),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.weight(1f))
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(Radius.pill),
                    onClick = onUndo,
                ) {
                    Text(
                        "Deshacer",
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = TypeScale.meta,
                        modifier = Modifier.padding(horizontal = Space.md, vertical = Space.xs + 1.dp),
                    )
                }
            }
            Box(Modifier.fillMaxWidth().height(3.dp).background(MaterialTheme.colorScheme.surfaceVariant)) {
                Box(
                    Modifier.fillMaxHeight()
                        .fillMaxWidth(progress.value.coerceIn(0f, 1f))
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}

/** Menú de acciones de una fila: lo mismo para las tres secciones, solo cambia si «Visto» aplica. */
@Composable
private fun RowMenu(
    row: ImageRowState,
    onAcknowledge: (() -> Unit)?,
    onRefresh: () -> Unit,
    onEdit: () -> Unit,
    onToggleSilence: () -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val reference = "${row.registry}:${if (row.remote != "—") row.remote else row.local}"

    Box {
        IconButton({ expanded = true }, modifier = Modifier.size(KEBAB_WIDTH)) {
            SvgIcon(AppSvg.KEBAB, MaterialTheme.colorScheme.onSurfaceVariant, Modifier.size(IconSize.md))
        }
        DropdownMenu(expanded, { expanded = false }) {
            if (onAcknowledge != null) {
                DropdownMenuItem(
                    text = { Text("Visto") },
                    leadingIcon = {
                        SvgIcon(AppSvg.CHECK, MaterialTheme.colorScheme.onSurfaceVariant, Modifier.size(IconSize.sm))
                    },
                    onClick = {
                        expanded = false
                        onAcknowledge()
                    },
                )
            }
            DropdownMenuItem(
                text = { Text("Comprobar ahora") },
                leadingIcon = {
                    SvgIcon(AppSvg.REFRESH, MaterialTheme.colorScheme.onSurfaceVariant, Modifier.size(IconSize.sm))
                },
                onClick = {
                    expanded = false
                    onRefresh()
                },
            )
            DropdownMenuItem(
                text = { Text("Copiar referencia") },
                leadingIcon = {
                    SvgIcon(AppSvg.COPY, MaterialTheme.colorScheme.onSurfaceVariant, Modifier.size(IconSize.sm))
                },
                onClick = {
                    expanded = false
                    clipboard.setText(AnnotatedString(reference))
                },
            )
            DropdownMenuItem(
                text = { Text("Renombrar") },
                leadingIcon = {
                    SvgIcon(AppSvg.PENCIL, MaterialTheme.colorScheme.onSurfaceVariant, Modifier.size(IconSize.sm))
                },
                onClick = {
                    expanded = false
                    onEdit()
                },
            )
            DropdownMenuItem(
                text = { Text(if (row.muted) "Reactivar avisos" else "Silenciar avisos") },
                leadingIcon = {
                    SvgIcon(
                        if (row.muted) AppSvg.BELL else AppSvg.BELL_OFF,
                        MaterialTheme.colorScheme.onSurfaceVariant,
                        Modifier.size(IconSize.sm),
                    )
                },
                onClick = {
                    expanded = false
                    onToggleSilence()
                },
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Quitar de la lista", color = MaterialTheme.colorScheme.error) },
                leadingIcon = { SvgIcon(AppSvg.TRASH, MaterialTheme.colorScheme.error, Modifier.size(IconSize.sm)) },
                onClick = {
                    expanded = false
                    onDelete()
                },
            )
        }
    }
}

/** Fondo de resaltado -señalada desde un aviso, o novedad recién llegada-, igual que antes. */
@Composable
private fun emphasisModifier(row: ImageRowState): Modifier = when (row.emphasis) {
    RowEmphasis.SENALADA -> Modifier.background(
        MaterialTheme.colorScheme.primaryContainer,
        RoundedCornerShape(Radius.md),
    )

    RowEmphasis.NOVEDAD -> Modifier.background(bumpColor(), RoundedCornerShape(Radius.md))

    RowEmphasis.NINGUNO -> Modifier
}

/**
 * Late en lugar de encenderse fijo: un color estatico se confundiria con el resaltado de «Ver».
 */
@Composable
private fun bumpColor(): Color {
    val transition = rememberInfiniteTransition(label = "bump")
    val fraction by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(Motion.BUMP), RepeatMode.Reverse),
        label = "bumpAlpha",
    )
    return MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = fraction)
}
