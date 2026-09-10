package io.github.shizukajiku.imagewatch.infrastructure.remote

import io.github.shizukajiku.imagewatch.application.ImageResult
import io.github.shizukajiku.imagewatch.application.ImageSource

/**
 * Origen sin configurar: ni simulación ni URL de registry. Es el estado de una instalación limpia
 * antes de que el usuario introduzca la URL en Ajustes. Devuelve un fallo por imagen en lugar de
 * construir un [HttpImageSource] con URL vacía, que lanzaría en el constructor.
 *
 * Con la lista de imágenes vacía —lo normal en ese estado— no devuelve nada y el sondeo no hace
 * trabajo.
 */
object EmptyImageSource : ImageSource {
    override suspend fun findByNames(names: List<String>): List<ImageResult> =
        names.map { ImageResult.failed(it, "Sin URL de registry: configúrala en Ajustes") }
}
