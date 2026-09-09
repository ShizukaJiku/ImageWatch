package io.github.shizukajiku.imagewatch.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.shizukajiku.imagewatch.config.ThemePreference
import io.github.shizukajiku.imagewatch.ui.components.AppSvg
import io.github.shizukajiku.imagewatch.ui.components.SvgIcon
import io.github.shizukajiku.imagewatch.ui.dialogs.ConfirmDialog
import io.github.shizukajiku.imagewatch.ui.theme.IconSize
import io.github.shizukajiku.imagewatch.ui.theme.Layout
import io.github.shizukajiku.imagewatch.ui.theme.Radius
import io.github.shizukajiku.imagewatch.ui.theme.Space
import io.github.shizukajiku.imagewatch.ui.theme.TabularNums
import io.github.shizukajiku.imagewatch.ui.theme.TypeScale
import io.github.shizukajiku.imagewatch.ui.theme.focusRing

private enum class Confirm { RESET, WIPE }

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    /** Si el sondeo esta corriendo ahora mismo. No es parte del formulario. */
    polling: Boolean,
    /** Cuantas imagenes se vigilan; va en el pie. No se edita aqui. */
    watchedCount: Int,
    onTogglePolling: () -> Unit,
    onUrlChange: (String) -> Unit,
    onUrlCommit: () -> Unit,
    onIntervalChange: (String) -> Unit,
    onIntervalCommit: () -> Unit,
    onIgnoreSslChange: (Boolean) -> Unit,
    onThemeChange: (ThemePreference) -> Unit,
    onToastsChange: (Boolean) -> Unit,
    onToastSecondsChange: (String) -> Unit,
    onToastSecondsCommit: () -> Unit,
    onSoundsChange: (Boolean) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onMutedAllChange: (Boolean) -> Unit,
    onAutostartChange: (Boolean) -> Unit,
    onResetSettings: () -> Unit,
    onWipeLocalData: () -> Unit,
    onBack: () -> Unit,
) {
    var confirming by remember { mutableStateOf<Confirm?>(null) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.md),
            modifier = Modifier.padding(start = Space.xl, top = Space.lg, end = Space.xl, bottom = Space.md),
        ) {
            Box(
                Modifier.size(32.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                    .clickable(onClick = onBack)
                    .focusRing(Radius.pill),
                contentAlignment = Alignment.Center,
            ) {
                SvgIcon(AppSvg.BACK, MaterialTheme.colorScheme.onSurfaceVariant, Modifier.size(IconSize.md))
            }
            Column {
                Text("Ajustes", fontWeight = FontWeight.Bold, fontSize = TypeScale.title)
                Text(
                    "Los cambios se aplican al momento. No hay que guardar.",
                    fontSize = TypeScale.meta,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = Space.xl),
            horizontalArrangement = Arrangement.spacedBy(Space.lg),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.lg)) {
                SettingsCard("Origen") {
                    FieldLabel("URL del registry")
                    UrlField(state.remoteUrl, onUrlChange, onUrlCommit, state.urlError != null)
                    HelpLine(state.urlError ?: "Se comprueba al salir del campo o con Enter.", state.urlError != null)
                    Toggle(
                        "Ignorar errores de TLS",
                        "Acepta certificados que no se pueden validar",
                        state.ignoreSslErrors,
                        onIgnoreSslChange,
                    )
                }
                SettingsCard("Comprobación") {
                    Toggle(
                        "Iniciar al encender el equipo",
                        "La ventana arranca minimizada",
                        state.autostart,
                        onAutostartChange,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.lg)) {
                        Column {
                            Text(
                                "Intervalo",
                                fontSize = TypeScale.meta,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            NumberField(
                                state.intervalSeconds,
                                "s",
                                state.intervalError != null,
                                onIntervalChange,
                                onIntervalCommit,
                            )
                            HelpLine(state.intervalError ?: "Entre 5 s y 3600 s.", state.intervalError != null)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                "Estado",
                                fontSize = TypeScale.meta,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            FootPill(
                                if (polling) "Detener" else "Iniciar",
                                MaterialTheme.colorScheme.surfaceVariant,
                                MaterialTheme.colorScheme.onSurfaceVariant,
                                onTogglePolling,
                            )
                            Text(
                                if (polling) "activo" else "detenido",
                                fontSize = TypeScale.caption,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.height(Layout.settingsHelpLine),
                            )
                        }
                    }
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.lg)) {
                SettingsCard("Apariencia") {
                    Segmented(state.theme, onThemeChange)
                    Text(
                        "Ahora mismo el sistema pide tema ${systemThemeLabel()}.",
                        fontSize = TypeScale.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                SettingsCard("Avisos") {
                    Toggle(
                        "Silenciar todos los avisos",
                        "No cambia el silencio de cada imagen",
                        state.mutedAll,
                        onMutedAllChange,
                    )
                    Column {
                        Text("Duración", fontSize = TypeScale.meta, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        NumberField(
                            state.toastSeconds,
                            "s",
                            state.toastError != null,
                            onToastSecondsChange,
                            onToastSecondsCommit,
                        )
                        HelpLine(state.toastError ?: "Entre 3 s y 30 s.", state.toastError != null)
                    }
                    Toggle("Sonidos", "Uno por tanda, no uno por tarjeta", state.soundsEnabled, onSoundsChange)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Space.md),
                    ) {
                        Text(
                            "Volumen",
                            fontSize = TypeScale.meta,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(60.dp),
                        )
                        Slider(
                            value = state.soundVolume,
                            onValueChange = onVolumeChange,
                            enabled = state.soundsEnabled,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "${(state.soundVolume * 100).toInt()} %",
                            fontSize = TypeScale.meta,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.End,
                            style = TabularNums,
                            modifier = Modifier.width(44.dp),
                        )
                    }
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
            modifier = Modifier.fillMaxWidth().padding(horizontal = Space.xl, vertical = Space.lg),
        ) {
            FootPill(
                "Restablecer ajustes",
                MaterialTheme.colorScheme.surfaceVariant,
                MaterialTheme.colorScheme.onSurfaceVariant,
            ) { confirming = Confirm.RESET }
            FootPill(
                "Borrar datos locales",
                MaterialTheme.colorScheme.errorContainer,
                MaterialTheme.colorScheme.onErrorContainer,
            ) { confirming = Confirm.WIPE }
            Spacer(Modifier.weight(1f))
            Text(
                "$watchedCount imágenes vigiladas",
                fontSize = TypeScale.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = TabularNums,
            )
        }
    }

    when (confirming) {
        Confirm.RESET -> ConfirmDialog(
            title = "Restablecer ajustes",
            body = "Los ajustes vuelven a sus valores de fábrica. Las imágenes vigiladas y su versión vista no " +
                "se tocan.",
            lost = listOf(
                "URL del registry · intervalo de fábrica",
                "Apariencia · Sistema",
                "Avisos · duración y sonidos de fábrica",
            ),
            confirmLabel = "Restablecer",
            onDismiss = { confirming = null },
            onConfirm = onResetSettings,
        )

        Confirm.WIPE -> ConfirmDialog(
            title = "Borrar datos locales",
            body = "Se borra todo lo que la app guarda en este equipo y la aplicación se cierra. Las imágenes " +
                "seguirán en el registry; la lista de vigilancia no. Vuelve a abrirla para empezar de cero.",
            lost = listOf(
                "$watchedCount imágenes vigiladas",
                "La versión vista de cada una",
                "Los ajustes de la app",
            ),
            confirmLabel = "Borrar todo",
            onDismiss = { confirming = null },
            onConfirm = onWipeLocalData,
        )

        null -> {}
    }
}

private fun themeLabel(preference: ThemePreference) = when (preference) {
    ThemePreference.SYSTEM -> "Sistema"
    ThemePreference.LIGHT -> "Claro"
    ThemePreference.DARK -> "Oscuro"
}

@Composable
private fun systemThemeLabel() = if (androidx.compose.foundation.isSystemInDarkTheme()) "oscuro" else "claro"

@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    androidx.compose.material3.Surface(
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(Radius.md),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Space.lg), verticalArrangement = Arrangement.spacedBy(Space.md)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = TypeScale.body)
            content()
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, fontSize = TypeScale.meta, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Línea de ayuda de altura reservada: al fallar solo cambian el texto y el color; nada baja. */
@Composable
private fun HelpLine(text: String, isError: Boolean) {
    Text(
        text,
        fontSize = TypeScale.caption,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.height(Layout.settingsHelpLine),
    )
}

@Composable
private fun Segmented(selected: ThemePreference, onSelect: (ThemePreference) -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(Radius.pill))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        ThemePreference.entries.forEach { option ->
            val on = option == selected
            Text(
                themeLabel(option),
                textAlign = TextAlign.Center,
                fontSize = TypeScale.meta,
                fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                color = if (on) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.weight(1f)
                    .then(
                        if (on) {
                            Modifier.background(
                                MaterialTheme.colorScheme.primaryContainer,
                                RoundedCornerShape(Radius.pill),
                            )
                        } else {
                            Modifier
                        },
                    )
                    .clickable { onSelect(option) }
                    .padding(vertical = Space.sm)
                    .focusRing(Radius.pill),
            )
        }
    }
}

@Composable
private fun UrlField(value: String, onChange: (String) -> Unit, onCommit: () -> Unit, isError: Boolean) {
    FieldBox(isError) {
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = TypeScale.body,
                fontFamily = FontFamily.Monospace,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onCommit() }),
            modifier = Modifier.fillMaxWidth().onFocusChanged { if (!it.isFocused) onCommit() },
        )
    }
}

@Composable
private fun NumberField(
    value: String,
    unit: String,
    isError: Boolean,
    onChange: (String) -> Unit,
    onCommit: () -> Unit,
) {
    Box(Modifier.width(Layout.settingsField)) {
        FieldBox(isError) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(
                    value = value,
                    onValueChange = onChange,
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = TypeScale.body,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onCommit() }),
                    modifier = Modifier.weight(1f).onFocusChanged { if (!it.isFocused) onCommit() },
                )
                Text(unit, fontSize = TypeScale.meta, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun FieldBox(isError: Boolean, content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxWidth()
            .height(34.dp)
            .background(MaterialTheme.colorScheme.background, RoundedCornerShape(Radius.sm))
            .then(
                Modifier.border(
                    BorderStroke(
                        1.dp,
                        if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.surfaceVariant,
                    ),
                    RoundedCornerShape(Radius.sm),
                ),
            )
            .padding(horizontal = Space.md),
        contentAlignment = Alignment.CenterStart,
    ) { content() }
}

@Composable
private fun FootPill(text: String, container: Color, onContent: Color, onClick: () -> Unit) {
    androidx.compose.material3.Surface(
        color = container,
        shape = RoundedCornerShape(Radius.pill),
        onClick = onClick,
        modifier = Modifier.focusRing(Radius.pill),
    ) {
        Text(
            text,
            color = onContent,
            fontSize = TypeScale.meta,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = Space.lg, vertical = Space.sm),
        )
    }
}

@Composable
private fun Toggle(label: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = TypeScale.body)
            Text(sub, fontSize = TypeScale.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            enabled = enabled,
            modifier = Modifier.focusRing(Radius.pill),
        )
    }
}
