package io.github.shizukajiku.imagewatch.application

/** Persiste la lista de imágenes que el vigilante consulta, editable en caliente desde la interfaz. */
interface TrackedImageStore {
    fun findAll(): List<String>

    fun save(names: List<String>)
}
