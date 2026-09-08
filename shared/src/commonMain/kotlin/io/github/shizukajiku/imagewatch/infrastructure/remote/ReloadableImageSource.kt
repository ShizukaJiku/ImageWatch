package io.github.shizukajiku.imagewatch.infrastructure.remote

import io.github.shizukajiku.imagewatch.application.ImageResult
import io.github.shizukajiku.imagewatch.application.ImageSource
import kotlin.concurrent.Volatile

/**
 * Origen que puede sustituirse sin reiniciar la aplicación.
 *
 * El campo es `volatile` y no hay bloqueo: la escritura ocurre en el hilo de la interfaz y la
 * lectura en el del planificador, así que lo que hace falta es visibilidad. Un `synchronized` en
 * `findByNames` dejaría el guardado de ajustes esperando a que terminase un ciclo de red entero.
 *
 * Un ciclo ya en vuelo termina con el origen anterior. Es aceptable: dura un ciclo y el siguiente
 * ya usa el nuevo.
 */
class ReloadableImageSource(initial: ImageSource) : ImageSource {
    @Volatile
    private var delegate: ImageSource = initial

    fun swap(replacement: ImageSource) {
        delegate = replacement
    }

    override suspend fun findByNames(names: List<String>): List<ImageResult> = delegate.findByNames(names)
}
