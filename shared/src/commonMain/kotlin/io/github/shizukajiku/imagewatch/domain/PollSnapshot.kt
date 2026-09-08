package io.github.shizukajiku.imagewatch.domain

import kotlin.time.Instant

/** Fotografia inmutable de todas las imagenes vigiladas al terminar un ciclo. */
class PollSnapshot(images: List<ImageState>, val at: Instant) {
    /**
     * La copia defensiva es lo que permite al snapshot cruzar hilos sin sincronizacion: quien lo
     * publica no puede modificarlo despues de entregarlo.
     */
    val images: List<ImageState> = images.toList()

    fun find(name: String): ImageState? = images.firstOrNull { it.name == name }

    fun pending(): List<ImageState> = images.filter { it.status == ImageStatus.PENDING }

    companion object {
        val EMPTY = PollSnapshot(emptyList(), Instant.fromEpochSeconds(0))
    }
}
