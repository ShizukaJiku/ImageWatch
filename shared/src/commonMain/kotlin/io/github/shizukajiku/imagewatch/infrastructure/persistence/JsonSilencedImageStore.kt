package io.github.shizukajiku.imagewatch.infrastructure.persistence

import io.github.shizukajiku.imagewatch.application.SilencedImageStore
import okio.Path

/**
 * [SilencedImageStore] respaldado por un fichero JSON. Sin fichero, ninguna imagen está silenciada:
 * no hay una semilla que sembrar, a diferencia de `JsonTrackedImageStore`.
 */
class JsonSilencedImageStore(private val file: Path) : SilencedImageStore {
    private val lock = Lock()

    override fun findAll(): Set<String> = lock.withLock {
        if (!JsonFiles.fileSystem.exists(file)) {
            return@withLock emptySet()
        }
        JsonFiles.read<List<String>>(file, DESCRIPTION).toSet()
    }

    override fun save(names: Set<String>) = lock.withLock {
        // Se ordena al escribir para que el fichero no cambie de orden entre guardados y los
        // cambios que se vean en un diff sean los reales.
        JsonFiles.write(file, names.sorted(), DESCRIPTION)
    }

    private companion object {
        private const val DESCRIPTION = "las imágenes con avisos silenciados"
    }
}
