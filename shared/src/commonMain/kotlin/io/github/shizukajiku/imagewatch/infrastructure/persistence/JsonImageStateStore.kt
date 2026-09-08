package io.github.shizukajiku.imagewatch.infrastructure.persistence

import io.github.shizukajiku.imagewatch.application.ImageStateStore
import io.github.shizukajiku.imagewatch.domain.ImageRelease
import okio.Path

class JsonImageStateStore(private val file: Path) : ImageStateStore {
    private val lock = Lock()

    override fun find(name: String): ImageRelease? = lock.withLock {
        read().firstOrNull { it.name == name }
    }

    override fun save(releases: List<ImageRelease>) = lock.withLock {
        val byName = LinkedHashMap<String, ImageRelease>()
        read().forEach { byName[it.name] = it }
        releases.forEach { byName[it.name] = it }
        JsonFiles.write(file, byName.values.toList(), DESCRIPTION)
    }

    override fun rename(previous: String, current: String) = lock.withLock {
        val known = find(previous) ?: return@withLock
        val byName = LinkedHashMap<String, ImageRelease>()
        read().forEach { byName[it.name] = it }
        byName.remove(previous)
        byName[current] = known.copy(name = current)
        JsonFiles.write(file, byName.values.toList(), DESCRIPTION)
    }

    private fun read(): List<ImageRelease> {
        if (!JsonFiles.fileSystem.exists(file)) {
            return emptyList()
        }
        return JsonFiles.read<List<ImageRelease>>(file, DESCRIPTION)
    }

    private companion object {
        private const val DESCRIPTION = "las versiones reconocidas"
    }
}
