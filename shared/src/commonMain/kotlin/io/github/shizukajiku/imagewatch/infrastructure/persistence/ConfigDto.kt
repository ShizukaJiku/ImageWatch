package io.github.shizukajiku.imagewatch.infrastructure.persistence

import kotlinx.serialization.Serializable

/**
 * Formato de cable de la configuración. El JSON se escribe con tipos elementales —números, cadenas
 * y listas— y la traducción a `Duration`, rutas y enumeraciones queda en un único sitio.
 *
 * `stateFile` no está aquí a propósito: no es editable y llega desde el entorno, así que
 * persistirlo permitiría que un fichero viejo apuntara el estado a una ruta que ya no existe.
 */
@Serializable
internal data class ConfigDto(
    val remoteUrl: String,
    val pollIntervalSeconds: Long,
    val imageNames: List<String>,
    val simulationMode: Boolean,
    val ignoreSslErrors: Boolean,
    val theme: String,
    val toastsEnabled: Boolean,
    val toastSeconds: Long,
    val soundsEnabled: Boolean,
    val soundVolume: Double,
    /**
     * Con valor por defecto porque se añadió después de que hubiera ficheros escritos sin él, y
     * un `config.json` de una versión anterior tiene que seguir cargando. Jackson lo daba gratis;
     * kotlinx-serialization exige todos los campos salvo los que declaran un valor por defecto.
     * **Todo campo que se añada aquí en adelante necesita uno**, o la aplicación deja de arrancar
     * para quien ya la tuviera instalada.
     */
    val mutedAll: Boolean = false,
)
