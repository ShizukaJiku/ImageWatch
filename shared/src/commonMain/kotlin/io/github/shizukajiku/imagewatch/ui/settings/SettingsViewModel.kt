package io.github.shizukajiku.imagewatch.ui.settings

import io.github.shizukajiku.imagewatch.application.AutostartPort
import io.github.shizukajiku.imagewatch.config.AppConfig
import io.github.shizukajiku.imagewatch.config.ThemePreference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
    val teamsEnabled: Boolean,
    val teamsWebhookUrl: String,
    val teamsTestState: TeamsTestState = TeamsTestState.Idle,
    /** Cada error vive en la línea de ayuda -de altura reservada- de su propio campo. */
    val urlError: String? = null,
    val intervalError: String? = null,
    val toastError: String? = null,
    val teamsWebhookError: String? = null,
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
 * @param scope donde corre el envío del botón «Probar webhook». No comparte ciclo de vida con
 *   nada más de esta pantalla: sobrevive a que el usuario navegue fuera de Ajustes mientras la
 *   prueba sigue en vuelo, igual que `ImagesViewModel` con sus temporizadores.
 * @param testTeamsWebhook manda un mensaje de prueba a la URL indicada. Llega como función y no
 *   como el cliente entero para que este view model no dependa de Ktor ni de infraestructura.
 */
class SettingsViewModel(
    current: AppConfig,
    private val apply: (AppConfig) -> String?,
    private val autostart: AutostartPort,
    private val scope: CoroutineScope,
    private val testTeamsWebhook: suspend (String) -> Result<Unit>,
) {
    private val stateFile = current.stateFile
    private val imageNames = current.imageNames
    private val simulationMode = current.simulationMode

    // Últimos valores válidos ya persistidos de los dos campos numéricos. Mientras el usuario
    // teclea algo inválido en el campo del intervalo o la duración -o lo deja en blanco- y sin
    // haber hecho commit, un cambio en un toggle no debe arrastrar ese texto roto a `config.json`:
    // `editAndApply` construye el candidato sobre estos, no sobre lo que haya en el campo.
    private var lastGoodInterval = current.pollInterval
    private var lastGoodToast = current.toastDuration

    private val mutableState = MutableStateFlow(stateOf(current))
    val state: StateFlow<SettingsUiState> = mutableState.asStateFlow()

    private fun stateOf(config: AppConfig) = SettingsUiState(
        remoteUrl = config.remoteUrl,
        intervalSeconds = config.pollInterval.inWholeSeconds.toString(),
        ignoreSslErrors = config.ignoreSslErrors,
        theme = config.theme,
        toastsEnabled = config.toastsEnabled,
        toastSeconds = config.toastDuration.inWholeSeconds.toString(),
        soundsEnabled = config.soundsEnabled,
        soundVolume = config.soundVolume.toFloat(),
        mutedAll = config.mutedAll,
        autostart = autostart.isEnabled(),
        teamsEnabled = config.teamsEnabled,
        teamsWebhookUrl = config.teamsWebhookUrl,
    )

    /**
     * Repuebla el formulario desde una configuración recién aplicada por fuera -hoy solo
     * «Restablecer ajustes»-. Sin esto el formulario seguiría mostrando los valores previos
     * mientras la pantalla está montada, y el primer toggle reconstruiría el candidato sobre
     * ellos, deshaciendo el restablecido.
     */
    fun reload(config: AppConfig) {
        lastGoodInterval = config.pollInterval
        lastGoodToast = config.toastDuration
        mutableState.value = stateOf(config)
    }

    // --- se aplican al instante ---

    fun onIgnoreSslChange(value: Boolean) = editAndApply { it.copy(ignoreSslErrors = value) }

    fun onThemeChange(value: ThemePreference) = editAndApply { it.copy(theme = value) }

    fun onToastsChange(value: Boolean) = editAndApply { it.copy(toastsEnabled = value) }

    fun onSoundsChange(value: Boolean) = editAndApply { it.copy(soundsEnabled = value) }

    fun onVolumeChange(value: Float) = editAndApply { it.copy(soundVolume = value) }

    fun onMutedAllChange(value: Boolean) = editAndApply { it.copy(mutedAll = value) }

    fun onTeamsEnabledChange(value: Boolean) = editAndApply { it.copy(teamsEnabled = value) }

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

    fun onTeamsWebhookUrlChange(value: String) {
        mutableState.value = mutableState.value.copy(
            teamsWebhookUrl = value,
            teamsWebhookError = null,
            // La URL cambió: el resultado de la última prueba ya no dice nada de esta.
            teamsTestState = TeamsTestState.Idle,
        )
    }

    /**
     * Valida la forma de la URL aquí mismo -no hay ningún recurso vivo que reconstruir, a
     * diferencia de `onUrlCommit`, cuyo `apply` sí levanta un `HttpImageSource`-, y solo persiste
     * si pasa. Una URL en blanco es válida: es el estado de fábrica, con la integración
     * desactivada.
     */
    fun onTeamsWebhookUrlCommit() {
        val url = mutableState.value.teamsWebhookUrl.trim()
        val error = if (url.isNotEmpty() && !url.startsWith("https://", ignoreCase = true)) {
            "La URL del webhook debe empezar por https://"
        } else {
            null
        }
        mutableState.value = mutableState.value.copy(teamsWebhookError = error)
        if (error == null) {
            apply(configFromForm())
        }
    }

    /**
     * Manda un mensaje de prueba a la URL tal como está en el campo ahora mismo -no hace falta
     * haber hecho commit-, para que el usuario vea si funciona antes de dejarla guardada.
     */
    fun onTestTeamsWebhook() {
        // Un segundo clic mientras la prueba anterior sigue en vuelo no lanza un segundo envío:
        // el botón de Ajustes no deshabilita su Surface mientras se envía.
        if (mutableState.value.teamsTestState == TeamsTestState.Sending) return
        val url = mutableState.value.teamsWebhookUrl.trim()
        if (url.isEmpty() || !url.startsWith("https://", ignoreCase = true)) {
            mutableState.value = mutableState.value.copy(
                teamsTestState = TeamsTestState.Failed("La URL del webhook debe empezar por https://"),
            )
            return
        }
        mutableState.value = mutableState.value.copy(teamsTestState = TeamsTestState.Sending)
        scope.launch {
            val result = testTeamsWebhook(url)
            mutableState.value = mutableState.value.copy(
                teamsTestState = result.fold(
                    onSuccess = { TeamsTestState.Success },
                    onFailure = { TeamsTestState.Failed(it.message ?: "No se pudo enviar el mensaje de prueba") },
                ),
            )
        }
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

            else -> {
                lastGoodInterval = seconds.seconds
                apply(configFromForm())
            }
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

            else -> {
                lastGoodToast = seconds.seconds
                apply(configFromForm())
            }
        }
        mutableState.value = mutableState.value.copy(toastError = error)
    }

    private fun editAndApply(change: (SettingsUiState) -> SettingsUiState) {
        mutableState.value = change(mutableState.value)
        // Un fallo de E/S al guardar el volumen es excepcional; `applyConfig` ya lo registra en
        // el log. Estos campos no tienen línea de ayuda, así que el mensaje no se pinta.
        apply(configFromForm())
    }

    /**
     * El candidato a persistir. Los dos campos numéricos solo aportan su valor si está dentro de
     * rango; si el usuario los dejó a medio teclear, se usa el último válido ya guardado. Así
     * ningún camino que pase por aquí sin validar antes -`editAndApply`, `onUrlCommit`- puede
     * escribir un intervalo de 0 s que haga que la app no arranque en el siguiente inicio.
     */
    private fun configFromForm(): AppConfig {
        val form = mutableState.value
        val interval = form.intervalSeconds.trim().toLongOrNull()
            ?.takeIf { it in INTERVAL_MIN..INTERVAL_MAX }?.seconds ?: lastGoodInterval
        val toast = form.toastSeconds.trim().toLongOrNull()
            ?.takeIf { it in TOAST_MIN..TOAST_MAX }?.seconds ?: lastGoodToast
        return AppConfig(
            stateFile = stateFile,
            remoteUrl = form.remoteUrl.trim(),
            pollInterval = interval,
            imageNames = imageNames,
            simulationMode = simulationMode,
            ignoreSslErrors = form.ignoreSslErrors,
            theme = form.theme,
            toastsEnabled = form.toastsEnabled,
            toastDuration = toast,
            soundsEnabled = form.soundsEnabled,
            soundVolume = form.soundVolume.toDouble(),
            mutedAll = form.mutedAll,
            teamsEnabled = form.teamsEnabled,
            teamsWebhookUrl = form.teamsWebhookUrl.trim(),
        )
    }
}
