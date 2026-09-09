package io.github.shizukajiku.imagewatch.application

import io.github.shizukajiku.imagewatch.domain.ImageState

interface NotificationPort {
    /** Recibe únicamente las imágenes que acaban de pasar a tener una versión pendiente. */
    fun notifyUpdates(updates: List<ImageState>)

    /**
     * Recibe las imágenes que acaban de pasar a ERROR: no lo estaban en el ciclo anterior. Por
     * defecto no hace nada, para que un notificador al que no le interese no tenga que
     * implementarlo.
     */
    fun notifyFailures(failures: List<ImageState>) {}
}
