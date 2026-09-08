package io.github.shizukajiku.imagewatch.infrastructure.persistence

import okio.FileSystem
import okio.Path
import kotlin.random.Random

/**
 * Sustituye al `@TempDir` de JUnit, que era lo último que ataba estas pruebas a la JVM.
 *
 * Crea un directorio propio bajo el temporal del sistema y lo borra al terminar, pase lo que pase
 * dentro. El nombre lleva un sufijo aleatorio para que dos pruebas que corran a la vez no compartan
 * carpeta: se comprueba escritura de ficheros reales, y dos tests pisándose el mismo directorio
 * fallarían de forma intermitente y sin explicación.
 */
internal fun withTempDir(block: (Path) -> Unit) {
    val dir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "imagewatch-${Random.nextLong().toULong().toString(16)}"
    FileSystem.SYSTEM.createDirectories(dir)
    try {
        block(dir)
    } finally {
        FileSystem.SYSTEM.deleteRecursively(dir, mustExist = false)
    }
}
