package io.github.shizukajiku.imagewatch.ui.settings

import io.github.shizukajiku.imagewatch.application.UpdatePhase

/**
 * Lo que la tarjeta «Actualizaciones» necesita. No es parte de `SettingsUiState` -no se edita, como
 * `polling` o `verifying`-: lo arma `SettingsPane` desde `UpdateService`.
 */
data class UpdateUiState(
    val currentVersion: String,
    val phase: UpdatePhase,
    /** Instalación portable: la tarjeta solo enlaza a GitHub, sin botón de instalar. */
    val portable: Boolean,
)
