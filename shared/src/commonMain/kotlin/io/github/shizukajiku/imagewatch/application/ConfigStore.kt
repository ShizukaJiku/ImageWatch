package io.github.shizukajiku.imagewatch.application

import io.github.shizukajiku.imagewatch.config.AppConfig

/** Configuración persistida. La siembra el entorno una vez; después manda el fichero. */
interface ConfigStore {
    fun load(): AppConfig

    fun save(config: AppConfig)
}
