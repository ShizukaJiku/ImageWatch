package io.github.shizukajiku.imagewatch.ui.images

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.shizukajiku.imagewatch.domain.ImageStatus
import io.github.shizukajiku.imagewatch.ui.components.AppSvg
import io.github.shizukajiku.imagewatch.ui.components.SvgIcon
import io.github.shizukajiku.imagewatch.ui.theme.IconSize
import io.github.shizukajiku.imagewatch.ui.theme.Layout
import io.github.shizukajiku.imagewatch.ui.theme.LocalIsDark
import io.github.shizukajiku.imagewatch.ui.theme.Motion
import io.github.shizukajiku.imagewatch.ui.theme.Radius
import io.github.shizukajiku.imagewatch.ui.theme.Space
import io.github.shizukajiku.imagewatch.ui.theme.TabularNums
import io.github.shizukajiku.imagewatch.ui.theme.TypeScale
import io.github.shizukajiku.imagewatch.ui.theme.focusRing
import io.github.shizukajiku.imagewatch.ui.theme.ghostBackground
import io.github.shizukajiku.imagewatch.ui.theme.mutedText
import io.github.shizukajiku.imagewatch.ui.theme.statusColors

/** Una entrada de la lista: cabecera de sección o fila. Un único `LazyColumn` para que alta, baja
 * y reordenación sigan animando con `animateItem()`, aunque la lista se vea como tres secciones. */
private sealed interface Entry {
    data object PendingHeader : Entry
    data class PendingItem(val row: ImageRowState) : Entry
    data object ErrorHeader : Entry
    data class ErrorItem(val row: ImageRowState) : Entry
    data class OkHeader(val open: Boolean) : Entry
    data class OkItem(val row: ImageRowState) : Entry
}

private fun Entry.key(): String = when (this) {
    Entry.PendingHeader -> "header:pending"
    is Entry.PendingItem -> "row:${row.name}"
    Entry.ErrorHeader -> "header:error"
    is Entry.ErrorItem -> "row:${row.name}"
    is Entry.OkHeader -> "header:ok"
    is Entry.OkItem -> "row:${row.name}"
}

private fun Entry.rowName(): String? = when (this) {
    is Entry.PendingItem -> row.name
    is Entry.ErrorItem -> row.name
    is Entry.OkItem -> row.name
    else -> null
}

@Composable
fun ImagesScreen(
    state: ImagesUiState,
    mutedAll: Boolean,
    onSearchChange: (String) -> Unit,
    onAdd: () -> Unit,
    onAcknowledge: (String) -> Unit,
    onAcknowledgeAll: () -> Unit,
    onRefresh: (String?) -> Unit,
    onDelete: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onToggleMuteAll: () -> Unit,
    onToggleSilence: (String) -> Unit,
    onToggleExpand: (String) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Header(state, mutedAll, onSearchChange, onAdd, onOpenSettings, onAcknowledgeAll, onRefresh, onToggleMuteAll)

        if (state.total == 0) {
            // Solo «Sin imágenes vigiladas» es un vacío de verdad -no hay nada en local que
            // enseñar-: los otros dos vacíos del diseño («Al día», «Sin conexión») siguen
            // mostrando la lista con lo que ya se sabe, así que llevan aviso y secciones, no esto.
            Box(Modifier.weight(1f)) { EmptyState(state.pollIntervalSeconds, onAdd) }
        } else {
            NoticeBanner(state) { onRefresh(null) }

            val pendingRows = state.rows.filter { it.status == ImageStatus.PENDING }
            val errorRows = state.rows.filter { it.status == ImageStatus.ERROR }
            val okRows = state.rows.filter { it.status != ImageStatus.PENDING && it.status != ImageStatus.ERROR }

            // Blueprint: «Por defecto plegada por encima de 12 imágenes». `rememberSaveable`
            // conserva la elección posterior del usuario; el valor inicial solo decide el arranque.
            var okOpen by rememberSaveable { mutableStateOf(okRows.size <= 12) }
            val listState = rememberLazyListState()

            // Qué fila tiene el foco de teclado ahora mismo -no cuál está desplegada, ese es
            // `state.expandedRow`-. Solo lo necesita Espacio, que decide a qué imagen reconocer;
            // Enter y Escape no necesitan saber el nombre.
            var focusedRowName by remember { mutableStateOf<String?>(null) }
            val focusManager = LocalFocusManager.current

            val entries = buildList {
                if (pendingRows.isNotEmpty()) {
                    add(Entry.PendingHeader)
                    pendingRows.forEach { add(Entry.PendingItem(it)) }
                }
                if (errorRows.isNotEmpty()) {
                    add(Entry.ErrorHeader)
                    errorRows.forEach { add(Entry.ErrorItem(it)) }
                }
                if (okRows.isNotEmpty()) {
                    add(Entry.OkHeader(okOpen))
                    if (okOpen) okRows.forEach { add(Entry.OkItem(it)) }
                }
            }

            // Reaccion a un estado, no una regla: el view model ya decide cuanto dura el
            // resaltado, aqui solo se busca la fila senalada y se desplaza hasta ella. La clave es
            // el NOMBRE, no el indice: colgado del indice, borrar otra imagen movia la fila
            // resaltada de posicion.
            val senalada = state.rows.firstOrNull { it.emphasis == RowEmphasis.SENALADA }?.name
            LaunchedEffect(senalada, entries) {
                val posicion = entries.indexOfFirst { it.rowName() == senalada }
                if (senalada != null && posicion >= 0) {
                    listState.animateScrollToItem(posicion)
                }
            }

            LazyColumn(
                Modifier.weight(1f).fillMaxWidth().onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.DirectionDown -> {
                            focusManager.moveFocus(FocusDirection.Down)
                            true
                        }

                        Key.DirectionUp -> {
                            focusManager.moveFocus(FocusDirection.Up)
                            true
                        }

                        Key.Spacebar -> {
                            val name = focusedRowName
                            if (name != null && pendingRows.any { it.name == name }) {
                                onAcknowledge(name)
                                true
                            } else {
                                false
                            }
                        }

                        Key.Enter, Key.NumPadEnter -> {
                            val name = focusedRowName
                            if (name != null) {
                                onToggleExpand(name)
                                true
                            } else {
                                false
                            }
                        }

                        Key.Escape -> {
                            val open = state.expandedRow
                            if (open != null) {
                                onToggleExpand(open)
                                true
                            } else {
                                false
                            }
                        }

                        else -> false
                    }
                },
                state = listState,
                contentPadding = PaddingValues(
                    start = Space.xl,
                    end = Space.xl,
                    bottom = Space.md,
                ),
                verticalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                items(entries, key = { it.key() }) { entry ->
                    // Entrada, salida y reordenación de fila con la curva de énfasis (Blueprint 06:
                    // «400 ms con énfasis»), en vez del spec por defecto de `animateItem`.
                    val itemMotion = Modifier.animateItem(
                        fadeInSpec = tween(Motion.EMPHASIS),
                        fadeOutSpec = tween(Motion.EMPHASIS),
                        placementSpec = tween(Motion.EMPHASIS, easing = Motion.emphasisEasing),
                    )
                    when (entry) {
                        Entry.PendingHeader ->
                            PendingSectionHeader(pendingRows.size, onAcknowledgeAll)

                        is Entry.PendingItem ->
                            PendingRow(
                                row = entry.row,
                                expanded = state.expandedRow == entry.row.name,
                                onToggleExpand = { onToggleExpand(entry.row.name) },
                                onAcknowledge = { onAcknowledge(entry.row.name) },
                                onRefresh = { onRefresh(entry.row.name) },
                                onToggleSilence = { onToggleSilence(entry.row.name) },
                                onDelete = { onDelete(entry.row.name) },
                                onFocusedChange = { focused ->
                                    focusedRowName = if (focused) {
                                        entry.row.name
                                    } else {
                                        focusedRowName.takeUnless { it == entry.row.name }
                                    }
                                },
                                modifier = itemMotion,
                            )

                        Entry.ErrorHeader ->
                            ErrorSectionHeader(errorRows.size) { onRefresh(null) }

                        is Entry.ErrorItem ->
                            ErrorRow(
                                row = entry.row,
                                expanded = state.expandedRow == entry.row.name,
                                onToggleExpand = { onToggleExpand(entry.row.name) },
                                onRefresh = { onRefresh(entry.row.name) },
                                onToggleSilence = { onToggleSilence(entry.row.name) },
                                onDelete = { onDelete(entry.row.name) },
                                onFocusedChange = { focused ->
                                    focusedRowName = if (focused) {
                                        entry.row.name
                                    } else {
                                        focusedRowName.takeUnless { it == entry.row.name }
                                    }
                                },
                                modifier = itemMotion,
                            )

                        is Entry.OkHeader ->
                            OkSectionHeader(
                                open = entry.open,
                                count = okRows.size,
                                names = okRows.joinToString(", ") { it.name },
                                trace = state.trace?.takeIf { name -> okRows.any { it.name == name } },
                                onToggle = { okOpen = !okOpen },
                            )

                        is Entry.OkItem -> {
                            val disconnected = state.allFailing
                            val palette = if (disconnected) {
                                statusColors(ImageStatus.UNKNOWN, LocalIsDark.current)
                            } else {
                                statusColors(ImageStatus.OK, LocalIsDark.current)
                            }
                            OkRow(
                                row = entry.row,
                                ageCaption = if (disconnected) "sin verificar" else "comprobada",
                                pillBackground = palette.background,
                                pillForeground = palette.foreground,
                                verifying = state.verifying,
                                expanded = state.expandedRow == entry.row.name,
                                onToggleExpand = { onToggleExpand(entry.row.name) },
                                onRefresh = { onRefresh(entry.row.name) },
                                onToggleSilence = { onToggleSilence(entry.row.name) },
                                onDelete = { onDelete(entry.row.name) },
                                onFocusedChange = { focused ->
                                    focusedRowName = if (focused) {
                                        entry.row.name
                                    } else {
                                        focusedRowName.takeUnless { it == entry.row.name }
                                    }
                                },
                                modifier = itemMotion,
                            )
                        }
                    }
                }
            }
        }

        Footer(state, mutedAll)
    }
}

@Composable
private fun Header(
    state: ImagesUiState,
    mutedAll: Boolean,
    onSearchChange: (String) -> Unit,
    onAdd: () -> Unit,
    onOpenSettings: () -> Unit,
    onAcknowledgeAll: () -> Unit,
    onRefresh: (String?) -> Unit,
    onToggleMuteAll: () -> Unit,
) {
    var searchExpanded by rememberSaveable { mutableStateOf(state.search.isNotEmpty()) }

    Column(Modifier.padding(start = Space.xl, top = Space.lg, end = Space.xl, bottom = Space.md)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                // state.pending ya cuenta las encoladas -son PENDING igual, solo esperan tras la
                // banda-: sumar queuedCount aqui las contaria dos veces.
                Text(
                    "${state.pending} imágenes que atender",
                    fontWeight = FontWeight.Bold,
                    fontSize = TypeScale.title,
                    style = TabularNums,
                )
                Text(
                    "de ${state.total} vigiladas · comprobando cada ${state.pollIntervalSeconds} s",
                    fontSize = TypeScale.meta,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = TabularNums,
                )
            }
            // Comprobar todas ya. Junto a los demas iconos y no en la barra de sondeo: es una
            // accion sobre la lista entera, como agregar, no un control del calendario.
            IconButton({ onRefresh(null) }) {
                SvgIcon(AppSvg.REFRESH, MaterialTheme.colorScheme.onSurfaceVariant, Modifier.size(IconSize.lg))
            }
            // Solo aparece si hay algo que dar por visto: un boton que no hace nada ensena a
            // desconfiar de los botones.
            if (state.canAcknowledgeAll) {
                IconButton(onAcknowledgeAll) {
                    SvgIcon(AppSvg.CHECK_ALL, MaterialTheme.colorScheme.primary, Modifier.size(IconSize.lg))
                }
            }
            // Buscador replegado a icono (Blueprint «Cabecera de la bandeja»): se expande a una
            // píldora de 34 dp en su sitio -no empuja el titular ni crece en vertical-. Se
            // repliega al perder el foco solo si está vacío y ya llegó a tenerlo: así no colapsa
            // en el fotograma en que aparece, antes de que el foco aterrice.
            if (searchExpanded) {
                val searchFocus = remember { FocusRequester() }
                var hasHadFocus by remember { mutableStateOf(false) }
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(Radius.pill),
                    modifier = Modifier.width(Layout.searchPill).height(34.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Space.sm),
                        modifier = Modifier.padding(horizontal = Space.md),
                    ) {
                        SvgIcon(
                            AppSvg.SEARCH,
                            MaterialTheme.colorScheme.onSurfaceVariant,
                            Modifier.size(IconSize.sm),
                        )
                        BasicTextField(
                            value = state.search,
                            onValueChange = onSearchChange,
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = TypeScale.body,
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(searchFocus)
                                .onFocusChanged {
                                    if (it.isFocused) {
                                        hasHadFocus = true
                                    } else if (hasHadFocus && state.search.isEmpty()) {
                                        searchExpanded = false
                                    }
                                },
                            decorationBox = { inner ->
                                if (state.search.isEmpty()) {
                                    Text(
                                        "Buscar imagen…",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = TypeScale.body,
                                    )
                                }
                                inner()
                            },
                        )
                    }
                }
                LaunchedEffect(Unit) { searchFocus.requestFocus() }
            } else {
                IconButton({ searchExpanded = true }) {
                    SvgIcon(AppSvg.SEARCH, MaterialTheme.colorScheme.onSurfaceVariant, Modifier.size(IconSize.md))
                }
            }
            // Atajo de «silenciar todos los avisos»: la campana se tacha y se apaga sobre fondo
            // marcado, el mismo interruptor que vive en Ajustes → Avisos.
            Surface(
                color = if (mutedAll) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
                shape = CircleShape,
            ) {
                IconButton(onToggleMuteAll) {
                    val tint = if (mutedAll) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    SvgIcon(if (mutedAll) AppSvg.BELL_OFF else AppSvg.BELL, tint, Modifier.size(IconSize.lg))
                }
            }
            IconButton(onOpenSettings) {
                SvgIcon(AppSvg.GEAR, MaterialTheme.colorScheme.onSurfaceVariant, Modifier.size(IconSize.lg))
            }
            Button(
                onAdd,
                modifier = Modifier.height(33.dp),
                shape = RoundedCornerShape(Radius.pill),
                contentPadding = PaddingValues(horizontal = Space.lg, vertical = Space.sm),
            ) {
                SvgIcon(AppSvg.PLUS, MaterialTheme.colorScheme.onPrimary, Modifier.size(IconSize.sm))
                Spacer(Modifier.width(Space.sm))
                Text("Agregar imagen", fontSize = TypeScale.body, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/**
 * Aviso contextual: «Nada que atender» cuando todo está al día, o «No se puede acceder al
 * registry» cuando falla todo. Sustituye al banner de sin conexión de la versión anterior: ahora
 * comparte forma con el caso positivo, solo cambia la paleta.
 */
@Composable
private fun NoticeBanner(state: ImagesUiState, onRetry: () -> Unit) {
    val dark = LocalIsDark.current
    val disconnected = state.allFailing
    val nadaQueAtender = !disconnected && state.pending == 0 && state.errorCount == 0

    AnimatedVisibility(
        disconnected || nadaQueAtender,
        enter = expandVertically(tween(Motion.EMPHASIS, easing = Motion.emphasisEasing)) +
            fadeIn(tween(Motion.EMPHASIS)),
        exit = shrinkVertically(tween(Motion.EMPHASIS, easing = Motion.emphasisEasing)) +
            fadeOut(tween(Motion.EMPHASIS)),
    ) {
        val palette = statusColors(if (disconnected) ImageStatus.ERROR else ImageStatus.OK, dark)
        Box(Modifier.padding(start = Space.xl, top = Space.sm, end = Space.xl)) {
            Surface(
                color = palette.background,
                shape = RoundedCornerShape(Radius.md),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.sm),
                    modifier = Modifier.padding(horizontal = Space.lg, vertical = 11.dp),
                ) {
                    if (!disconnected) {
                        SvgIcon(AppSvg.CHECK, palette.foreground, Modifier.size(IconSize.sm))
                    }
                    Text(
                        if (disconnected) "No se puede acceder al registry" else "Nada que atender",
                        color = palette.foreground,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = TypeScale.meta,
                        maxLines = 1,
                    )
                    Text(
                        if (disconnected) {
                            "· se muestran las últimas versiones conocidas en local"
                        } else {
                            "· las ${state.total} imágenes vigiladas están en su última versión"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = TypeScale.meta,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.weight(1f))
                    if (disconnected) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(Radius.pill),
                            onClick = onRetry,
                            modifier = Modifier.focusRing(Radius.pill),
                        ) {
                            Text(
                                "Reintentar ahora",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = TypeScale.meta,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = Space.md, vertical = Space.xs + 1.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PendingSectionHeader(count: Int, onAcknowledgeAll: () -> Unit) {
    val palette = statusColors(ImageStatus.PENDING, LocalIsDark.current)
    SectionHeader(palette.foreground, "Versión nueva", count) {
        // Blueprint (mapa de acciones): «Aplica «Visto» a todas las filas de la sección en una
        // sola escritura». Antes limpiaba el buscador, que no es lo que el chip promete.
        HeaderChip("Ver todas", onClick = onAcknowledgeAll)
    }
}

@Composable
private fun ErrorSectionHeader(count: Int, onRetryAll: () -> Unit) {
    val palette = statusColors(ImageStatus.ERROR, LocalIsDark.current)
    SectionHeader(palette.foreground, "No se pudo verificar", count) {
        HeaderChip("Reintentar", onClick = onRetryAll)
    }
}

/** Cabecera plegable de «Al día»: fondo fantasma, cuenta, nombres cuando esta plegada, y el
 * rastro de «X se ha movido aquí» durante Dwell.TRACE_MILLIS tras confirmarse un «Visto». */
@Composable
private fun OkSectionHeader(open: Boolean, count: Int, names: String, trace: String?, onToggle: () -> Unit) {
    val dark = LocalIsDark.current
    val palette = statusColors(ImageStatus.OK, dark)
    Surface(
        color = ghostBackground(dark),
        shape = RoundedCornerShape(Radius.md),
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth().focusRing(Radius.md),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
            modifier = Modifier.padding(horizontal = Space.lg).height(40.dp),
        ) {
            SvgIcon(
                AppSvg.CHEVRON_DOWN,
                mutedText(dark),
                Modifier.size(IconSize.sm).rotate(if (open) 0f else -90f),
            )
            Box(Modifier.size(8.dp).background(palette.foreground, CircleShape))
            Text("Al día", fontSize = TypeScale.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(count.toString(), fontSize = TypeScale.meta, color = mutedText(dark), style = TabularNums)
            if (!open && names.isNotEmpty()) {
                Text(
                    names,
                    fontSize = TypeScale.meta,
                    color = mutedText(dark),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
            Spacer(Modifier.weight(1f))
            if (!open && trace != null) {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(Radius.pill)) {
                    Text(
                        "$trace se ha movido aquí",
                        fontSize = TypeScale.caption,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = Space.sm, vertical = Space.xs),
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(dotColor: Color, title: String, count: Int, trailing: @Composable () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
        modifier = Modifier.fillMaxWidth().height(28.dp),
    ) {
        Box(Modifier.size(8.dp).background(dotColor, CircleShape))
        Text(title, fontWeight = FontWeight.Bold, fontSize = TypeScale.body, color = dotColor)
        Text(
            count.toString(),
            fontSize = TypeScale.meta,
            color = mutedText(LocalIsDark.current),
            style = TabularNums,
        )
        Spacer(Modifier.weight(1f))
        trailing()
    }
}

@Composable
private fun HeaderChip(text: String, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(Radius.pill),
        onClick = onClick,
        modifier = Modifier.focusRing(Radius.pill),
    ) {
        Text(
            text,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.SemiBold,
            fontSize = TypeScale.meta,
            modifier = Modifier.padding(horizontal = Space.md, vertical = Space.xs + 1.dp),
        )
    }
}

@Composable
private fun EmptyState(pollIntervalSeconds: Long, onAdd: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(bottom = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = CircleShape) {
            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                // 20 dp -Medidas.dc.html §06 "vacío"-: no es un paso de IconSize (14/16/18), es
                // la cota propia de este icono, el único fuera de la lista de la bandeja.
                SvgIcon(AppSvg.PLUS, MaterialTheme.colorScheme.onSurfaceVariant, Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.height(Space.md))
        Text("Ninguna imagen vigilada", fontWeight = FontWeight.SemiBold, fontSize = TypeScale.body)
        Spacer(Modifier.height(Space.xs))
        Text(
            "Agrega la referencia de una imagen del registry y la app la comprobará " +
                "cada ${pollIntervalSeconds}s.",
            fontSize = TypeScale.meta,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(320.dp),
        )
        Spacer(Modifier.height(Space.md))
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(Radius.pill),
            onClick = onAdd,
        ) {
            Text(
                "Agregar imagen",
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.SemiBold,
                fontSize = TypeScale.meta,
                modifier = Modifier.padding(horizontal = Space.lg, vertical = Space.sm),
            )
        }
    }
}

@Composable
private fun Footer(state: ImagesUiState, mutedAll: Boolean) {
    val dark = LocalIsDark.current
    val footState = when {
        state.verifying -> "Comprobando"
        state.allFailing -> "Sin conexión"
        state.polling -> "Activo"
        else -> "Detenido"
    }
    val footColor = when {
        state.verifying -> MaterialTheme.colorScheme.primary
        state.allFailing -> statusColors(ImageStatus.ERROR, dark).foreground
        state.polling -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.md),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.xl, vertical = Space.sm),
    ) {
        Row(
            Modifier.width(Layout.footState),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            Box(Modifier.size(6.dp).background(footColor, CircleShape))
            Text(footState, fontSize = TypeScale.caption, color = footColor, maxLines = 1)
        }
        Text(
            "· ${state.lastSuccessLabel}",
            fontSize = TypeScale.caption,
            color = mutedText(dark),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TabularNums,
            modifier = Modifier.width(Layout.footNote),
        )
        Spacer(Modifier.weight(1f))
        Box(Modifier.width(Layout.footMuted), contentAlignment = Alignment.CenterEnd) {
            if (mutedAll) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    SvgIcon(AppSvg.BELL_OFF, MaterialTheme.colorScheme.onSurfaceVariant, Modifier.size(IconSize.sm))
                    Text(
                        "Avisos silenciados",
                        fontSize = TypeScale.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
        Text(
            "${state.total} vigiladas",
            fontSize = TypeScale.caption,
            color = mutedText(dark),
            textAlign = TextAlign.End,
            style = TabularNums,
            modifier = Modifier.width(Layout.footState),
        )
    }
}
