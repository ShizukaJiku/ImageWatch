package io.github.shizukajiku.imagewatch.application

/**
 * Lo que la sección «Actualizaciones» de Ajustes pinta. No lleva el `UpdateManifest` -eso vive en
 * [UpdateService.pendingManifest]-: la UI solo necesita el número de versión y las notas.
 */
sealed interface UpdatePhase {
    data object Idle : UpdatePhase

    data object Checking : UpdatePhase

    data object UpToDate : UpdatePhase

    data class Available(val version: String, val notes: String) : UpdatePhase

    /** `fraction` nula = barra indeterminada (sin `Content-Length`). */
    data class Downloading(val fraction: Float?) : UpdatePhase

    data class ReadyToApply(val version: String) : UpdatePhase

    data class Failed(val message: String) : UpdatePhase
}
