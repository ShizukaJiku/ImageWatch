package io.github.shizukajiku.imagewatch.application

import io.github.shizukajiku.imagewatch.domain.ImageRelease

/**
 * Guarda, por imagen, la publicación que el usuario ya ha dado por vista.
 *
 * No es «lo último que publicó el origen»: si lo fuera, cada ciclo reconocería automáticamente lo
 * que acaba de detectar y ninguna imagen podría quedarse pendiente. Solo se escribe al establecer
 * la línea base de una imagen nueva y al reconocer explícitamente.
 */
interface ImageStateStore {
    fun find(name: String): ImageRelease?

    /**
     * Inserta o actualiza las publicaciones indicadas, *sin tocar* las que no aparezcan en la
     * lista. Reemplazar el contenido entero haría que reconocer una imagen borrase la versión
     * reconocida de todas las demás.
     */
    fun save(releases: List<ImageRelease>)

    /**
     * Traslada la publicación reconocida de `previous` a `current`. Es un no-op si no había
     * ninguna.
     *
     * Renombrar una imagen no debería costarle al usuario su historial: sin esto, la imagen
     * renombrada nace sin versión reconocida —vuelve a avisar de lo que ya había dado por visto— y
     * la entrada vieja se queda huérfana en el fichero para siempre.
     */
    fun rename(previous: String, current: String)
}
