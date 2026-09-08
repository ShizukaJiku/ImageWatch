package io.github.shizukajiku.imagewatch.application

import io.github.shizukajiku.imagewatch.domain.ImageState

interface NotificationPort {
    /** Recibe únicamente las imágenes que acaban de pasar a tener una versión pendiente. */
    fun notifyUpdates(updates: List<ImageState>)
}
