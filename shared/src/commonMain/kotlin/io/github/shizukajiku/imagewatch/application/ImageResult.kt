package io.github.shizukajiku.imagewatch.application

import io.github.shizukajiku.imagewatch.domain.ImageRelease

/**
 * Resultado de consultar una imagen concreta.
 *
 * Que el fallo viaje por imagen, y no como excepción del lote, es lo que impide que una imagen
 * caída deje sin actualizar a todas las demás.
 */
data class ImageResult(val name: String, val release: ImageRelease?, val error: String?) {
    companion object {
        fun found(release: ImageRelease) = ImageResult(release.name, release, null)

        fun failed(name: String, message: String) = ImageResult(name, null, message)
    }
}
