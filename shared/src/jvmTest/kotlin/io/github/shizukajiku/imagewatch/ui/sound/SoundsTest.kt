package io.github.shizukajiku.imagewatch.ui.sound

import java.util.concurrent.ConcurrentHashMap
import javax.sound.sampled.Clip
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SoundsTest {

    @Test
    fun `los cuatro recursos estan empaquetados`() {
        // Un recurso que falta es un fallo de empaquetado y no se nota al ejecutar, porque el
        // subsistema de sonido calla ante cualquier problema por diseno.
        Sound.entries.forEach { sound ->
            val stream = Sounds::class.java.getResourceAsStream(sound.resourcePath)
            assertNotNull(stream, "Falta ${sound.resourcePath}")
            stream.close()
        }
    }

    @Test
    fun `reproducir sin dispositivo de audio no lanza`() {
        // En integracion continua y por escritorio remoto no hay linea de audio. Una aplicacion
        // de escritorio no puede caerse porque la maquina no tenga altavoces.
        val sounds = Sounds(enabled = { true }, volume = { 1.0 }, windowFocused = { false })
        sounds.preload()

        Sound.entries.forEach { sounds.play(it) }
        sounds.close()

        assertTrue(true, "Llegar aquí sin excepción es el aserto")
    }

    @Test
    fun `las confirmaciones suenan aunque la ventana tenga el foco`() {
        // Son respuesta a un clic del usuario, y ese clic solo puede darse con la ventana
        // delante: aplicarles la regla del foco las convertia en codigo muerto.
        val reproducidos = mutableListOf<Sound>()
        val sounds = Sounds(
            enabled = { true },
            volume = { 1.0 },
            windowFocused = { true },
            onPlay = { reproducidos.add(it) },
        )

        sounds.play(Sound.SUCCESS)
        sounds.play(Sound.TOGGLE)

        assertEquals(listOf(Sound.SUCCESS, Sound.TOGGLE), reproducidos)
    }

    @Test
    fun `no reproduce si la ventana tiene el foco`() {
        var reproducciones = 0
        val sounds = Sounds(
            enabled = { true },
            volume = { 1.0 },
            windowFocused = { true },
            onPlay = { reproducciones++ },
        )

        sounds.play(Sound.UPDATE)

        assertTrue(reproducciones == 0, "Los avisos callan si el usuario ya está mirando")
    }

    @Test
    fun `reproduce si no hay foco y esta activado`() {
        var reproducido: Sound? = null
        val sounds = Sounds(
            enabled = { true },
            volume = { 1.0 },
            windowFocused = { false },
            onPlay = { reproducido = it },
        )

        sounds.play(Sound.UPDATE)

        assertEquals(Sound.UPDATE, reproducido)
    }

    @Test
    fun `no reproduce si el sonido esta desactivado`() {
        var reproducciones = 0
        val sounds = Sounds(
            enabled = { false },
            volume = { 1.0 },
            windowFocused = { false },
            onPlay = { reproducciones++ },
        )

        sounds.play(Sound.UPDATE)

        assertTrue(reproducciones == 0)
    }

    @Test
    fun `un segundo aviso reinicia el clip en vez de continuar donde iba`() {
        // Sounds no expone el Clip real, asi que se llega a el por reflexion solo para observar
        // el efecto de play(): framePosition solo puede subir mientras un clip reproduce por su
        // cuenta, asi que verla bajar entre dos llamadas a play() del mismo sonido solo puede
        // deberse al reinicio explicito (clip.stop() + framePosition = 0) que hace la clase, no a
        // que el clip "avance menos". Sin ese reinicio, un segundo aviso mientras el primero
        // segia sonando continuaria desde donde iba y el usuario no notaria nada nuevo.
        val sounds = Sounds(enabled = { true }, volume = { 1.0 }, windowFocused = { false })
        sounds.preload()
        val clip = esperarClipCargado(sounds, Sound.SUCCESS)
            ?: return // sin dispositivo de audio en esta maquina no hay clip que reiniciar

        sounds.play(Sound.SUCCESS)
        Thread.sleep(80)
        val posicionAntesDelSegundoAviso = clip.framePosition
        assertTrue(
            posicionAntesDelSegundoAviso > 0,
            "el clip debe llevar avanzado para que el reinicio sea observable",
        )

        sounds.play(Sound.SUCCESS)

        assertTrue(
            clip.framePosition < posicionAntesDelSegundoAviso,
            "un segundo aviso reinicia el clip; si solo lo reanudara, la posicion seguiria subiendo",
        )
    }

    /** Espera a que `preload()` -que carga en un hilo aparte- termine de abrir el clip. */
    private fun esperarClipCargado(sounds: Sounds, sound: Sound): Clip? {
        val field = Sounds::class.java.getDeclaredField("clips").apply { isAccessible = true }

        @Suppress("UNCHECKED_CAST")
        val clips = field.get(sounds) as ConcurrentHashMap<Sound, Clip>
        repeat(100) {
            clips[sound]?.let { return it }
            Thread.sleep(20)
        }
        return null
    }
}
