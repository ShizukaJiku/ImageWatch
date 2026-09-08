package io.github.shizukajiku.imagewatch.ui.settings

import io.github.shizukajiku.imagewatch.config.AppConfig
import io.github.shizukajiku.imagewatch.config.ThemePreference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
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
)

class SettingsViewModelTest {

    @Test
    fun `arranca con los valores vigentes`() {
        val viewModel = SettingsViewModel(config()) { null }

        val state = viewModel.state.value

        assertEquals("https://origen.ejemplo/api", state.remoteUrl)
        assertEquals("300", state.intervalSeconds)
        assertEquals(ThemePreference.SYSTEM, state.theme)
    }

    @Test
    fun `entrega al aplicador la configuracion editada`() {
        var recibida: AppConfig? = null
        val viewModel = SettingsViewModel(config()) {
            recibida = it
            null
        }

        viewModel.onIntervalChange("15")
        viewModel.onSimulationChange(false)
        viewModel.onThemeChange(ThemePreference.DARK)
        viewModel.save()

        assertEquals(15.seconds, recibida?.pollInterval)
        assertEquals(false, recibida?.simulationMode)
        assertEquals(ThemePreference.DARK, recibida?.theme)
        assertNull(viewModel.state.value.error)
        assertTrue(viewModel.state.value.savedAt > 0)
    }

    @Test
    fun `silenciar todos los avisos llega al aplicador`() {
        var recibida: AppConfig? = null
        val viewModel = SettingsViewModel(config()) {
            recibida = it
            null
        }

        viewModel.onMutedAllChange(true)
        viewModel.save()

        assertEquals(true, recibida?.mutedAll)
    }

    @Test
    fun `un intervalo no numerico se rechaza sin llegar al aplicador`() {
        var llamado = false
        val viewModel = SettingsViewModel(config()) {
            llamado = true
            null
        }

        viewModel.onIntervalChange("cada rato")
        viewModel.save()

        assertEquals("El intervalo debe ser un número de segundos", viewModel.state.value.error)
        assertEquals(false, llamado)
    }

    @Test
    fun `el mensaje del aplicador se muestra tal cual`() {
        // La pantalla no reimplementa la validacion: ensena lo que devuelven las reglas que ya
        // existen en el nucleo.
        val viewModel = SettingsViewModel(config()) { "El endpoint remoto debe utilizar HTTPS" }

        viewModel.onUrlChange("http://inseguro.ejemplo")
        viewModel.save()

        assertEquals("El endpoint remoto debe utilizar HTTPS", viewModel.state.value.error)
        assertEquals(0L, viewModel.state.value.savedAt)
    }
}
