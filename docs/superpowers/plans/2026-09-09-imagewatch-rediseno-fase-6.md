# Fase 6 — Barra de título — Plan de implementación

> **Para quien ejecute:** REQUIRED SUB-SKILL: usar `superpowers:subagent-driven-development`
> (recomendado) o `superpowers:executing-plans` para ejecutar este plan tarea a tarea. Los pasos
> usan casillas (`- [ ]`) para el seguimiento.

**Objetivo:** La barra de título propia gana una campana de silenciar-todo a la izquierda del
botón de cerrar, alto fijo por token y hairline inferior — sin tocar el contrato de «solo cerrar,
sin minimizar ni maximizar» (D-1).

**Arquitectura:** Cambio de firma en un único composable (`TitleBar`) más su único call site
(`Main.kt`). Reutiliza la misma lambda `onToggleMuteAll` que ya existe para la campana de la
cabecera (`ImagesScreen.Header`) — no hay estado nuevo, `mutedAll` ya vive en `AppConfig`.

**Tech Stack:** Kotlin Multiplatform, Compose Desktop (`jvmMain`).

**Spec:** `docs/superpowers/specs/2026-09-08-imagewatch-rediseno-design.md`, §11 (líneas
1064-1105). Decisión D-1 (§3): barra de título solo cerrar + campana, sin minimizar ni maximizar.

## Restricciones globales

- Commits en español, `<tipo>: <descripción>`, terminando con la atribución vigente del
  `system-reminder` de esta sesión.
- **Nunca** `git checkout`/`git restore <fichero>`. `git add` solo con rutas explícitas — nunca
  `git add -A` (dos ficheros de otra rama, `2026-09-07-migracion-plantilla-kmp.md` y `ESTADO.md`,
  siguen modificados y no son de este trabajo).
- ktlint: línea máxima **120**, no 140.
- **Sin test de Compose UI para esta fase** (spec §11: "no hay test de Compose UI; el cambio va al
  repaso visual"). La verificación es compilar + `:desktopApp:run` + mirar.
- Verificación final, **solo desde PowerShell** con `JAVA_HOME` fijado:
  ```powershell
  $env:JAVA_HOME='C:\Users\shizu\.jdks\ms-21.0.12.1'
  Set-Location C:\Users\shizu\IdeaProjects\ImageWatch
  .\gradlew.bat :shared:jvmTest :shared:koverVerify :desktopApp:compileKotlin spotlessCheck --console=plain
  ```
- **No** se toca `MainScreen` en `Main.kt`: `TitleBar` se llama directamente dentro de `main()`
  (línea ~430), donde `config` y `wiring` ya están en scope — no hace falta propagar
  `onToggleMuteAll` a través de `MainScreen` como sugiere la letra del spec, porque en este
  repositorio la llamada a `TitleBar` no vive dentro de `MainScreen`.

---

## Fichero por fichero

| Fichero | Qué cambia |
|---|---|
| `shared/src/jvmMain/kotlin/.../ui/components/TitleBar.kt` | Firma gana `mutedAll`/`onToggleMuteAll`; añade campana; alto fijo `Layout.titleBarHeight`; hairline inferior |
| `desktopApp/src/main/kotlin/.../Main.kt` | La llamada a `TitleBar` (línea ~430) pasa `config.mutedAll` y la lambda de silenciar-todo |

---

### Task 1: `TitleBar` gana campana, alto fijo y hairline

**Files:**
- Modify: `shared/src/jvmMain/kotlin/io/github/shizukajiku/imagewatch/ui/components/TitleBar.kt`
- Modify: `desktopApp/src/main/kotlin/io/github/shizukajiku/imagewatch/Main.kt:430-434`

**Interfaces:**
- Consumes: `Layout.titleBarHeight` (`ui/theme/Tokens.kt`, ya existe, `38.dp`), `AppSvg.BELL`/
  `AppSvg.BELL_OFF` (`ui/components/SvgIcon.kt`, ya existen), `IconSize.md` (`16.dp`, ya existe).
- Produces: `WindowScope.TitleBar(title: String, mutedAll: Boolean, onToggleMuteAll: () -> Unit, onClose: () -> Unit)`
  — cambia la firma existente (antes `TitleBar(title, onClose)`); el único call site es `Main.kt`.

No hay test que escribir primero (spec §11: sin test de Compose UI). Se implementa directo y se
verifica con compilación + repaso visual.

- [ ] **Step 1: Reescribir `TitleBar.kt`**

Reemplazar el fichero completo:

```kotlin
package io.github.shizukajiku.imagewatch.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowScope
import io.github.shizukajiku.imagewatch.ui.theme.IconSize
import io.github.shizukajiku.imagewatch.ui.theme.Layout
import io.github.shizukajiku.imagewatch.ui.theme.TypeScale

// Ancho de la zona de clic del boton de cerrar: no es un paso de la escala, es el tamaño minimo
// comodo para acertar el clic sin invadir el area que arrastra la ventana.
private val CLOSE_HIT_WIDTH = 46.dp

// Igual que la campana de la cabecera (ImagesScreen.Header): zona de clic redonda de 24 dp, mas
// angosta que CLOSE_HIT_WIDTH porque aqui no compite con el area de arrastre de la ventana.
private val BELL_HIT_WIDTH = 24.dp

/**
 * Barra de título propia. La ventana va sin decoración del sistema y con dos botones: silenciar
 * todos los avisos y cerrar (a la bandeja).
 *
 * No hay minimizar ni maximizar a propósito. Una aplicación residente que se minimiza acaba
 * duplicando su sitio —en la barra de tareas y en la bandeja— y deja a Windows decidiendo cuándo
 * se puede volver a poner delante, cosa que no concede a un proceso que no está en primer plano.
 * Con un solo estado, «visible» o «en la bandeja», reaparecer siempre funciona.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WindowScope.TitleBar(
    title: String,
    mutedAll: Boolean,
    onToggleMuteAll: () -> Unit,
    onClose: () -> Unit,
) {
    // La barra entera arrastra la ventana, que es lo que el usuario espera de una barra de título.
    WindowDraggableArea {
        Column {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth().height(Layout.titleBarHeight),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    // El start = 14.dp no esta en la escala de Space (12 o 16): es el margen que
                    // ya tenia la barra frente al borde de la ventana.
                    modifier = Modifier.fillMaxWidth().padding(start = 14.dp),
                ) {
                    SvgIcon(AppSvg.LOGO, MaterialTheme.colorScheme.primary, Modifier.size(IconSize.md))
                    Text(
                        "  $title",
                        fontSize = TypeScale.meta,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    // Mismo interruptor que la campana de la cabecera: silenciar todos los avisos.
                    Surface(
                        color = if (mutedAll) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
                        shape = CircleShape,
                    ) {
                        IconButton(onToggleMuteAll, modifier = Modifier.width(BELL_HIT_WIDTH)) {
                            val tint = if (mutedAll) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            SvgIcon(if (mutedAll) AppSvg.BELL_OFF else AppSvg.BELL, tint, Modifier.size(IconSize.md))
                        }
                    }
                    IconButton(onClose, modifier = Modifier.width(CLOSE_HIT_WIDTH)) {
                        SvgIcon(
                            AppSvg.CLOSE,
                            MaterialTheme.colorScheme.onSurfaceVariant,
                            Modifier.size(IconSize.sm),
                        )
                    }
                }
            }
            Box(
                Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.surfaceVariant),
            )
        }
    }
}
```

Nota sobre el hairline: se implementa como `Box` de 1 dp, la alternativa que el spec deja explícita
("o un `Box` de 1 dp") — más simple que `Modifier.drawBehind { drawLine(...) }` para una línea
sólida de un color plano.

- [ ] **Step 2: Actualizar el call site en `Main.kt`**

En `Main.kt`, dentro de `main()` (línea ~430), reemplazar:

```kotlin
                            TitleBar("ImageWatch — imágenes monitoreadas") {
                                windowVisible = false
                                viewModel.clearHighlight()
                                wiring.windowFocused.value = false
                            }
```

por:

```kotlin
                            TitleBar(
                                title = "ImageWatch — imágenes monitoreadas",
                                mutedAll = config.mutedAll,
                                onToggleMuteAll = { wiring.applyConfig(config.copy(mutedAll = !config.mutedAll)) },
                                onClose = {
                                    windowVisible = false
                                    viewModel.clearHighlight()
                                    wiring.windowFocused.value = false
                                },
                            )
```

`config` ya está en scope en ese punto de `main()` (`val config by wiring.config.collectAsState()`,
línea ~304) — es el mismo `config` que usa `MainScreen` unas líneas más abajo para el mismo
propósito (`onToggleMuteAll = { wiring.applyConfig(config.copy(mutedAll = !config.mutedAll)) }` en
`ImagesScreen`). No hace falta declarar nada nuevo.

- [ ] **Step 3: Compilar**

```powershell
$env:JAVA_HOME='C:\Users\shizu\.jdks\ms-21.0.12.1'
Set-Location C:\Users\shizu\IdeaProjects\ImageWatch
.\gradlew.bat :desktopApp:compileKotlin --console=plain
```

Esperado: compila sin error.

- [ ] **Step 4: Repaso visual con la app real**

```powershell
.\gradlew.bat :desktopApp:run --console=plain
```

En background (no termina sola: es una app de bandeja). Abrir la ventana principal (desde el
`Tray`) y comprobar:

- La barra de título mide `Layout.titleBarHeight` (38 dp) y tiene una línea de 1 dp abajo, del
  color `surfaceVariant`.
- La campana está a la izquierda de la cruz de cerrar, círculo de 24 dp.
- Pulsar la campana de la barra de título alterna `mutedAll`: el icono cambia a `BELL_OFF` con
  fondo `surfaceVariant`, y el mismo cambio se refleja en la campana de la cabecera (`ImagesScreen`)
  y en «Avisos silenciados» del pie — misma fuente, `config.mutedAll`.
- Pulsar la campana de la cabecera y comprobar que la barra de título también cambia (la misma
  lambda, en ambas direcciones).
- La cruz sigue cerrando a la bandeja igual que antes (sin minimizar ni maximizar).

Matar el proceso (`java`/`gradle`/`kotlin`) al terminar, igual que en fases anteriores.

- [ ] **Step 5: `spotlessApply` + verificación completa**

```powershell
.\gradlew.bat spotlessApply --console=plain
.\gradlew.bat :shared:jvmTest :shared:koverVerify :desktopApp:compileKotlin spotlessCheck --console=plain
```

- [ ] **Step 6: Commit**

```bash
git add shared/src/jvmMain/kotlin/io/github/shizukajiku/imagewatch/ui/components/TitleBar.kt desktopApp/src/main/kotlin/io/github/shizukajiku/imagewatch/Main.kt
git commit -m "feat: TitleBar gana campana de silenciar todos los avisos"
```

---

## Self-Review

**Cobertura del spec §11:**
- Firma `TitleBar(title, mutedAll, onToggleMuteAll, onClose)` con campana a la izquierda de cerrar,
  mismo patrón visual que la cabecera → Task 1 Step 1.
- Alto fijo `Layout.titleBarHeight`, hairline inferior 1 dp `surfaceVariant` → Task 1 Step 1.
- `Main.kt` propaga `config.mutedAll` y la lambda de `applyConfig` → Task 1 Step 2 (sin pasar por
  `MainScreen`, que en este repo no envuelve la llamada a `TitleBar` — ver Restricciones globales).
- Sin test de Compose UI, repaso visual → Task 1 Steps 3-4.

**Placeholder scan:** ninguno — todos los pasos traen código completo.

**Consistencia de tipos:** `TitleBar` pasa de 2 a 4 parámetros; el único call site (`Main.kt`) se
actualiza en el mismo task, así que no queda ninguna llamada con la firma vieja.

**Puntos abiertos para revisión manual (no bloquean el cierre de fase):**
1. `BELL_HIT_WIDTH = 24.dp` es una decisión de esta implementación — el spec dice
   "`IconButton(onToggleMuteAll, width = 24.dp)`" pero no nombra la constante; se le puso nombre
   para que quede autoexplicativa junto a `CLOSE_HIT_WIDTH`.
2. El icono de la campana usa `IconSize.md` (16 dp) tal como pide el spec explícitamente para esta
   barra (distinto de `IconSize.lg`, 18 dp, que usa la campana de la cabecera) — es intencional,
   no una inconsistencia.
