package io.github.shizukajiku.imagewatch.config

import kotlin.time.Duration

/**
 * Configuración efectiva de la aplicación.
 *
 * `stateFile` es una ruta en texto, no un tipo de fichero de la plataforma: el dominio no debe
 * saber cómo se llama un fichero en cada sistema. La conversión ocurre en el adaptador de
 * persistencia, que es quien sí lo sabe.
 *
 * La versión Java copiaba `imageNames` al construir porque `java.util.List` es mutable y la
 * configuración cruza hilos. Aquí el tipo es `List`, la interfaz de solo lectura de Kotlin, y
 * todos los sitios que construyen un `AppConfig` parten de una lista recién creada: la copia
 * defendía de un problema que el sistema de tipos ya resuelve.
 */
data class AppConfig(
    val stateFile: String,
    val remoteUrl: String,
    val pollInterval: Duration,
    val imageNames: List<String>,
    val simulationMode: Boolean,
    val ignoreSslErrors: Boolean,
    val theme: ThemePreference,
    val toastsEnabled: Boolean,
    val toastDuration: Duration,
    val soundsEnabled: Boolean,
    val soundVolume: Double,
    val mutedAll: Boolean,
)
