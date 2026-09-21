package io.github.shizukajiku.imagewatch.application

/**
 * Guarda, por imagen, la última versión remota de la que ya se avisó a Teams.
 *
 * Vive separado de [ImageStateStore] a propósito: ese guarda lo que el usuario ha dado por visto
 * en el escritorio, y el aviso a Teams no tiene "dar por visto" -no hay botón en Teams que lo
 * confirme-. Si compartiera fichero con el escritorio, reconocer una imagen ahí apagaría también
 * el aviso a Teams sin que Teams supiera nada del asunto. Esta lista es la línea base propia de la
 * integración: se actualiza sola, en silencio, cada vez que Teams decide que ya ha avisado -o que
 * no hacía falta avisar porque es la primera vez que ve la imagen- de una versión.
 */
interface TeamsNotifiedStore {
    /**
     * La versión -en texto, comparable con `Version`- de la que ya se avisó, o `null` si Teams
     * nunca ha visto esta imagen.
     */
    fun find(name: String): String?

    /**
     * Inserta o actualiza las versiones indicadas, *sin tocar* las que no aparezcan. Reemplazar el
     * contenido entero haría que avisar de una imagen borrase lo ya sabido de las demás.
     */
    fun save(versions: Map<String, String>)
}
