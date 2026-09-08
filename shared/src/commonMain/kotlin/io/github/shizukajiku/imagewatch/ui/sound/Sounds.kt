package io.github.shizukajiku.imagewatch.ui.sound

/**
 * Los cuatro avisos. `silenciadoPorFoco` distingue los dos oficios que tiene el sonido aquí:
 *
 * - **Notificar** algo que ocurrió por su cuenta —una versión nueva, el origen que se cae—. Si el
 *   usuario ya está mirando la ventana, el aviso en pantalla basta y el sonido sobra.
 * - **Confirmar** lo que el usuario acaba de pulsar. Eso ocurre siempre con la ventana enfocada,
 *   por definición: silenciarlo con la misma regla lo convertiría en código muerto.
 */
enum class Sound(private val file: String, val silenciadoPorFoco: Boolean) {
    UPDATE("update", silenciadoPorFoco = true),
    ERROR("error", silenciadoPorFoco = true),
    SUCCESS("success", silenciadoPorFoco = false),
    TOGGLE("toggle", silenciadoPorFoco = false),
    ;

    val resourcePath: String get() = "/sounds/$file.wav"
}

/**
 * Los cuatro avisos sonoros y las tres reglas que los gobiernan.
 *
 * @param onPlay gancho de prueba: recibe el sonido que se va a reproducir justo antes de hacerlo.
 *   Existe porque los tests no pueden comprobar que algo sonó, solo que se decidió reproducirlo.
 */
expect class Sounds(
    enabled: () -> Boolean,
    volume: () -> Double,
    windowFocused: () -> Boolean,
    onPlay: (Sound) -> Unit = {},
) {
    fun preload()

    fun play(sound: Sound)

    fun close()
}
