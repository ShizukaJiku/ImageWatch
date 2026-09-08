package io.github.shizukajiku.imagewatch.application

interface ImageSource {
    /**
     * Consulta cada nombre y devuelve un resultado por imagen, en el mismo orden en que se
     * pidieron.
     *
     * Las implementaciones no propagan el fallo de una imagen concreta: lo devuelven dentro de su
     * [ImageResult].
     */
    suspend fun findByNames(names: List<String>): List<ImageResult>
}
