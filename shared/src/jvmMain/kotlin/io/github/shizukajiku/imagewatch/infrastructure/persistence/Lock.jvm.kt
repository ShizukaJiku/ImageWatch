package io.github.shizukajiku.imagewatch.infrastructure.persistence

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Reentrante a propósito: `JsonTrackedImageStore.findAll()` llama a `save()` cuando el fichero no
 * existe, y `JsonImageStateStore.rename()` llama a `find()`. Con un cerrojo no reentrante, ambas se
 * bloquearían contra sí mismas. `@Synchronized`, que es lo que había antes, también era reentrante.
 */
internal actual class Lock actual constructor() {
    private val delegate = ReentrantLock()

    actual fun <T> withLock(block: () -> T): T = delegate.withLock(block)
}
