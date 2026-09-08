package io.github.shizukajiku.imagewatch.ui.sound

import org.slf4j.LoggerFactory
import java.io.BufferedInputStream
import java.util.concurrent.ConcurrentHashMap
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip
import javax.sound.sampled.FloatControl
import kotlin.math.log10
import kotlin.math.max

private val LOG = LoggerFactory.getLogger(Sounds::class.java)

/** Atenuación base. Un aviso a volumen completo sobresalta. */
private const val BASE_ATTENUATION_DB = -12f

actual class Sounds actual constructor(
    private val enabled: () -> Boolean,
    private val volume: () -> Double,
    private val windowFocused: () -> Boolean,
    private val onPlay: (Sound) -> Unit,
) {
    private val clips = ConcurrentHashMap<Sound, Clip>()

    /**
     * Precarga en un hilo de fondo. `getClip()` y `open()` bloquean —unos 100 ms la primera vez— y
     * `start()` no: sin precarga, el primer aviso llega tarde y con un tirón visible en la
     * interfaz.
     */
    actual fun preload() {
        Thread.ofVirtual().name("sound-preload").start {
            Sound.entries.forEach { sound ->
                load(sound)?.let { clips[sound] = it }
            }
        }
    }

    actual fun play(sound: Sound) {
        // Regla 1, aplicada al sonido y no a ciegas: los avisos callan si el usuario ya está
        // mirando la tabla, porque para eso está el toast. Las confirmaciones no, porque
        // responden a un clic suyo y ese clic solo puede darse con la ventana enfocada.
        if (!enabled() || (sound.silenciadoPorFoco && windowFocused())) {
            return
        }
        onPlay(sound)
        val clip = clips[sound] ?: return
        // Regla 2: un Clip no puede sonar dos veces a la vez. Ante dos avisos seguidos se
        // reinicia en lugar de encolar, que es lo que un aviso debe hacer.
        clip.stop()
        clip.framePosition = 0
        applyVolume(clip)
        clip.start()
    }

    actual fun close() {
        clips.values.forEach { runCatching { it.close() } }
        clips.clear()
    }

    /**
     * Regla 3: degradación silenciosa. Por escritorio remoto, en máquinas sin dispositivo de audio
     * y en integración continua, esto lanza. Se registra una vez y el subsistema queda mudo.
     */
    private fun load(sound: Sound): Clip? = runCatching {
        val resource =
            requireNotNull(Sounds::class.java.getResourceAsStream(sound.resourcePath)) {
                "Falta el recurso ${sound.resourcePath}"
            }
        BufferedInputStream(resource).use { stream ->
            AudioSystem.getAudioInputStream(stream).use { audio ->
                AudioSystem.getClip().apply { open(audio) }
            }
        }
    }.onFailure {
        LOG.info("Sonido desactivado para {}: {}", sound, it.message)
    }.getOrNull()

    private fun applyVolume(clip: Clip) {
        if (!clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            return
        }
        val control = clip.getControl(FloatControl.Type.MASTER_GAIN) as FloatControl
        // El volumen del ajuste es lineal y el control va en decibelios: sin la conversión, la
        // mitad del deslizador no suena a la mitad.
        val decibels = (20 * log10(max(volume(), 0.0001))).toFloat() + BASE_ATTENUATION_DB
        control.value = decibels.coerceIn(control.minimum, control.maximum)
    }
}
