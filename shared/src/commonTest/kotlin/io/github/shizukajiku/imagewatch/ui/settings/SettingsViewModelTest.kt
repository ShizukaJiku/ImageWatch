package io.github.shizukajiku.imagewatch.ui.settings

import io.github.shizukajiku.imagewatch.application.AutostartPort
import io.github.shizukajiku.imagewatch.config.AppConfig
import io.github.shizukajiku.imagewatch.config.ThemePreference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds

private fun config() = AppConfig(
    "images.json",
    "https://origen.ejemplo/api",
    300.seconds,
    listOf("alpha"),
    true,
    true,
    ThemePreference.SYSTEM,
    true,
    8.seconds,
    true,
    0.5,
    false,
    false,
    "",
)

private class FakeAutostart(private var on: Boolean = false) : AutostartPort {
    val enabled get() = on

    override fun isEnabled() = on

    override fun setEnabled(enabled: Boolean) {
        on = enabled
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private fun viewModel(
        onApply: (AppConfig) -> String? = { null },
        autostart: AutostartPort = FakeAutostart(),
        scope: CoroutineScope = CoroutineScope(Job()),
        testTeamsWebhook: suspend (String) -> Result<Unit> = { Result.success(Unit) },
    ) = SettingsViewModel(config(), onApply, autostart, scope, testTeamsWebhook)

    @Test
    fun `arranca con los valores vigentes y el estado del autostart`() {
        val vm = viewModel(autostart = FakeAutostart(on = true))

        val state = vm.state.value

        assertEquals("https://origen.ejemplo/api", state.remoteUrl)
        assertEquals("300", state.intervalSeconds)
        assertEquals(ThemePreference.SYSTEM, state.theme)
        assertEquals(true, state.autostart)
    }

    @Test
    fun `un toggle se aplica al instante`() {
        var recibida: AppConfig? = null
        val vm = viewModel(onApply = {
            recibida = it
            null
        })

        vm.onThemeChange(ThemePreference.DARK)

        assertEquals(ThemePreference.DARK, recibida?.theme)
        assertEquals(ThemePreference.DARK, vm.state.value.theme)
    }

    @Test
    fun `el volumen se aplica al instante`() {
        var recibida: AppConfig? = null
        val vm = viewModel(onApply = {
            recibida = it
            null
        })

        vm.onVolumeChange(0.2f)

        // Float -> Double no da exactamente 0.2; se compara de vuelta en Float.
        assertEquals(0.2f, recibida?.soundVolume?.toFloat())
    }

    @Test
    fun `silenciar todos los avisos se aplica al instante`() {
        var recibida: AppConfig? = null
        val vm = viewModel(onApply = {
            recibida = it
            null
        })

        vm.onMutedAllChange(true)

        assertEquals(true, recibida?.mutedAll)
    }

    @Test
    fun `el intervalo valido se aplica en el commit`() {
        var recibida: AppConfig? = null
        val vm = viewModel(onApply = {
            recibida = it
            null
        })

        vm.onIntervalChange("15")
        assertNull(recibida, "Teclear no aplica todavía")

        vm.onIntervalCommit()
        assertEquals(15.seconds, recibida?.pollInterval)
        assertNull(vm.state.value.intervalError)
    }

    @Test
    fun `un intervalo por debajo del minimo no se aplica y pinta su linea de ayuda`() {
        var llamado = false
        val vm = viewModel(onApply = {
            llamado = true
            null
        })

        vm.onIntervalChange("3")
        vm.onIntervalCommit()

        assertEquals("El intervalo mínimo es 5 s.", vm.state.value.intervalError)
        assertEquals(false, llamado)
    }

    @Test
    fun `un intervalo por encima del maximo no se aplica`() {
        val vm = viewModel(onApply = { error("no debería llamarse") })

        vm.onIntervalChange("4000")
        vm.onIntervalCommit()

        assertEquals("El intervalo máximo es 3600 s.", vm.state.value.intervalError)
    }

    @Test
    fun `una duracion de aviso fuera de rango pinta su linea de ayuda`() {
        val vm = viewModel(onApply = { error("no debería llamarse") })

        vm.onToastSecondsChange("40")
        vm.onToastSecondsCommit()

        assertEquals("Entre 3 s y 30 s.", vm.state.value.toastError)
    }

    @Test
    fun `un toggle con el intervalo a medio teclear persiste el ultimo intervalo valido, no cero`() {
        var recibida: AppConfig? = null
        val vm = viewModel(onApply = {
            recibida = it
            null
        })

        // El usuario borra el campo del intervalo y, sin hacer commit, cambia un toggle.
        vm.onIntervalChange("")
        vm.onMutedAllChange(true)

        assertEquals(300.seconds, recibida?.pollInterval, "No debe colar un intervalo de 0 s")
        assertEquals(true, recibida?.mutedAll)
    }

    @Test
    fun `un toggle tras un commit valido persiste ese intervalo aunque el campo quede invalido`() {
        var recibida: AppConfig? = null
        val vm = viewModel(onApply = {
            recibida = it
            null
        })

        vm.onIntervalChange("15")
        vm.onIntervalCommit()
        vm.onIntervalChange("abc")
        vm.onThemeChange(ThemePreference.DARK)

        assertEquals(15.seconds, recibida?.pollInterval)
    }

    @Test
    fun `una URL rechazada por el aplicador pinta la linea de ayuda de la URL`() {
        val vm = viewModel(onApply = { "El endpoint remoto debe utilizar HTTPS" })

        vm.onUrlChange("http://inseguro.ejemplo")
        vm.onUrlCommit()

        assertEquals("El endpoint remoto debe utilizar HTTPS", vm.state.value.urlError)
    }

    @Test
    fun `reload repuebla el formulario y el siguiente toggle parte de la config nueva`() {
        var recibida: AppConfig? = null
        val vm = viewModel(onApply = {
            recibida = it
            null
        })

        val restablecida = config().copy(remoteUrl = "https://otra.ejemplo/api", pollInterval = 60.seconds)
        vm.reload(restablecida)

        assertEquals("https://otra.ejemplo/api", vm.state.value.remoteUrl)
        assertEquals("60", vm.state.value.intervalSeconds)

        vm.onMutedAllChange(true)
        assertEquals("https://otra.ejemplo/api", recibida?.remoteUrl)
        assertEquals(60.seconds, recibida?.pollInterval)
    }

    @Test
    fun `activar el autostart llega al puerto`() {
        val autostart = FakeAutostart()
        val vm = viewModel(autostart = autostart)

        vm.onAutostartChange(true)

        assertEquals(true, autostart.enabled)
        assertEquals(true, vm.state.value.autostart)
    }

    @Test
    fun `una URL de webhook https se aplica en el commit`() {
        var recibida: AppConfig? = null
        val vm = viewModel(onApply = {
            recibida = it
            null
        })

        vm.onTeamsWebhookUrlChange("https://prod-01.westus.logic.azure.com/workflows/abc")
        assertNull(recibida, "Teclear no aplica todavía")

        vm.onTeamsWebhookUrlCommit()
        assertEquals("https://prod-01.westus.logic.azure.com/workflows/abc", recibida?.teamsWebhookUrl)
        assertNull(vm.state.value.teamsWebhookError)
    }

    @Test
    fun `una URL de webhook sin https no se aplica y pinta su linea de ayuda`() {
        val vm = viewModel(onApply = { error("no debería llamarse") })

        vm.onTeamsWebhookUrlChange("http://inseguro.ejemplo/workflow")
        vm.onTeamsWebhookUrlCommit()

        assertEquals("La URL del webhook debe empezar por https://", vm.state.value.teamsWebhookError)
    }

    @Test
    fun `una URL de webhook en blanco se acepta como estado de fabrica`() {
        var recibida: AppConfig? = null
        val vm = viewModel(onApply = {
            recibida = it
            null
        })

        vm.onTeamsWebhookUrlChange("")
        vm.onTeamsWebhookUrlCommit()

        assertNull(vm.state.value.teamsWebhookError)
        assertEquals("", recibida?.teamsWebhookUrl)
    }

    @Test
    fun `activar el envio a Teams se aplica al instante`() {
        var recibida: AppConfig? = null
        val vm = viewModel(onApply = {
            recibida = it
            null
        })

        vm.onTeamsEnabledChange(true)

        assertEquals(true, recibida?.teamsEnabled)
    }

    @Test
    fun `probar webhook con una URL invalida no llama al puerto de prueba`() = runTest {
        var llamado = false
        val vm = viewModel(scope = backgroundScope, testTeamsWebhook = {
            llamado = true
            Result.success(Unit)
        })

        vm.onTeamsWebhookUrlChange("http://inseguro.ejemplo")
        vm.onTestTeamsWebhook()
        runCurrent()

        assertEquals(false, llamado)
        assertEquals(
            TeamsTestState.Failed("La URL del webhook debe empezar por https://"),
            vm.state.value.teamsTestState,
        )
    }

    @Test
    fun `probar webhook con exito publica el estado de exito`() = runTest {
        val vm = viewModel(scope = backgroundScope, testTeamsWebhook = { Result.success(Unit) })

        vm.onTeamsWebhookUrlChange("https://prod-01.westus.logic.azure.com/workflows/abc")
        vm.onTestTeamsWebhook()
        runCurrent()

        assertEquals(TeamsTestState.Success, vm.state.value.teamsTestState)
    }

    @Test
    fun `probar webhook con fallo publica el mensaje del error`() = runTest {
        val vm = viewModel(
            scope = backgroundScope,
            testTeamsWebhook = { Result.failure(RuntimeException("HTTP 404")) },
        )

        vm.onTeamsWebhookUrlChange("https://prod-01.westus.logic.azure.com/workflows/abc")
        vm.onTestTeamsWebhook()
        runCurrent()

        assertEquals(TeamsTestState.Failed("HTTP 404"), vm.state.value.teamsTestState)
    }

    @Test
    fun `cambiar la URL tras una prueba limpia su resultado`() = runTest {
        val vm = viewModel(scope = backgroundScope, testTeamsWebhook = { Result.success(Unit) })
        vm.onTeamsWebhookUrlChange("https://prod-01.westus.logic.azure.com/workflows/abc")
        vm.onTestTeamsWebhook()
        runCurrent()
        assertEquals(TeamsTestState.Success, vm.state.value.teamsTestState)

        vm.onTeamsWebhookUrlChange("https://prod-01.westus.logic.azure.com/workflows/otro")

        assertEquals(TeamsTestState.Idle, vm.state.value.teamsTestState)
    }
}
