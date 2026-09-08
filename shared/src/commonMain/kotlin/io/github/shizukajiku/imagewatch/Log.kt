package io.github.shizukajiku.imagewatch

/**
 * Registro mínimo para el código que vive en `commonMain`. Un solo consumidor:
 * `VersionPollingService`. El resto del proyecto está en el lado JVM y usa slf4j directamente.
 *
 * Pierde la evaluación diferida de los `{}` de slf4j a cambio de plantillas de Kotlin. Con un
 * puñado de líneas por ciclo de sondeo, el coste es irrelevante.
 */
expect class Log(name: String) {
    fun debug(message: String)

    fun info(message: String)

    fun warn(message: String, error: Throwable? = null)

    fun error(message: String, error: Throwable? = null)
}
