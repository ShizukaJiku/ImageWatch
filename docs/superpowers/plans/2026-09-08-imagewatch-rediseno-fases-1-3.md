# ImageWatch — Rediseño, plan de implementación: Fases 1–3

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Dejar la Bandeja principal alineada con el rediseño de Claude Design — tokens consolidados, buscador replegable, «Ver todas» que reconoce, fila abierta con detalle en su sitio — y retirar la mecánica de «Deshacer», la cola «Ponerlas arriba» y el renombrado, que el diseño elimina.

**Architecture:** Tres PRs en cadena (`feature/rediseno-fase-1-tokens` → `-fase-2-bandeja` → `-fase-3-fila-abierta`). La frontera UX/apariencia de la fase 7 se conserva: los composables siguen recibiendo estado cocido y lambdas, y todo valor visual nuevo entra en `ui/theme/Tokens.kt` o `Colors.kt`. El núcleo hexagonal (`domain/`, `application/`, `infrastructure/`) **no se toca** en estas tres fases. Los únicos cambios de lógica viven en `ImagesViewModel` (estado de fila abierta; retirada de undo/cola/rename).

**Tech Stack:** Kotlin Multiplatform (`shared` = `commonMain` + `jvmMain`), Compose Desktop Material 3, `kotlin.test` + `kotlinx-coroutines-test`, Gradle con Spotless (ktlint `ktlint_official`, línea a 140) y Kover (umbral 80 % sobre el núcleo). JDK Microsoft 21 (`C:\Users\shizu\.jdks\ms-21.0.12.1`).

**Spec:** `docs/superpowers/specs/2026-09-08-imagewatch-rediseno-design.md`

**Plan anterior:** `docs/superpowers/plans/2026-09-07-migracion-plantilla-kmp.md` (completado; ver `ESTADO.md`).

## Global Constraints

- **Construir SIEMPRE desde PowerShell**, nunca desde Bash (el wrapper sale con código 127 sin mensaje):
  ```powershell
  $env:JAVA_HOME='C:\Users\shizu\.jdks\ms-21.0.12.1'
  Set-Location C:\Users\shizu\IdeaProjects\ImageWatch
  .\gradlew.bat <tarea>
  ```
- **Verde al cerrar cada tarea:** `.\gradlew.bat :shared:jvmTest :shared:koverVerify spotlessCheck`. Si Spotless se queja: `.\gradlew.bat spotlessApply` y volver a construir. La tarea no se da por cerrada con Spotless en rojo.
- **Paquete raíz:** `io.github.shizukajiku.imagewatch`.
- **Comentarios, KDoc y nombres de test en español**, explicando el *por qué*, como el resto del repo. Los nombres de test en Kotlin van entre backticks: `` fun `descripción en español`() ``.
- **Todo cambio de comportamiento va precedido de su test:** rojo → implementación mínima → verde. Los cambios que son solo de apariencia (sin superficie de test unitario, porque el proyecto no tiene tests de Compose UI) se cierran con `build` en verde + repaso visual anotado en el PR.
- **`ktlint_official` con excepción para composables:** `ktlint().editorConfigOverride(mapOf("ktlint_function_naming_ignore_when_annotated_with" to "Composable"))` ya está en `build.gradle.kts`. No añadir `.editorconfig`.
- **Nunca `git checkout` ni `git restore`** en este repo: el índice guarda la plantilla original y el árbol lleva encima el renombrado de paquete. Un checkout lo destruye.
- **Un PR por rama**, en orden. La rama sale de `master` (rama por defecto de este repo; no hay `main` ni remoto).
- Mensajes de commit en español, formato `<tipo>: <descripción>`, terminando con:
  ```
  Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
  Claude-Session: https://claude.ai/code/session_019RioxagAx3sXkNcUVk6ihF
  ```
- **`historias-de-usuario.md`** (`docs/superpowers/specs/2026-09-05-imagewatch-historias-de-usuario.md`) se actualiza en la fase 3: retirar las historias de undo, cola y renombrar; añadir «Abrir una fila muestra su detalle» y «Copiar referencia copia solo el origen».

---

## Estructura de ficheros

Rutas relativas a la raíz del repo. Prefijo común de `commonMain`:
`shared/src/commonMain/kotlin/io/github/shizukajiku/imagewatch/`.

### Se modifican

| Fichero | Fase | Responsabilidad tras el cambio |
|---|---|---|
| `ui/theme/Tokens.kt` | 1, 3 | Añade `Elevation`, `Layout`, `Motion.emphasisEasing`. Fase 3: elimina `Dwell.UNDO_MILLIS`. |
| `ui/theme/Colors.kt` | 1 | Etiqueta de PENDING → «Versión nueva». `tertiaryContainer`/`onTertiaryContainer` en ambos esquemas. |
| `ui/theme/Typography.kt` | 1 | **Se crea.** `TabularNums: TextStyle` compartido. |
| `ui/images/ImageRow.kt` | 1, 3 | Fase 1: `private val` de anchos → `Layout`; `tnum` en cifras vivas. Fase 3: sin `UndoRow` ni «Renombrar»; `RowCard` acepta `expanded`/`onToggleExpand`; banda de detalle + banda de acciones; «copiar referencia» copia `origin` + check de 2 s. |
| `ui/images/ImagesScreen.kt` | 1, 2, 3 | Fase 1: anchos → `Layout`, `tnum`. Fase 2: buscador replegable, «Ver todas» → `onAcknowledgeAll`, umbral de plegado de «Al día», easing de énfasis. Fase 3: sin `QueuedBanner` ni rama `UndoRow`; parámetros nuevos `onToggleExpand`/`onCopyReference`, fuera `onEdit`/`onUndoAcknowledge`/`onPromoteQueued`. |
| `ui/images/ImagesViewModel.kt` | 3 | `ImagesUiState.expandedRow` + `toggleExpand`. Fuera: `requestAcknowledge`, `undoAcknowledge`, `promoteQueued`, `newlyPending`, `renameImage`, `undoJobs`, `TimerState.queued`, `TimerState.pendingUndo`, `ImagesUiState.queuedCount`, `ImageRowState.pendingUndo`. |
| `ui/settings/SettingsScreen.kt` | 1 | `FIELD_WIDTH`/`SLIDER_WIDTH` → `Layout`. |
| `shared/src/jvmMain/.../ui/toast/ToastWindow.kt` | 1 | `TOAST_WIDTH`/`MARGIN` → `Layout`; `tonalElevation = 6.dp` → `Elevation.toast`. |
| `shared/src/commonTest/.../ui/theme/ColorsTest.kt` | 1 | Casos para la etiqueta y el `tertiaryContainer`. |
| `shared/src/commonTest/.../ui/images/ImagesViewModelTest.kt` | 3 | Fuera los casos de undo/cola/rename; dentro `toggleExpand` y «reconocer mueve la fila de sección». |
| `desktopApp/src/main/kotlin/io/github/shizukajiku/imagewatch/Main.kt` | 2, 3 | Fase 2: nada nuevo (la lambda de «Ver todas» ya existe). Fase 3: `MainScreen` sin `editing`/segundo `NameDialog`/`onEdit`/`onUndoAcknowledge`/`onPromoteQueued`; `onAcknowledge` → `viewModel::acknowledge`; añade `onToggleExpand`/`onCopyReference`. |

### Se crean

| Fichero | Fase | Responsabilidad |
|---|---|---|
| `ui/theme/Typography.kt` | 1 | `val TabularNums = TextStyle(fontFeatureSettings = "tnum")`. |

### No se toca

`domain/`, `application/`, `infrastructure/`, `ui/toast/ToastState.kt`,
`ui/toast/ToastNotificationPort.kt`, `ui/settings/SettingsViewModel.kt`,
`ui/components/TitleBar.kt`. (Entran en fases 4–7.)

---

## FASE 1 — Tokens, color, movimiento

Objetivo: consolidar valores visuales dispersos y corregir dos colores.
**Cambio de comportamiento: ninguno.** Todos los tests existentes siguen verdes.

---

### Task 1: Rama de la fase 1 y línea base verde

**Files:** ninguno.

- [ ] **Step 1: Comprobar que el árbol está limpio salvo los dos ficheros de planificación ya modificados**

Run (PowerShell):
```powershell
git status -sb
```
Expected: rama `master`; como modificados solo
`docs/superpowers/plans/2026-09-07-migracion-plantilla-kmp.md` y
`docs/superpowers/plans/ESTADO.md` (cambios previos, ajenos a este plan) y, sin
seguimiento, los dos documentos de esta sesión
(`...specs/2026-09-08-imagewatch-rediseno-design.md`,
`...plans/2026-09-08-imagewatch-rediseno-fases-1-3.md`).

- [ ] **Step 2: Línea base verde**

Run:
```powershell
$env:JAVA_HOME='C:\Users\shizu\.jdks\ms-21.0.12.1'
.\gradlew.bat :shared:jvmTest :shared:koverVerify spotlessCheck
```
Expected: `BUILD SUCCESSFUL`. Anota el número de tests que reporta `:shared:jvmTest` (referencia de `ESTADO.md`: 167 en 19 ficheros).

- [ ] **Step 3: Crear la rama**

Run:
```powershell
git switch -c feature/rediseno-fase-1-tokens
```

- [ ] **Step 4: Commitear los dos documentos de esta sesión en la rama**

```powershell
git add "docs/superpowers/specs/2026-09-08-imagewatch-rediseno-design.md" "docs/superpowers/plans/2026-09-08-imagewatch-rediseno-fases-1-3.md"
git commit -m "docs: spec y plan del rediseño de la interfaz (fases 1-3)"
```

---

### Task 2: `Elevation` y `Motion.emphasisEasing` en `Tokens.kt`

**Files:**
- Modify: `shared/src/commonMain/kotlin/io/github/shizukajiku/imagewatch/ui/theme/Tokens.kt`

**Interfaces:**
- Consumes: nada.
- Produces: `object Elevation { val card: Dp; val toast: Dp; val dialog: Dp }`;
  `Motion.emphasisEasing: Easing`. Los consumen las tareas 5, 11, 17 y las fases 5 y 7.

- [ ] **Step 1: Añadir el import de `Easing` y `CubicBezierEasing`**

En la cabecera de imports de `Tokens.kt`:
```kotlin
import androidx.compose.animation.core.CubicBezierEasing
```
(`Dp` y `.dp` ya están importados.)

- [ ] **Step 2: Añadir `emphasisEasing` al objeto `Motion`**

Dentro de `object Motion`, tras `const val BUMP = 600`:
```kotlin
    /**
     * Curva de énfasis para las entradas y salidas de fila y de sección (Blueprint 06:
     * «400 ms con énfasis»). Sale rápido y frena al final, para que un elemento que aparece o
     * desaparece se lea como un movimiento, no como un parpadeo.
     */
    val emphasisEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
```

- [ ] **Step 3: Añadir el objeto `Elevation` al final del fichero**

```kotlin
/**
 * Sombra y elevación tonal. Categoría propia porque no es espacio ni forma: hasta ahora vivía
 * como literal (`tonalElevation = 6.dp` en la tarjeta de aviso) o como sombra escrita a mano.
 */
object Elevation {
    /** Las tarjetas de fila no se elevan: se separan con 8 dp de hueco, no con sombra. */
    val card = 0.dp

    /** La tarjeta de aviso, que se dibuja sobre cualquier ventana. */
    val toast = 6.dp

    /** El diálogo de confirmación, sobre su velo. */
    val dialog = 24.dp
}
```

- [ ] **Step 4: Compilar y pasar Spotless**

Run:
```powershell
$env:JAVA_HOME='C:\Users\shizu\.jdks\ms-21.0.12.1'
.\gradlew.bat :shared:compileKotlinJvm spotlessCheck
```
Expected: `BUILD SUCCESSFUL`. Si Spotless falla, `.\gradlew.bat spotlessApply` y repetir.

- [ ] **Step 5: Commit**

```powershell
git add shared/src/commonMain/kotlin/io/github/shizukajiku/imagewatch/ui/theme/Tokens.kt
git commit -m "feat: tokens de elevación y curva de énfasis"
```

---

### Task 3: `Layout` — anchos reservados con nombre

Consolida los `private val` de anchos repartidos por cuatro ficheros en un solo
objeto. Blueprint 1i: son huecos de composición para que cambiar de estado no
desplace a un vecino.

**Files:**
- Modify: `ui/theme/Tokens.kt`
- Modify: `ui/images/ImageRow.kt`
- Modify: `ui/images/ImagesScreen.kt`
- Modify: `ui/settings/SettingsScreen.kt`
- Modify: `shared/src/jvmMain/kotlin/io/github/shizukajiku/imagewatch/ui/toast/ToastWindow.kt`

**Interfaces:**
- Produces: `object Layout` con los campos `Dp` listados abajo. Los consumen las
  tareas 5, 10, 11, 17 y las fases 4, 5, 7.

- [ ] **Step 1: Añadir `object Layout` a `Tokens.kt`**

Tras `object Elevation`:
```kotlin
/**
 * Anchos y altos reservados que el diseño fija para que cambiar de estado no desplace a un
 * vecino (Blueprint 1i). Ninguno es un paso de `Space`: son huecos de composición. Vivían como
 * `private val` repartidos por `ImageRow`, `ImagesScreen`, `SettingsScreen` y `ToastWindow`.
 */
object Layout {
    // Fila de la bandeja
    val rowAge = 104.dp
    val rowSkip = 44.dp
    val rowPill = 104.dp
    val rowChip = 88.dp
    val rowKebab = 30.dp
    val rowPadH = 14.dp
    val rowPadV = 11.dp

    // Cabecera de la bandeja
    val searchPill = 260.dp

    // Pie de la ventana
    val footState = 92.dp
    val footNote = 260.dp
    val footMuted = 148.dp

    // Cabecera de sección
    val sectionCounter = 20.dp

    // Barra de título
    val titleBarHeight = 38.dp

    // Aviso
    val toastWidth = 340.dp
    val toastHeight = 78.dp
    val toastIcon = 26.dp
    val toastAction = 84.dp
    val toastClose = 24.dp
    val toastTimer = 64.dp
    val toastBar = 3.dp
    val toastMetaMax = 200.dp
    val toastScreenMargin = 16.dp

    // Ajustes
    val settingsField = 132.dp
    val settingsHelpLine = 16.dp
    val dialogWidth = 420.dp
    val dialogConfirmMin = 132.dp
}
```

- [ ] **Step 2: `ImageRow.kt` — sustituir los `private val` de anchos**

Borrar el bloque:
```kotlin
private val AGE_WIDTH = 104.dp
private val SKIP_WIDTH = 44.dp
private val PILL_WIDTH = 104.dp
private val CHIP_WIDTH = 88.dp
private val KEBAB_WIDTH = 30.dp
private val CARD_PADDING_H = 14.dp
private val CARD_PADDING_V = 11.dp
```
Añadir el import:
```kotlin
import io.github.shizukajiku.imagewatch.ui.theme.Layout
```
Reemplazar en todo el fichero (son referencias directas, sin ambigüedad):
`AGE_WIDTH` → `Layout.rowAge`, `SKIP_WIDTH` → `Layout.rowSkip`,
`PILL_WIDTH` → `Layout.rowPill`, `CHIP_WIDTH` → `Layout.rowChip`,
`KEBAB_WIDTH` → `Layout.rowKebab`, `CARD_PADDING_H` → `Layout.rowPadH`,
`CARD_PADDING_V` → `Layout.rowPadV`.

- [ ] **Step 3: `ImagesScreen.kt` — sustituir los `private val` de anchos**

Borrar:
```kotlin
private val SEARCH_WIDTH = 260.dp
private val FOOT_STATE_WIDTH = 92.dp
private val FOOT_NOTE_WIDTH = 260.dp
private val FOOT_MUTED_WIDTH = 148.dp
```
El import de `Layout` ya está tras el paso 2 si es el mismo fichero — no lo es;
añadir `import io.github.shizukajiku.imagewatch.ui.theme.Layout` a `ImagesScreen.kt`.
Reemplazar: `SEARCH_WIDTH` → `Layout.searchPill`,
`FOOT_STATE_WIDTH` → `Layout.footState`, `FOOT_NOTE_WIDTH` → `Layout.footNote`,
`FOOT_MUTED_WIDTH` → `Layout.footMuted`.

- [ ] **Step 4: `SettingsScreen.kt` — sustituir los `private val` de anchos**

Borrar:
```kotlin
private val FIELD_WIDTH = 220.dp
private val SLIDER_WIDTH = 260.dp
```
Añadir `import io.github.shizukajiku.imagewatch.ui.theme.Layout`.
Reemplazar `FIELD_WIDTH` → `Layout.settingsField` y `SLIDER_WIDTH` → `Layout.searchPill`.

> Nota: `FIELD_WIDTH` valía 220 y `Layout.settingsField` vale 132 (el ancho del
> Blueprint para el campo numérico). Es un cambio visible menor y **buscado** —
> el campo pasa a su medida de diseño. Anótalo en el repaso visual del PR. La
> fase 4 rehace esta pantalla entera.

- [ ] **Step 5: `ToastWindow.kt` — sustituir constantes**

Borrar:
```kotlin
private const val TOAST_WIDTH = 340
private const val MARGIN = 16
```
Añadir `import io.github.shizukajiku.imagewatch.ui.theme.Layout`.
`TOAST_WIDTH` se usa en aritmética `Int` (`bounds.x + bounds.width - TOAST_WIDTH - MARGIN`
y `.dp`, y `TOAST_WIDTH.dp - 12.dp`). Cambiar esas expresiones a trabajar en `Dp`:
```kotlin
    val position = remember(bounds) {
        WindowPosition(
            x = (bounds.x + bounds.width).dp - Layout.toastWidth - Layout.toastScreenMargin,
            y = (bounds.y + bounds.height - LAYER_HEIGHT).dp - Layout.toastScreenMargin,
        )
    }
```
y `state = rememberWindowState(position = position, width = Layout.toastWidth, height = LAYER_HEIGHT.dp)`,
y en `ToastCard` `.width(Layout.toastWidth - 12.dp)`.
`LAYER_HEIGHT` **se queda** como `private const val` — es compensación de la
ventana sin decoración, no medida de diseño.

- [ ] **Step 6: Sustituir `tonalElevation = 6.dp` por `Elevation.toast`**

En `ToastWindow.ToastCard`, dentro del `Surface`:
```kotlin
            tonalElevation = Elevation.toast,
```
Añadir `import io.github.shizukajiku.imagewatch.ui.theme.Elevation`.

- [ ] **Step 7: Compilar todo `shared` y pasar tests + Spotless**

Run:
```powershell
$env:JAVA_HOME='C:\Users\shizu\.jdks\ms-21.0.12.1'
.\gradlew.bat :shared:jvmTest spotlessCheck
```
Expected: `BUILD SUCCESSFUL`, mismo número de tests que la línea base.

- [ ] **Step 8: Commit**

```powershell
git add shared/src/commonMain/kotlin/io/github/shizukajiku/imagewatch/ui/theme/Tokens.kt shared/src/commonMain/kotlin/io/github/shizukajiku/imagewatch/ui/images/ImageRow.kt shared/src/commonMain/kotlin/io/github/shizukajiku/imagewatch/ui/images/ImagesScreen.kt shared/src/commonMain/kotlin/io/github/shizukajiku/imagewatch/ui/settings/SettingsScreen.kt shared/src/jvmMain/kotlin/io/github/shizukajiku/imagewatch/ui/toast/ToastWindow.kt
git commit -m "refactor: anchos reservados a un objeto Layout con nombre"
```

---

### Task 4: `Colors.kt` — etiqueta de PENDING y `tertiaryContainer`

**Files:**
- Modify: `ui/theme/Colors.kt`
- Modify: `shared/src/commonTest/kotlin/io/github/shizukajiku/imagewatch/ui/theme/ColorsTest.kt`

**Interfaces:**
- Produces: `statusColors(PENDING, dark).label == "Versión nueva"`;
  `DarkColors.tertiaryContainer` y `LightColors.tertiaryContainer` definidos.
  Los consume `ImageRow.bumpColor()` (ya lee `tertiaryContainer`) y la tarea 5.

- [ ] **Step 1: Escribir los tests que fallan**

En `ColorsTest.kt`, añadir:
```kotlin
    @Test
    fun `la etiqueta del estado pendiente es la del Blueprint`() {
        assertEquals("Versión nueva", statusColors(ImageStatus.PENDING, dark = true).label)
        assertEquals("Versión nueva", statusColors(ImageStatus.PENDING, dark = false).label)
    }

    @Test
    fun `el contenedor de acento terciario esta definido y responde al tema`() {
        assertEquals(androidx.compose.ui.graphics.Color(0xFF2B3557), DarkColors.tertiaryContainer)
        assertEquals(androidx.compose.ui.graphics.Color(0xFFDDE3FF), LightColors.tertiaryContainer)
        assertNotEquals(DarkColors.tertiaryContainer, LightColors.tertiaryContainer)
    }
```

- [ ] **Step 2: Ejecutar y ver que fallan**

Run:
```powershell
$env:JAVA_HOME='C:\Users\shizu\.jdks\ms-21.0.12.1'
.\gradlew.bat :shared:jvmTest --tests "io.github.shizukajiku.imagewatch.ui.theme.ColorsTest"
```
Expected: FALLAN — `expected <"Versión nueva"> but was <"Nueva versión">` y, para el
segundo, el `tertiaryContainer` por defecto de Material3 (un morado), distinto de
`0xFF2B3557`.

- [ ] **Step 3: Corregir la etiqueta en `Colors.kt`**

En `statusColors`, rama `ImageStatus.PENDING`, en las dos líneas:
```kotlin
        if (dark) {
            StatusColors(Color(0xFF3A1414), Color(0xFFFF8F8F), "Versión nueva")
        } else {
            StatusColors(Color(0xFFFFE3E3), Color(0xFFC92A2A), "Versión nueva")
        }
```

- [ ] **Step 4: Añadir `tertiaryContainer` a los dos esquemas**

En `DarkColors = darkColorScheme(...)`, tras `onErrorContainer`:
```kotlin
        // «Recién movida»: el resaltado de 4 s de una fila que acaba de entrar en «Al día»
        // (Blueprint state colour «Contenedor de acento»). Alias explícito de primaryContainer:
        // dicen lo mismo —«mira esto»— y `bumpColor()` lo lee sin conocer `ImageStatus`. Si algún
        // día se separan, este deja de ser alias y toma color propio.
        tertiaryContainer = Color(0xFF2B3557),
        onTertiaryContainer = Color(0xFFD9E0FF),
```
En `LightColors = lightColorScheme(...)`, tras `onErrorContainer`:
```kotlin
        tertiaryContainer = Color(0xFFDDE3FF),
        onTertiaryContainer = Color(0xFF1B2A5C),
```

- [ ] **Step 5: Ejecutar los tests y ver que pasan**

Run:
```powershell
$env:JAVA_HOME='C:\Users\shizu\.jdks\ms-21.0.12.1'
.\gradlew.bat :shared:jvmTest --tests "io.github.shizukajiku.imagewatch.ui.theme.ColorsTest" spotlessCheck
```
Expected: PASAN. El caso ya existente `cada estado tiene su propia etiqueta` sigue
verde (ninguna otra etiqueta es «Versión nueva»).

- [ ] **Step 6: Commit**

```powershell
git add shared/src/commonMain/kotlin/io/github/shizukajiku/imagewatch/ui/theme/Colors.kt shared/src/commonTest/kotlin/io/github/shizukajiku/imagewatch/ui/theme/ColorsTest.kt
git commit -m "fix: etiqueta «Versión nueva» y contenedor de acento terciario"
```

---

### Task 5: `TabularNums` y auditoría de `tnum`

Blueprint 01: toda cifra que cambia en vivo lleva `FontFeature "tnum"` —
antigüedad, contadores, cuenta atrás, volumen, intervalo.

**Files:**
- Create: `shared/src/commonMain/kotlin/io/github/shizukajiku/imagewatch/ui/theme/Typography.kt`
- Modify: `ui/images/ImageRow.kt`
- Modify: `ui/images/ImagesScreen.kt`

**Interfaces:**
- Produces: `val TabularNums: TextStyle`. Lo consumen las tareas de esta fase y
  las fases 4 y 5.

- [ ] **Step 1: Crear `Typography.kt`**

```kotlin
package io.github.shizukajiku.imagewatch.ui.theme

import androidx.compose.ui.text.TextStyle

/**
 * Cifras tabulares: cada dígito ocupa lo mismo, así un número que cambia en vivo no descuadra a
 * su vecino. Blueprint 01: se aplica a antigüedad, contadores, cuenta atrás, volumen e intervalo.
 * Se usa como `style = TabularNums` o fusionado con `.merge(...)` en un `Text` que ya lleva otro
 * estilo.
 */
val TabularNums = TextStyle(fontFeatureSettings = "tnum")
```

- [ ] **Step 2: `ImageRow.kt` — `tnum` en antigüedad y contador de saltos**

En `AgeCell`, el `Text` de `age`: añadir `style = TabularNums`. En
`SkipAndPillCell`, el `Text` de `skip`: añadir `style = TabularNums`. En el
`Text` de la píldora (`pillText`): añadir `style = TabularNums` (además del
`fontFamily = FontFamily.Monospace` que ya lleva; se fusiona por parámetros, no
choca). Añadir `import io.github.shizukajiku.imagewatch.ui.theme.TabularNums`.

- [ ] **Step 3: `ImagesScreen.kt` — `tnum` en titular, subtítulo, contadores y pie**

Añadir `style = TabularNums` (o `fontFeatureSettings = "tnum"` en el `Text` si ya
lleva `fontWeight`/`fontSize` sueltos — Compose los admite juntos) en:
- `Header`: el `Text` de `"${state.pending} imágenes que atender"` y el de
  `"de ${state.total} vigiladas · comprobando cada ${state.pollIntervalSeconds}s"`.
- `SectionHeader`: el `Text` de `count.toString()`.
- `OkSectionHeader`: el `Text` de `count.toString()`.
- `Footer`: el `Text` de `"· ${state.lastSuccessLabel}"` y el de `"${state.total} vigiladas"`.

Añadir el import de `TabularNums`.

- [ ] **Step 4: Compilar, tests y Spotless**

Run:
```powershell
$env:JAVA_HOME='C:\Users\shizu\.jdks\ms-21.0.12.1'
.\gradlew.bat :shared:jvmTest spotlessCheck
```
Expected: `BUILD SUCCESSFUL`, mismo número de tests.

- [ ] **Step 5: Repaso visual**

Run:
```powershell
$env:JAVA_HOME='C:\Users\shizu\.jdks\ms-21.0.12.1'
.\gradlew.bat :desktopApp:run
```
Abrir la ventana desde el icono de la bandeja. Comprobar contra
`Bandeja.dc.html`: los números de antigüedad y de contador de sección no bailan
al cambiar, y el pie mantiene alineada la nota. Cerrar.

- [ ] **Step 6: Commit**

```powershell
git add shared/src/commonMain/kotlin/io/github/shizukajiku/imagewatch/ui/theme/Typography.kt shared/src/commonMain/kotlin/io/github/shizukajiku/imagewatch/ui/images/ImageRow.kt shared/src/commonMain/kotlin/io/github/shizukajiku/imagewatch/ui/images/ImagesScreen.kt
git commit -m "feat: cifras tabulares en toda cifra que cambia en vivo"
```

---

### Task 6: Cerrar la fase 1

**Files:** ninguno.

- [ ] **Step 1: Verde completo**

Run:
```powershell
$env:JAVA_HOME='C:\Users\shizu\.jdks\ms-21.0.12.1'
.\gradlew.bat :shared:jvmTest :shared:koverVerify spotlessCheck
```
Expected: `BUILD SUCCESSFUL`; `koverVerify` cumple el 80 %.

- [ ] **Step 2: Abrir el PR**

```powershell
git push -u origin feature/rediseno-fase-1-tokens
```
> Si `origin` no existe (el repo no tenía remoto según `ESTADO.md`), omitir el
> push y dejar la rama local; el «PR» es la revisión de rama. Anota en el
> mensaje de cierre: qué se consolidó (`Elevation`, `Layout`,
> `Motion.emphasisEasing`, `TabularNums`), los dos colores corregidos (K-1, K-2),
> y el único cambio visible menor buscado: el campo de Ajustes pasa de 220 a
> 132 dp.

---

## FASE 2 — Bandeja (armazón de la lista)

Objetivo: buscador replegable, «Ver todas» que reconoce, umbral de plegado de
«Al día», easing de énfasis. Todo en `ImagesScreen.kt`. Sin superficie de test
unitario nueva (no hay tests de Compose UI); cada tarea cierra con `build` verde
+ repaso visual.

---

### Task 7: Rama de la fase 2

**Files:** ninguno.

- [ ] **Step 1: Partir de la fase 1**

```powershell
git switch feature/rediseno-fase-1-tokens
git switch -c feature/rediseno-fase-2-bandeja
```

- [ ] **Step 2: Verde de partida**

Run:
```powershell
$env:JAVA_HOME='C:\Users\shizu\.jdks\ms-21.0.12.1'
.\gradlew.bat :shared:jvmTest spotlessCheck
```
Expected: `BUILD SUCCESSFUL`.

---

### Task 8: «Ver todas» de la sección pendiente reconoce todo

Hoy el chip llama a `onSearchChange("")` — limpia el buscador. El Blueprint
(mapa de acciones): «Aplica «Visto» a todas las filas de la sección en una sola
escritura.»

**Files:**
- Modify: `ui/images/ImagesScreen.kt`

**Interfaces:**
- Consumes: `onAcknowledgeAll: () -> Unit` (ya es un parámetro de `ImagesScreen`,
  cableado en `Main.kt` a `viewModel::acknowledgeAll`).

- [ ] **Step 1: Cambiar la firma y el cuerpo de `PendingSectionHeader`**

```kotlin
@Composable
private fun PendingSectionHeader(count: Int, onAcknowledgeAll: () -> Unit) {
    val palette = statusColors(ImageStatus.PENDING, LocalIsDark.current)
    SectionHeader(palette.foreground, "Versión nueva", count) {
        HeaderChip("Ver todas", onClick = onAcknowledgeAll)
    }
}
```

- [ ] **Step 2: Cambiar la llamada en el `when` de `entries`**

```kotlin
                        Entry.PendingHeader ->
                            PendingSectionHeader(pendingRows.size, onAcknowledgeAll)
```
(Antes pasaba `onSearchChange`.)

- [ ] **Step 3: Comprobar que `HeaderChip` acepta `onClick` con nombre**

`HeaderChip(text: String, onClick: () -> Unit)` ya lo hace. Sin cambio.

- [ ] **Step 4: Compilar y Spotless**

Run:
```powershell
$env:JAVA_HOME='C:\Users\shizu\.jdks\ms-21.0.12.1'
.\gradlew.bat :shared:compileKotlinJvm spotlessCheck
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Repaso visual**

`.\gradlew.bat :desktopApp:run`. Con al menos una imagen en «Versión nueva»,
pulsar «Ver todas» en la cabecera de esa sección: todas las filas pendientes
pasan a «Al día» de una vez (animando el alto). El buscador no se toca.

- [ ] **Step 6: Commit**

```powershell
git add shared/src/commonMain/kotlin/io/github/shizukajiku/imagewatch/ui/images/ImagesScreen.kt
git commit -m "fix: «Ver todas» reconoce la sección, no limpia el buscador"
```

---

### Task 9: «Al día» plegada por defecto por encima de 12 imágenes

Blueprint: «Por defecto plegada por encima de 12 imágenes.» Hoy `okOpen` arranca
siempre en `true`.

**Files:**
- Modify: `ui/images/ImagesScreen.kt`

- [ ] **Step 1: Subir el cálculo de las tres listas por encima de `var okOpen`**

En `ImagesScreen`, hoy el orden es: `var okOpen by rememberSaveable { ... }`,
luego `val listState = ...`, luego `val pendingRows = ...`, `errorRows`, `okRows`.
Reordenar para que `pendingRows`/`errorRows`/`okRows` se calculen **antes** de
`var okOpen`:
```kotlin
            val pendingRows = state.rows.filter { it.status == ImageStatus.PENDING }
            val errorRows = state.rows.filter { it.status == ImageStatus.ERROR }
            val okRows = state.rows.filter { it.status != ImageStatus.PENDING && it.status != ImageStatus.ERROR }

            var okOpen by rememberSaveable { mutableStateOf(okRows.size <= 12) }
            val listState = rememberLazyListState()
```
Ninguna de las tres listas depende de `okOpen` (solo la construcción de
`entries` lo usa, más abajo), así que el movimiento es seguro.

- [ ] **Step 2: Compilar y Spotless**

Run:
```powershell
$env:JAVA_HOME='C:\Users\shizu\.jdks\ms-21.0.12.1'
.\gradlew.bat :shared:compileKotlinJvm spotlessCheck
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Repaso visual**

`.\gradlew.bat :desktopApp:run` en modo simulación (por defecto, 4 imágenes):
«Al día» arranca **desplegada** (≤ 12). El chevron sigue plegando y desplegando
a mano. `rememberSaveable` conserva la elección del usuario entre recomposiciones.

> Con el origen simulado no se llega a 13 imágenes; el caso «arranca plegada» se
> verifica contra `Bandeja.dc.html` variante `carga` (32 vigiladas) en el
> repaso, o temporalmente añadiendo nombres en `IMAGE_NAMES`.

- [ ] **Step 4: Commit**

```powershell
git add shared/src/commonMain/kotlin/io/github/shizukajiku/imagewatch/ui/images/ImagesScreen.kt
git commit -m "feat: «Al día» arranca plegada por encima de 12 imágenes"
```

---

### Task 10: Buscador replegable

Blueprint bloque «Cabecera de la bandeja»: el buscador está replegado a icono de
32 dp y se expande a píldora al pulsarlo, **sin empujar el titular**. Hoy es un
`OutlinedTextField` de 260 dp siempre visible en una segunda fila.

**Files:**
- Modify: `ui/images/ImagesScreen.kt`

- [ ] **Step 1: Añadir estado local y `FocusRequester` a `Header`**

Imports nuevos en `ImagesScreen.kt`:
```kotlin
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
```
(`LaunchedEffect` puede que ya esté importado — comprobar.)

- [ ] **Step 2: Reescribir la parte del buscador en `Header`**

`Header` hoy tiene un `Column` con un `Row` (titular + iconos) y, debajo, el
`OutlinedTextField`. Nueva estructura: el buscador entra **en el mismo `Row`**,
entre «comprobar todas» y la campana, y el `Column` externo pierde la segunda
fila. Dentro de `Header`:
```kotlin
    val searchFocus = remember { FocusRequester() }
    var searchExpanded by rememberSaveable { mutableStateOf(state.search.isNotEmpty()) }

    // Si `highlight()` limpió el filtro, el buscador vuelve a su icono cuando pierde el foco.
    LaunchedEffect(state.search) {
        if (state.search.isEmpty()) searchExpanded = false
    }
```
En el `Row` de controles, sustituir el actual `IconButton` de búsqueda (no hay
uno hoy en el `Row`; el buscador está fuera) por:
```kotlin
            if (searchExpanded) {
                OutlinedTextField(
                    value = state.search,
                    onValueChange = onSearchChange,
                    placeholder = { Text("Buscar imagen…") },
                    leadingIcon = {
                        SvgIcon(AppSvg.SEARCH, MaterialTheme.colorScheme.onSurfaceVariant, Modifier.size(IconSize.sm))
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(Radius.pill),
                    modifier = Modifier
                        .width(Layout.searchPill)
                        .focusRequester(searchFocus)
                        .onFocusChanged { if (!it.isFocused && state.search.isEmpty()) searchExpanded = false }
                        .animateContentSize(),
                )
                LaunchedEffect(Unit) { searchFocus.requestFocus() }
            } else {
                IconButton({ searchExpanded = true }) {
                    SvgIcon(AppSvg.SEARCH, MaterialTheme.colorScheme.onSurfaceVariant, Modifier.size(IconSize.md))
                }
            }
```
Borrar el `OutlinedTextField` suelto que hoy cuelga bajo el `Row` y el
`Modifier.padding(top = Space.md)` asociado. El `Column` externo de `Header`
puede quedar como un solo `Row` si ya no hay segunda fila; mantener el `Column`
con su `padding` de pantalla (`start/top/end/bottom`) para no mover los márgenes.

- [ ] **Step 3: Compilar y Spotless**

Run:
```powershell
$env:JAVA_HOME='C:\Users\shizu\.jdks\ms-21.0.12.1'
.\gradlew.bat :shared:compileKotlinJvm spotlessCheck
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Repaso visual**

`.\gradlew.bat :desktopApp:run`. El buscador aparece como icono de 32 dp. Al
pulsarlo se expande a píldora y toma el foco sin desplazar el titular. Al
escribir, filtra. Al borrar todo y salir del campo (clic fuera), se repliega.
Pulsar «Ver» en un aviso (que limpia el filtro) también lo repliega.

- [ ] **Step 5: Commit**

```powershell
git add shared/src/commonMain/kotlin/io/github/shizukajiku/imagewatch/ui/images/ImagesScreen.kt
git commit -m "feat: buscador replegable a icono que no empuja el titular"
```

---

### Task 11: Easing de énfasis en entradas y salidas

Blueprint 06: fila y sección entran y salen a 400 ms «con énfasis»; una sección
que se vacía anima su alto. Hoy se usan los specs por defecto de
`AnimatedVisibility` y `animateItem()`.

**Files:**
- Modify: `ui/images/ImagesScreen.kt`

- [ ] **Step 1: Imports de animación**

```kotlin
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import io.github.shizukajiku.imagewatch.ui.theme.Motion
```
(Algunos pueden estar ya.)

- [ ] **Step 2: Spec en los `AnimatedVisibility` de la pantalla**

Los dos `AnimatedVisibility` que quedan tras la fase 3 son el de `NoticeBanner`
y —en esta fase todavía— el de `QueuedBanner` (se borra en la fase 3). Para el
`NoticeBanner`:
```kotlin
    AnimatedVisibility(
        disconnected || nadaQueAtender,
        enter = expandVertically(tween(Motion.EMPHASIS, easing = Motion.emphasisEasing)) +
            fadeIn(tween(Motion.EMPHASIS)),
        exit = shrinkVertically(tween(Motion.EMPHASIS, easing = Motion.emphasisEasing)) +
            fadeOut(tween(Motion.EMPHASIS)),
    ) {
```

- [ ] **Step 3: Spec en `animateItem()` de las filas**

En el `items(entries, key = ...)`, cada rama que hoy pasa
`Modifier.animateItem()` pasa a:
```kotlin
Modifier.animateItem(
    fadeInSpec = tween(Motion.EMPHASIS),
    fadeOutSpec = tween(Motion.EMPHASIS),
    placementSpec = tween(Motion.EMPHASIS, easing = Motion.emphasisEasing),
)
```
Son las ramas `PendingItem`, `ErrorItem`, `OkItem` (y, hasta la fase 3, la de
`UndoRow`). Para no repetir la expresión cinco veces, extraer al principio del
`items { }`:
```kotlin
                    val itemMotion = Modifier.animateItem(
                        fadeInSpec = tween(Motion.EMPHASIS),
                        fadeOutSpec = tween(Motion.EMPHASIS),
                        placementSpec = tween(Motion.EMPHASIS, easing = Motion.emphasisEasing),
                    )
```
y usar `modifier = itemMotion` en cada fila.

- [ ] **Step 4: Compilar, tests y Spotless**

Run:
```powershell
$env:JAVA_HOME='C:\Users\shizu\.jdks\ms-21.0.12.1'
.\gradlew.bat :shared:jvmTest spotlessCheck
```
Expected: `BUILD SUCCESSFUL`, mismo número de tests.

- [ ] **Step 5: Repaso visual**

`.\gradlew.bat :desktopApp:run` en simulación. Al marcar «Visto» una fila, sale
animando su alto con una curva que frena al final (no un corte). Cuando la
sección «Versión nueva» se queda sin filas, su cabecera sale animando el alto.

- [ ] **Step 6: Commit y cierre de la fase 2**

```powershell
git add shared/src/commonMain/kotlin/io/github/shizukajiku/imagewatch/ui/images/ImagesScreen.kt
git commit -m "feat: entradas y salidas de fila y sección con curva de énfasis"
```
Run el verde completo:
```powershell
$env:JAVA_HOME='C:\Users\shizu\.jdks\ms-21.0.12.1'
.\gradlew.bat :shared:jvmTest :shared:koverVerify spotlessCheck
```
Expected: `BUILD SUCCESSFUL`. Abrir el PR `feature/rediseno-fase-2-bandeja`
(o dejar la rama y anotar la revisión).

---

## FASE 3 — Fila abierta y retirada de Undo / cola / renombrar

Objetivo: click-para-desplegar el detalle de una fila; retirar la mecánica de
«Deshacer», la cola «Ponerlas arriba» y el renombrado (decisiones D-2 y D-5 del
spec). Aquí sí hay superficie de test unitario: `ImagesViewModel`.

---

### Task 12: Rama de la fase 3

**Files:** ninguno.

- [ ] **Step 1: Partir de la fase 2**

```powershell
git switch feature/rediseno-fase-2-bandeja
git switch -c feature/rediseno-fase-3-fila-abierta
```

- [ ] **Step 2: Verde de partida**

Run:
```powershell
$env:JAVA_HOME='C:\Users\shizu\.jdks\ms-21.0.12.1'
.\gradlew.bat :shared:jvmTest spotlessCheck
```
Expected: `BUILD SUCCESSFUL`.

---

### Task 13: `ImagesUiState.expandedRow` y `toggleExpand`

**Files:**
- Modify: `ui/images/ImagesViewModel.kt`
- Modify: `shared/src/commonTest/kotlin/io/github/shizukajiku/imagewatch/ui/images/ImagesViewModelTest.kt`

**Interfaces:**
- Produces: `ImagesUiState.expandedRow: String?`; `ImagesViewModel.toggleExpand(name: String)`.
  Los consumen las tareas 17 y 19.

- [ ] **Step 1: Escribir los tests que fallan**

En `ImagesViewModelTest.kt`, junto a los demás casos:
```kotlin
    @Test
    fun `abrir una fila fija expandedRow y volver a pulsarla lo limpia`() = runTest {
        val fixture = fixture(listOf("alpha"))
        fixture.service.poll()

        fixture.viewModel.toggleExpand("alpha")
        assertEquals("alpha", fixture.viewModel.state.value.expandedRow)

        fixture.viewModel.toggleExpand("alpha")
        assertNull(fixture.viewModel.state.value.expandedRow)
    }

    @Test
    fun `abrir otra fila cierra la anterior`() = runTest {
        val fixture = fixture(listOf("alpha", "beta"))
        fixture.service.poll()

        fixture.viewModel.toggleExpand("alpha")
        fixture.viewModel.toggleExpand("beta")

        assertEquals("beta", fixture.viewModel.state.value.expandedRow)
    }
```

- [ ] **Step 2: Ejecutar y ver que fallan**

Run:
```powershell
$env:JAVA_HOME='C:\Users\shizu\.jdks\ms-21.0.12.1'
.\gradlew.bat :shared:jvmTest --tests "io.github.shizukajiku.imagewatch.ui.images.ImagesViewModelTest"
```
Expected: FALLAN al compilar — `unresolved reference: toggleExpand` y
`expandedRow`.

- [ ] **Step 3: Añadir el campo a `ImagesUiState`**

En `data class ImagesUiState(...)`, tras `trace`:
```kotlin
    /** Nombre de la fila desplegada en su sitio, o nulo si ninguna lo está. Un solo dueño a la vez. */
    val expandedRow: String? = null,
```

- [ ] **Step 4: Añadir `toggleExpand` al view model**

Junto a los demás métodos públicos (p. ej. tras `onSearchChange`):
```kotlin
    /**
     * Despliega o pliega el detalle de una fila en su sitio (Blueprint «Fila (clic)»). Estado
     * puro: no toca el snapshot ni arranca temporizadores. Un solo dueño —abrir una cierra la
     * anterior—, por eso se compara con el nombre en vez de togglear un booleano por fila.
     */
    fun toggleExpand(name: String) {
        mutableState.update { it.copy(expandedRow = if (it.expandedRow == name) null else name) }
    }
```

- [ ] **Step 5: Comprobar que `derive()` conserva `expandedRow`**

`derive()` construye el `ImagesUiState` con `current.copy(...)`; `current` ya
trae `expandedRow` intacto (igual que `search`). Verificar que la lista de
campos de `current.copy(...)` en `derive()` **no** lo pisa (no lo menciona → se
conserva). Sin cambio.

- [ ] **Step 6: Ejecutar los tests y ver que pasan**

Run:
```powershell
$env:JAVA_HOME='C:\Users\shizu\.jdks\ms-21.0.12.1'
.\gradlew.bat :shared:jvmTest --tests "io.github.shizukajiku.imagewatch.ui.images.ImagesViewModelTest" spotlessCheck
```
Expected: PASAN.

- [ ] **Step 7: Commit**

```powershell
git add shared/src/commonMain/kotlin/io/github/shizukajiku/imagewatch/ui/images/ImagesViewModel.kt shared/src/commonTest/kotlin/io/github/shizukajiku/imagewatch/ui/images/ImagesViewModelTest.kt
git commit -m "feat: estado de fila desplegada en el view model"
```

---

### Task 14: Retirar la mecánica de «Deshacer» del view model

Decisión D-2. `«Visto»` pasa a reconocer al momento vía `acknowledge`.

**Files:**
- Modify: `ui/images/ImagesViewModel.kt`
- Modify: `shared/src/commonTest/kotlin/io/github/shizukajiku/imagewatch/ui/images/ImagesViewModelTest.kt`

- [ ] **Step 1: Borrar los tests de undo y ajustar el que comprueba el rastro**

En `ImagesViewModelTest.kt`, **borrar** estos cuatro casos completos:
- `` `pedir Visto no reconoce al momento, la fila queda en espera de deshacer` ``
- `` `si llega una version mas nueva durante la ventana de deshacer, no se reconoce sola` ``
- `` `deshacer dentro de la ventana no reconoce nada` ``
- `` `pedir Visto de una fila que no esta pendiente no hace nada` ``

Y **reescribir** `` `pasado el tiempo de deshacer, Visto se confirma solo y deja un rastro` ``
como comprobación de que `acknowledge` directo también deja rastro:
```kotlin
    @Test
    fun `reconocer una imagen la mueve a «Al día» y deja un rastro que se apaga solo`() = runTest {
        val fixture = fixture(
            listOf("alpha"),
            localVersion = "1.0.0",
            remoteVersion = "2.0.0",
            scope = backgroundScope,
        )
        fixture.service.poll()

        fixture.viewModel.acknowledge("alpha")

        val fila = fixture.viewModel.state.value.rows.single()
        assertEquals(ImageStatus.OK, fila.status)
        assertEquals("alpha", fixture.viewModel.state.value.trace)

        advanceTimeBy(Dwell.TRACE_MILLIS + 100)
        assertNull(fixture.viewModel.state.value.trace, "El rastro se apaga solo")
    }
```
(Mantiene el import de `Dwell` — solo se usa `TRACE_MILLIS`.)

- [ ] **Step 2: Ejecutar y ver el estado (rojo de compilación esperado)**

Run:
```powershell
$env:JAVA_HOME='C:\Users\shizu\.jdks\ms-21.0.12.1'
.\gradlew.bat :shared:jvmTest --tests "io.github.shizukajiku.imagewatch.ui.images.ImagesViewModelTest"
```
Expected: falla al compilar el test — `leaveTrace` es privado y `acknowledge`
existe, pero el test nuevo espera que `acknowledge` llame a `leaveTrace`, que hoy
solo lo hace `requestAcknowledge`. Se arregla en el paso 4.

- [ ] **Step 3: Borrar del view model todo lo de undo**

En `ImagesViewModel.kt`:
- Borrar los métodos `requestAcknowledge(name: String)` y `undoAcknowledge(name: String)` completos.
- Borrar el campo `private val undoJobs = MutableStateFlow<Map<String, Job>>(emptyMap())` y su KDoc.
- En `close()`, borrar la línea `undoJobs.value.values.forEach { it.cancel() }`.
- En `TimerState`, borrar el campo `val pendingUndo: Set<String> = emptySet()` y su comentario.
- En `toRow(...)`, borrar `val pendingUndo = name in timerState.pendingUndo` y el
  argumento `pendingUndo = pendingUndo` en las dos construcciones de `ImageRowState`.
- En `data class ImageRowState(...)`, borrar el campo `val pendingUndo: Boolean = false` y su KDoc.
- Borrar el import `import kotlinx.coroutines.flow.getAndUpdate` si ya no se usa
  (lo usaba `undoAcknowledge`; comprobar que no queda otro uso — `acknowledgeAll`
  usa `snapshot.pending()`, no `getAndUpdate`; sí lo usa `close()`? no. Si el
  compilador marca el import como no usado, Spotless lo quita con `spotlessApply`).

- [ ] **Step 4: Hacer que `acknowledge` deje el rastro**

Hoy `leaveTrace(name)` lo llama solo el job de `requestAcknowledge`. Moverlo a
`acknowledge`:
```kotlin
    fun acknowledge(name: String) {
        service.acknowledge(name)
        snapshot = service.lastSnapshot()
        dismissToastsFor(name)
        sounds.play(Sound.SUCCESS)
        leaveTrace(name)
        recompute()
    }
```
`leaveTrace` ya existe como privado y usa `scope` + `Dwell.TRACE_MILLIS`; no
cambia.

- [ ] **Step 5: Ejecutar los tests y ver que pasan**

Run:
```powershell
$env:JAVA_HOME='C:\Users\shizu\.jdks\ms-21.0.12.1'
.\gradlew.bat :shared:jvmTest --tests "io.github.shizukajiku.imagewatch.ui.images.ImagesViewModelTest" spotlessCheck
```
Expected: PASAN. `` `reconocer una imagen retira su aviso en pantalla` `` y
`` `reconocer una imagen reproduce el sonido de exito` `` siguen verdes
(`acknowledge` no cambió esas responsabilidades).

- [ ] **Step 6: Commit**

```powershell
git add shared/src/commonMain/kotlin/io/github/shizukajiku/imagewatch/ui/images/ImagesViewModel.kt shared/src/commonTest/kotlin/io/github/shizukajiku/imagewatch/ui/images/ImagesViewModelTest.kt
git commit -m "refactor: «Visto» reconoce al momento, sin ventana de deshacer"
```

---

### Task 15: Retirar la cola «Ponerlas arriba» del view model

Decisión D-2. Las novedades entran arriba directamente.

**Files:**
- Modify: `ui/images/ImagesViewModel.kt`
- Modify: `shared/src/commonTest/kotlin/io/github/shizukajiku/imagewatch/ui/images/ImagesViewModelTest.kt`

- [ ] **Step 1: Reescribir los tres tests de cola**

En `ImagesViewModelTest.kt`:
- **Borrar** `` `ponerlas arriba muestra las novedades que esperaban` ``.
- **Reescribir** `` `una imagen que pasa a pendiente en un ciclo posterior espera tras la banda de novedades` `` como:
```kotlin
    @Test
    fun `una imagen que pasa a pendiente en un ciclo posterior aparece arriba sin esperar`() = runTest {
        val fixture = fixture(listOf("alpha"), localVersion = "1.0.0", remoteVersion = "1.0.0")
        fixture.service.poll()
        assertEquals(ImageStatus.OK, fixture.viewModel.state.value.rows.single().status)

        fixture.source.version = "2.0.0"
        fixture.service.poll()

        val state = fixture.viewModel.state.value
        assertEquals(listOf("alpha"), state.rows.map { it.name }, "La novedad se ve ya, no se encola")
        assertEquals(1, state.pending)
    }
```
- **Reescribir** `` `el primer ciclo de la sesion no encola nada` `` quitando la
  aserción de `queuedCount`:
```kotlin
    @Test
    fun `el primer ciclo de la sesion enseña el pendiente que ya habia`() = runTest {
        val fixture = fixture(listOf("alpha"), localVersion = "1.0.0", remoteVersion = "2.0.0")

        fixture.service.poll()

        assertEquals(listOf("alpha"), fixture.viewModel.state.value.rows.map { it.name })
    }
```

- [ ] **Step 2: Ejecutar y ver el estado**

Run:
```powershell
$env:JAVA_HOME='C:\Users\shizu\.jdks\ms-21.0.12.1'
.\gradlew.bat :shared:jvmTest --tests "io.github.shizukajiku.imagewatch.ui.images.ImagesViewModelTest"
```
Expected: falla al compilar — `queuedCount`, `promoteQueued` referenciados en el
test todavía… no, ya se quitaron del test. Falla porque `promoteQueued` sigue en
el VM sin usar → aviso, no error; y porque el test nuevo espera que las novedades
no se encolen, cosa que hoy sí pasa. Se arregla en el paso 3.

- [ ] **Step 3: Borrar del view model todo lo de la cola**

En `ImagesViewModel.kt`:
- Borrar el método `promoteQueued()` completo.
- Borrar el método privado `newlyPending(previous, current)` completo.
- En `onSnapshot(...)`, borrar el bloque:
  ```kotlin
        val nuevasPendientes = newlyPending(snapshot, received)
        ...
        if (nuevasPendientes.isNotEmpty()) {
            timers.update { it.copy(queued = it.queued + nuevasPendientes) }
        }
  ```
  (dejar `val nuevas = novedadesSobrePendientes(snapshot, received)` y el
  `snapshot = received` que va justo después; ese cálculo alimenta el latido
  `bumped` y **se conserva**).
- En `TimerState`, borrar `val queued: Set<String> = emptySet()` y su comentario.
- En `derive()`, borrar:
  ```kotlin
        val encoladas = timerState.queued.filter { byName[it]?.status == ImageStatus.PENDING }.toSet()
        val visibles = all.filterNot { it.name in encoladas }
  ```
  y usar `all` directamente donde antes iba `visibles`:
  ```kotlin
        return current.copy(
            rows = if (term.isEmpty()) all else all.filter { it.name.lowercase().contains(term) },
  ```
  y borrar la línea `queuedCount = encoladas.size,` de ese `copy`.
- En `data class ImagesUiState(...)`, borrar el campo `val queuedCount: Int = 0` y su KDoc.

- [ ] **Step 4: Ejecutar los tests y ver que pasan**

Run:
```powershell
$env:JAVA_HOME='C:\Users\shizu\.jdks\ms-21.0.12.1'
.\gradlew.bat :shared:jvmTest --tests "io.github.shizukajiku.imagewatch.ui.images.ImagesViewModelTest" spotlessCheck
```
Expected: PASAN.

- [ ] **Step 5: Commit**

```powershell
git add shared/src/commonMain/kotlin/io/github/shizukajiku/imagewatch/ui/images/ImagesViewModel.kt shared/src/commonTest/kotlin/io/github/shizukajiku/imagewatch/ui/images/ImagesViewModelTest.kt
git commit -m "refactor: las novedades entran arriba directamente, sin cola"
```

---

### Task 16: Retirar el renombrado del view model

Decisión D-5. El Blueprint no tiene «Renombrar» en ninguna parte.

**Files:**
- Modify: `ui/images/ImagesViewModel.kt`
- Modify: `shared/src/commonTest/kotlin/io/github/shizukajiku/imagewatch/ui/images/ImagesViewModelTest.kt`

- [ ] **Step 1: Borrar los tests de renombrar**

En `ImagesViewModelTest.kt`, **borrar** los tres casos:
- `` `renombrar no colisiona con el propio nombre` ``
- `` `renombrar una imagen reproduce el sonido de exito` ``
- `` `renombrar una imagen conserva su version reconocida` ``

- [ ] **Step 2: Ejecutar (rojo de compilación esperado en pasos siguientes)**

Run:
```powershell
$env:JAVA_HOME='C:\Users\shizu\.jdks\ms-21.0.12.1'
.\gradlew.bat :shared:jvmTest --tests "io.github.shizukajiku.imagewatch.ui.images.ImagesViewModelTest"
```
Expected: los tests restantes pasan; `renameImage` queda sin usar (aviso).

- [ ] **Step 3: Borrar `renameImage` y simplificar `saveName`**

En `ImagesViewModel.kt`:
- Borrar `fun renameImage(previous: String, candidate: String): String? = ...`.
- `addImage` hoy es `fun addImage(name: String): String? = saveName(editing = null, candidate = name)`.
  Reescribir `saveName` como `addImage` directo, sin el parámetro `editing`:
```kotlin
    fun addImage(candidate: String): String? {
        val value = candidate.trim()
        if (value.isEmpty() || !VALID_NAME.matches(value)) {
            return "Usa solo letras, números, '.', '_' o '-'"
        }
        val current = trackedImages.findAll()
        if (current.any { it == value }) {
            return "Ya existe una imagen con ese nombre"
        }
        trackedImages.save(current + value)
        sounds.play(Sound.SUCCESS)
        recompute()
        return null
    }
```
Borrar la función privada `saveName(...)` entera (su lógica queda absorbida
arriba) y el `import` de `VALID_NAME`… `VALID_NAME` es un `private val` del
fichero, se conserva (lo usa `addImage`).

> `VersionPollingService.renameImage` y `ImageStateStore.rename` quedan sin
> llamador desde la UI. **No se tocan** (spec §15): son del núcleo, tienen tests
> propios, y su retirada es un refactor del núcleo aparte de este rediseño.

- [ ] **Step 4: Ejecutar los tests y ver que pasan**

Run:
```powershell
$env:JAVA_HOME='C:\Users\shizu\.jdks\ms-21.0.12.1'
.\gradlew.bat :shared:jvmTest --tests "io.github.shizukajiku.imagewatch.ui.images.ImagesViewModelTest" spotlessCheck
```
Expected: PASAN. `` `un nombre valido se agrega` ``, `` `un nombre invalido se rechaza con mensaje` ``,
`` `un nombre duplicado se rechaza con mensaje` `` y
`` `agregar una imagen valida reproduce el sonido de exito` `` siguen verdes.

- [ ] **Step 5: Commit**

```powershell
git add shared/src/commonMain/kotlin/io/github/shizukajiku/imagewatch/ui/images/ImagesViewModel.kt shared/src/commonTest/kotlin/io/github/shizukajiku/imagewatch/ui/images/ImagesViewModelTest.kt
git commit -m "refactor: quitar el renombrado de imágenes (fuera del rediseño)"
```

---

### Task 17: `ImageRow.kt` — fila abierta, sin `UndoRow` ni «Renombrar»

**Files:**
- Modify: `ui/images/ImageRow.kt`

**Interfaces:**
- Consumes: `Layout.*`, `Motion.emphasisEasing`, `TabularNums`,
  `ImagesUiState.expandedRow` (a través de un `Boolean expanded` que pasa la pantalla).
- Produces: `RowCard(background, borderColor, expanded, onToggleExpand, modifier, content)`;
  `PendingRow`/`ErrorRow`/`OkRow` con parámetros nuevos
  `expanded: Boolean`, `onToggleExpand: () -> Unit`, `onCopyReference: () -> Unit`;
  sin `onEdit`. Los consume la tarea 19.

- [ ] **Step 1: Borrar `UndoRow` y el import de `Dwell`/`Animatable`**

Borrar la función `UndoRow(...)` completa. Borrar los imports que quedan sin uso
(`Dwell`, `Animatable` si solo lo usaba `UndoRow` — comprobar; `Animatable` no lo
usa nadie más en el fichero tras quitar `UndoRow`). Spotless los elimina con
`spotlessApply` si quedan.

- [ ] **Step 2: Borrar «Renombrar» del `RowMenu`**

En `RowMenu`, borrar el `DropdownMenuItem` con `text = { Text("Renombrar") }` y
`leadingIcon = { SvgIcon(AppSvg.PENCIL, ...) }` y su `onClick = { expanded = false; onEdit() }`.
Quitar el parámetro `onEdit` de la firma de `RowMenu` y de sus tres llamadas
(`PendingRow`, `ErrorRow`, `OkRow`).

- [ ] **Step 3: `RowCard` acepta `expanded` y `onToggleExpand`**

```kotlin
@Composable
private fun RowCard(
    background: Color,
    borderColor: Color,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    modifier: Modifier = Modifier,
    detail: (@Composable () -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    Surface(
        color = background,
        border = BorderStroke(1.dp, borderColor),
        shape = RoundedCornerShape(Radius.md),
        modifier = modifier.fillMaxWidth().animateContentSize(
            tween(Motion.EMPHASIS, easing = Motion.emphasisEasing),
        ),
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.md),
                modifier = Modifier
                    .clickable(onClick = onToggleExpand)
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
Imports nuevos: `androidx.compose.foundation.clickable`,
`androidx.compose.animation.animateContentSize`,
`androidx.compose.animation.core.tween`,
`io.github.shizukajiku.imagewatch.ui.theme.Motion` (si no está),
`androidx.compose.material3.HorizontalDivider` (ya está).

> Los controles internos (`ActionChip`, `RowMenu`, y las píldoras del pie de la
> fila abierta) consumen el clic por sí mismos (`Surface(onClick=...)`,
> `IconButton`), así que un clic sobre ellos no llega al `clickable` de la fila.

- [ ] **Step 4: Componente `RowDetail` — banda de detalle + banda de acciones**

Nuevo composable privado en `ImageRow.kt`:
```kotlin
/**
 * El detalle que se despliega bajo la fila (Blueprint D2): cuatro datos que la app tiene de
 * verdad —nombre, origen, última versión, cuándo se detectó— y las cuatro acciones que son la
 * razón de ser de la fila abierta.
 */
@Composable
private fun RowDetail(
    row: ImageRowState,
    copied: Boolean,
    onRefresh: () -> Unit,
    onCopyReference: () -> Unit,
    onToggleSilence: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(Modifier.padding(Layout.rowPadH)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Space.xl)) {
            listOf(
                "Nombre" to row.name,
                "Origen" to row.registry,
                "Última versión" to (if (row.remote != "—") row.remote else row.local),
                "Detectada" to row.detail,
            ).forEach { (k, v) ->
                Column(Modifier.weight(1f)) {
                    Text(
                        k.uppercase(),
                        fontSize = TypeScale.caption,
                        color = mutedText(LocalIsDark.current),
                    )
                    Text(
                        v,
                        fontSize = TypeScale.meta,
                        style = TabularNums,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = Space.md),
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DetailPill("Comprobar ahora", onRefresh)
            DetailPill(if (copied) "Copiado" else "Copiar referencia", onCopyReference)
            DetailPill(
                if (row.muted) "Reactivar avisos" else "Silenciar avisos",
                onToggleSilence,
            )
            Spacer(Modifier.weight(1f))
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(Radius.pill),
                onClick = onDelete,
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
    }
}

@Composable
private fun DetailPill(text: String, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(Radius.pill),
        onClick = onClick,
    ) {
        Text(
            text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = TypeScale.meta,
            modifier = Modifier.padding(horizontal = Space.md, vertical = Space.xs + 2.dp),
        )
    }
}
```
Imports: `androidx.compose.foundation.layout.Spacer` (ya), `TabularNums`.

- [ ] **Step 5: `PendingRow` / `ErrorRow` / `OkRow` — firma y paso de `expanded`/detalle**

A cada una añadir los parámetros `expanded: Boolean`, `onToggleExpand: () -> Unit`,
`onCopyReference: () -> Unit`; quitar `onEdit`. En el cuerpo, pasar a `RowCard`:
```kotlin
    RowCard(
        background = MaterialTheme.colorScheme.surface,
        borderColor = palette.background,
        expanded = expanded,
        onToggleExpand = onToggleExpand,
        modifier = modifier.then(emphasisModifier(row)),
        detail = {
            RowDetail(row, copied, onRefresh, onCopyReference, onToggleSilence, onDelete)
        },
    ) {
        // …celdas iguales que ahora…
    }
```
donde `copied` es un estado local (paso siguiente).

- [ ] **Step 6: Estado local `copied` de 2 s**

Al principio de cada `*Row` (o mejor, dentro de `RowDetail` para no repetir):
mover el estado a `RowDetail`:
```kotlin
    var copiedAt by remember { mutableStateOf(0L) }
    val copied = copiedAt > 0
    LaunchedEffect(copiedAt) {
        if (copiedAt > 0) { delay(2000); copiedAt = 0L }
    }
```
y el `onCopyReference` que recibe `RowDetail` se envuelve:
```kotlin
            DetailPill(if (copied) "Copiado" else "Copiar referencia") {
                onCopyReference()
                copiedAt = Clock.System.now().toEpochMilliseconds()
            }
```
Imports: `kotlinx.coroutines.delay`, `kotlin.time.Clock`,
`androidx.compose.runtime.*` (ya).

- [ ] **Step 7: Compilar (fallará por las llamadas de `ImagesScreen`)**

Run:
```powershell
$env:JAVA_HOME='C:\Users\shizu\.jdks\ms-21.0.12.1'
.\gradlew.bat :shared:compileKotlinJvm
```
Expected: FALLA en `ImagesScreen.kt` — las llamadas a `PendingRow`/`ErrorRow`/`OkRow`
todavía pasan `onEdit` y no pasan `expanded`. Se arregla en la tarea 19.

- [ ] **Step 8: Commit parcial**

```powershell
git add shared/src/commonMain/kotlin/io/github/shizukajiku/imagewatch/ui/images/ImageRow.kt
git commit -m "feat: fila desplegable con detalle y acciones; sin UndoRow ni Renombrar"
```
(El árbol no compila entre este commit y el de la tarea 19; ambos van en la misma
rama y PR, y el reviewer los ve juntos. Si se prefiere no dejar un commit que no
compila, fusionar tareas 17 y 19 en una — pero son 40 min juntas.)

---

### Task 18: «Copiar referencia» copia el origen

Blueprint: «Copia `origin` al portapapeles. Sin aviso: el cambio de icono … es la
única señal.» Hoy copia `registry:version`.

**Files:**
- Modify: `ui/images/ImageRow.kt`

- [ ] **Step 1: Quitar la copia del `RowMenu` y dejarla como lambda**

Hoy `RowMenu` hace la copia dentro (`clipboard.setText(AnnotatedString(reference))`
con `reference = "${row.registry}:${...}"`). Cambiar el `DropdownMenuItem` de
«Copiar referencia» para que llame a un `onCopyReference: () -> Unit` que recibe
`RowMenu` por parámetro (igual que `onRefresh`, `onToggleSilence`…), y borrar el
`val reference` y el `LocalClipboardManager` de `RowMenu` si ya no los usa nadie
más ahí.

- [ ] **Step 2: La lambda `onCopyReference` vive en las `*Row` y copia `row.registry`**

En cada `*Row`, `onCopyReference` es un parámetro que llega desde `ImagesScreen`
(tarea 19) y hace `clipboard.setText(AnnotatedString(row.registry))`. Para tener
el `clipboard` disponible, capturarlo en cada `*Row`:
```kotlin
    val clipboard = LocalClipboardManager.current
    // …
    onCopyReference = { clipboard.setText(AnnotatedString(row.registry)) },
```
pasando esa lambda tanto a `RowDetail` como a `RowMenu`.

> Alternativa más limpia: que `ImagesScreen` reciba `onCopyReference: (String) -> Unit`
> y haga la copia con el `clipboard` de la pantalla. **Elegido:** hacerlo en la
> fila —el `clipboard` es un `CompositionLocal`, disponible en cualquier
> composable— para no ensanchar la firma de `ImagesScreen` con lógica de
> portapapeles. `ImagesScreen` solo enruta `onCopyReference: (String) -> Unit`
> hacia la fila (nombre → nada; la fila ya tiene `row.registry`). Ver tarea 19.

- [ ] **Step 3: Compilar `ImageRow` aislado no es posible; se valida en la tarea 19**

Sin `run` aquí. Commit:
```powershell
git add shared/src/commonMain/kotlin/io/github/shizukajiku/imagewatch/ui/images/ImageRow.kt
git commit -m "feat: «copiar referencia» copia solo el origen, señal de icono 2 s"
```

---

### Task 19: `ImagesScreen.kt` + `Main.kt` — cablear la fila abierta, quitar `QueuedBanner` y `Dwell.UNDO_MILLIS`

Cierra la fase 3: el árbol vuelve a compilar y a estar verde.

**Files:**
- Modify: `ui/images/ImagesScreen.kt`
- Modify: `ui/theme/Tokens.kt`
- Modify: `desktopApp/src/main/kotlin/io/github/shizukajiku/imagewatch/Main.kt`

- [ ] **Step 1: `Tokens.kt` — borrar `Dwell.UNDO_MILLIS`**

En `object Dwell`, borrar:
```kotlin
    /** Cuanto se enseña la linea de «Deshacer» tras marcar «Visto» antes de reconocer de verdad. */
    const val UNDO_MILLIS = 4000L
```
`HIGHLIGHT_MILLIS`, `BUMP_MILLIS`, `TRACE_MILLIS` se quedan.

- [ ] **Step 2: `ImagesScreen.kt` — firma nueva**

```kotlin
@Composable
fun ImagesScreen(
    state: ImagesUiState,
    mutedAll: Boolean,
    onSearchChange: (String) -> Unit,
    onAdd: () -> Unit,
    onAcknowledge: (String) -> Unit,
    onAcknowledgeAll: () -> Unit,
    onRefresh: (String?) -> Unit,
    onDelete: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onToggleMuteAll: () -> Unit,
    onToggleSilence: (String) -> Unit,
    onToggleExpand: (String) -> Unit,
    onCopyReference: (String) -> Unit,
) {
```
Quitados: `onUndoAcknowledge`, `onEdit`, `onPromoteQueued`.

- [ ] **Step 3: Quitar `QueuedBanner` y la rama `UndoRow`**

- Borrar la función `QueuedBanner(...)` completa y su `AnimatedVisibility`
  dentro de `ImagesScreen`:
  ```kotlin
            AnimatedVisibility(state.queuedCount > 0, ...) { ... QueuedBanner(...) }
  ```
- En el `when (entry)` del `items`, la rama `is Entry.PendingItem` hoy hace
  `if (entry.row.pendingUndo) { UndoRow(...) } else { PendingRow(...) }`.
  Dejar solo:
  ```kotlin
                        is Entry.PendingItem ->
                            PendingRow(
                                row = entry.row,
                                expanded = state.expandedRow == entry.row.name,
                                onToggleExpand = { onToggleExpand(entry.row.name) },
                                onAcknowledge = { onAcknowledge(entry.row.name) },
                                onRefresh = { onRefresh(entry.row.name) },
                                onCopyReference = { onCopyReference(entry.row.name) },
                                onToggleSilence = { onToggleSilence(entry.row.name) },
                                onDelete = { onDelete(entry.row.name) },
                                modifier = itemMotion,
                            )
  ```
- Igual para `ErrorItem` y `OkItem`: añadir
  `expanded = state.expandedRow == entry.row.name`,
  `onToggleExpand = { onToggleExpand(entry.row.name) }`,
  `onCopyReference = { onCopyReference(entry.row.name) }`; quitar
  `onEdit = { onEdit(entry.row.name) }`.

- [ ] **Step 4: `Main.kt` — `MainScreen` sin `editing`, con las lambdas nuevas**

En `MainScreen`:
- Borrar `var editing by remember { mutableStateOf<String?>(null) }`.
- Borrar el bloque:
  ```kotlin
      editing?.let { previous ->
          NameDialog("Editar imagen", previous, { editing = null }) {
              viewModel.renameImage(previous, it)
          }
      }
  ```
- En la llamada a `ImagesScreen(...)`:
  - `onAcknowledge = viewModel::acknowledge` (antes `::requestAcknowledge`).
  - Quitar `onUndoAcknowledge = viewModel::undoAcknowledge`.
  - Quitar `onEdit = { editing = it }`.
  - Quitar `onPromoteQueued = viewModel::promoteQueued`.
  - Añadir `onToggleExpand = viewModel::toggleExpand`.
  - Añadir `onCopyReference = { /* la fila copia row.registry por su cuenta */ }`
    — como la copia la hace la fila con su `CompositionLocal` (tarea 18), este
    parámetro de `ImagesScreen` puede ser `(String) -> Unit` que no hace nada, o
    —más honesto— eliminarlo también de `ImagesScreen` y que la fila no reciba
    `onCopyReference` sino que copie directamente. **Decisión de cierre:** la
    fila copia directamente (`LocalClipboardManager` + `row.registry`), y
    `ImagesScreen`/`Main` **no** llevan `onCopyReference`. Ajustar la firma del
    paso 2 quitando `onCopyReference`, y las `*Row` de la tarea 17/18 no reciben
    `onCopyReference` — hacen la copia dentro del `DetailPill`/`RowMenu`.
- `Screen.IMAGES` deja de necesitar `NameDialog` para editar; el de agregar
  (`if (adding) { NameDialog("Agregar imagen", "", ...) { viewModel.addImage(it) } }`)
  se queda igual.

> **Nota de consistencia:** por el paso anterior, `onCopyReference` **no** existe
> como parámetro en `ImagesScreen` ni en `Main`. La fila hace la copia por su
> cuenta. Revisar que las tareas 17 y 18 se implementaron así (la fila captura
> `LocalClipboardManager` y copia `row.registry`); si se implementaron con
> `onCopyReference` como parámetro, quitarlo ahora.

- [ ] **Step 5: Compilar todo, tests y Spotless**

Run:
```powershell
$env:JAVA_HOME='C:\Users\shizu\.jdks\ms-21.0.12.1'
.\gradlew.bat :shared:jvmTest :desktopApp:compileKotlin spotlessCheck
```
Expected: `BUILD SUCCESSFUL`. Si Spotless marca imports sin usar
(`getAndUpdate`, `Dwell` en `ImageRow`, `PENCIL` si no se usa en otro sitio),
`.\gradlew.bat spotlessApply` y repetir.

- [ ] **Step 6: Repaso visual**

Run:
```powershell
$env:JAVA_HOME='C:\Users\shizu\.jdks\ms-21.0.12.1'
.\gradlew.bat :desktopApp:run
```
Contra `Bandeja.dc.html` variante `abierta`:
- Clic en una fila (no en un control) → despliega el detalle en su sitio,
  animando el alto con la curva de énfasis. Otro clic lo pliega. Abrir otra
  cierra la primera.
- En el detalle: 4 columnas (Nombre, Origen, Última versión, Detectada) y 4
  acciones; «Quitar de la lista» en rojo a la derecha.
- «Copiar referencia» → el texto de la píldora cambia a «Copiado» 2 s; el
  portapapeles tiene el origen (`registry.local/alpha`), sin `:version`.
- Marcar «Visto» → la fila sale al momento hacia «Al día» (sin línea de
  «Deshacer»), y aparece el rastro «X se ha movido aquí» 4 s en la cabecera
  plegada.
- El menú kebab ya no tiene «Renombrar».
- No hay banda «Ponerlas arriba»: una novedad simulada entra arriba sola.

> Verificar el matiz del spec §16.6: en la variante `abierta` la banda superior
> de la tarjeta usa una rejilla de 3 columnas (sin la de `+N`/píldora). Si el
> repaso lo confirma como necesario, la fila abierta oculta esa columna; si no,
> se deja la de 4 y se anota.

- [ ] **Step 7: Actualizar `historias-de-usuario.md`**

En `docs/superpowers/specs/2026-09-05-imagewatch-historias-de-usuario.md`:
- **Retirar** las historias de «Deshacer un Visto», «Ponerlas arriba» y
  «Renombrar una imagen» (buscar por esos términos; marcarlas como
  `~~retirada en el rediseño (2026-09-08)~~` o borrarlas, según el estilo del
  documento).
- **Añadir**, con el formato Dado/Cuando/Entonces del documento:
  - `H-NN · Abrir una fila muestra su detalle en su sitio` — código:
    `ImagesViewModel.toggleExpand`, `ImageRow.RowDetail`. Prueba:
    `ImagesViewModelTest` (`abrir una fila fija expandedRow…`).
  - `H-NN · Copiar referencia copia solo el origen` — código: `ImageRow.RowMenu` /
    `RowDetail`. Sin prueba unitaria (portapapeles); repaso visual.
  - `H-NN · Marcar «Visto» reconoce al momento y deja un rastro` — código:
    `ImagesViewModel.acknowledge`. Prueba: `ImagesViewModelTest`
    (`reconocer una imagen la mueve a «Al día» y deja un rastro…`).

- [ ] **Step 8: Commit y cierre de la fase 3**

```powershell
git add shared/src/commonMain/kotlin/io/github/shizukajiku/imagewatch/ui/images/ImagesScreen.kt shared/src/commonMain/kotlin/io/github/shizukajiku/imagewatch/ui/theme/Tokens.kt desktopApp/src/main/kotlin/io/github/shizukajiku/imagewatch/Main.kt shared/src/commonMain/kotlin/io/github/shizukajiku/imagewatch/ui/images/ImageRow.kt docs/superpowers/specs/2026-09-05-imagewatch-historias-de-usuario.md
git commit -m "feat: cablear fila abierta; quitar QueuedBanner y Dwell.UNDO_MILLIS"
```
Verde completo:
```powershell
$env:JAVA_HOME='C:\Users\shizu\.jdks\ms-21.0.12.1'
.\gradlew.bat :shared:jvmTest :shared:koverVerify spotlessCheck
```
Expected: `BUILD SUCCESSFUL`; `koverVerify` cumple el 80 %. Anota en el cierre
del PR cuántos tests hay ahora (bajarán respecto a la línea base: se retiran ~10
casos de undo/cola/rename y se añaden ~4).

---

## Fases 4–7 (planes propios, tras cerrar 1–3)

El repo escribe un plan por fase (`fase-3.md`, `fase-4.md`, …). Las fases 4–7 son
independientes entre sí una vez cerrada la 1, y su detalle se fija mejor sobre lo
que las fases 1–3 dejen realmente en el árbol. Cada una tendrá su
`docs/superpowers/plans/2026-MM-DD-imagewatch-rediseno-fase-N.md` cuando toque:

| Fase | Contenido (ver spec §9–§12, §14) | Rama |
|---|---|---|
| **4 — Ajustes** | Aplicar-al-momento en `SettingsViewModel` (sin «Guardar»), layout de dos columnas de tarjetas, control segmentado, líneas de ayuda reservadas; `AutostartPort` (expect/actual Windows) + toggle «Iniciar al encender»; `wipeLocalData` (borrar + reiniciar, sin recablear `Wiring`) y `resetSettings`; `ConfirmDialog` compartido (sustituye a `DeleteDialog`). | `feature/rediseno-fase-4-ajustes` |
| **5 — Aviso** | `Toast` con `kind`/`meta`/`action`, cuerpo que no nombra la versión anterior, rejilla fija 340×78 con cuenta atrás, tarjeta de resumen a partir de la 4.ª (`TOASTS_VISIBLES` → 3), `kind = ERROR` con `NotificationPort.notifyFailures` (un bucle nuevo en `VersionPollingService.notifyTransitions`). | `feature/rediseno-fase-5-aviso` |
| **6 — Barra de título** | `TitleBar` con campana de silencio general + cerrar (sin min/max, D-1), alto 38 dp con hairline; cablear `mutedAll`/`onToggleMuteAll` desde `MainScreen`. | `feature/rediseno-fase-6-barra-titulo` |
| **7 — Teclado** | `Modifier.focusRing()` (2 dp acento, offset 2, por fuera del borde); flechas/Espacio/Enter/Escape/Tab en la lista; foco visible en todos los controles de Bandeja, Ajustes y diálogos. | `feature/rediseno-fase-7-teclado` |

---

## Self-review

**Cobertura del spec (§ del spec → tarea):**

- §6 Fase 1 (Elevation, Layout, emphasisEasing, K-1, K-2, tnum) → Tasks 2–5. ✔
- §7 Fase 2 (B-1 buscador, B-2 «Ver todas», B-4 umbral, B-5 easing, B-11 pie) →
  Tasks 8–11 + el pie ya se migró a `Layout` en Task 3. ✔
- §8 Fase 3 (retirar undo/cola/rename, fila abierta, copiar origen) →
  Tasks 13–19. ✔
- §8.1 «se conserva `leaveTrace`/`Dwell.TRACE_MILLIS`» → Task 14 Step 4 lo mueve
  a `acknowledge`, no lo borra. ✔
- §16.6 (rejilla 3-col de la fila abierta) → Task 19 Step 6, marcado para el
  repaso visual. ✔
- §5.1 `notifyFailures`, §5.2 `AutostartPort`, §9–§12 → **fases 4–7**, fuera de
  este plan por diseño (planes propios). Anotado.

**Escaneo de placeholders:** sin «TBD/TODO/implementar luego». Los pasos de
código llevan el código real. Los pasos sin test unitario (refactor de Compose)
están marcados como tal y cierran con `build` + repaso visual, que es lo que el
proyecto puede hacer (no hay tests de Compose UI).

**Consistencia de tipos:**
- `toggleExpand(name: String)` / `ImagesUiState.expandedRow: String?` — Task 13,
  consumidos igual en Tasks 17 y 19. ✔
- `RowCard(background, borderColor, expanded, onToggleExpand, modifier, detail, content)` —
  Task 17 Step 3, usado igual en Step 5. ✔
- `onCopyReference`: Tasks 17–18 lo plantean como parámetro; Task 19 Step 4
  **resuelve** que la copia la hace la fila con `LocalClipboardManager` y que
  `onCopyReference` **no** entra en la firma de `ImagesScreen`/`Main`. La nota de
  Task 19 Step 4 pide revisar Tasks 17/18 para implementarlo así. Consistente,
  con la decisión concentrada en un punto.
- `addImage(candidate: String)` — Task 16 Step 3, pierde el parámetro `editing`;
  su único llamador (`Main.MainScreen`, `if (adding) NameDialog(...) { viewModel.addImage(it) }`)
  ya pasa un solo argumento. ✔
- `Dwell.UNDO_MILLIS` — se elimina en Task 19 Step 1 (fase 3), no en la fase 1,
  para no dejar la fase 1 con referencias rotas. `Dwell` en `ImageRow.kt` (import)
  se limpia en Task 17 Step 1. ✔
