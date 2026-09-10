# ImageWatch — Componentes reutilizables y correcciones de fidelidad al diseño

## Contexto

Tras cerrar las 7 fases del rediseño, una revisión con acceso directo al proyecto de diseño real
(`claude.ai/design`, vía la tool `DesignSync`) encontró defectos sistemáticos que las revisiones
manuales por captura de pantalla no habían detectado: iconos que no existen en el diseño, un token
de color que hace de dos cosas distintas, y un bug de geometría en el anillo de foco. Además, la
implementación actual reconstruye la misma "píldora" y el mismo "botón de icono circular" a mano en
siete y cuatro sitios respectivamente, lo que es la causa raíz de que estas inconsistencias
aparezcan y de que corregir una no corrija las demás.

Este documento specifica: (1) tres componentes base reutilizables, (2) el catálogo completo de
correcciones encontradas por la auditoría, y (3) la migración de cada sitio de uso actual a los
componentes nuevos.

**Fuente de verdad:** proyecto de diseño `5438f112-46c4-4627-a9fd-aabed26319ba` en `claude.ai/design`
(ficheros `Bandeja.dc.html`, `Ajustes.dc.html`, `Aviso.dc.html` — los componentes reales
renderizados, más autorizados que `Blueprint.dc.html`, que es narrativa y puede estar desactualizada).

## Alcance

Cubre `commonMain` (`ui/components/`, `ui/images/`, `ui/settings/`, `ui/dialogs/`, `ui/theme/`) y
`jvmMain` (`ui/toast/ToastWindow.kt`, `ui/components/TitleBar.kt`). No añade tests de Compose UI
(sigue sin haberlos en este proyecto, decisión aceptada desde la fase 7); la verificación es
compilar + `:desktopApp:run` + repaso visual del usuario, igual que las fases anteriores.

No toca: D-1 (barra de título solo cerrar, sin minimizar/maximizar/campana — la auditoría confirmó
que el componente real `Bandeja.dc.html` tampoco lleva campana en la barra, así que la decisión ya
tomada de quitarla queda ratificada, no en duda).

---

## 1. Tres componentes nuevos en `ui/components/`

### 1.1 `Pill.kt`

```kotlin
package io.github.shizukajiku.imagewatch.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import io.github.shizukajiku.imagewatch.ui.theme.IconSize
import io.github.shizukajiku.imagewatch.ui.theme.Radius
import io.github.shizukajiku.imagewatch.ui.theme.Space
import io.github.shizukajiku.imagewatch.ui.theme.TypeScale
import io.github.shizukajiku.imagewatch.ui.theme.focusRing

/**
 * La píldora de la app: texto centrado, icono inicial opcional, ancho fijo o al contenido. Es la
 * MISMA forma detrás de un chip de fila, un chip de cabecera, un botón del pie, el botón
 * "Agregar", la acción de un aviso y los botones de un diálogo — antes de este componente, cada
 * uno era su propio `Surface(shape = pill) { Text(...) }` escrito a mano.
 */
@Composable
fun Pill(
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    leadingIcon: AppSvg? = null,
    iconTint: Color = contentColor,
    width: Dp? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = Space.md, vertical = Space.xs + 1.dp),
    fontFamily: FontFamily? = null,
    fontWeight: FontWeight = FontWeight.SemiBold,
    fontSize: TextUnit = TypeScale.meta,
    maxLines: Int = 1,
) {
    val sizedModifier = if (width != null) modifier.width(width) else modifier
    Surface(
        color = containerColor,
        shape = RoundedCornerShape(Radius.pill),
        onClick = onClick ?: {},
        enabled = onClick != null,
        modifier = if (onClick != null) sizedModifier.focusRing(Radius.pill) else sizedModifier,
    ) {
        Row(
            horizontalArrangement = if (leadingIcon != null) {
                Arrangement.spacedBy(Space.xs + 2.dp, Alignment.CenterHorizontally)
            } else {
                Arrangement.Center
            },
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(contentPadding),
        ) {
            if (leadingIcon != null) {
                SvgIcon(leadingIcon, iconTint, Modifier.size(IconSize.sm))
            }
            Text(
                text,
                color = contentColor,
                fontFamily = fontFamily,
                fontWeight = fontWeight,
                fontSize = fontSize,
                textAlign = TextAlign.Center,
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
```

`Surface(onClick = ...)` no acepta `onClick` nulo, de ahí el `onClick ?: {}` con `enabled = onClick
!= null`: una píldora sin acción (p. ej. la píldora de versión) no debe ser clicable ni recibir
foco, y por eso el `.focusRing()` solo se aplica cuando sí hay `onClick`.

### 1.2 `IwIconButton.kt`

```kotlin
package io.github.shizukajiku.imagewatch.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.shizukajiku.imagewatch.ui.theme.IconSize
import io.github.shizukajiku.imagewatch.ui.theme.focusRing

/**
 * Botón de icono circular: buscar/campana/ajustes de la cabecera, kebab de fila, volver de
 * Ajustes, cerrar/minimizar/maximizar de la barra de título. `toggledOn` es para el único caso
 * con dos estados visuales -la campana de silenciar-todo-: fondo `toggledBackground` y tinte
 * `toggledTint` cuando está activo, transparente si no.
 */
@Composable
fun IwIconButton(
    icon: AppSvg,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    boxSize: Dp = 32.dp,
    iconSize: Dp = IconSize.lg,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    toggledOn: Boolean = false,
    toggledTint: Color = MaterialTheme.colorScheme.onSurface,
    toggledBackground: Color = MaterialTheme.colorScheme.surfaceVariant,
) {
    Surface(
        color = if (toggledOn) toggledBackground else Color.Transparent,
        shape = CircleShape,
    ) {
        IconButton(onClick, modifier = modifier.size(boxSize).focusRing(999.dp)) {
            SvgIcon(icon, if (toggledOn) toggledTint else tint, Modifier.size(iconSize))
        }
    }
}
```

### 1.3 `SurfaceCard.kt`

```kotlin
package io.github.shizukajiku.imagewatch.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.shizukajiku.imagewatch.ui.theme.LocalIsDark
import io.github.shizukajiku.imagewatch.ui.theme.Radius
import io.github.shizukajiku.imagewatch.ui.theme.hairline

/**
 * La tarjeta con borde de la app: fondo, borde de 1 dp y radio, envolviendo una `Column`. Es el
 * esqueleto detrás de `SettingsCard`, `RowCard`, `ConfirmDialog` y la tarjeta del aviso — antes
 * cada una repetía `Surface(border = BorderStroke(1.dp, ...), shape = RoundedCornerShape(...))`.
 */
@Composable
fun SurfaceCard(
    modifier: Modifier = Modifier,
    background: Color = MaterialTheme.colorScheme.surface,
    borderColor: Color = hairline(LocalIsDark.current),
    radius: Dp = Radius.md,
    tonalElevation: Dp = 0.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        color = background,
        border = BorderStroke(1.dp, borderColor),
        shape = RoundedCornerShape(radius),
        tonalElevation = tonalElevation,
    ) {
        Column(content = content)
    }
}
```

---

## 2. `ui/theme/Colors.kt` — token `hairline` nuevo

El diseño real distingue dos variables que en oscuro coinciden pero en claro no:
`--hair` (borde/divisor, `#2A2A2E` oscuro / `#E4E4EA` claro) y `--surfv` (fondo de píldora/chip,
`#2A2A2E` oscuro / `#ECECF0` claro). El código solo tenía `MaterialTheme.colorScheme.surfaceVariant`
para los dos usos — correcto en oscuro por coincidencia, ligeramente incorrecto en claro en cada
borde/divisor de la app.

```kotlin
/**
 * Borde y divisor fino -`--hair` en el diseño-, distinto de `surfaceVariant` -`--surfv`, fondo de
 * píldora- en tema claro (en oscuro los dos valores coinciden, así que ahí no hay cambio visible).
 * Antes de este token, cada borde/divisor usaba `surfaceVariant` porque era el único disponible.
 */
fun hairline(dark: Boolean): Color = if (dark) Color(0xFF2A2A2E) else Color(0xFFE4E4EA)
```

Añadir junto a `ghostBackground`/`mutedText` en `Colors.kt`, mismo patrón (función de nivel
superior con `dark: Boolean`, no extensión de `ColorScheme`).

**Sitios que pasan de `surfaceVariant` a `hairline(dark)`** (todos bordes/divisores, ninguno es un
fondo de superficie):
- `ImageRow.kt` — `RowCard` cuando `borderColor = MaterialTheme.colorScheme.surfaceVariant` (caso
  `OkRow`, fila «Al día» sin estado de atención).
- `ImageRow.kt` — `HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)` en el
  detalle desplegado de `RowCard`.
- `TitleBar.kt` — el `Box` de 1 dp del hairline inferior.
- `SettingsScreen.kt` — `BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)` de
  `SettingsCard` (se resuelve solo al migrar `SettingsCard` a `SurfaceCard`, que ya usa
  `hairline()` por defecto).

---

## 3. `ui/theme/FocusRing.kt` — fix del óvalo

**Causa exacta** (confirmada): `drawRoundRect` (API de bajo nivel de `DrawScope`) clampa el radio
**de forma independiente por eje** al dibujar, a diferencia de `RoundedCornerShape`, que clampa
uniformemente al mínimo de ambas dimensiones para producir una píldora real. Con
`cornerRadius = Radius.pill` (999 dp) sobre un chip de 88×30 (+ 2 dp de offset por lado = 92×34), el
radio pedido (~1001 dp) se clampa por separado a 46 en X y a 17 en Y — dos arcos elípticos en las
esquinas, no una píldora.

**Fix** — clampar el radio al mínimo de las dos dimensiones antes de construir `CornerRadius`:

```kotlin
.drawWithContent {
    drawContent()
    if (focused) {
        val strokeWidthPx = RING_WIDTH.toPx()
        val offsetPx = RING_OFFSET.toPx()
        val ringWidth = size.width + offsetPx * 2
        val ringHeight = size.height + offsetPx * 2
        val effectiveRadius = minOf(cornerRadius.toPx() + offsetPx, ringWidth / 2f, ringHeight / 2f)
        drawRoundRect(
            color = color,
            topLeft = Offset(-offsetPx, -offsetPx),
            size = Size(ringWidth, ringHeight),
            cornerRadius = CornerRadius(effectiveRadius),
            style = Stroke(strokeWidthPx),
        )
    }
}
```

(Reemplaza el cuerpo del `if (focused)` actual; el resto de `focusRing()` — `composed {}`,
`onFocusChanged`, la firma pública — no cambia.)

---

## 4. Catálogo de iconos — qué sobra, qué falta, qué está mal

| Sitio | Hoy | Debe ser | Acción |
|---|---|---|---|
| Cabecera de la bandeja | `IconButton(REFRESH)` suelto | no existe en el diseño | **Quitar** (`ImagesScreen.kt`, `Header`, el `IconButton({ onRefresh(null) })`). |
| Cabecera de la bandeja | `IconButton(CHECK_ALL)` suelto (si `canAcknowledgeAll`) | no existe en el diseño | **Quitar** (mismo bloque; el `CHECK_ALL` correcto es el de la píldora "Ver todas" de la cabecera de sección, que ya está bien y no se toca). |
| Chip "Visto" de la fila (`ActionChip`) | solo texto | icono `CHECK` + texto, hueco 6 | **Añadir** `leadingIcon = AppSvg.CHECK` al migrar a `Pill`. `check.svg` ya existe y coincide exacto con el path del diseño (`polyline 4 12 10 18 20 6`) — no hace falta nuevo asset. |
| Pill "Reintentar" de `ErrorSectionHeader` (`HeaderChip`) | solo texto | icono `REFRESH` + texto | **Añadir** `leadingIcon = AppSvg.REFRESH` al migrar a `Pill` (mismo asset que ya existe, solo cambia dónde se usa: aquí sí pertenece, en la cabecera general no). |
| "Silenciar avisos" / "Reactivar avisos" (`DetailPill` en `RowDetail`) | solo texto | icono `BELL_OFF`/`BELL` + texto | **Añadir** `leadingIcon` condicional al migrar a `Pill` (`bell-off.svg`/`bell.svg` ya existen, coinciden exacto). |
| `CheckingRow` (fila con comprobación en vuelo) | `AppSvg.REFRESH` rotando | arco simple sin cabeza de flecha, rotando | **Nuevo asset** `spinner.svg` + `AppSvg.SPINNER` (sección 5). |
| Botón "Agregar" | texto "Agregar imagen" | texto "Agregar" | **Corregir** el texto (confirmado en `Blueprint.dc.html` sección 02 y en `Medidas.dc.html` "«Agregar» (34 de alto)"). |

---

## 5. Icono nuevo: `AppSvg.SPINNER`

**Fichero:** `shared/src/jvmMain/resources/icons/spinner.svg`

```xml
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="#9a9aa2" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 4a8 8 0 1 1-8 8"/></svg>
```

(Mismo patrón que el resto de `icons/*.svg`: `viewBox 0 0 24 24`, `stroke-width 2`, el color real lo
pone `SvgIcon`'s `ColorFilter.tint` en tiempo de ejecución, el `#9a9aa2` del fichero es solo
plantilla.)

**`SvgIcon.kt`** — añadir `SPINNER("spinner")` al enum `AppSvg`, junto a los demás.

---

## 6. Migración archivo por archivo

### `ImagesScreen.kt`
- `Header`: quitar los `IconButton(REFRESH)`/`IconButton(CHECK_ALL)` sueltos (§4). Los que quedan
  (búsqueda, campana, ajustes) migran a `IwIconButton` (32 dp, `IconSize.lg`); la campana usa
  `toggledOn = mutedAll`.
- `Header`: botón "Agregar" migra a `Pill(text = "Agregar", leadingIcon = AppSvg.PLUS, width =
  null, containerColor = primary, contentColor = onPrimary, contentPadding = PaddingValues(16.dp,
  8.dp))`, alto resultante 33 dp (icono 14 + texto 13/600 + relleno 8 vertical ≈ 33, ya validado en
  el fix anterior de esta sesión).
- `HeaderChip` (privado): eliminar, sus dos usos (`PendingSectionHeader` "Ver todas" con `CHECK_ALL`,
  `ErrorSectionHeader` "Reintentar" con `REFRESH` nuevo) pasan a llamar `Pill` directo con
  `containerColor = primaryContainer`, `contentColor = onPrimaryContainer`.
- `OkSectionHeader`, `NoticeBanner` ("Reintentar ahora"): su `Surface(onClick=...)` pasa a `Pill`
  (sin icono) donde aplique, o se queda igual si es un contenedor no-píldora (`OkSectionHeader` es
  una barra ancha, no una píldora — **no** migra a `Pill`, se queda como está salvo el `focusRing`
  que ya tiene).
- `EmptyState`: su `Surface(...) { Pill(...) }` para "Agregar imagen" pasa igual a `Pill(text =
  "Agregar imagen", ...)` — aquí el texto largo SÍ es correcto, es el botón de la cabecera el que
  cambia, no este (verificar contra `Bandeja.dc.html` variante `sinimagenes` si el texto del vacío
  difiere; si no hay evidencia de que difiera, se deja "Agregar imagen" aquí).

### `ImageRow.kt`
- `ActionChip` (privado): eliminar, sus 3 usos (`PendingRow` "Visto", `ErrorRow` "Reintentar",
  `OkRow` — no tiene chip, solo el pill "En silencio") pasan a `Pill` directo, con `leadingIcon`
  según §4.
- `RowCard`: `borderColor` de `OkRow` pasa de `surfaceVariant` a `hairline(dark)`.
  `HorizontalDivider` del detalle igual.
- `RowDetail`'s `DetailPill` (privado): eliminar, sus usos pasan a `Pill` con `leadingIcon`
  condicional (§4) donde aplique ("Silenciar/Reactivar avisos"); "Comprobar ahora"/"Copiar
  referencia" sin icono, confirmado en el diseño.
- `SkipAndPillCell`: la píldora de versión/estado (104 dp, mono) pasa a `Pill(width = 104.dp,
  fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = TypeScale.body)`.
- `CheckingRow`: `AppSvg.REFRESH` → `AppSvg.SPINNER`.
- `RowMenu`'s kebab `IconButton`: migra a `IwIconButton(boxSize = Layout.rowKebab, iconSize =
  IconSize.md)`.

### `SettingsScreen.kt`
- `SettingsCard` (privado): su `Surface(border=..., shape=...)` pasa a `SurfaceCard`.
- `FootPill` (privado): eliminar, sus 3 usos (Iniciar/Detener, Restablecer, Borrar) pasan a `Pill`
  directo.
- El botón de volver (`Box` con `.clickable`): migra a `IwIconButton(icon = AppSvg.BACK, boxSize =
  32.dp)`.
- `Segmented`: cada opción (hoy `Text` con `.clickable` manual) pasa a `Pill` sin icono, ancho
  `Modifier.weight(1f)` (compatible con el parámetro `width: Dp?` de `Pill` siendo `null` y
  aplicando el `.weight(1f)` en el `modifier` que se le pasa desde fuera).

### `ui/dialogs/ConfirmDialog.kt`
- Los dos botones (`Cancelar`, confirmación) pasan a `Pill` directo.

### `ui/toast/ToastWindow.kt` (`jvmMain`)
- El pill de acción del aviso (`Visto`/`Reintentar`/`Ver todas`) pasa a `Pill(width =
  Layout.toastAction)`.
- El icono circular del aviso (26 dp) **no** migra a `IwIconButton` — no es un botón, es un
  indicador de estado sin `onClick`; se queda como `Box` + `SvgIcon`.

### `ui/components/TitleBar.kt`
- El botón de cerrar migra a `IwIconButton(icon = AppSvg.CLOSE, boxSize = CLOSE_HIT_WIDTH,
  iconSize = IconSize.sm)` — nota: `CLOSE_HIT_WIDTH` (46 dp) es más ancho que alto (barra de 38 dp),
  así que el `Surface(shape = CircleShape)` interno de `IwIconButton` se ve como una elipse, no un
  círculo, con ese `boxSize`. **Este es un caso donde `IwIconButton` no encaja tal cual** — la
  decisión es dejar el botón de cerrar de la barra de título con su implementación actual
  (`IconButton` simple, sin `Surface` circular, tal como está hoy) en vez de forzarlo al
  componente nuevo. Anotado como excepción deliberada, no como pendiente.

---

## Self-Review

- **Placeholders:** ninguno — cada componente tiene su cuerpo completo, cada icono tiene su path o
  su fichero de referencia existente confirmado.
- **Contradicción:** ninguna encontrada entre las secciones.
- **Ambigüedad resuelta:** el caso del cerrar de la barra de título (§6, `TitleBar.kt`) se
  resuelve explícitamente como excepción en vez de dejarlo abierto.
- **Alcance:** un solo plan de implementación (no hace falta descomponer en sub-proyectos) — son
  3 componentes + su migración mecánica a un número acotado de sitios, todos en `commonMain`/una
  clase de `jvmMain` ya tocada en fases anteriores.
