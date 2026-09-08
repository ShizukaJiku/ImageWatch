package io.github.shizukajiku.imagewatch.ui.toast

import io.github.shizukajiku.imagewatch.application.SilencedImageStore
import io.github.shizukajiku.imagewatch.config.AppConfig
import io.github.shizukajiku.imagewatch.config.ThemePreference
import io.github.shizukajiku.imagewatch.domain.ImageState
import io.github.shizukajiku.imagewatch.domain.ImageStatus
import io.github.shizukajiku.imagewatch.domain.Version
import io.github.shizukajiku.imagewatch.ui.sound.Sound
import io.github.shizukajiku.imagewatch.ui.sound.Sounds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Duración fija y muy holgada: estas pruebas no ejercitan el reloj, y un `CoroutineScope` real
 * —no de tiempo virtual— no la va a agotar en lo que tarda la prueba en correr.
 */
private fun estadoSinReloj() = ToastState(CoroutineScope(Job()), { 60.seconds })

private fun pending(name: String) = ImageState(
    name,
    Version("1.0.0"),
    Version("1.1.0"),
    "registry.local/$name",
    ImageStatus.PENDING,
    null,
    Instant.fromEpochSeconds(0),
)

private fun config(toastsEnabled: Boolean, mutedAll: Boolean = false) = AppConfig(
    "images.json",
    "https://origen.ejemplo/api",
    300.seconds,
    listOf("alpha"),
    true,
    true,
    ThemePreference.SYSTEM,
    toastsEnabled,
    8.seconds,
    true,
    0.5,
    mutedAll,
)

private class FakeSilencedImageStore(private val silenced: Set<String> = emptySet()) : SilencedImageStore {
    override fun findAll(): Set<String> = silenced

    override fun save(names: Set<String>) = error("no usado en estas pruebas")
}

class ToastNotificationPortTest {

    @Test
    fun `con los toasts desactivados el sonido de actualizacion suena igual`() {
        // H-89: son dos interruptores independientes. El sonido corre antes de comprobar
        // toastsEnabled, asi que apagar el aviso en pantalla no debe silenciar de paso el sonido.
        val reproducidos = mutableListOf<Sound>()
        val sounds = Sounds(
            enabled = { true },
            volume = { 1.0 },
            windowFocused = { false },
            onPlay = { reproducidos.add(it) },
        )
        val toasts = estadoSinReloj()
        val port = ToastNotificationPort(toasts, sounds, FakeSilencedImageStore()) { config(toastsEnabled = false) }

        port.notifyUpdates(listOf(pending("alpha")))

        assertEquals(listOf(Sound.UPDATE), reproducidos)
        assertTrue(toasts.toasts.value.isEmpty(), "Solo el aviso en pantalla se omite")
    }

    @Test
    fun `con los toasts activados tambien se muestra el aviso`() {
        val sounds = Sounds(enabled = { false }, volume = { 1.0 }, windowFocused = { false })
        val toasts = estadoSinReloj()
        val port = ToastNotificationPort(toasts, sounds, FakeSilencedImageStore()) { config(toastsEnabled = true) }

        port.notifyUpdates(listOf(pending("alpha")))

        assertEquals(listOf("alpha"), toasts.toasts.value.map { it.imageName })
    }

    @Test
    fun `silenciar todos los avisos no suena ni muestra nada`() {
        val reproducidos = mutableListOf<Sound>()
        val sounds = Sounds(
            enabled = { true },
            volume = { 1.0 },
            windowFocused = { false },
            onPlay = { reproducidos.add(it) },
        )
        val toasts = estadoSinReloj()
        val port = ToastNotificationPort(toasts, sounds, FakeSilencedImageStore()) {
            config(toastsEnabled = true, mutedAll = true)
        }

        port.notifyUpdates(listOf(pending("alpha")))

        assertTrue(reproducidos.isEmpty(), "El silencio general tambien corta el sonido")
        assertTrue(toasts.toasts.value.isEmpty())
    }

    @Test
    fun `una imagen silenciada no suena ni se muestra, pero las demas de la tanda si`() {
        val reproducidos = mutableListOf<Sound>()
        val sounds = Sounds(
            enabled = { true },
            volume = { 1.0 },
            windowFocused = { false },
            onPlay = { reproducidos.add(it) },
        )
        val toasts = estadoSinReloj()
        val port = ToastNotificationPort(toasts, sounds, FakeSilencedImageStore(setOf("alpha"))) {
            config(toastsEnabled = true)
        }

        port.notifyUpdates(listOf(pending("alpha"), pending("beta")))

        assertEquals(listOf(Sound.UPDATE), reproducidos)
        assertEquals(listOf("beta"), toasts.toasts.value.map { it.imageName })
    }

    @Test
    fun `si toda la tanda esta silenciada no suena nada`() {
        val reproducidos = mutableListOf<Sound>()
        val sounds = Sounds(
            enabled = { true },
            volume = { 1.0 },
            windowFocused = { false },
            onPlay = { reproducidos.add(it) },
        )
        val toasts = estadoSinReloj()
        val port = ToastNotificationPort(toasts, sounds, FakeSilencedImageStore(setOf("alpha"))) {
            config(toastsEnabled = true)
        }

        port.notifyUpdates(listOf(pending("alpha")))

        assertTrue(reproducidos.isEmpty())
        assertTrue(toasts.toasts.value.isEmpty())
    }
}
