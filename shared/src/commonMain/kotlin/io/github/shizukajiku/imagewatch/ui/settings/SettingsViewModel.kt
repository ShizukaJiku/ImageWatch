package io.github.shizukajiku.imagewatch.ui.settings

import io.github.shizukajiku.imagewatch.config.AppConfig
import io.github.shizukajiku.imagewatch.config.ThemePreference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

data class SettingsUiState(
    val remoteUrl: String,
    val intervalSeconds: String,
    val simulationMode: Boolean,
    val ignoreSslErrors: Boolean,
    val theme: ThemePreference,
    val toastsEnabled: Boolean,
    val toastSeconds: String,
    val soundsEnabled: Boolean,
    val soundVolume: Float,
    val mutedAll: Boolean,
    val error: String? = null,
    /** Marca de tiempo del último guardado correcto. Cero mientras no haya ninguno. */
    val savedAt: Long = 0,
)

/**
 * Estado del formulario de ajustes.
 *
 * No valida por su cuenta más que el formato de los dos campos numéricos: el resto de reglas ya
 * viven en el núcleo —HTTPS obligatorio en el adaptador HTTP, intervalo mínimo en el controlador— y
 * duplicarlas aquí significaría mantener dos versiones de la misma regla.
 *
 * @param apply aplica la configuración sobre la aplicación viva y la persiste. Devuelve `null` si
 *   todo fue bien, o el mensaje a mostrar.
 */
class SettingsViewModel(current: AppConfig, private val apply: (AppConfig) -> String?) {
    private val stateFile = current.stateFile
    private val imageNames = current.imageNames
    private val mutableState =
        MutableStateFlow(
            SettingsUiState(
                remoteUrl = current.remoteUrl,
                intervalSeconds = current.pollInterval.inWholeSeconds.toString(),
                simulationMode = current.simulationMode,
                ignoreSslErrors = current.ignoreSslErrors,
                theme = current.theme,
                toastsEnabled = current.toastsEnabled,
                toastSeconds = current.toastDuration.inWholeSeconds.toString(),
                soundsEnabled = current.soundsEnabled,
                soundVolume = current.soundVolume.toFloat(),
                mutedAll = current.mutedAll,
            ),
        )
    val state: StateFlow<SettingsUiState> = mutableState.asStateFlow()

    fun onUrlChange(value: String) = edit { it.copy(remoteUrl = value) }

    fun onIntervalChange(value: String) = edit { it.copy(intervalSeconds = value) }

    fun onSimulationChange(value: Boolean) = edit { it.copy(simulationMode = value) }

    fun onIgnoreSslChange(value: Boolean) = edit { it.copy(ignoreSslErrors = value) }

    fun onThemeChange(value: ThemePreference) = edit { it.copy(theme = value) }

    fun onToastsChange(value: Boolean) = edit { it.copy(toastsEnabled = value) }

    fun onToastSecondsChange(value: String) = edit { it.copy(toastSeconds = value) }

    fun onSoundsChange(value: Boolean) = edit { it.copy(soundsEnabled = value) }

    fun onVolumeChange(value: Float) = edit { it.copy(soundVolume = value) }

    fun onMutedAllChange(value: Boolean) = edit { it.copy(mutedAll = value) }

    fun save() {
        val form = mutableState.value
        val interval = form.intervalSeconds.trim().toLongOrNull()
        if (interval == null) {
            mutableState.value = form.copy(error = "El intervalo debe ser un número de segundos")
            return
        }
        val toastSeconds = form.toastSeconds.trim().toLongOrNull()
        if (toastSeconds == null || toastSeconds <= 0) {
            mutableState.value = form.copy(error = "La duración del toast debe ser un número de segundos")
            return
        }
        val message =
            apply(
                AppConfig(
                    stateFile,
                    form.remoteUrl.trim(),
                    interval.seconds,
                    imageNames,
                    form.simulationMode,
                    form.ignoreSslErrors,
                    form.theme,
                    form.toastsEnabled,
                    toastSeconds.seconds,
                    form.soundsEnabled,
                    form.soundVolume.toDouble(),
                    form.mutedAll,
                ),
            )
        mutableState.value =
            when (message) {
                null -> form.copy(error = null, savedAt = Clock.System.now().toEpochMilliseconds())
                else -> form.copy(error = message)
            }
    }

    /** Editar limpia el error anterior: dejarlo en pantalla mientras se corrige es ruido. */
    private fun edit(change: (SettingsUiState) -> SettingsUiState) {
        // Se limpian las dos marcas, no solo el error: dejar el "Guardado" en pantalla mientras
        // el usuario teclea cambios que aun no ha guardado es decirle que ya estan a salvo.
        mutableState.value = change(mutableState.value).copy(error = null, savedAt = 0)
    }
}
