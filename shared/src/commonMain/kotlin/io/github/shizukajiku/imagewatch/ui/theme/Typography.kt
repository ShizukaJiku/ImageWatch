package io.github.shizukajiku.imagewatch.ui.theme

import androidx.compose.ui.text.TextStyle

/**
 * Cifras tabulares: cada dígito ocupa lo mismo, así un número que cambia en vivo no descuadra a
 * su vecino. Blueprint 01: se aplica a antigüedad, contadores, cuenta atrás, volumen e intervalo.
 * Se usa como `style = TabularNums` en un `Text`; cuando el `Text` ya lleva otro estilo, se
 * fusiona con `.merge(TabularNums)`.
 */
val TabularNums = TextStyle(fontFeatureSettings = "tnum")
