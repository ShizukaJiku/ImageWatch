package io.github.shizukajiku.imagewatch.ui.settings

import io.github.shizukajiku.imagewatch.application.AutostartPort
import io.github.shizukajiku.imagewatch.config.AppConfig
import io.github.shizukajiku.imagewatch.config.ThemePreference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Duration.Companion.seconds

data class SettingsUiState(
    val remoteUrl: String,
    val intervalSeconds: String,
    val ignoreSslErrors: Boolean,
    val theme: ThemePreference,
    val toastsEnabled: Boolean,
    val toastSeconds: String,
    val soundsEnabled: Boolean,
    val soundVolume: Float,
    val mutedAll: Boolean,
    val autostart: Boolean,
    /** Cada error vive en la línea de ayuda -de altura reservada- de su propio campo. */
    val urlError: String? = null,
    val intervalError: String? = null,
    val toastError: String? = null,
)

/** El intervalo mínimo lo garantiza también el controlador; el máximo, la sensatez. */
private const val INTERVAL_MIN = 5L
private const val INTERVAL_MAX = 3600L
private const val TOAST_MIN = 3L
private const val TOAST_MAX = 30L

/**
 * Estado de la pantalla de ajustes. **No hay «Guardar»**: cada cambio se aplica al momento
 * (Blueprint: «Los cambios se aplican al momento»). Los toggles, el tema y el volumen aplican en
 * cuanto cambian; la URL, el intervalo y la duración validan en un commit explícito -al salir del
 * campo o con Enter- y, si fallan, pintan su mensaje en la línea de ayuda de su campo sin mover
 * nada más.
 *
 * El rango del intervalo y de la duración se comprueba aquí **antes** de aplicar solo para poder
 * pintar la línea de ayuda sin lanzar una consulta ni reiniciar el sondeo. Es duplicación
 * consciente y acotada -dos números- de reglas que el núcleo ya tiene.
 *
 * @param apply aplica la configuración sobre la aplicación viva y la persiste. Devuelve `null` si
 *   todo fue bien, o el mensaje a mostrar.
 * @param autostart puerto del arranque al iniciar sesión. No pasa por [apply]: es estado del SO.
 */
class SettingsViewModel(
    current: AppConfig,
    private val apply: (AppConfig) -> String?,
    private val autostart: AutostartPort,
) {
    private val stateFile = current.stateFile
    private val imageNames = current.imageNames
    private val simulationMode = current.simulationMode

    private val mutableState = MutableStateFlow(
        SettingsUiState(
            remoteUrl = current.remoteUrl,
            intervalSeconds = current.pollInterval.inWholeSeconds.toString(),
            ignoreSslErrors = current.ignoreSslErrors,
            theme = current.theme,
            toastsEnabled = current.toastsEnabled,
            toastSeconds = current.toastDuration.inWholeSeconds.toString(),
            soundsEnabled = current.soundsEnabled,
            soundVolume = current.soundVolume.toFloat(),
            mutedAll = current.mutedAll,
            autostart = autostart.isEnabled(),
        ),
    )
    val state: StateFlow<SettingsUiState> = mutableState.asStateFlow()

    // --- se aplican al instante ---

    fun onIgnoreSslChange(value: Boolean) = editAndApply { it.copy(ignoreSslErrors = value) }

    fun onThemeChange(value: ThemePreference) = editAndApply { it.copy(theme = value) }

    fun onToastsChange(value: Boolean) = editAndApply { it.copy(toastsEnabled = value) }

    fun onSoundsChange(value: Boolean) = editAndApply { it.copy(soundsEnabled = value) }

    fun onVolumeChange(value: Float) = editAndApply { it.copy(soundVolume = value) }

    fun onMutedAllChange(value: Boolean) = editAndApply { it.copy(mutedAll = value) }

    fun onAutostartChange(value: Boolean) {
        autostart.setEnabled(value)
        mutableState.value = mutableState.value.copy(autostart = autostart.isEnabled())
    }

    // --- teclear no aplica; el commit valida y aplica ---

    fun onUrlChange(value: String) {
        mutableState.value = mutableState.value.copy(remoteUrl = value, urlError = null)
    }

    fun onUrlCommit() {
        mutableState.value = mutableState.value.copy(urlError = apply(configFromForm()))
    }

    fun onIntervalChange(value: String) {
        mutableState.value = mutableState.value.copy(intervalSeconds = value, intervalError = null)
    }

    fun onIntervalCommit() {
        val seconds = mutableState.value.intervalSeconds.trim().toLongOrNull()
        val error = when {
            seconds == null -> "El intervalo debe ser un número de segundos."
            seconds < INTERVAL_MIN -> "El intervalo mínimo es $INTERVAL_MIN s."
            seconds > INTERVAL_MAX -> "El intervalo máximo es $INTERVAL_MAX s."
            else -> apply(configFromForm())
        }
        mutableState.value = mutableState.value.copy(intervalError = error)
    }

    fun onToastSecondsChange(value: String) {
        mutableState.value = mutableState.value.copy(toastSeconds = value, toastError = null)
    }

    fun onToastSecondsCommit() {
        val seconds = mutableState.value.toastSeconds.trim().toLongOrNull()
        val error = when {
            seconds == null || seconds < TOAST_MIN || seconds > TOAST_MAX -> "Entre $TOAST_MIN s y $TOAST_MAX s."
            else -> apply(configFromForm())
        }
        mutableState.value = mutableState.value.copy(toastError = error)
    }

    private fun editAndApply(change: (SettingsUiState) -> SettingsUiState) {
        mutableState.value = change(mutableState.value)
        // Un fallo de E/S al guardar el volumen es excepcional; `applyConfig` ya lo registra en
        // el log. Estos campos no tienen línea de ayuda, así que el mensaje no se pinta.
        apply(configFromForm())
    }

    private fun configFromForm(): AppConfig {
        val form = mutableState.value
        return AppConfig(
            stateFile = stateFile,
            remoteUrl = form.remoteUrl.trim(),
            pollInterval = (form.intervalSeconds.trim().toLongOrNull() ?: 0L).seconds,
            imageNames = imageNames,
            simulationMode = simulationMode,
            ignoreSslErrors = form.ignoreSslErrors,
            theme = form.theme,
            toastsEnabled = form.toastsEnabled,
            toastDuration = (form.toastSeconds.trim().toLongOrNull() ?: 0L).seconds,
            soundsEnabled = form.soundsEnabled,
            soundVolume = form.soundVolume.toDouble(),
            mutedAll = form.mutedAll,
        )
    }
}
