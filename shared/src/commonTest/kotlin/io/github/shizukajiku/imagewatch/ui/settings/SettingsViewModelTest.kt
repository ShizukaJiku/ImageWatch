package io.github.shizukajiku.imagewatch.ui.settings

import io.github.shizukajiku.imagewatch.application.AutostartPort
import io.github.shizukajiku.imagewatch.config.AppConfig
import io.github.shizukajiku.imagewatch.config.ThemePreference
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
)

private class FakeAutostart(private var on: Boolean = false) : AutostartPort {
    val enabled get() = on

    override fun isEnabled() = on

    override fun setEnabled(enabled: Boolean) {
        on = enabled
    }
}

class SettingsViewModelTest {

    private fun viewModel(onApply: (AppConfig) -> String? = { null }, autostart: AutostartPort = FakeAutostart()) =
        SettingsViewModel(config(), onApply, autostart)

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
    fun `una URL rechazada por el aplicador pinta la linea de ayuda de la URL`() {
        val vm = viewModel(onApply = { "El endpoint remoto debe utilizar HTTPS" })

        vm.onUrlChange("http://inseguro.ejemplo")
        vm.onUrlCommit()

        assertEquals("El endpoint remoto debe utilizar HTTPS", vm.state.value.urlError)
    }

    @Test
    fun `activar el autostart llega al puerto`() {
        val autostart = FakeAutostart()
        val vm = viewModel(autostart = autostart)

        vm.onAutostartChange(true)

        assertEquals(true, autostart.enabled)
        assertEquals(true, vm.state.value.autostart)
    }
}
