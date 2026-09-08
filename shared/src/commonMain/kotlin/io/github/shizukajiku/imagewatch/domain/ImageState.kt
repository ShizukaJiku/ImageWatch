package io.github.shizukajiku.imagewatch.domain

import kotlin.time.Instant

/**
 * Estado calculado de una imagen vigilada. Reemplaza a la representacion privada que vivia dentro
 * de la capa de presentacion, de modo que la interfaz deja de contener logica de dominio.
 *
 * `lastCheckedAt` es el momento de la verificacion local, no la marca de tiempo que declara el
 * origen.
 */
data class ImageState(
    val name: String,
    val local: Version?,
    val remote: Version?,
    val registry: String,
    val status: ImageStatus,
    val error: String?,
    val lastCheckedAt: Instant,
) {
    companion object {
        private const val NO_REGISTRY = "—"

        fun unknown(name: String, at: Instant): ImageState = ImageState(
            name = name,
            local = null,
            remote = null,
            registry = NO_REGISTRY,
            status = ImageStatus.UNKNOWN,
            error = null,
            lastCheckedAt = at,
        )

        /**
         * Conserva la version y el registro que ya se conocian de la imagen: un fallo de consulta
         * no debe borrar lo ultimo que se sabia de ella.
         */
        fun failed(
            name: String,
            local: Version? = null,
            registry: String? = null,
            message: String,
            at: Instant,
        ): ImageState = ImageState(
            name = name,
            local = local,
            remote = null,
            registry = registry ?: NO_REGISTRY,
            status = ImageStatus.ERROR,
            error = message,
            lastCheckedAt = at,
        )
    }
}
