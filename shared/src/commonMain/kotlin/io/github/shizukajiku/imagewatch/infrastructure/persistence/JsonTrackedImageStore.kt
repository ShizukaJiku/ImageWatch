package io.github.shizukajiku.imagewatch.infrastructure.persistence

import io.github.shizukajiku.imagewatch.application.TrackedImageStore
import okio.Path

/** [TrackedImageStore] respaldado por un fichero JSON, sembrado una vez desde una lista. */
class JsonTrackedImageStore(private val file: Path, defaults: List<String>) : TrackedImageStore {
    private val defaults: List<String> = defaults.toList()
    private val lock = Lock()

    override fun findAll(): List<String> = lock.withLock {
        if (!JsonFiles.fileSystem.exists(file)) {
            save(defaults)
            return@withLock defaults
        }
        JsonFiles.read<List<String>>(file, DESCRIPTION)
    }

    override fun save(names: List<String>) = lock.withLock {
        JsonFiles.write(file, names, DESCRIPTION)
    }

    private companion object {
        private const val DESCRIPTION = "la lista de imágenes vigiladas"
    }
}
