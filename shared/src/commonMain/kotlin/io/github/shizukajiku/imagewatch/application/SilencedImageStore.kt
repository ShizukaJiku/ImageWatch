package io.github.shizukajiku.imagewatch.application

/**
 * Persiste qué imágenes tienen sus avisos silenciados. Silenciar no deja de vigilar la imagen
 * —sigue comprobándose y su versión sigue al día en la lista—, solo evita que salte el aviso; por
 * eso vive separado de [TrackedImageStore], que decide qué se vigila, no cómo se avisa.
 */
interface SilencedImageStore {
    fun findAll(): Set<String>

    fun save(names: Set<String>)
}
