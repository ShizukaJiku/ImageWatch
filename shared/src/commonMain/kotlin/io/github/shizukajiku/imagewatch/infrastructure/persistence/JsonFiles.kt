package io.github.shizukajiku.imagewatch.infrastructure.persistence

import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath

/**
 * Escritura de ficheros JSON que no deja el destino a medias.
 *
 * Se escribe primero un fichero temporal y luego se sustituye el destino con un movimiento
 * atómico. Si el sistema de ficheros no lo soporta se recurre a copiar y borrar, que es lo mejor
 * disponible en ese caso. El temporal se borra siempre, aunque el movimiento falle.
 *
 * Vive aparte porque la usan varios almacenes: duplicarla significaría corregir cualquier fallo de
 * esta secuencia tantas veces como copias haya.
 */
internal object JsonFiles {
    // Configuración por defecto a propósito: rechaza campos desconocidos, igual que hacía Jackson.
    // Un fichero con un campo que esta versión no entiende es un fichero que no se sabe leer, y
    // fallar es más honesto que cargar la mitad.
    val json = Json

    /** El sistema de ficheros real. Aparte para que un test pueda leerlo sin duplicar la constante. */
    val fileSystem: FileSystem get() = FileSystem.SYSTEM

    inline fun <reified T> read(file: Path, description: String): T = try {
        json.decodeFromString<T>(fileSystem.read(file) { readUtf8() })
    } catch (e: RuntimeException) {
        throw IllegalStateException("No se pudo leer $description", e)
    } catch (e: IOException) {
        throw IllegalStateException("No se pudo leer $description", e)
    }

    inline fun <reified T> write(file: Path, value: T, description: String) {
        val directory = file.parent ?: ".".toPath()
        val temporary = directory.resolve("${file.name}.tmp")
        try {
            fileSystem.createDirectories(directory)
            fileSystem.write(temporary) { writeUtf8(json.encodeToString(value)) }
            replace(temporary, file)
        } catch (e: IOException) {
            throw IllegalStateException("No se pudo guardar $description", e)
        } finally {
            deleteQuietly(temporary)
        }
    }

    fun replace(temporary: Path, target: Path) {
        try {
            fileSystem.atomicMove(temporary, target)
        } catch (ignored: IOException) {
            // okio no distingue «este sistema de ficheros no soporta movimientos atómicos» de un
            // fallo de E/S real, cosa que `java.nio` sí hacía con `AtomicMoveNotSupportedException`.
            // Se degrada igual que antes —copiar y borrar— y, si el fallo era real, la copia vuelve
            // a fallar y el error sube. Lo que se pierde es la distinción, no la garantía.
            fileSystem.copy(temporary, target)
            fileSystem.delete(temporary)
        }
    }

    fun deleteQuietly(temporary: Path) {
        try {
            fileSystem.delete(temporary, mustExist = false)
        } catch (ignored: IOException) {
            // La siguiente escritura puede reemplazar el temporal sin problema.
        }
    }
}
