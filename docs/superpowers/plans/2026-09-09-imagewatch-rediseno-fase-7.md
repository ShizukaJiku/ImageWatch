# Fase 7 — Navegación por teclado — Plan de implementación

> **Para quien ejecute:** REQUIRED SUB-SKILL: usar `superpowers:subagent-driven-development`
> (recomendado) o `superpowers:executing-plans` para ejecutar este plan tarea a tarea. Los pasos
> usan casillas (`- [ ]`) para el seguimiento.

**Goal:** La bandeja se navega entera con teclado — flechas mueven el foco entre filas, Espacio
reconoce una pendiente, Enter despliega, Escape pliega, Tab entra en las acciones de la fila — y
todo control interactivo bandeja/Ajustes/diálogo enseña un anillo de foco visible sin cambiar de
tamaño. Es la última fase del rediseño.

**Arquitectura:** Un helper de UI nuevo (`Modifier.focusRing()`) reutilizado en todos los controles
personalizados (`Surface(onClick=…)` sin indicación de foco propia). La navegación por flechas usa
el sistema de foco nativo de Compose (`LocalFocusManager.moveFocus`), no un `FocusRequester` por
fila: cada fila ya es un objetivo de foco real porque `RowCard` ya la envuelve en
`Modifier.clickable(onClick = onToggleExpand)`, que registra foco por Tab. Solo hace falta:
(a) pintar el anillo cuando ese nodo tiene foco, (b) saber qué fila tiene el foco para que Espacio
decida a cuál reconocer, y (c) traducir flechas/Espacio/Enter/Escape a las acciones que ya existen
(`onAcknowledge`, `onToggleExpand`).

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform (`commonMain` — a diferencia de fases
5-6, esta fase no toca nada de `jvmMain`: todo lo tocado ya vive en `commonMain`).

**Spec:** `docs/superpowers/specs/2026-09-08-imagewatch-rediseno-design.md`, §12 (líneas
1107-1140). Decisión D-7 (troceado por fase) y D-1..D-6 no aplican aquí.

## Restricciones globales

- Commits en español, `<tipo>: <descripción>`, terminando con la atribución vigente del
  `system-reminder` de esta sesión.
- **Nunca** `git checkout`/`git restore <fichero>`. `git add` solo con rutas explícitas — nunca
  `git add -A` (dos ficheros de otra rama, `2026-09-07-migracion-plantilla-kmp.md` y `ESTADO.md`,
  siguen modificados y no son de este trabajo).
- ktlint: línea máxima **120**, no 140.
- **Sin test de Compose UI para esta fase** (spec §12: "sin test de UI; repaso visual + una lista
  de comprobación manual"). La verificación es compilar + `:desktopApp:run` + la lista del spec.
- Verificación final, **solo desde PowerShell** con `JAVA_HOME` fijado:
  ```powershell
  $env:JAVA_HOME='C:\Users\shizu\.jdks\ms-21.0.12.1'
  Set-Location C:\Users\shizu\IdeaProjects\ImageWatch
  .\gradlew.bat :shared:jvmTest :shared:koverVerify :desktopApp:compileKotlin spotlessCheck --console=plain
  ```
- **No** el `ToastLayer` recibe foco ni anillo: su ventana es `focusable = false` por diseño
  (fase 5) y esta fase no la toca.
- **Alcance de `focusRing()` en esta fase** — se aplica a los controles que el spec nombra
  explícitamente («chips de cabecera, botones del pie, toggles de Ajustes, botones del diálogo»)
  más la fila entera y sus dos acciones (chip + kebab), que es el bloque nuevo de navegación. Los
  `IconButton` sueltos de la cabecera (refrescar, ajustes, buscador replegado, agregar) y los
  campos de texto (`BasicTextField` de Ajustes y del buscador) quedan fuera — ya son alcanzables
  por `Tab` (todo `IconButton`/`BasicTextField` es foco real de por sí) y no los nombra el spec.
  Anotado como punto abierto para revisión, igual que en fases anteriores.

---

## Fichero por fichero

| Fichero | Qué cambia |
|---|---|
| `ui/theme/FocusRing.kt` (nuevo) | `Modifier.focusRing(cornerRadius: Dp)` |
| `ui/images/ImagesScreen.kt` | Estado `focusedRowName`, `onPreviewKeyEvent` en el `LazyColumn`, `focusRing()` en `HeaderChip`/`OkSectionHeader`/aviso de reintentar, nuevo parámetro `onFocusedChange` pasado a cada fila |
| `ui/images/ImageRow.kt` | `RowCard`/`PendingRow`/`ErrorRow`/`OkRow` ganan `onFocusChanged`; `focusRing()` en la fila, `ActionChip` y el kebab |
| `ui/settings/SettingsScreen.kt` | `focusRing()` en `FootPill`, `Toggle` (el `Switch`), `Segmented`, el botón de volver |
| `ui/dialogs/ConfirmDialog.kt` | `focusRing()` en los dos botones |
| `docs/superpowers/specs/2026-09-05-imagewatch-historias-de-usuario.md` | + H-98 |

---

### Task 1: `Modifier.focusRing()`

**Files:**
- Create: `shared/src/commonMain/kotlin/io/github/shizukajiku/imagewatch/ui/theme/FocusRing.kt`

**Interfaces:**
- Produces: `fun Modifier.focusRing(cornerRadius: Dp = Radius.md): Modifier`

Sin test: es un modifier puramente visual, verificado en el repaso de la Task 6.

- [ ] **Step 1: Crear el fichero**

```kotlin
package io.github.shizukajiku.imagewatch.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val RING_WIDTH = 2.dp
private val RING_OFFSET = 2.dp

/**
 * Anillo de foco de teclado (Blueprint 1h, «Foco de teclado»): 2 dp del color de acento, separado
 * 2 dp por fuera del borde del componente, sin cambiar su tamaño.
 *
 * No añade un segundo objetivo de foco: `onFocusChanged` aquí observa el foco del nodo que ya es
 * foco real más adelante en la misma cadena de modificadores -el `clickable`/`Surface(onClick=…)`/
 * `IconButton`/`Switch` al que se le pasa este modifier-. Añadir un `.focusable()` propio
 * duplicaría la parada de `Tab` sobre el mismo control visual.
 *
 * `cornerRadius` no tiene por qué coincidir en tipo con el radio real de la forma: un valor mayor
 * que la mitad del lado corto -como `Radius.pill`, 999 dp- se recorta solo al dibujar, así que
 * sirve igual para una tarjeta con esquinas y para una píldora o un círculo.
 */
fun Modifier.focusRing(cornerRadius: Dp = Radius.md): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    val color = MaterialTheme.colorScheme.primary
    this
        .onFocusChanged { focused = it.isFocused }
        .drawWithContent {
            drawContent()
            if (focused) {
                val strokeWidthPx = RING_WIDTH.toPx()
                val offsetPx = RING_OFFSET.toPx()
                drawRoundRect(
                    color = color,
                    topLeft = Offset(-offsetPx, -offsetPx),
                    size = Size(size.width + offsetPx * 2, size.height + offsetPx * 2),
                    cornerRadius = CornerRadius(cornerRadius.toPx() + offsetPx),
                    style = Stroke(strokeWidthPx),
                )
            }
        }
}
```

- [ ] **Step 2: Compilar**

```powershell
$env:JAVA_HOME='C:\Users\shizu\.jdks\ms-21.0.12.1'
Set-Location C:\Users\shizu\IdeaProjects\ImageWatch
.\gradlew.bat :shared:compileKotlinJvm --console=plain
```

Esperado: compila. Si `composed {}` da un aviso de deprecación (no error), se deja — es una
función utilitaria pequeña, no un modifier de alto tráfico donde importe el coste de
`Modifier.Node`; ninguna otra parte del código usa ese patrón todavía, así que no hay uno que
seguir.

- [ ] **Step 3: Commit**

```bash
git add shared/src/commonMain/kotlin/io/github/shizukajiku/imagewatch/ui/theme/FocusRing.kt
git commit -m "feat: Modifier.focusRing para el anillo de foco de teclado"
```

---

### Task 2: Navegación por teclado en la lista + anillo en la fila

**Files:**
- Modify: `shared/src/commonMain/kotlin/io/github/shizukajiku/imagewatch/ui/images/ImageRow.kt`
- Modify: `shared/src/commonMain/kotlin/io/github/shizukajiku/imagewatch/ui/images/ImagesScreen.kt`

**Interfaces:**
- Consumes: `Modifier.focusRing(cornerRadius: Dp)` (Task 1).
- Produces:
  - `RowCard(..., onFocusChanged: ((Boolean) -> Unit)? = null, ...)` — nuevo parámetro.
  - `PendingRow(..., onFocusedChange: (Boolean) -> Unit = {})`, `ErrorRow(..., onFocusedChange: (Boolean) -> Unit = {})`, `OkRow(..., onFocusedChange: (Boolean) -> Unit = {})` — nuevo parámetro en las tres, delante de `modifier`.

- [ ] **Step 1: `RowCard` gana `onFocusChanged` y el anillo**

En `ImageRow.kt`, reemplazar la firma y el `Row` interior de `RowCard` (líneas 85-110):

```kotlin
@Composable
private fun RowCard(
    background: Color,
    borderColor: Color,
    modifier: Modifier = Modifier,
    expanded: Boolean = false,
    onToggleExpand: (() -> Unit)? = null,
    onFocusChanged: ((Boolean) -> Unit)? = null,
    detail: (@Composable () -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    Surface(
        color = background,
        border = BorderStroke(1.dp, borderColor),
        shape = RoundedCornerShape(Radius.md),
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(tween(Motion.EMPHASIS, easing = Motion.emphasisEasing)),
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.md),
                modifier = Modifier
                    .onFocusChanged { onFocusChanged?.invoke(it.isFocused) }
                    .then(if (onToggleExpand != null) Modifier.clickable(onClick = onToggleExpand) else Modifier)
                    .focusRing(Radius.md)
                    .padding(horizontal = Layout.rowPadH, vertical = Layout.rowPadV),
                content = content,
            )
            if (expanded && detail != null) {
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                detail()
            }
        }
    }
}
```

Añadir a las importaciones de `ImageRow.kt`:

```kotlin
import androidx.compose.ui.focus.onFocusChanged
import io.github.shizukajiku.imagewatch.ui.theme.focusRing
```

- [ ] **Step 2: `PendingRow`/`ErrorRow`/`OkRow` reciben y reenvían `onFocusedChange`**

En `PendingRow` (línea 320-328), añadir el parámetro y reenviarlo:

```kotlin
@Composable
fun PendingRow(
    row: ImageRowState,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onAcknowledge: () -> Unit,
    onRefresh: () -> Unit,
    onToggleSilence: () -> Unit,
    onDelete: () -> Unit,
    onFocusedChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
```

y en su llamada a `RowCard` (línea 336-342) añadir `onFocusChanged = onFocusedChange,`.

En `ErrorRow` (línea 368-376), mismo cambio: añadir `onFocusedChange: (Boolean) -> Unit = {},`
antes de `modifier` en la firma, y `onFocusChanged = onFocusedChange,` en su llamada a `RowCard`
(línea 384-390).

En `OkRow` (línea 428-439), mismo cambio: añadir `onFocusedChange: (Boolean) -> Unit = {},` antes
de `modifier`, y `onFocusChanged = onFocusedChange,` en su llamada a `RowCard` (línea 447-453).

- [ ] **Step 3: `ActionChip` y el kebab ganan el anillo**

En `ImageRow.kt`, `ActionChip` (línea 206-223), cambiar su `modifier`:

```kotlin
@Composable
private fun ActionChip(text: String, background: Color, foreground: Color, onClick: () -> Unit) {
    Surface(
        color = background,
        shape = RoundedCornerShape(Radius.pill),
        modifier = Modifier.fillMaxWidth().focusRing(Radius.pill),
        onClick = onClick,
    ) {
```

(el resto del cuerpo no cambia).

En `RowMenu` (línea 538), el `IconButton` del kebab:

```kotlin
        IconButton({ expanded = true }, modifier = Modifier.size(Layout.rowKebab).focusRing(Radius.sm)) {
```

- [ ] **Step 4: Estado de fila enfocada + teclado en `ImagesScreen`**

En `ImagesScreen.kt`, añadir a las importaciones:

```kotlin
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.platform.LocalFocusManager
import io.github.shizukajiku.imagewatch.ui.theme.Radius
import io.github.shizukajiku.imagewatch.ui.theme.focusRing
```

(`Radius` puede que ya esté importado indirectamente; comprobar y no duplicar el import si ya
existe una línea igual).

Dentro de `ImagesScreen`, justo después de `val listState = rememberLazyListState()` (línea 128),
añadir:

```kotlin
            // Qué fila tiene el foco de teclado ahora mismo -no cuál está desplegada, ese es
            // `state.expandedRow`-. Solo lo necesita Espacio, que decide a qué imagen reconocer;
            // Enter y Escape no necesitan saber el nombre.
            var focusedRowName by remember { mutableStateOf<String?>(null) }
            val focusManager = LocalFocusManager.current
```

Reemplazar la apertura del `LazyColumn` (línea 157-166):

```kotlin
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth().onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.DirectionDown -> {
                            focusManager.moveFocus(FocusDirection.Down)
                            true
                        }
                        Key.DirectionUp -> {
                            focusManager.moveFocus(FocusDirection.Up)
                            true
                        }
                        Key.Spacebar -> {
                            val name = focusedRowName
                            if (name != null && pendingRows.any { it.name == name }) {
                                onAcknowledge(name)
                                true
                            } else {
                                false
                            }
                        }
                        Key.Enter, Key.NumPadEnter -> {
                            val name = focusedRowName
                            if (name != null) {
                                onToggleExpand(name)
                                true
                            } else {
                                false
                            }
                        }
                        Key.Escape -> {
                            val open = state.expandedRow
                            if (open != null) {
                                onToggleExpand(open)
                                true
                            } else {
                                false
                            }
                        }
                        else -> false
                    }
                },
                state = listState,
                contentPadding = PaddingValues(
                    start = Space.xl,
                    end = Space.xl,
                    bottom = Space.md,
                ),
                verticalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
```

Y en el `when (entry)` de dentro, pasar `onFocusedChange` a las tres filas. Para `PendingItem`
(línea 179-189):

```kotlin
                        is Entry.PendingItem ->
                            PendingRow(
                                row = entry.row,
                                expanded = state.expandedRow == entry.row.name,
                                onToggleExpand = { onToggleExpand(entry.row.name) },
                                onAcknowledge = { onAcknowledge(entry.row.name) },
                                onRefresh = { onRefresh(entry.row.name) },
                                onToggleSilence = { onToggleSilence(entry.row.name) },
                                onDelete = { onDelete(entry.row.name) },
                                onFocusedChange = { focused ->
                                    focusedRowName = if (focused) {
                                        entry.row.name
                                    } else {
                                        focusedRowName.takeUnless { it == entry.row.name }
                                    }
                                },
                                modifier = itemMotion,
                            )
```

Para `ErrorItem` (línea 194-203), el mismo bloque `onFocusedChange` antes de `modifier = itemMotion,`:

```kotlin
                        is Entry.ErrorItem ->
                            ErrorRow(
                                row = entry.row,
                                expanded = state.expandedRow == entry.row.name,
                                onToggleExpand = { onToggleExpand(entry.row.name) },
                                onRefresh = { onRefresh(entry.row.name) },
                                onToggleSilence = { onToggleSilence(entry.row.name) },
                                onDelete = { onDelete(entry.row.name) },
                                onFocusedChange = { focused ->
                                    focusedRowName = if (focused) {
                                        entry.row.name
                                    } else {
                                        focusedRowName.takeUnless { it == entry.row.name }
                                    }
                                },
                                modifier = itemMotion,
                            )
```

Para `OkItem` (línea 214-234), dentro del bloque `val disconnected = …` existente, en la llamada a
`OkRow` añadir igual, antes de `modifier = itemMotion,`:

```kotlin
                                onFocusedChange = { focused ->
                                    focusedRowName = if (focused) {
                                        entry.row.name
                                    } else {
                                        focusedRowName.takeUnless { it == entry.row.name }
                                    }
                                },
```

- [ ] **Step 5: `HeaderChip` y `OkSectionHeader` ganan el anillo**

En `ImagesScreen.kt`, `HeaderChip` (línea 532-546):

```kotlin
@Composable
private fun HeaderChip(text: String, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(Radius.pill),
        onClick = onClick,
        modifier = Modifier.focusRing(Radius.pill),
    ) {
```

(el resto del cuerpo no cambia).

`OkSectionHeader` (línea 464-472), cambiar su `Surface`:

```kotlin
    Surface(
        color = ghostBackground(dark),
        shape = RoundedCornerShape(Radius.md),
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth().focusRing(Radius.md),
    ) {
```

`NoticeBanner`'s botón «Reintentar ahora» (línea 423-427), cambiar su `Surface`:

```kotlin
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(Radius.pill),
                            onClick = onRetry,
                            modifier = Modifier.focusRing(Radius.pill),
                        ) {
```

- [ ] **Step 6: Compilar**

```powershell
$env:JAVA_HOME='C:\Users\shizu\.jdks\ms-21.0.12.1'
Set-Location C:\Users\shizu\IdeaProjects\ImageWatch
.\gradlew.bat :shared:compileKotlinJvm --console=plain
```

Esperado: compila. Revisar que no queda ningún import duplicado de `Radius` en `ImagesScreen.kt`.

- [ ] **Step 7: Commit**

```bash
git add shared/src/commonMain/kotlin/io/github/shizukajiku/imagewatch/ui/images/ImageRow.kt shared/src/commonMain/kotlin/io/github/shizukajiku/imagewatch/ui/images/ImagesScreen.kt
git commit -m "feat: navegacion por teclado en la lista de imagenes"
```

---

### Task 3: Anillo en Ajustes

**Files:**
- Modify: `shared/src/commonMain/kotlin/io/github/shizukajiku/imagewatch/ui/settings/SettingsScreen.kt`

**Interfaces:**
- Consumes: `Modifier.focusRing(cornerRadius: Dp)` (Task 1).

- [ ] **Step 1: `FootPill`, `Toggle`, `Segmented` y el botón de volver**

`FootPill` (línea 438-452):

```kotlin
@Composable
private fun FootPill(text: String, container: Color, onContent: Color, onClick: () -> Unit) {
    androidx.compose.material3.Surface(
        color = container,
        shape = RoundedCornerShape(Radius.pill),
        onClick = onClick,
        modifier = Modifier.focusRing(Radius.pill),
    ) {
```

`Toggle` (línea 455-466), el `Switch`:

```kotlin
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            enabled = enabled,
            modifier = Modifier.focusRing(Radius.pill),
        )
```

`Segmented` (línea 330-365), añadir `.focusRing(Radius.pill)` al final de la cadena de `modifier`
de cada opción (tras `.padding(vertical = Space.sm)`):

```kotlin
                modifier = Modifier.weight(1f)
                    .then(
                        if (on) {
                            Modifier.background(
                                MaterialTheme.colorScheme.primaryContainer,
                                RoundedCornerShape(Radius.pill),
                            )
                        } else {
                            Modifier
                        },
                    )
                    .clickable { onSelect(option) }
                    .padding(vertical = Space.sm)
                    .focusRing(Radius.pill),
```

El botón de volver (línea 93-100):

```kotlin
            Box(
                Modifier.size(32.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                    .clickable(onClick = onBack)
                    .focusRing(Radius.pill),
                contentAlignment = Alignment.Center,
            ) {
```

Añadir a las importaciones de `SettingsScreen.kt`:

```kotlin
import io.github.shizukajiku.imagewatch.ui.theme.focusRing
```

- [ ] **Step 2: Compilar**

```powershell
.\gradlew.bat :shared:compileKotlinJvm --console=plain
```

- [ ] **Step 3: Commit**

```bash
git add shared/src/commonMain/kotlin/io/github/shizukajiku/imagewatch/ui/settings/SettingsScreen.kt
git commit -m "feat: anillo de foco en los controles de Ajustes"
```

---

### Task 4: Anillo en el diálogo de confirmación

**Files:**
- Modify: `shared/src/commonMain/kotlin/io/github/shizukajiku/imagewatch/ui/dialogs/ConfirmDialog.kt`

**Interfaces:**
- Consumes: `Modifier.focusRing(cornerRadius: Dp)` (Task 1).

- [ ] **Step 1: Los dos botones**

Reemplazar el `Row` final de `ConfirmDialog` (línea 106-139):

```kotlin
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    Spacer(Modifier.weight(1f))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(Radius.pill),
                        onClick = onDismiss,
                        modifier = Modifier.focusRing(Radius.pill),
                    ) {
                        Text(
                            "Cancelar",
                            fontSize = TypeScale.meta,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = Space.md, vertical = Space.sm),
                        )
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(Radius.pill),
                        onClick = {
                            onConfirm()
                            onDismiss()
                        },
                        modifier = Modifier.focusRing(Radius.pill),
                    ) {
                        Text(
                            confirmLabel,
                            fontSize = TypeScale.meta,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.widthIn(min = Layout.dialogConfirmMin)
                                .padding(horizontal = Space.md, vertical = Space.sm),
                        )
                    }
                }
```

Añadir a las importaciones de `ConfirmDialog.kt`:

```kotlin
import io.github.shizukajiku.imagewatch.ui.theme.focusRing
```

- [ ] **Step 2: Compilar**

```powershell
.\gradlew.bat :shared:compileKotlinJvm --console=plain
```

- [ ] **Step 3: Commit**

```bash
git add shared/src/commonMain/kotlin/io/github/shizukajiku/imagewatch/ui/dialogs/ConfirmDialog.kt
git commit -m "feat: anillo de foco en los botones del dialogo de confirmacion"
```

---

### Task 5: Historia de usuario, verificación completa y repaso visual

**Files:**
- Modify: `docs/superpowers/specs/2026-09-05-imagewatch-historias-de-usuario.md`

- [ ] **Step 1: Añadir H-98**

Insertar tras H-97 (antes de `## Sondeo`, línea 230):

```markdown
### H-98 · La bandeja se maneja entera con teclado (rediseño, fase 7)

Como usuario que prefiere no soltar el teclado
quiero mover el foco entre filas con las flechas, reconocer con Espacio, desplegar con Enter
y plegar con Escape
para no depender del ratón para las acciones del día a día.

**Dado** el foco en una fila de la lista
**Cuando** pulso ↓ o ↑
**Entonces** el foco pasa a la fila siguiente o anterior.

**Dado** el foco en una fila de «Versión nueva»
**Cuando** pulso Espacio
**Entonces** se reconoce igual que pulsar «Visto».

**Dado** el foco en cualquier fila
**Cuando** pulso Enter
**Entonces** se despliega o pliega su detalle, igual que un clic en la banda.

**Dado** una fila desplegada
**Cuando** pulso Escape
**Entonces** se pliega.

**Y** todo control interactivo de la bandeja, Ajustes y el diálogo de confirmación enseña un
anillo de foco de 2 dp al recibir el foco por teclado, sin cambiar de tamaño.

Código: `Modifier.focusRing` (`ui/theme/FocusRing.kt`), `ImagesScreen.kt` (`onPreviewKeyEvent`).
Prueba: repaso visual — sin test de Compose UI.
```

- [ ] **Step 2: Commit**

```bash
git add docs/superpowers/specs/2026-09-05-imagewatch-historias-de-usuario.md
git commit -m "docs: H-98 navegacion por teclado"
```

- [ ] **Step 3: Verificación completa**

```powershell
$env:JAVA_HOME='C:\Users\shizu\.jdks\ms-21.0.12.1'
Set-Location C:\Users\shizu\IdeaProjects\ImageWatch
.\gradlew.bat spotlessApply --console=plain
.\gradlew.bat :shared:jvmTest :shared:koverVerify :desktopApp:compileKotlin spotlessCheck --console=plain
```

Esperado: `BUILD SUCCESSFUL`. Los tests existentes no deberían verse afectados — esta fase no toca
ningún fichero con test asociado.

- [ ] **Step 4: Repaso visual con la app real**

```powershell
.\gradlew.bat :desktopApp:run --console=plain
```

En background. Con la ventana principal abierta y al menos 3-4 imágenes vigiladas (el origen
simulado por defecto ya trae `alpha,beta,gamma,delta`), comprobar la lista del spec §12:

- `Tab` desde el buscador/botones de cabecera llega a la primera fila; se ve el anillo de 2 dp
  alrededor de la banda completa de la fila, sin que la fila cambie de tamaño.
- `↓`/`↑` mueven el foco entre filas (el anillo salta de una a la siguiente).
- Con el foco en una fila de «Versión nueva», `Espacio` la reconoce (desaparece de la sección,
  igual que pulsar «Visto»).
- `Enter` sobre cualquier fila despliega su detalle; `Enter` otra vez lo pliega.
- Con una fila desplegada, `Escape` la pliega sin mover el foco de vuelta a otro sitio.
- `Tab` con el foco en la fila entra en el chip de acción y luego en el kebab (⋮); ambos enseñan
  su propio anillo.
- En Ajustes: `Tab` alcanza los interruptores, el segmentado de tema, y «Restablecer
  ajustes»/«Borrar datos locales» al pie — cada uno con su anillo.
- Abrir el diálogo de confirmación (p. ej. «Borrar datos locales» sin llegar a confirmar) y
  comprobar que `Tab` alcanza «Cancelar» y el botón de acción, cada uno con su anillo; cerrar con
  «Cancelar».

Matar el proceso (`java`/`gradle`/`kotlin`) al terminar.

---

## Self-Review

**Cobertura del spec §12:**
- `Modifier.focusRing()`, contorno 2 dp por fuera, sin cambiar tamaño, activado por
  `interactionSource`/foco → Task 1.
- `↑`/`↓` mueven foco, Espacio reconoce (solo pendiente), Enter despliega, Escape pliega, Tab entra
  en el cluster de acciones → Task 2.
- Cada control nombrado explícitamente (chips de cabecera, botones del pie, toggles de Ajustes,
  botones del diálogo) con target de foco real y `focusRing()` → Tasks 2-4.
- `ToastLayer` no participa (ya es `focusable = false` desde la fase 5, sin cambios aquí).
- H-\* de navegación por teclado en `historias-de-usuario.md` → Task 5.
- Sin test de UI, repaso visual con lista de comprobación → Task 5 Step 4.

**Placeholder scan:** ninguno — cada paso trae el código completo del bloque que cambia.

**Consistencia de tipos:** `onFocusedChange: (Boolean) -> Unit` en `PendingRow`/`ErrorRow`/`OkRow`
reenvía a `onFocusChanged: ((Boolean) -> Unit)?` en `RowCard` — mismo tipo de función, solo cambia
la nulabilidad (el default `{}` en las tres filas hace innecesario el `?` ahí). `focusRing`
siempre recibe `Dp`, nunca `Float` ni literal sin unidad.

**Puntos abiertos para revisión manual (no bloquean el cierre de fase):**
1. Alcance de `focusRing()` deliberadamente recortado (ver Restricciones globales): los
   `IconButton` sueltos de la cabecera (refrescar, ajustes, buscador, agregar, mute) y los
   `BasicTextField` de Ajustes/buscador no lo llevan. Ya son foco real por `Tab`; el spec no los
   nombra explícitamente y ya tienen alguna señal propia (cursor parpadeante en los campos).
2. `CheckingRow` (fila con una comprobación individual en vuelo) no participa en la navegación:
   no recibe `onFocusChanged` ni `focusRing`, así que `↓`/`↑` la saltan mientras dura la
   comprobación. Es un estado transitorio (unos segundos), y `RowCard` no la hace clicable hoy
   tampoco (sin `onToggleExpand`).
3. `Key.Spacebar` sobre una fila **no pendiente** (Error/Al día) no está interceptado por el
   `onPreviewKeyEvent`: cae hacia el `clickable(onToggleExpand)` de `RowCard`. Si Compose
   Foundation vincula Espacio a `onClick` en un nodo clicable enfocado, ese caso también
   desplegaría el detalle; si no lo vincula, no pasa nada. Ninguna de las dos posibilidades rompe
   el contrato del spec (que solo define Espacio para «Versión nueva»), así que no se fuerza
   ningún comportamiento adicional aquí — repasar a ojo si molesta.
4. Ninguna fila desplazada fuera del área visible de la `LazyColumn` (más allá del margen de
   composición perezosa) es alcanzable con `↓`/`↑` hasta que se compone — `moveFocus` solo
   encuentra objetivos ya compuestos. `LazyColumn` trae de vuelta al viewport la fila que sí gana
   el foco, así que el desplazamiento sigue funcionando fila a fila; el límite es listas muy
   largas con saltos grandes, que no es el caso de uso principal de esta app.
