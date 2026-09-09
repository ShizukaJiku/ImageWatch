package io.github.shizukajiku.imagewatch.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import io.github.shizukajiku.imagewatch.config.ThemePreference
import io.github.shizukajiku.imagewatch.ui.components.AppSvg
import io.github.shizukajiku.imagewatch.ui.components.SvgIcon
import io.github.shizukajiku.imagewatch.ui.theme.IconSize
import io.github.shizukajiku.imagewatch.ui.theme.Layout
import io.github.shizukajiku.imagewatch.ui.theme.Space
import io.github.shizukajiku.imagewatch.ui.theme.TypeScale

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    /** Si el sondeo esta corriendo ahora mismo. No es parte del formulario: no se guarda. */
    polling: Boolean,
    onTogglePolling: () -> Unit,
    onUrlChange: (String) -> Unit,
    onIntervalChange: (String) -> Unit,
    onSimulationChange: (Boolean) -> Unit,
    onIgnoreSslChange: (Boolean) -> Unit,
    onThemeChange: (ThemePreference) -> Unit,
    onToastsChange: (Boolean) -> Unit,
    onToastSecondsChange: (String) -> Unit,
    onSoundsChange: (Boolean) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onMutedAllChange: (Boolean) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = Space.md, top = Space.lg, end = Space.xl, bottom = Space.sm),
        ) {
            IconButton(onBack) {
                SvgIcon(AppSvg.BACK, MaterialTheme.colorScheme.onSurfaceVariant, Modifier.size(IconSize.lg))
            }
            Text("Ajustes", fontWeight = FontWeight.Bold, fontSize = TypeScale.title)
        }

        Section("Origen") {
            OutlinedTextField(
                value = state.remoteUrl,
                onValueChange = onUrlChange,
                label = { Text("URL del origen") },
                singleLine = true,
                enabled = !state.simulationMode,
                modifier = Modifier.fillMaxWidth(),
            )
            Toggle("Modo simulación", state.simulationMode, onSimulationChange)
            Toggle(
                "Ignorar errores de TLS",
                state.ignoreSslErrors,
                onIgnoreSslChange,
                enabled = !state.simulationMode,
            )
        }

        // El intervalo y el interruptor viven aqui, no en la cabecera de la lista: la pantalla
        // principal es para las imagenes. En la cabecera solo queda el indicador, que informa.
        Section("Sondeo") {
            OutlinedTextField(
                value = state.intervalSeconds,
                onValueChange = onIntervalChange,
                label = { Text("Intervalo de sondeo (s)") },
                singleLine = true,
                modifier = Modifier.width(Layout.settingsField),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = Space.sm),
            ) {
                // Boton y no interruptor: detener el sondeo surte efecto al pulsarlo, mientras
                // que todo lo demas de esta pantalla espera al boton de guardar. Un interruptor
                // entre interruptores prometeria las mismas reglas que sus vecinos.
                TextButton(onTogglePolling) {
                    Text(if (polling) "Detener sondeo" else "Iniciar sondeo")
                }
                Spacer(Modifier.width(Space.md))
                Text(
                    if (polling) "● Activo" else "● Detenido",
                    fontSize = TypeScale.caption,
                    color = if (polling) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }

        Section("Apariencia") {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                ThemePreference.entries.forEach { option ->
                    FilterChip(
                        selected = state.theme == option,
                        onClick = { onThemeChange(option) },
                        label = { Text(themeLabel(option)) },
                    )
                }
            }
        }

        Section("Avisos") {
            Toggle("Mostrar toasts", state.toastsEnabled, onToastsChange)
            OutlinedTextField(
                value = state.toastSeconds,
                onValueChange = onToastSecondsChange,
                label = { Text("Duración del toast (s)") },
                singleLine = true,
                enabled = state.toastsEnabled,
                modifier = Modifier.width(Layout.settingsField),
            )
            Toggle("Sonidos", state.soundsEnabled, onSoundsChange)
            Text("Volumen", fontSize = TypeScale.meta, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Slider(
                value = state.soundVolume,
                onValueChange = onVolumeChange,
                enabled = state.soundsEnabled,
                modifier = Modifier.width(Layout.searchPill),
            )
            // Mismo interruptor que la campana de la cabecera de la lista: silenciar es general,
            // no vacia la lista ni la reordena, asi que vive en Ajustes y no como una banda mas.
            Toggle("Silenciar todos los avisos", state.mutedAll, onMutedAllChange)
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = Space.xl, vertical = Space.lg),
        ) {
            Button(onSave) { Text("Guardar") }
            Spacer(Modifier.width(Space.lg))
            // El error del guardado aparece aqui, junto al boton que lo provoco, y no arriba:
            // un mensaje lejos de su causa obliga a buscarlo.
            AnimatedVisibility(state.error != null) {
                Text(
                    state.error.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    fontSize = TypeScale.meta,
                )
            }
            AnimatedVisibility(state.error == null && state.savedAt > 0) {
                Text(
                    "Guardado",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = TypeScale.meta,
                )
            }
        }
    }
}

private fun themeLabel(preference: ThemePreference) = when (preference) {
    ThemePreference.SYSTEM -> "Sistema"
    ThemePreference.LIGHT -> "Claro"
    ThemePreference.DARK -> "Oscuro"
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(Modifier.padding(horizontal = Space.xl, vertical = Space.sm)) {
        Text(
            title,
            fontWeight = FontWeight.Bold,
            fontSize = TypeScale.meta,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = Space.sm),
        )
        content()
        HorizontalDivider(Modifier.padding(top = Space.md))
    }
}

@Composable
private fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = Space.xs),
    ) {
        Text(label, fontSize = TypeScale.body, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}
