package io.github.shizukajiku.imagewatch.domain

/**
 * Un Release de GitHub que se puede instalar: trae MSI y su checksum, y una versión mayor que la
 * instalada. Si al Release le falta cualquiera de las dos piezas, no llega a construirse esto:
 * el chequeo devuelve `CheckFailed`.
 */
data class UpdateManifest(
    val latestVersion: SemanticVersion,
    val notes: String,
    val msiUrl: String,
    val sha256Url: String,
)
