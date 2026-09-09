package io.github.shizukajiku.imagewatch.ui.toast

import io.github.shizukajiku.imagewatch.application.NotificationPort
import io.github.shizukajiku.imagewatch.application.SilencedImageStore
import io.github.shizukajiku.imagewatch.config.AppConfig
import io.github.shizukajiku.imagewatch.domain.ImageState
import io.github.shizukajiku.imagewatch.ui.sound.Sound
import io.github.shizukajiku.imagewatch.ui.sound.Sounds

/**
 * Presentación implementando un puerto de la aplicación: el núcleo sigue avisando de transiciones
 * sin saber que ahora el canal es una ventana propia.
 *
 * La configuración llega como función y no como valor porque puede cambiar mientras la aplicación
 * corre: capturarla aquí congelaría el ajuste en el que estuviera al arrancar.
 */
class ToastNotificationPort(
    private val toasts: ToastState,
    private val sounds: Sounds,
    private val silencedImages: SilencedImageStore,
    private val config: () -> AppConfig,
) : NotificationPort {
    override fun notifyUpdates(updates: List<ImageState>) {
        // El silencio general corta el aviso entero -sonido incluido-: es lo que promete
        // «Silenciar todos los avisos», no solo el toast en pantalla. Sin este corte, silenciar
        // cambiaba el estado persistido y la insignia de la fila, pero el aviso seguia saltando.
        if (config().mutedAll) {
            return
        }
        // Cada imagen silenciada se cae de la tanda antes de decidir nada: el sonido -«uno por
        // tanda», regla (a)- y el toast se calculan sobre lo que queda, no sobre lo que llegó.
        val silenciadas = silencedImages.findAll()
        val relevantes = updates.filterNot { it.name in silenciadas }
        if (relevantes.isEmpty()) {
            return
        }
        // El sonido va antes de la salida por toasts desactivados: son dos interruptores
        // independientes en la pantalla de ajustes, y apagar los avisos en pantalla no debe
        // silenciar de paso los sonidos sin decirlo.
        sounds.play(Sound.UPDATE)
        if (!config().toastsEnabled) {
            return
        }
        toasts.show(relevantes)
    }

    override fun notifyFailures(failures: List<ImageState>) {
        if (config().mutedAll) {
            return
        }
        if (!config().toastsEnabled) {
            return
        }
        // Sin sonido: el pitido de "todo falla" ya lo emite ImagesViewModel.onSnapshot en la
        // transicion. Añadir uno aqui duplicaria el aviso sonoro por cada imagen que cae.
        toasts.showFailures(failures)
    }
}
