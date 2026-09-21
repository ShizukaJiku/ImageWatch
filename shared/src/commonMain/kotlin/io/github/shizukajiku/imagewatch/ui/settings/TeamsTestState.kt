package io.github.shizukajiku.imagewatch.ui.settings

/** Lo que pinta el botón «Probar webhook» de la tarjeta «Teams», igual en espíritu que `UpdatePhase`. */
sealed interface TeamsTestState {
    data object Idle : TeamsTestState

    data object Sending : TeamsTestState

    data object Success : TeamsTestState

    data class Failed(val message: String) : TeamsTestState
}
