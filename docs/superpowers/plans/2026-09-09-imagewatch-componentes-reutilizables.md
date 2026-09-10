# Componentes reutilizables y correcciones de fidelidad al diseño — Plan de implementación

> **Para quien ejecute:** REQUIRED SUB-SKILL: usar `superpowers:subagent-driven-development`
> (recomendado) o `superpowers:executing-plans` para ejecutar este plan tarea a tarea. Los pasos
> usan casillas (`- [ ]`) para el seguimiento.

**Goal:** Sustituir las siete reimplementaciones ad-hoc de "píldora" y las cuatro de "botón de
icono circular" por tres componentes reutilizables (`Pill`, `IwIconButton`, `SurfaceCard`), y de
paso corregir todo lo que la auditoría contra el proyecto de diseño real encontró: iconos que
sobran en la cabecera, iconos que faltan en varios chips, un color de borde que solo estaba bien en
tema oscuro, y el bug de geometría que convertía el anillo de foco en un óvalo sobre el chip
"Visto".

**Arquitectura:** Tres composables nuevos en `ui/components/` (sin dependencias de dominio, solo
`ui/theme`), un token de color nuevo en `Colors.kt`, un icono nuevo (`SPINNER`), y un fix de una
función existente (`focusRing`). Todo lo demás es migración mecánica: cada `Surface(shape =
RoundedCornerShape(Radius.pill), ...)`/`Surface(border = ..., shape = RoundedCornerShape(...))`
escrito a mano pasa a llamar al componente nuevo con los mismos parámetros visuales.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform. Todo en `commonMain` salvo el icono
`spinner.svg` (recurso `jvmMain`, como el resto de `icons/*.svg`).

**Spec:** `docs/superpowers/specs/2026-09-09-imagewatch-componentes-reutilizables-design.md`.

## Restricciones globales

- Commits en español, `<tipo>: <descripción>`, terminando con la atribución vigente del
  `system-reminder` de esta sesión.
- **Nunca** `git checkout`/`git restore <fichero>`. `git add` solo con rutas explícitas — nunca
  `git add -A` (dos ficheros de otra rama, `2026-09-07-migracion-plantilla-kmp.md` y `ESTADO.md`,
  siguen modificados y no son de este trabajo).
- ktlint: línea máxima **120**, no 140.
- **Sin test de Compose UI** (decisión aceptada desde la fase 7). Verificación: compilar +
  `:shared:jvmTest` (no debe romper nada existente) + `:desktopApp:run` + repaso visual del
  usuario en la última tarea.
- Verificación por tarea, **solo desde PowerShell** con `JAVA_HOME` fijado:
  ```powershell
  $env:JAVA_HOME='C:\Users\shizu\.jdks\ms-21.0.12.1'
  Set-Location C:\Users\shizu\IdeaProjects\ImageWatch
  .\gradlew.bat :shared:jvmTest :shared:koverVerify :desktopApp:compileKotlin --console=plain
  ```
  y `spotlessApply` + `spotlessCheck` antes del commit final de cada tarea.
- Los componentes nuevos no dependen de `ui/images`/`ui/settings`/`ui/dialogs`/`ui/toast` — solo de
  `ui/theme` y de sí mismos (`SvgIcon`, `AppSvg`). La dependencia va siempre en el sentido
  pantalla → componente, nunca al revés.

---

## Fichero por fichero

| Fichero | Qué cambia |
|---|---|
| `ui/theme/Colors.kt` | + `hairline(dark: Boolean): Color` |
| `ui/components/SvgIcon.kt` | + `AppSvg.SPINNER` |
| `shared/src/jvmMain/resources/icons/spinner.svg` (nuevo) | arco simple sin cabeza de flecha |
| `ui/theme/FocusRing.kt` | fix: radio de esquina clampado a `min(cornerRadius, minDimension/2)` |
| `ui/components/Pill.kt` (nuevo) | píldora configurable |
| `ui/components/IwIconButton.kt` (nuevo) | botón de icono circular configurable |
| `ui/components/SurfaceCard.kt` (nuevo) | tarjeta con borde configurable |
| `ui/images/ImagesScreen.kt` | quita iconos sueltos de cabecera, migra `Header`/`HeaderChip`/`NoticeBanner`/`EmptyState` a los componentes nuevos, corrige texto "Agregar" |
| `ui/images/ImageRow.kt` | migra `ActionChip`/`DetailPill`/`SkipAndPillCell`/kebab, `hairline` en bordes, icono `SPINNER` en `CheckingRow`, iconos que faltaban |
| `ui/settings/SettingsScreen.kt` | migra `SettingsCard`/`FootPill`/`Segmented`/botón de volver |
| `ui/dialogs/ConfirmDialog.kt` | migra sus dos botones |
| `ui/toast/ToastWindow.kt` (`jvmMain`) | migra el pill de acción |

---

### Task 1: Token de color, icono nuevo y fix del anillo de foco

**Files:**
- Modify: `shared/src/commonMain/kotlin/io/github/shizukajiku/imagewatch/ui/theme/Colors.kt`
- Modify: `shared/src/commonMain/kotlin/io/github/shizukajiku/imagewatch/ui/components/SvgIcon.kt`
- Create: `shared/src/jvmMain/resources/icons/spinner.svg`
- Modify: `shared/src/commonMain/kotlin/io/github/shizukajiku/imagewatch/ui/theme/FocusRing.kt`

**Interfaces:**
- Produces: `fun hairline(dark: Boolean): Color`, `AppSvg.SPINNER`.
- No consumidores todavía en esta tarea — solo se añade y se corrige, se usa desde la Task 6+.

- [ ] **Step 1: `hairline()` en `Colors.kt`**

Añadir junto a `ghostBackground`/`mutedText`, al final del fichero:

```kotlin
/**
 * Borde y divisor fino -`--hair` en el diseño-, distinto de `surfaceVariant` -`--surfv`, fondo de
 * píldora- en tema claro (en oscuro los dos valores coinciden, así que ahí no hay cambio visible).
 * Antes de este token, cada borde/divisor usaba `surfaceVariant` porque era el único disponible,
 * y en tema claro salía `#ECECF0` en vez de `#E4E4EA`.
 */
fun hairline(dark: Boolean): Color = if (dark) Color(0xFF2A2A2E) else Color(0xFFE4E4EA)
```

- [ ] **Step 2: `AppSvg.SPINNER` + `spinner.svg`**

`SvgIcon.kt`, añadir al enum (junto a `REFRESH`):

```kotlin
    REFRESH("refresh"),
    SPINNER("spinner"),
```

Crear `shared/src/jvmMain/resources/icons/spinner.svg` (mismo patrón que el resto de
`icons/*.svg`: `viewBox 0 0 24 24`, `stroke-width 2`, el color real lo pone `SvgIcon` en tiempo de
ejecución):

```xml
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="#9a9aa2" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 4a8 8 0 1 1-8 8"/></svg>
```

- [ ] **Step 3: Fix del óvalo en `FocusRing.kt`**

`drawRoundRect` clampa el radio de forma independiente por eje (no como `RoundedCornerShape`, que
clampa uniforme al mínimo de ambas dimensiones). Con `cornerRadius = Radius.pill` (999 dp) sobre un
chip de 88×30, el resultado son dos arcos elípticos, no una píldora. Reemplazar el cuerpo del
`if (focused)`:

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

(El resto de `focusRing()` — `composed {}`, `onFocusChanged`, la firma pública, las importaciones —
no cambia; `CornerRadius`/`Offset`/`Size` ya están importados.)

- [ ] **Step 4: Compilar**

```powershell
$env:JAVA_HOME='C:\Users\shizu\.jdks\ms-21.0.12.1'
Set-Location C:\Users\shizu\IdeaProjects\ImageWatch
.\gradlew.bat :shared:compileKotlinJvm --console=plain
```

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/io/github/shizukajiku/imagewatch/ui/theme/Colors.kt shared/src/commonMain/kotlin/io/github/shizukajiku/imagewatch/ui/components/SvgIcon.kt shared/src/jvmMain/resources/icons/spinner.svg shared/src/commonMain/kotlin/io/github/shizukajiku/imagewatch/ui/theme/FocusRing.kt
git commit -m "feat: token hairline, icono spinner y fix de geometria del anillo de foco"
```

---

### Task 2: `Pill.kt`

**Files:**
- Create: `shared/src/commonMain/kotlin/io/github/shizukajiku/imagewatch/ui/components/Pill.kt`

**Interfaces:**
- Consumes: `focusRing` (`ui/theme/FocusRing.kt`), `SvgIcon`/`AppSvg` (mismo paquete).
- Produces: `Pill(text, containerColor, contentColor, modifier, onClick, leadingIcon, iconTint, width, contentPadding, fontFamily, fontWeight, fontSize, maxLines)`.

- [ ] **Step 1: Crear el fichero**

```kotlin
package io.github.shizukajiku.imagewatch.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
 * misma forma detrás de un chip de fila, un chip de cabecera, un botón del pie, "Agregar", la
 * acción de un aviso y los botones de un diálogo.
 *
 * Sin `onClick` es una etiqueta -la píldora de versión-: no lleva foco ni ripple. Con `onClick`
 * pasa por la rama que sí acepta gestos y `focusRing()`.
 *
 * `width == null` dibuja al contenido -`HeaderChip`, `FootPill`, botones de diálogo-; con `width`
 * fijo -chip de fila, píldora de versión, acción del aviso- el `Row` interior sí ocupa todo el
 * ancho para que `Arrangement.Center`/`spacedBy(..., CenterHorizontally)` centren de verdad: un
 * `Row` sin `fillMaxWidth` mide su propio contenido, no el ancho fijo del `Surface` que lo envuelve
 * -sin este detalle, una píldora de ancho fijo con icono queda pegada a la izquierda en vez de
 * centrada-.
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
    val shape = RoundedCornerShape(Radius.pill)
    val body: @Composable () -> Unit = {
        Row(
            horizontalArrangement = if (leadingIcon != null) {
                Arrangement.spacedBy(Space.xs + 2.dp, Alignment.CenterHorizontally)
            } else {
                Arrangement.Center
            },
            verticalAlignment = Alignment.CenterVertically,
            modifier = (if (width != null) Modifier.fillMaxWidth() else Modifier).padding(contentPadding),
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
    if (onClick != null) {
        Surface(
            onClick = onClick,
            color = containerColor,
            shape = shape,
            modifier = sizedModifier.focusRing(Radius.pill),
            content = body,
        )
    } else {
        Surface(color = containerColor, shape = shape, modifier = sizedModifier, content = body)
    }
}
```

- [ ] **Step 2: Compilar**

```powershell
.\gradlew.bat :shared:compileKotlinJvm --console=plain
```

- [ ] **Step 3: Commit**

```bash
git add shared/src/commonMain/kotlin/io/github/shizukajiku/imagewatch/ui/components/Pill.kt
git commit -m "feat: componente Pill reutilizable"
```

---

### Task 3: `IwIconButton.kt`

**Files:**
- Create: `shared/src/commonMain/kotlin/io/github/shizukajiku/imagewatch/ui/components/IwIconButton.kt`

- [ ] **Step 1: Crear el fichero**

```kotlin
package io.github.shizukajiku.imagewatch.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.shizukajiku.imagewatch.ui.theme.IconSize
import io.github.shizukajiku.imagewatch.ui.theme.focusRing

/**
 * Botón de icono circular: buscar/campana/ajustes de la cabecera, kebab de fila, volver de
 * Ajustes. `toggledOn` es para el único caso con dos estados visuales -la campana de
 * silenciar-todo-: fondo `toggledBackground` recortado en círculo + tinte `toggledTint` cuando
 * está activo.
 *
 * El fondo y el anillo de foco van en la MISMA cadena de modificadores que el propio `IconButton`
 * -no un `Surface` padre separado-: si el círculo de fondo fuera un `Surface` envolvente del mismo
 * tamaño, su `shape = CircleShape` recortaría el anillo de foco, que se dibuja 2 dp por fuera del
 * borde del botón.
 *
 * El anillo siempre usa radio de píldora (círculo): todo botón de icono del diseño es circular,
 * cualquiera que sea su tamaño (24 en la barra de título, 30 en el kebab, 32 en cabecera/Ajustes).
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
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(boxSize)
            .then(if (toggledOn) Modifier.background(toggledBackground, CircleShape) else Modifier)
            .focusRing(999.dp),
    ) {
        SvgIcon(icon, if (toggledOn) toggledTint else tint, Modifier.size(iconSize))
    }
}
```

- [ ] **Step 2: Compilar**

```powershell
.\gradlew.bat :shared:compileKotlinJvm --console=plain
```

- [ ] **Step 3: Commit**

```bash
git add shared/src/commonMain/kotlin/io/github/shizukajiku/imagewatch/ui/components/IwIconButton.kt
git commit -m "feat: componente IwIconButton reutilizable"
```

---

### Task 4: `SurfaceCard.kt`

**Files:**
- Create: `shared/src/commonMain/kotlin/io/github/shizukajiku/imagewatch/ui/components/SurfaceCard.kt`

- [ ] **Step 1: Crear el fichero**

```kotlin
package io.github.shizukajiku.imagewatch.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.shizukajiku.imagewatch.ui.theme.LocalIsDark
import io.github.shizukajiku.imagewatch.ui.theme.Radius
import io.github.shizukajiku.imagewatch.ui.theme.hairline

/**
 * La tarjeta con borde de la app: fondo, borde de 1 dp y radio, envolviendo una `Column`. Es el
 * esqueleto detrás de `SettingsCard`, `RowCard`, `ConfirmDialog` y la tarjeta del aviso.
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
        modifier = modifier,
    ) {
        Column(content = content)
    }
}
```

`LocalIsDark` es `staticCompositionLocalOf<Boolean>` (`ui/theme/Theme.kt`), así que
`LocalIsDark.current` ya es `Boolean` — `hairline(LocalIsDark.current)` compila tal cual, sin `by`.

- [ ] **Step 2: Compilar**

```powershell
.\gradlew.bat :shared:compileKotlinJvm --console=plain
```

- [ ] **Step 3: Commit**

```bash
git add shared/src/commonMain/kotlin/io/github/shizukajiku/imagewatch/ui/components/SurfaceCard.kt
git commit -m "feat: componente SurfaceCard reutilizable"
```

---

### Task 5: Migrar `ImagesScreen.kt`

**Files:**
- Modify: `shared/src/commonMain/kotlin/io/github/shizukajiku/imagewatch/ui/images/ImagesScreen.kt`

**Interfaces:**
- Consumes: `Pill`, `IwIconButton` (Tasks 2-3).

- [ ] **Step 1: Importaciones**

Añadir:

```kotlin
import io.github.shizukajiku.imagewatch.ui.components.IwIconButton
import io.github.shizukajiku.imagewatch.ui.components.Pill
```

Quitar (dejan de usarse tras este task, comprobar al final del Step 5 que ninguna otra función del
fichero los sigue necesitando antes de borrar la línea): `androidx.compose.material3.Button`
(el botón "Agregar" pasa a `Pill`), `androidx.compose.material3.IconButton` (todos sus usos en este
fichero migran a `IwIconButton`).

- [ ] **Step 2: `Header` — quitar iconos que sobran, migrar botones e "Agregar"**

Reemplazar el cuerpo de `Header` desde el comentario "Comprobar todas ya" hasta el cierre del
`Row` (todo el bloque de iconos + botón "Agregar" al final de `Header`):

```kotlin
            // Buscador replegado a icono (Blueprint «Cabecera de la bandeja»): se expande a una
            // píldora de 34 dp en su sitio -no empuja el titular ni crece en vertical-. Se
            // repliega al perder el foco solo si está vacío y ya llegó a tenerlo: así no colapsa
            // en el fotograma en que aparece, antes de que el foco aterrice.
            if (searchExpanded) {
                val searchFocus = remember { FocusRequester() }
                var hasHadFocus by remember { mutableStateOf(false) }
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(Radius.pill),
                    modifier = Modifier.width(Layout.searchPill).height(34.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Space.sm),
                        modifier = Modifier.padding(horizontal = Space.md),
                    ) {
                        SvgIcon(
                            AppSvg.SEARCH,
                            MaterialTheme.colorScheme.onSurfaceVariant,
                            Modifier.size(IconSize.sm),
                        )
                        BasicTextField(
                            value = state.search,
                            onValueChange = onSearchChange,
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = TypeScale.body,
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(searchFocus)
                                .onFocusChanged {
                                    if (it.isFocused) {
                                        hasHadFocus = true
                                    } else if (hasHadFocus && state.search.isEmpty()) {
                                        searchExpanded = false
                                    }
                                },
                            decorationBox = { inner ->
                                if (state.search.isEmpty()) {
                                    Text(
                                        "Buscar imagen…",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = TypeScale.body,
                                    )
                                }
                                inner()
                            },
                        )
                    }
                }
                LaunchedEffect(Unit) { searchFocus.requestFocus() }
            } else {
                IwIconButton(AppSvg.SEARCH, { searchExpanded = true }, iconSize = IconSize.md)
            }
            // Atajo de «silenciar todos los avisos»: la campana se tacha y se apaga sobre fondo
            // marcado, el mismo interruptor que vive en Ajustes → Avisos.
            IwIconButton(
                icon = if (mutedAll) AppSvg.BELL_OFF else AppSvg.BELL,
                onClick = onToggleMuteAll,
                toggledOn = mutedAll,
            )
            IwIconButton(AppSvg.GEAR, onOpenSettings)
            Pill(
                text = "Agregar",
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                onClick = onAdd,
                leadingIcon = AppSvg.PLUS,
                contentPadding = PaddingValues(horizontal = Space.lg, vertical = Space.sm),
                fontSize = TypeScale.body,
            )
        }
    }
}
```

Esto quita del todo los dos `IconButton` sueltos de "comprobar todas" (`REFRESH`) y "reconocer
todas" (`CHECK_ALL`) que no existen en el diseño — con ellos se va también el parámetro `onRefresh`
de `Header` si ya no lo usa nadie más en la función; **comprobar**: `onRefresh` solo se usaba ahí
(`{ onRefresh(null) }`), así que también se quita de la firma de `Header` y de su única llamada en
`ImagesScreen` (`Header(state, mutedAll, onSearchChange, onAdd, onOpenSettings, onAcknowledgeAll,
onRefresh, onToggleMuteAll)` → quitar `onRefresh` de esa lista). `onAcknowledgeAll` tampoco se usa
ya dentro de `Header` (era el `if (state.canAcknowledgeAll)` que también se quita) — **pero sí lo
sigue usando** `PendingSectionHeader` más abajo en el fichero (no relacionado con `Header`), así
que `onAcknowledgeAll` se queda en la firma de `ImagesScreen` tal cual, solo se quita del parámetro
de `Header` y de esa llamada.

Firma de `Header` resultante:

```kotlin
@Composable
private fun Header(
    state: ImagesUiState,
    mutedAll: Boolean,
    onSearchChange: (String) -> Unit,
    onAdd: () -> Unit,
    onOpenSettings: () -> Unit,
    onToggleMuteAll: () -> Unit,
) {
```

Y su llamada en `ImagesScreen`:

```kotlin
        Header(state, mutedAll, onSearchChange, onAdd, onOpenSettings, onToggleMuteAll)
```

- [ ] **Step 3: `PendingSectionHeader`/`ErrorSectionHeader`/`HeaderChip` → `Pill` directo**

Eliminar la función `HeaderChip` y sus dos llamadas:

```kotlin
@Composable
private fun PendingSectionHeader(count: Int, onAcknowledgeAll: () -> Unit) {
    val palette = statusColors(ImageStatus.PENDING, LocalIsDark.current)
    SectionHeader(palette.foreground, "Versión nueva", count) {
        // Blueprint (mapa de acciones): «Aplica «Visto» a todas las filas de la sección en una
        // sola escritura». Antes limpiaba el buscador, que no es lo que el chip promete.
        Pill(
            text = "Ver todas",
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            onClick = onAcknowledgeAll,
            leadingIcon = AppSvg.CHECK_ALL,
        )
    }
}

@Composable
private fun ErrorSectionHeader(count: Int, onRetryAll: () -> Unit) {
    val palette = statusColors(ImageStatus.ERROR, LocalIsDark.current)
    SectionHeader(palette.foreground, "No se pudo verificar", count) {
        Pill(
            text = "Reintentar",
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            onClick = onRetryAll,
            leadingIcon = AppSvg.REFRESH,
        )
    }
}
```

(El icono `REFRESH` en "Reintentar" de la cabecera de sección de error faltaba del todo antes de
esta tarea — es uno de los hallazgos de la auditoría, no una regresión de esta migración.)

- [ ] **Step 4: `NoticeBanner` — "Reintentar ahora" a `Pill`**

Reemplazar el bloque `if (disconnected) { Surface(...) { Text(...) } }` dentro de `NoticeBanner`:

```kotlin
                    if (disconnected) {
                        Pill(
                            text = "Reintentar ahora",
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            onClick = onRetry,
                        )
                    }
```

- [ ] **Step 5: `EmptyState` — texto y botón**

El texto de este botón **no** cambia (es el vacío de "sin imágenes vigiladas", no el de la
cabecera; no hay evidencia de que el diseño lo acorte aquí). Solo migra a `Pill`:

```kotlin
        Spacer(Modifier.height(Space.md))
        Pill(
            text = "Agregar imagen",
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            onClick = onAdd,
            contentPadding = PaddingValues(horizontal = Space.lg, vertical = Space.sm),
        )
    }
}
```

(Reemplaza el `Surface(...) { Text(...) }` final de `EmptyState`, incluida su llave de cierre.)

- [ ] **Step 6: Compilar**

```powershell
.\gradlew.bat :shared:jvmTest :shared:koverVerify :desktopApp:compileKotlin --console=plain
```

Revisar avisos de import no usado (`Button`, `IconButton` si ya no quedan usos) y quitarlos.

- [ ] **Step 7: Commit**

```bash
git add shared/src/commonMain/kotlin/io/github/shizukajiku/imagewatch/ui/images/ImagesScreen.kt
git commit -m "refactor: ImagesScreen usa Pill/IwIconButton, quita iconos que no estan en el diseno"
```

---

### Task 6: Migrar `ImageRow.kt`

**Files:**
- Modify: `shared/src/commonMain/kotlin/io/github/shizukajiku/imagewatch/ui/images/ImageRow.kt`

**Interfaces:**
- Consumes: `Pill`, `IwIconButton` (Tasks 2-3), `hairline` (Task 1).

- [ ] **Step 1: Importaciones**

Añadir:

```kotlin
import io.github.shizukajiku.imagewatch.ui.components.IwIconButton
import io.github.shizukajiku.imagewatch.ui.components.Pill
import io.github.shizukajiku.imagewatch.ui.theme.hairline
```

Quitar cuando ya no queden usos (comprobar al final): `androidx.compose.material3.IconButton`
(el kebab migra a `IwIconButton`).

- [ ] **Step 2: `RowCard` — `hairline` en el divisor del detalle**

```kotlin
            if (expanded && detail != null) {
                HorizontalDivider(color = hairline(LocalIsDark.current))
                detail()
            }
```

- [ ] **Step 3: Eliminar `ActionChip`, migrar sus 2 usos**

Eliminar la función `ActionChip` completa. En `PendingRow`, dentro de `ActionsCell { chip = { ... } }`:

```kotlin
        ActionsCell(
            chip = {
                Pill(
                    text = "Visto",
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    onClick = onAcknowledge,
                    leadingIcon = AppSvg.CHECK,
                    width = Layout.rowChip,
                    contentPadding = PaddingValues(vertical = Space.sm - 1.dp),
                )
            },
            menu = { RowMenu(row, onAcknowledge, onRefresh, onToggleSilence, onDelete) },
        )
```

En `ErrorRow`, el chip "Reintentar" **no** lleva icono (confirmado en el diseño — a diferencia del
pill "Reintentar" de la cabecera de sección, que sí):

```kotlin
            ActionsCell(
                chip = {
                    Pill(
                        text = "Reintentar",
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = onRefresh,
                        width = Layout.rowChip,
                        contentPadding = PaddingValues(vertical = Space.sm - 1.dp),
                    )
                },
                menu = { RowMenu(row, onAcknowledge = null, onRefresh, onToggleSilence, onDelete) },
            )
```

`ActionsCell`'s `Box(Modifier.width(Layout.rowChip), contentAlignment = Alignment.Center) { chip() }`
ya reserva el ancho de 88 dp — el `Pill` con `width = Layout.rowChip` dibuja exactamente ese ancho
dentro; no hace falta tocar `ActionsCell`.

- [ ] **Step 4: `SkipAndPillCell` — píldora de versión/estado a `Pill`**

```kotlin
@Composable
private fun RowScope.SkipAndPillCell(skip: String, pillText: String, pillBackground: Color, pillForeground: Color) {
    Row(Modifier.width(Layout.rowSkip + Layout.rowPill), horizontalArrangement = Arrangement.End) {
        Text(
            skip,
            fontSize = TypeScale.caption,
            color = mutedText(LocalIsDark.current),
            textAlign = TextAlign.End,
            style = TabularNums,
            modifier = Modifier.width(Layout.rowSkip).padding(end = Space.sm),
        )
        Pill(
            text = pillText,
            containerColor = pillBackground,
            contentColor = pillForeground,
            width = Layout.rowPill,
            contentPadding = PaddingValues(vertical = 6.dp),
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = TypeScale.meta,
        )
    }
}
```

(Sin `onClick`: es una etiqueta, no un botón — coherente con que hoy tampoco lo era.)

- [ ] **Step 5: Eliminar `DetailPill`, migrar sus 3 usos con los iconos que faltaban**

Eliminar la función `DetailPill`. En `RowDetail`:

```kotlin
        Row(
            Modifier.fillMaxWidth().padding(top = Space.md),
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Pill(
                text = "Comprobar ahora",
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                onClick = onRefresh,
                contentPadding = PaddingValues(horizontal = Space.md, vertical = Space.xs + 2.dp),
            )
            Pill(
                text = if (copied) "Copiado" else "Copiar referencia",
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                onClick = {
                    clipboard.setText(AnnotatedString(row.registry))
                    copiedAt = Clock.System.now().toEpochMilliseconds()
                },
                contentPadding = PaddingValues(horizontal = Space.md, vertical = Space.xs + 2.dp),
            )
            Pill(
                text = if (row.muted) "Reactivar avisos" else "Silenciar avisos",
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                onClick = onToggleSilence,
                leadingIcon = if (row.muted) AppSvg.BELL else AppSvg.BELL_OFF,
                contentPadding = PaddingValues(horizontal = Space.md, vertical = Space.xs + 2.dp),
            )
            Spacer(Modifier.weight(1f))
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(Radius.pill),
                onClick = onDelete,
                modifier = Modifier.focusRing(Radius.pill),
            ) {
                Row(
                    Modifier.padding(horizontal = Space.md, vertical = Space.xs + 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(Space.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SvgIcon(AppSvg.TRASH, MaterialTheme.colorScheme.onErrorContainer, Modifier.size(IconSize.sm))
                    Text(
                        "Quitar de la lista",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontSize = TypeScale.meta,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
```

("Quitar de la lista" no migra a `Pill` — su icono+texto no encajan en el `leadingIcon` simple de
`Pill` sin recrear todo su `Row` interno para un único uso; se queda como `Surface` propia, ahora
con `focusRing` añadido, que antes no tenía. "Silenciar avisos" gana el icono `BELL_OFF`/`BELL`
que faltaba — hallazgo de la auditoría.)

- [ ] **Step 6: `CheckingRow` — icono `SPINNER` en vez de `REFRESH`**

```kotlin
            SvgIcon(
                AppSvg.SPINNER,
                MaterialTheme.colorScheme.onSurfaceVariant,
                Modifier.size(IconSize.sm).rotate(rotation),
            )
```

- [ ] **Step 7: `RowMenu` — kebab a `IwIconButton`**

```kotlin
    Box {
        IwIconButton(AppSvg.KEBAB, { expanded = true }, boxSize = Layout.rowKebab, iconSize = IconSize.md)
        DropdownMenu(expanded, { expanded = false }) {
```

(El resto de `RowMenu` — el `DropdownMenu` y sus `DropdownMenuItem` — no cambia.)

- [ ] **Step 8: `OkRow`/`CheckingRow` — `hairline` en el borde**

En `OkRow`:

```kotlin
    RowCard(
        background = MaterialTheme.colorScheme.surface,
        borderColor = hairline(LocalIsDark.current),
```

En `CheckingRow`:

```kotlin
    RowCard(
        background = MaterialTheme.colorScheme.surface,
        borderColor = hairline(LocalIsDark.current),
```

- [ ] **Step 9: Compilar y correr tests**

```powershell
.\gradlew.bat :shared:jvmTest :shared:koverVerify :desktopApp:compileKotlin --console=plain
```

- [ ] **Step 10: Commit**

```bash
git add shared/src/commonMain/kotlin/io/github/shizukajiku/imagewatch/ui/images/ImageRow.kt
git commit -m "refactor: ImageRow usa Pill/IwIconButton/hairline, anade iconos que faltaban"
```

---

### Task 7: Migrar `SettingsScreen.kt`

**Files:**
- Modify: `shared/src/commonMain/kotlin/io/github/shizukajiku/imagewatch/ui/settings/SettingsScreen.kt`

- [ ] **Step 1: Importaciones**

Añadir:

```kotlin
import io.github.shizukajiku.imagewatch.ui.components.IwIconButton
import io.github.shizukajiku.imagewatch.ui.components.Pill
import io.github.shizukajiku.imagewatch.ui.components.SurfaceCard
```

- [ ] **Step 2: Botón de volver → `IwIconButton`**

```kotlin
            IwIconButton(AppSvg.BACK, onBack)
```

(Reemplaza el `Box(Modifier.size(32.dp).background(...).clickable(...).focusRing(...)) { SvgIcon(...) }`
completo.)

- [ ] **Step 3: Eliminar `SettingsCard`, usar `SurfaceCard` directo**

Eliminar la función privada `SettingsCard`. Sus dos llamadas (`SettingsCard("Origen") { ... }`,
`SettingsCard("Comprobación") { ... }`, y las otras dos en la columna derecha) pasan a:

```kotlin
SurfaceCard(modifier = Modifier.fillMaxWidth()) {
    Column(Modifier.padding(Space.lg), verticalArrangement = Arrangement.spacedBy(Space.md)) {
        Text("Origen", fontWeight = FontWeight.Bold, fontSize = TypeScale.body)
        FieldLabel("URL del registry")
        // ... el resto del contenido de cada tarjeta igual que hoy
    }
}
```

Concretamente, cada uno de los 4 sitios que hoy llaman `SettingsCard("Título") { contenido }`
pasa a:

```kotlin
SurfaceCard(modifier = Modifier.fillMaxWidth()) {
    Column(Modifier.padding(Space.lg), verticalArrangement = Arrangement.spacedBy(Space.md)) {
        Text("Título", fontWeight = FontWeight.Bold, fontSize = TypeScale.body)
        contenido()
    }
}
```

es decir, se sustituye literalmente `SettingsCard("Origen") {` por `SurfaceCard(modifier =
Modifier.fillMaxWidth()) { Column(Modifier.padding(Space.lg), verticalArrangement =
Arrangement.spacedBy(Space.md)) { Text("Origen", fontWeight = FontWeight.Bold, fontSize =
TypeScale.body)` y se cierran las dos llaves nuevas (`Column` y `SurfaceCard`) al final de cada
bloque en vez de la una que cerraba `SettingsCard`. Repetir para "Comprobación", "Apariencia" y
"Avisos".

- [ ] **Step 4: Eliminar `FootPill`, migrar sus 3 usos**

Eliminar la función `FootPill`. Los 3 usos (`Comprobación` — Iniciar/Detener, pie — Restablecer,
pie — Borrar):

```kotlin
                            Pill(
                                text = if (polling) "Detener" else "Iniciar",
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                onClick = onTogglePolling,
                            )
```

```kotlin
            Pill(
                text = "Restablecer ajustes",
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                onClick = { confirming = Confirm.RESET },
            )
            Pill(
                text = "Borrar datos locales",
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                onClick = { confirming = Confirm.WIPE },
            )
```

- [ ] **Step 5: `Segmented` → cada opción a `Pill`**

```kotlin
@Composable
private fun Segmented(selected: ThemePreference, onSelect: (ThemePreference) -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(Radius.pill))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        ThemePreference.entries.forEach { option ->
            val on = option == selected
            Pill(
                text = themeLabel(option),
                containerColor = if (on) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                contentColor = if (on) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                onClick = { onSelect(option) },
                modifier = Modifier.weight(1f),
                fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                contentPadding = PaddingValues(vertical = Space.sm),
            )
        }
    }
}
```

- [ ] **Step 6: Compilar y correr tests**

```powershell
.\gradlew.bat :shared:jvmTest :shared:koverVerify :desktopApp:compileKotlin --console=plain
```

`SettingsViewModelTest` no toca la UI, no debería verse afectado; confirmar igualmente que sigue
en verde.

- [ ] **Step 7: Commit**

```bash
git add shared/src/commonMain/kotlin/io/github/shizukajiku/imagewatch/ui/settings/SettingsScreen.kt
git commit -m "refactor: SettingsScreen usa Pill/IwIconButton/SurfaceCard"
```

---

### Task 8: Migrar `ConfirmDialog.kt`

**Files:**
- Modify: `shared/src/commonMain/kotlin/io/github/shizukajiku/imagewatch/ui/dialogs/ConfirmDialog.kt`

- [ ] **Step 1: Importación**

```kotlin
import io.github.shizukajiku.imagewatch.ui.components.Pill
```

- [ ] **Step 2: Los dos botones**

```kotlin
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    Spacer(Modifier.weight(1f))
                    Pill(
                        text = "Cancelar",
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = onDismiss,
                        contentPadding = PaddingValues(horizontal = Space.md, vertical = Space.sm),
                    )
                    Pill(
                        text = confirmLabel,
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        onClick = {
                            onConfirm()
                            onDismiss()
                        },
                        width = Layout.dialogConfirmMin,
                        contentPadding = PaddingValues(horizontal = Space.md, vertical = Space.sm),
                    )
                }
```

`width = Layout.dialogConfirmMin` fija el ancho a 132 dp en vez del `widthIn(min = ...)` original
— es un ancho MÍNIMO en el diseño, no fijo, pero como el texto de `confirmLabel` ("Borrar todo",
"Restablecer") nunca lo supera en la práctica, un ancho fijo da el mismo resultado visual con la
API que ya tiene `Pill`; si algún `confirmLabel` futuro fuera más largo que 132 dp, `Pill` lo
recortaría con elipsis en vez de ensancharse — anotado como límite conocido, no bloqueante.

Añadir `import io.github.shizukajiku.imagewatch.ui.theme.Layout` si no está ya (comprobar; el
fichero no lo usaba antes porque `Layout.dialogWidth`/`Layout.dialogConfirmMin` se leían solo desde
`Modifier.widthIn`, que si ya estaba importado a través de `Layout` — confirmar que el import ya
existe, dado que `Layout.dialogWidth` se usa en el `Surface` principal del diálogo).

- [ ] **Step 3: Compilar**

```powershell
.\gradlew.bat :shared:jvmTest :shared:koverVerify :desktopApp:compileKotlin --console=plain
```

- [ ] **Step 4: Commit**

```bash
git add shared/src/commonMain/kotlin/io/github/shizukajiku/imagewatch/ui/dialogs/ConfirmDialog.kt
git commit -m "refactor: ConfirmDialog usa Pill"
```

---

### Task 9: Migrar `ToastWindow.kt` (`jvmMain`)

**Files:**
- Modify: `shared/src/jvmMain/kotlin/io/github/shizukajiku/imagewatch/ui/toast/ToastWindow.kt`

- [ ] **Step 1: Importación**

```kotlin
import io.github.shizukajiku.imagewatch.ui.components.Pill
```

- [ ] **Step 2: El pill de acción**

```kotlin
                    if (toast.action.isNotEmpty()) {
                        Pill(
                            text = toast.action,
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            onClick = {
                                onAction()
                                saliendoManual = true
                            },
                            width = Layout.toastAction,
                            contentPadding = PaddingValues(vertical = Space.xs),
                            fontSize = TypeScale.caption,
                        )
                    }
```

Añadir `import androidx.compose.foundation.layout.PaddingValues` si no está (comprobar; el fichero
puede no necesitarlo hoy).

- [ ] **Step 3: Compilar**

```powershell
.\gradlew.bat :desktopApp:compileKotlin --console=plain
```

- [ ] **Step 4: Commit**

```bash
git add shared/src/jvmMain/kotlin/io/github/shizukajiku/imagewatch/ui/toast/ToastWindow.kt
git commit -m "refactor: ToastWindow usa Pill para la accion del aviso"
```

---

### Task 10: Verificación completa y repaso visual

**Files:** ninguno (solo verificación).

- [ ] **Step 1: `spotlessApply` + verificación completa**

```powershell
$env:JAVA_HOME='C:\Users\shizu\.jdks\ms-21.0.12.1'
Set-Location C:\Users\shizu\IdeaProjects\ImageWatch
.\gradlew.bat spotlessApply --console=plain
.\gradlew.bat :shared:jvmTest :shared:koverVerify :desktopApp:compileKotlin spotlessCheck --console=plain
```

Si algo quedó sin comitear tras `spotlessApply`, comitearlo (`git status --short`, `git add` con
rutas explícitas) antes de seguir.

- [ ] **Step 2: `:desktopApp:run` — repaso funcional propio**

```powershell
.\gradlew.bat :desktopApp:run --console=plain
```

En background, ~60-90 s, comprobar en el log que no hay excepción, matar el proceso al terminar
(mismo procedimiento que en fases anteriores: identificar el PID hijo del daemon reutilizado con
`Get-CimInstance Win32_Process`, no el daemon en sí).

- [ ] **Step 3: Lista de comprobación para el usuario**

Reportar al usuario, para que lo confirme con sus propios ojos sobre la ventana real:

- Cabecera: ya no hay icono de refrescar ni de doble-check sueltos; quedan buscar, campana,
  ajustes, "Agregar" (sin "imagen").
- Chip "Visto" de una fila pendiente: lleva el check antes del texto.
- Enfocar con `Tab` el chip "Visto": el anillo es una píldora limpia, no un óvalo.
- Pill "Reintentar" de la cabecera de la sección de error: lleva icono de refrescar.
- Detalle de una fila → "Silenciar avisos": lleva icono de campana tachada.
- Una fila con comprobación en vuelo: el icono que gira es un arco simple, no una flecha completa.
- Tema claro: bordes/divisores (fila «Al día», línea de Ajustes, hairline de la barra de título)
  se ven ligeramente distintos del fondo de los chips — antes eran del mismo tono.

---

## Self-Review

**Cobertura de la spec:** los 3 componentes, el token `hairline`, el icono `SPINNER`, el fix de
`FocusRing`, y la migración de los 6 ficheros de consumo — todos cubiertos, uno por tarea.

**Placeholder scan:** ninguno — cada paso trae el código completo del bloque que cambia, con una
única nota explícita de "comprobar al implementar" en `SurfaceCard` sobre `LocalIsDark.current`
(un `CompositionLocal` cuyo tipo exacto de lectura no pude reconfirmar en esta pasada porque no
está en los ficheros leídos en este plan; ya se usa igual, sin `by`, en `ImageRow.kt`/
`ImagesScreen.kt`, así que el riesgo de que no compile tal cual es bajo).

**Consistencia de tipos:** `Pill`/`IwIconButton`/`SurfaceCard` no dependen de ningún tipo de
`ui/images`/`ui/settings`/`ui/dialogs`/`ui/toast` — solo `AppSvg`, `SvgIcon`, tokens de
`ui/theme`. Ninguna función eliminada (`ActionChip`, `DetailPill`, `HeaderChip`, `FootPill`,
`SettingsCard`) tiene ya ningún llamador fuera de los migrados en el mismo task.

**Puntos abiertos para revisión manual (no bloquean el cierre):**
1. El botón "Quitar de la lista" del detalle de fila (`ImageRow.kt`) no migra a `Pill` — su
   icono+texto no encajan en el `leadingIcon` simple sin ensanchar la API de `Pill` para un único
   uso; se queda como `Surface` propia (gana `focusRing`, que no tenía).
2. El botón de cerrar de `TitleBar.kt` no migra a `IwIconButton` — su ancho (46 dp) no es igual a
   su alto (barra de 38 dp), así que el círculo de `IwIconButton` saldría una elipse; se queda con
   su `IconButton` simple actual, sin fondo circular ni cambio.
3. El ancho de "Cancelar"/confirmación en `ConfirmDialog.kt` pasa de mínimo (132 dp) a fijo — ver
   nota en Task 8 Step 2.
4. La lista de comprobación de Task 10 Step 3 depende del repaso visual del usuario — no
   automatizable en este entorno (sin herramienta de captura para ventanas nativas de Compose
   Desktop, ya documentado en fases anteriores).
