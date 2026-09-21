package io.github.shizukajiku.imagewatch.infrastructure.persistence

import io.github.shizukajiku.imagewatch.application.TeamsNotifiedStore
import okio.Path

/** [TeamsNotifiedStore] respaldado por un fichero JSON propio, aparte de `images.json`. */
class JsonTeamsNotifiedStore(private val file: Path) : TeamsNotifiedStore {
    private val lock = Lock()

    override fun find(name: String): String? = lock.withLock {
        read()[name]
    }

    override fun save(versions: Map<String, String>) = lock.withLock {
        val merged = read().toMutableMap()
        merged.putAll(versions)
        JsonFiles.write(file, merged, DESCRIPTION)
    }

    private fun read(): Map<String, String> {
        if (!JsonFiles.fileSystem.exists(file)) {
            return emptyMap()
        }
        return JsonFiles.read<Map<String, String>>(file, DESCRIPTION)
    }

    private companion object {
        private const val DESCRIPTION = "las versiones ya avisadas a Teams"
    }
}
