package io.github.shizukajiku.imagewatch.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import io.github.shizukajiku.imagewatch.config.ThemePreference

/** Si el tema activo es oscuro. Lo consultan los composables que eligen color por estado. */
val LocalIsDark = staticCompositionLocalOf { true }

@Composable
fun ImageWatchTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalIsDark provides dark) {
        MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
    }
}

/**
 * La preferencia persistida decide el tema. `SYSTEM` delega en el escritorio, que es lo que
 * quiere quien no ha elegido: si el usuario cambia el suyo a oscuro por la noche, la aplicación
 * lo sigue sin que él vuelva aquí.
 */
@Composable
fun ImageWatchTheme(preference: ThemePreference, content: @Composable () -> Unit) {
    val dark = when (preference) {
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
        ThemePreference.LIGHT -> false
        ThemePreference.DARK -> true
    }
    ImageWatchTheme(dark = dark, content = content)
}
