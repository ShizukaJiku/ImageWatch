package io.github.shizukajiku.imagewatch.application

import io.github.shizukajiku.imagewatch.domain.UpdateManifest

/** Resultado de preguntar a GitHub si hay algo más nuevo. Nunca es una excepción. */
sealed interface UpdateStatus {
    data object UpToDate : UpdateStatus

    data class Available(val manifest: UpdateManifest) : UpdateStatus

    data class CheckFailed(val reason: String) : UpdateStatus
}
