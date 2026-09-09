package io.github.shizukajiku.imagewatch.application

/**
 * Arranque de la aplicación al iniciar sesión en el sistema. Es estado del sistema operativo,
 * no configuración de la app: no vive en `AppConfig` ni en `config.json`. La pantalla de ajustes
 * lo lee y lo escribe directamente a través de este puerto.
 */
interface AutostartPort {
    fun isEnabled(): Boolean

    fun setEnabled(enabled: Boolean)
}
