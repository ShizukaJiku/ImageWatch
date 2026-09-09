# ImageWatch — Rediseño de la interfaz: diseño de la refactorización

**Fecha:** 2026-09-08
**Estado:** aprobado para planificación
**Alcance:** implementar el rediseño completo de Claude Design (proyecto
`5438f112-46c4-4627-a9fd-aabed26319ba`) sobre la base ya migrada a Kotlin
Multiplatform. Cinco pantallas — Bandeja, Fila abierta, Aviso, Ajustes, Barra de
título — más la infraestructura que el diseño da por hecha: arranque al iniciar
sesión, borrado de datos locales, restablecer ajustes, navegación por teclado.
**No entra:** cambiar la arquitectura hexagonal, cambiar el origen de datos,
añadir objetivos de KMP nuevos.

---

## 1. Punto de partida

### 1.1 Lo que ya existe

El repositorio pasó por la **fase 7** (separación lógica de UX / apariencia) y
por la **migración a KMP** (commit `9e7325f`). De eso queda, y **no se toca en su
forma**:

- **Núcleo hexagonal en `commonMain`.** `domain/`, `application/` (puertos +
  servicios), `infrastructure/` (adaptadores). La UI recibe estado ya cocido
  (`ImagesUiState`, `SettingsUiState`) y lambdas; **ningún composable recibe un
  view model**. Esa frontera se conserva.
- **`ui/theme/Tokens.kt`** — `Space` (4/8/12/16/20), `Radius` (6/10/999),
  `IconSize` (14/16/18), `TypeScale` (16/13/12/11), `Motion`
  (220/320/400/700/600), `Dwell` (4000/3000/4000/4000).
- **`ui/theme/Colors.kt`** — `DarkColors`/`LightColors` de Material 3 y
  `statusColors(status, dark)`. La paleta **ya coincide** con la del Blueprint
  (fondo `#1B1B1D`, superficie `#232326`, acento `#4C6EF5`, contenedores de
  pendiente/error/al-día/sin-verificar).
- **Pantallas:** `ImagesScreen`, `ImageRow`, `SettingsScreen`, `TitleBar`
  (`jvmMain`), `ToastWindow` + `ToastState`. View models `ImagesViewModel`,
  `SettingsViewModel`.
- **Diálogos:** `NameDialog` (alta/edición) y `DeleteDialog` en
  `ui/dialogs/ImageDialogs.kt`.

### 1.2 Qué es el diseño

`Blueprint.dc.html` es la **especificación normativa**: sección 05 (mapa de
acciones — qué control, qué escribe, qué se ve después) y sección 06 (datos,
estado, movimiento) son el contrato. Regla del propio Blueprint: **«Lo que no
está aquí no se implementa.»** Las otras cuatro `.dc.html` (`Bandeja`,
`Ajustes`, `Aviso`, `Bandeja de novedades`) son las maquetas a tamaño, en claro
y oscuro, con los anchos reservados marcados.

El diseño es en gran parte una **formalización de lo ya construido en fase 7**,
más refinamientos concretos. Este documento cataloga las diferencias y las
ordena en ocho fases, cada una con su PR y revertible por separado.

### 1.3 Cómo se compila y qué tiene que quedar verde

```powershell
$env:JAVA_HOME='C:\Users\shizu\.jdks\ms-21.0.12.1'
Set-Location C:\Users\shizu\IdeaProjects\ImageWatch
.\gradlew.bat <tarea>
```

Gradle **solo** funciona desde PowerShell con `JAVA_HOME` fijado. En Bash,
`./gradlew` sale con código 127 sin decir nada.

Cada fase cierra con:

```
:shared:jvmTest      todos los tests pasan
:shared:koverVerify  cumple el umbral del 80 %
spotlessCheck        limpio
```

y con el repaso visual manual de la fase contra su `.dc.html`, apuntado en la
descripción del PR (es la puerta T14, que sigue abierta desde la migración).

---

## 2. Principios que gobiernan la refactorización

1. **El núcleo no cambia de forma.** Solo se le añaden dos cosas, mínimas y
   justificadas: un método al puerto de notificación (`notifyFailures`) y un
   puerto nuevo para integración con el sistema operativo (`AutostartPort`).
   Nada más de `domain/`, `application/` o `infrastructure/` se toca.
2. **La frontera UX / apariencia se conserva.** Todo valor visual nuevo entra en
   `Tokens.kt` o `Colors.kt`. Cambiar la apariencia no abre un fichero de
   lógica; cambiar la lógica no obliga a redecidir apariencia.
3. **El Blueprint es la ley.** Lo que no está en él se retira: renombrar, modo
   simulación en la UI, minimizar y maximizar en la barra de título.
4. **Cada fase es revertible.** Un PR por fase. Si una fase se revierte entera,
   no se pierde ninguna funcionalidad de las fases anteriores.
5. **`tnum` en toda cifra que cambia en vivo.** Blueprint 01: antigüedad,
   contadores, cuenta atrás, volumen, intervalo. Se audita en la fase 1.
6. **Nada se mueve solo.** Blueprint 1i: los anchos reservados (44, 88, 104,
   128, 148, 92, 260, 20, 64…) existen aunque estén vacíos, para que cambiar de
   estado no desplace a un vecino.

---

## 3. Decisiones tomadas (sesión de brainstorming, 2026-09-08)

| # | Decisión | Efecto |
|---|---|---|
| D-1 | **Barra de título: solo cerrar + campana.** Sin minimizar ni maximizar. | Se conserva el rationale de app de bandeja (`TitleBar.kt`). Se añade la campana de silencio general. |
| D-2 | **Quitar toda la mecánica de Undo y cola.** | Fuera `UndoRow`, `pendingUndo`, `requestAcknowledge`, `undoAcknowledge`, `undoJobs`, `Dwell.UNDO_MILLIS`, `QueuedBanner`, `queuedCount`, `promoteQueued`, `newlyPending` del view model, `timers.queued`, `timers.pendingUndo`. `«Visto»` reconoce al momento. Sobrevive el «rastro» de 4 s en «Al día». |
| D-3 | **Ajustes se aplica al momento.** Sin botón «Guardar». | `SettingsViewModel` reestructurado: cada `onXChange` aplica; URL e intervalo validan en blur/Enter con línea de ayuda reservada. |
| D-4 | **Alcance: rediseño completo**, incluida la infra (autostart, wipe, reset, teclado). | Ocho fases. |
| D-5 | **Renombrar se quita del todo.** | Fuera `ImagesViewModel.renameImage`, `saveName(editing=…)`, `VersionPollingService.renameImage` **NO** se toca (es del núcleo y lo usa la persistencia; queda muerto pero no se borra en este rediseño — ver §11), la rama `editing` de `MainScreen`, el segundo uso de `NameDialog`, y sus tests. |
| D-6 | **Modo simulación se quita de Ajustes.** | Fuera el toggle y `onSimulationChange`. El modo simulación queda solo por variable de entorno en `Main.kt` (`SIMULATION_MODE`). `AppConfig.simulationMode` **se mantiene** (lo lee `Wiring.sourceFor`). |
| D-7 | **Troceado por capa transversal.** | Fase 1 tokens → 2 Bandeja → 3 fila abierta + quitar undo/cola → 4 Ajustes → 5 Aviso → 6 barra título → 7 infra → 8 teclado. |

---

## 4. Inventario de diferencias diseño → código

Referencia rápida. Cada fila se desarrolla en su fase.

### 4.1 Bandeja (`Bandeja.dc.html`, Blueprint 03–04)

| # | Diseño | Código actual | Fase |
|---|---|---|---|
| B-1 | Buscador replegado a icono de 32 dp; se expande a píldora **sin empujar el titular** | `OutlinedTextField` de 260 dp siempre visible bajo la fila del titular | 2 |
| B-2 | Chip «Ver todas» de la sección pendiente **aplica «Visto» a todas** | `HeaderChip("Ver todas") { onSearchChange("") }` — limpia el buscador | 2 |
| B-3 | Botón de la cabecera: etiqueta **«Agregar»** (sin «imagen») | «Agregar imagen» | 2 |
| B-4 | «Al día» plegada por defecto **por encima de 12 imágenes** | `okOpen` arranca siempre en `true` | 2 |
| B-5 | Entrada/salida de fila y de sección con **easing de énfasis**, 400 ms; sección que se vacía anima su alto | `expandVertically()`/`shrinkVertically()` por defecto, `animateItem()` sin spec | 2 |
| B-6 | Campana de silencio general también en la cabecera (32 dp) | Ya está | — |
| B-7 | Fila: clic en zona no-control **despliega el detalle en su sitio** | No existe; solo menú kebab | 3 |
| B-8 | «Visto» quita la fila al momento; novedades entran arriba solas animando su alto; **sin «Deshacer», sin «Ponerlas arriba»** | `UndoRow` + `QueuedBanner` + toda su maquinaria | 3 |
| B-9 | «Copiar referencia» copia **solo `origin`**; señal = icono a check 2 s, sin aviso | Copia `registry:version`; sin señal visible | 3 |
| B-10 | Menú de fila: Visto · Comprobar ahora · Copiar referencia · Silenciar avisos · ─ · Quitar de la lista. **Sin «Renombrar»** | Tiene «Renombrar» | 3 |
| B-11 | Pie: anchos 92 / 260 / 148 / 92, `tnum` | Ya está; literales sin nombre | 1 |

### 4.2 Ajustes (`Ajustes.dc.html`, Blueprint 04)

| # | Diseño | Código actual | Fase |
|---|---|---|---|
| A-1 | **Sin «Guardar».** Cada campo se aplica al momento | `Button(onSave)` + «Guardado» | 4 |
| A-2 | Dos columnas de tarjetas con borde (`surface`, `Radius.md`) | Columna única con scroll y `HorizontalDivider` | 4 |
| A-3 | Control segmentado `Sistema \| Claro \| Oscuro` con pista | `FilterChip` en fila | 4 |
| A-4 | Línea de ayuda de **16 dp reservados** bajo URL e intervalo; al fallar solo cambian texto, color y borde | Sin línea por campo; el error sale junto a «Guardar» | 4 |
| A-5 | Toggle **«Iniciar al encender el equipo»** (la ventana arranca minimizada) | No existe | 4 |
| A-6 | **Sin «Modo simulación»** | Toggle presente | 4 |
| A-7 | Cluster «Estado» con botón Detener/Iniciar + caption `comprobando · N s` a la derecha de la tarjeta Comprobación | `TextButton` + «● Activo» en fila | 4 |
| A-8 | Pie: `Restablecer ajustes` (neutro) + `Borrar datos locales` (contenedor de error) a la izquierda, `N imágenes vigiladas` (`tnum`) a la derecha | No existe ninguna de las dos acciones | 4 |
| A-9 | Chevron de vuelta en círculo de 32 dp `surfaceVariant` | `IconButton` pelado | 4 |
| A-10 | Duración de aviso: rango **3 – 30 s** | Campo sin límite superior declarado | 4 |
| A-11 | Intervalo: rango **5 – 3600 s** | Validación solo de formato en el view model; el mínimo lo pone el controlador | 4 |

### 4.3 Aviso (`Aviso.dc.html`, Blueprint 04)

| # | Diseño | Código actual | Fase |
|---|---|---|---|
| V-1 | Rejilla fija 340 × 78: `26 icono · 1fr texto · 84 acción · 24 cerrar`; 2.ª línea `meta(mono, ≤200) · cuenta atrás(64, tnum)`; barra 3 dp pegada abajo | `Column` suelta: título / cuerpo / «Ver» / barra; sin icono, sin cuenta atrás numérica, sin meta | 5 |
| V-2 | Cuatro `kind`: `nueva` / `saltadas` / `error` / `resumen`, con tono que tiñe icono y borde | Un solo estilo, `surfaceVariant` | 5 |
| V-3 | Cuerpo **nunca nombra la versión anterior**: `nueva` → «versión 1.0.24»; `saltadas` → «versión X, la más reciente» | `body = "$local → $remote"` — nombra la anterior | 5 |
| V-4 | **Máx. 3** tarjetas + una de **resumen** («y N novedades más», nombres en meta, «Ver todas») | `TOASTS_VISIBLES = 4`; el resto espera turno sin resumirse | 5 |
| V-5 | Cuenta atrás `«6 s» → «0 s»` en 64 dp, `tnum` | Solo la barra | 5 |
| V-6 | Acción `error` = «Reintentar»; `resumen` = «Ver todas» (trae la ventana y va a «Versión nueva») | Solo «Ver» | 5 |
| V-7 | El aviso de `error` es un tipo de primera clase | `notifyUpdates` solo recibe transiciones a PENDING | 5 (núcleo) |
| V-8 | `surface` (no `surfaceVariant`), sombra `Elevation.toast`, borde del tono | `surfaceVariant`, `tonalElevation = 6.dp` literal | 5 |

### 4.4 Barra de título (`Blueprint` bloque «Barra de título»)

| # | Diseño | Código actual | Decisión |
|---|---|---|---|
| T-1 | Campana (silencio general) + minimizar + maximizar + cerrar | Solo cerrar | **D-1: cerrar + campana**, sin min/max |
| T-2 | Alto 38 dp, hairline inferior de 1 dp | Sin alto fijo; `Surface` sin borde | 6 |
| T-3 | Campana → tachada sobre `surfaceVariant` cuando el silencio está activo | La campana vive en la cabecera de la lista, no aquí | 6 |

### 4.5 Tokens / tema

| # | Diseño | Código actual | Fase |
|---|---|---|---|
| K-1 | Etiqueta del estado pendiente: **«Versión nueva»** | `statusColors(PENDING).label = "Nueva versión"` | 1 |
| K-2 | Color «Recién movida» = «Contenedor de acento» (`#2B3557` / `#DDE3FF`) | `bumpColor()` lee `tertiaryContainer`, que no está definido en los esquemas | 1 |
| K-3 | `tnum` en toda cifra viva | Aplicado a medias | 1 |
| K-4 | Elevación con nombre (toast, diálogo) | `tonalElevation = 6.dp` y sombras literales | 1 |
| K-5 | Anchos reservados con nombre | `private val` repartidos por 3 ficheros | 1 |
| K-6 | Easing de énfasis para entradas/salidas de 400 ms | `Motion.EMPHASIS` existe como número; sin curva | 1 |

---

## 5. Cambios al núcleo (mínimos)

### 5.1 `application/NotificationPort` — método nuevo `notifyFailures`

**Motivo:** el aviso de `error` (V-7) es un `kind` de primera clase en el
diseño, y hoy el puerto solo entrega transiciones a PENDING.

```kotlin
interface NotificationPort {
    /** Recibe únicamente las imágenes que acaban de pasar a tener una versión pendiente. */
    fun notifyUpdates(updates: List<ImageState>)

    /**
     * Recibe las imágenes que acaban de pasar a ERROR: no lo estaban en el ciclo anterior.
     * Por defecto no hace nada, para que un notificador al que no le interese no tenga que
     * implementarlo.
     */
    fun notifyFailures(failures: List<ImageState>) {}
}
```

**En `VersionPollingService.notifyTransitions`:** ya existe
`transitionedInto(previous, current, ImageStatus.ERROR)`. Se añade un segundo
bucle protegido igual que el de `notifyUpdates`:

```kotlin
val newlyFailed = transitionedInto(previous, current, ImageStatus.ERROR)
if (newlyFailed.isNotEmpty()) {
    notifiers.forEach { notifier ->
        try {
            notifier.notifyFailures(newlyFailed)
        } catch (e: RuntimeException) {
            log.warn("Un notificador falló al recibir los fallos", e)
        }
    }
}
```

**No cambia** la regla de «primer ciclo no avisa» (`previous === PollSnapshot.EMPTY`
sigue cortando antes). **No cambia** `logChanges`, que ya registra los fallos.

**Tests:** `VersionPollingServiceTest` gana un caso — una imagen que pasa a
ERROR dispara `notifyFailures` una vez, y una que ya fallaba en el ciclo
anterior no. El fake de `NotificationPort` de los tests implementa el método
nuevo.

### 5.2 `application/AutostartPort` — puerto nuevo (fase 7)

```kotlin
/**
 * Arranque de la aplicación al iniciar sesión en el sistema. Es estado del sistema operativo,
 * no configuración de la app: no vive en AppConfig ni en config.json.
 */
interface AutostartPort {
    fun isEnabled(): Boolean
    fun setEnabled(enabled: Boolean)
}
```

- **`commonMain`:** solo la interfaz.
- **`jvmMain`:** `WindowsAutostart : AutostartPort` que escribe
  `HKCU\Software\Microsoft\Windows\CurrentVersion\Run`, clave `ImageWatch`, con
  la ruta del ejecutable instalado + `--minimized`. Implementación con
  `java.util.prefs.Preferences` (nodo `Windows/CurrentVersion/Run`) o, si eso no
  resulta fiable en el runtime de jlink, con `Runtime.exec("reg", …)`. La
  decisión concreta se toma en la tarea, con un test de `jvmTest` que escribe y
  relee una clave de usuario de prueba.
- **`ponytail:`** el `actual` es solo Windows. Si algún día hay otro objetivo,
  su `actual` es un no-op hasta que exista un instalador para él.
- **Wiring:** `Wiring` construye `WindowsAutostart` y lo expone. El toggle de
  Ajustes llama a `port.setEnabled(...)` directamente y lee `port.isEnabled()`
  al montar la pantalla; **no pasa por `AppConfig` ni por `applyConfig`**.

---

## 6. Fase 1 — Tokens, color, movimiento

**Objetivo:** consolidar valores visuales dispersos y corregir dos colores.
**Cambio de comportamiento: ninguno.** Los tests visuales existentes siguen
verdes.

### 6.1 `ui/theme/Tokens.kt`

**`Elevation` (nuevo):**

```kotlin
/** Sombra y elevación tonal. Categoría propia porque no es ni espacio ni forma. */
object Elevation {
    val card = 0.dp     // las tarjetas de fila no se elevan: se separan con 8 dp de hueco
    val toast = 6.dp    // la tarjeta de aviso, sobre cualquier ventana
    val dialog = 24.dp  // el velo + la sombra del diálogo de confirmación
}
```

Reemplaza `tonalElevation = 6.dp` en `ToastWindow.ToastCard` y la sombra
literal del diálogo (fase 7).

**`Motion` (ampliado):**

```kotlin
object Motion {
    const val QUICK = 220
    const val NORMAL = 320
    const val EMPHASIS = 400
    const val PULSE = 700
    const val BUMP = 600

    /** Curva de énfasis para entradas y salidas de fila y de sección (Blueprint 06). */
    val emphasisEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
}
```

**`Dwell` (reducido):** se elimina `UNDO_MILLIS`. Su único consumidor
(`UndoRow`, `requestAcknowledge`) desaparece en la fase 3, pero la constante se
quita ya porque marcar «Visto» pasa a ser inmediato y dejar el número invita a
recablearlo.

> Riesgo de orden: si la fase 1 se mergea antes que la 3, `Dwell.UNDO_MILLIS`
> todavía tiene usos. **Mitigación:** la fase 1 no borra la constante; la
> renombra a `@Deprecated` y la fase 3 la elimina. O se acepta que la fase 3
> depende de la 1 y la eliminación va en la 3. **Elegido:** la eliminación de
> `Dwell.UNDO_MILLIS` va en la fase 3, junto con su consumidor. La fase 1 solo
> añade `emphasisEasing` y `Elevation`.

**`Layout` (nuevo):**

```kotlin
/**
 * Anchos y altos reservados que el diseño fija para que cambiar de estado no desplace a un
 * vecino (Blueprint 1i). Ninguno es un paso de Space: son huecos de composición.
 */
object Layout {
    // Fila de la bandeja
    val rowAge = 104.dp        // columna de antigüedad + etiqueta
    val rowSkip = 44.dp        // contador «+N» de versiones saltadas
    val rowPill = 104.dp       // píldora de versión / de estado
    val rowChip = 88.dp        // chip «Visto» / «Reintentar» / «En silencio»
    val rowKebab = 30.dp       // icono de más acciones
    val rowPadH = 14.dp        // relleno horizontal de la tarjeta de fila
    val rowPadV = 11.dp        // relleno vertical de la tarjeta de fila

    // Pie de la ventana
    val footState = 92.dp      // «Activo» / «Detenido» / «Sin conexión»
    val footNote = 260.dp      // nota de última comprobación
    val footMuted = 148.dp     // píldora «Avisos silenciados»

    // Cabeceras
    val sectionCounter = 20.dp // contador de sección (9 → 10)
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

    // Ajustes
    val settingsField = 132.dp  // campo numérico (intervalo, duración)
    val settingsHelpLine = 16.dp
    val dialogWidth = 420.dp
    val dialogConfirmMin = 132.dp
}
```

Los `private val` que se retiran a `Layout`: `AGE_WIDTH`, `SKIP_WIDTH`,
`PILL_WIDTH`, `CHIP_WIDTH`, `KEBAB_WIDTH`, `CARD_PADDING_H`, `CARD_PADDING_V`
(`ImageRow.kt`); `FOOT_STATE_WIDTH`, `FOOT_NOTE_WIDTH`, `FOOT_MUTED_WIDTH`,
`SEARCH_WIDTH` (`ImagesScreen.kt`); `FIELD_WIDTH`, `SLIDER_WIDTH`
(`SettingsScreen.kt`); `TOAST_WIDTH`, `MARGIN` (`ToastWindow.kt`).
`LAYER_HEIGHT` **se queda** en `ToastWindow` — es una compensación de
composición de la ventana sin decoración, no una medida de diseño.

### 6.2 `ui/theme/Colors.kt`

**K-1:** `statusColors(ImageStatus.PENDING, …).label`: `"Nueva versión"` →
`"Versión nueva"` en las dos ramas (oscuro y claro). `highlightPill` sigue
siendo un alias de `statusColors(PENDING, …)`, sin cambio.

**K-2:** añadir a `DarkColors` y `LightColors`:

```kotlin
// Dark
tertiaryContainer = Color(0xFF2B3557),   // = primaryContainer: «recién movida» y «Ver» comparten mensaje
onTertiaryContainer = Color(0xFFD9E0FF),
// Light
tertiaryContainer = Color(0xFFDDE3FF),
onTertiaryContainer = Color(0xFF1B2A5C),
```

`bumpColor()` en `ImageRow` ya lee `MaterialTheme.colorScheme.tertiaryContainer`;
hoy cae al morado por defecto de Material. Con esto usa la paleta de acento, que
es lo que pide el Blueprint («Fondo del color de contenedor de acento que se
disuelve en 4 s»).

> Alternativa considerada: apuntar `bumpColor()` a `primaryContainer` y no
> añadir `tertiary`. Rechazada: `primaryContainer` ya lo usan el resaltado de
> «Ver» (`emphasisModifier` → `SENALADA`), el `QueuedBanner` y varios chips;
> compartir el token hace que retocar uno mueva los otros. `tertiary` como
> alias explícito deja la puerta abierta a separarlos sin tocar consumidores.

### 6.3 `tnum` — auditoría (K-3)

Helper en `ui/theme`:

```kotlin
/** Cifras tabulares: mismo ancho por dígito, para que un número que cambia en vivo no descuadre. */
val TabularNums = TextStyle(fontFeatureSettings = "tnum")

@Composable
fun Modifier.tabularNums(): Modifier = this  // marcador; el estilo va en el Text, no en el Modifier
```

En la práctica se aplica como `style = TabularNums` o
`fontFeatureSettings = "tnum"` en cada `Text` de:

- **Antigüedad** de fila (`ImageRow.AgeCell`) — ya debería; verificar.
- **Contador de sección** (`SectionHeader`, `OkSectionHeader`).
- **Titular y subtítulo** de la cabecera (`ImagesScreen.Header`: «N imágenes
  que atender», «de N vigiladas · cada N s»).
- **Pie:** «N vigiladas», la nota de última comprobación.
- **Píldora de versión** (`ImageRow.SkipAndPillCell`) — mono ya, `tnum` refuerza.
- **Contador «+N»** de versiones saltadas.
- **Cuenta atrás** del aviso (fase 5).
- **Volumen** («70 %») e **intervalo** en Ajustes (fase 4).
- **Diálogo de confirmación:** líneas de «lo que se pierde» con cifras (fase 7).

### 6.4 Tests de la fase 1

- `ColorsTest` gana: `statusColors(PENDING, dark).label == "Versión nueva"`;
  `DarkColors.tertiaryContainer` y `LightColors.tertiaryContainer` son los hex
  esperados.
- No hay test nuevo para `Layout`/`Elevation`/`emphasisEasing` — son
  constantes. `spotlessCheck` valida el estilo.

---

## 7. Fase 2 — Bandeja (armazón de la lista)

**Ficheros:** `ui/images/ImagesScreen.kt` (y su firma, propagada a
`Main.MainScreen`).
**Cambios de comportamiento:** buscador replegable (B-1), «Ver todas» reconoce
(B-2), umbral de plegado de «Al día» (B-4).

### 7.1 Cabecera (`ImagesScreen.Header`)

**Buscador replegable (B-1):**

- Estado local nuevo: `var searchExpanded by rememberSaveable { mutableStateOf(state.search.isNotEmpty()) }`.
  Arranca expandido si ya hay término (p. ej. tras `highlight()`, que limpia el
  filtro — en ese caso arranca replegado).
- Replegado: `IconButton` de 32 dp con `AppSvg.SEARCH`. Al pulsarlo,
  `searchExpanded = true` y se pide el foco al campo.
- Expandido: el `OutlinedTextField` actual, animando su ancho de 32 dp a
  `Layout` (nuevo `searchPill = 260.dp`) con `Motion.QUICK`. Al perder el foco
  **y** estar vacío, se repliega.
- La fila de controles pasa de dos filas (`Row` + `OutlinedTextField` debajo) a
  **una sola**: `[titular/subtítulo weight(1f)] [buscar 32] [campana 32]
  [ajustes 32] [Agregar 34]`. El botón «Comprobar todas» (18 dp) se mantiene
  entre el titular y buscar, como en el Blueprint («Buscar (32) · campana (32) ·
  ajustes (32) · «Agregar»» — «comprobar todas» va con el grupo de la
  izquierda).

  > Matiz: el Blueprint lista en la cabecera «Buscar · campana · ajustes ·
  > Agregar» y no menciona «comprobar todas» ahí. El código actual sí lo tiene.
  > **Se conserva** «comprobar todas» como icono de 18 dp inmediatamente a la
  > izquierda de «buscar»: es una acción sobre la lista entera, útil, y ya
  > estaba. Queda anotado como desvío consciente del Blueprint.

- Etiqueta del botón: **«Agregar»** (B-3), icono `PLUS` 14 dp, alto 34, radio
  `Radius.pill`.
- El titular expandido usa `tnum` (K-3).

### 7.2 Secciones

**«Ver todas» reconoce (B-2):**

```kotlin
@Composable
private fun PendingSectionHeader(count: Int, onAcknowledgeAll: () -> Unit) {
    val palette = statusColors(ImageStatus.PENDING, LocalIsDark.current)
    SectionHeader(palette.foreground, "Versión nueva", count) {
        HeaderChip("Ver todas", onClick = onAcknowledgeAll)
    }
}
```

`ImagesScreen` deja de pasarle `onSearchChange` a esta cabecera; le pasa
`onAcknowledgeAll`. El chip «Ver todas» del Blueprint lleva el icono de doble
check (`AppSvg.CHECK_ALL`), en acento (`primaryContainer` / `onPrimaryContainer`).

**Umbral de «Al día» (B-4):**

```kotlin
var okOpen by rememberSaveable { mutableStateOf(okRows.size <= 12) }
```

Con el problema de que `okRows` se calcula dentro del `Column`, después del
`rememberSaveable`. Solución: mover el cálculo de `pendingRows`/`errorRows`/
`okRows` arriba del `var okOpen`, o usar
`rememberSaveable { mutableStateOf<Boolean?>(null) }` y resolver a
`okOpen ?: (okRows.size <= 12)` en el primer render. **Elegido:** subir el
cálculo de las tres listas antes del `var okOpen` — ya no dependen de `okOpen`
(solo la construcción de `entries` lo usa).

**Easing de énfasis (B-5):**

- `AnimatedVisibility` de las secciones y el `animateItem()` de las filas pasan
  a `tween(Motion.EMPHASIS, easing = Motion.emphasisEasing)` para
  `expandVertically`/`shrinkVertically` y para
  `fadeInSpec`/`placementSpec`/`fadeOutSpec` de `animateItem`.
- Sección que se vacía: al quedar `okRows.isEmpty()` (o pending/error), la
  cabecera correspondiente sale con `shrinkVertically(tween(Motion.EMPHASIS,
  easing = emphasisEasing))`. Ya lo hace `AnimatedVisibility` implícito por
  `entries`; se le fija el spec.

### 7.3 Pie (`Footer`)

Sin cambio de estructura. Solo:

- Literales `FOOT_*_WIDTH` → `Layout.foot*`.
- `tnum` en «N vigiladas» y en la nota.

### 7.4 Firma nueva de `ImagesScreen`

Tras fases 2 y 3, la firma queda (se marcan ⊖ los parámetros que se van en la
fase 3 y ⊕ los que entran):

```kotlin
@Composable
fun ImagesScreen(
    state: ImagesUiState,
    mutedAll: Boolean,
    onSearchChange: (String) -> Unit,
    onAdd: () -> Unit,
    onAcknowledge: (String) -> Unit,          // ahora → viewModel::acknowledge (fase 3)
  ⊖ onUndoAcknowledge: (String) -> Unit,
    onAcknowledgeAll: () -> Unit,
    onRefresh: (String?) -> Unit,
  ⊖ onEdit: (String) -> Unit,
    onDelete: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onToggleMuteAll: () -> Unit,
    onToggleSilence: (String) -> Unit,
  ⊖ onPromoteQueued: () -> Unit,
  ⊕ onToggleExpand: (String) -> Unit,
  ⊕ onCopyReference: (String) -> Unit,        // copia origin + señal de check 2 s
)
```

### 7.5 Tests de la fase 2

- `ImagesViewModelTest`: caso nuevo — «Ver todas» de la sección pendiente llama
  a `acknowledgeAll` (no toca `search`).
- El plegado por umbral es estado local de Compose; se cubre en el repaso visual
  (no hay test de Compose UI en el proyecto).

---

## 8. Fase 3 — Fila abierta y retirada de Undo / cola

**Ficheros:** `ui/images/ImageRow.kt`, `ui/images/ImagesScreen.kt`,
`ui/images/ImagesViewModel.kt`, `ui/theme/Tokens.kt` (quitar
`Dwell.UNDO_MILLIS`), `desktopApp/.../Main.kt`, tests.

### 8.1 Retirada (D-2, D-5)

**`ImagesViewModel`:**

| Se elimina | Notas |
|---|---|
| `requestAcknowledge`, `undoAcknowledge` | `onAcknowledge` de la pantalla pasa a llamar a `acknowledge` directamente |
| `promoteQueued` | — |
| `newlyPending` (privado) | — |
| `undoJobs: MutableStateFlow<Map<String, Job>>` | y su limpieza en `close()` |
| `TimerState.queued`, `TimerState.pendingUndo` | `TimerState` queda: `highlighted`, `bumped`, `checking`, `trace` |
| En `derive()`: cálculo de `encoladas`, `visibles`, `queuedCount` | `rows` = `all` filtrado solo por búsqueda |
| `ImagesUiState.queuedCount` | — |
| `ImageRowState.pendingUndo` | — |
| `onSnapshot`: bloque `if (nuevasPendientes.isNotEmpty())` y la llamada a `newlyPending` | Se conserva `novedadesSobrePendientes` → `bumped` (el latido de «otra más») |
| `renameImage`, la rama `editing` de `saveName` | `saveName` queda solo con `editing = null`; se puede simplificar a `addImage` directo |

**`ImageRow.kt`:** se elimina `UndoRow` entero y su rama en `ImagesScreen`
(`if (entry.row.pendingUndo) { UndoRow(...) }`). Se elimina «Renombrar» del
`RowMenu` (el `DropdownMenuItem` con `AppSvg.PENCIL` y su `onEdit`).

**`ImagesScreen.kt`:** se elimina `QueuedBanner` y su `AnimatedVisibility`; el
parámetro `onPromoteQueued`; el parámetro `onUndoAcknowledge`; el parámetro
`onEdit`.

**`Main.kt`:** `MainScreen` pierde `editing`, el segundo `NameDialog`,
`onEdit`, `onUndoAcknowledge`, `onPromoteQueued`. `onAcknowledge` pasa de
`viewModel::requestAcknowledge` a `viewModel::acknowledge`.

**`Tokens.kt`:** se elimina `Dwell.UNDO_MILLIS`.

**Comportamiento resultante:** «Visto» (en fila, menú o aviso) llama a
`acknowledge(name)` → `service.acknowledge` → la fila sale de «Versión nueva» y
entra en «Al día» animando su alto con `emphasisEasing`; se deja el «rastro» de
4 s (`leaveTrace`, `Dwell.TRACE_MILLIS`) en la cabecera plegada de «Al día».
Las novedades que llegan durante un ciclo entran arriba directamente (ya lo hace
`derive()` en cuanto `queued` desaparece) animando su alto.

**Contrapartida asumida (Blueprint 1f):** la lista se recoloca bajo el cursor si
llega una novedad mientras el operador apunta a una fila. Se mitiga con la
animación de 400 ms y el rastro; no hay estados intermedios que mantener.

### 8.2 Fila abierta (B-7, Blueprint «Fila (clic)» + D2 de `Bandeja de novedades`)

**Estado:**

```kotlin
data class ImagesUiState(
    // …
    val expandedRow: String? = null,
)
```

```kotlin
// ImagesViewModel
fun toggleExpand(name: String) {
    mutableState.update { it.copy(expandedRow = if (it.expandedRow == name) null else name) }
}
```

Estado puro, sin timers, sin tocar el snapshot. Un solo dueño: abrir una cierra
la anterior (lo hace el `if` de arriba al comparar con `name`).

**Render (`ImageRow.kt`):**

- `RowCard` recibe `expanded: Boolean` y `onToggleExpand: () -> Unit`. El `Row`
  interior de la tarjeta se envuelve en un `Column`:
  - Banda normal (nombre/origen · antigüedad · +N/píldora · acciones) — con
    `Modifier.clickable(onClick = onToggleExpand)` sobre la zona que **no** es un
    control. Los controles (`ActionChip`, `RowMenu`, y en la fila abierta las
    píldoras del pie) consumen el clic.
  - Si `expanded`: `HorizontalDivider(color = hair)` + banda de detalle +
    banda de acciones. Todo dentro de `animateContentSize(tween(Motion.EMPHASIS,
    easing = emphasisEasing))`.
- **Banda de detalle:** `Row`/`Grid` de 4 columnas `repeat(4,
  minmax(0,1fr))`, cada una `[etiqueta 11 sp terciaria mayúsculas] [valor 12 sp,
  ellipsis, tnum]`:
  - `Nombre` → `row.name`
  - `Origen` → `row.registry`
  - `Última versión` → `row.remote` si `!= "—"`, si no `row.local`
  - `Detectada` → `row.detail` (ya es «hoy, 09:12 · hace 2 h 14 min» o el
    mensaje de error). Para la fila abierta el Blueprint quiere fecha +
    antigüedad; `detail` ya lo trae para el caso normal. Si `row.error != null`,
    la columna «Detectada» muestra el motivo.
- **Banda de acciones:** `Row` con padding `0 14 14`:
  - Izquierda, en píldoras `surfaceVariant` de 6/14: **«Comprobar ahora»**
    (`onRefresh(name)`), **«Copiar referencia»** (`onCopyReference(name)`),
    **«Silenciar avisos»** (`onToggleSilence(name)`, con icono `BELL_OFF` y
    etiqueta que alterna a «Reactivar avisos» si `row.muted`).
  - `Spacer(weight(1f))`.
  - Derecha, píldora `errorContainer` / `onErrorContainer`: **«Quitar de la
    lista»** (`onDelete(name)`), icono `TRASH`.
- El kebab (`RowMenu`) **se conserva** en todas las filas (Blueprint lo dibuja a
  30 dp en la fila cerrada). Contenido del menú = las mismas acciones que la
  banda de la fila abierta, más «Visto» cuando aplica. Es redundante a
  propósito: el menú funciona sin abrir la fila.

### 8.3 «Copiar referencia» (B-9)

- La acción copia **`row.registry`** (el `origin`), no `registry:version`.
- Señal: el icono de la entrada (en el menú y en la banda de acciones) cambia a
  `AppSvg.CHECK` durante 2 s. Estado local del composable:
  `var copiedAt by remember { mutableStateOf(0L) }` + `LaunchedEffect(copiedAt)
  { delay(2000); ... }`. No hay aviso, no hay toast, no hay sonido nuevo (el
  Blueprint: «el cambio de icono … durante 2 s es la única señal»).
- `LocalClipboardManager.current.setText(AnnotatedString(row.registry))`.

### 8.4 Tests de la fase 3

- `ImagesViewModelTest`: **borrar** los casos de undo (`requestAcknowledge`,
  `undoAcknowledge`, ventana de deshacer, versión que cambia durante la ventana)
  y de cola (`promoteQueued`, `queuedCount`, `newlyPending`). **Añadir**: «Visto»
  llama a `acknowledge` y la fila cambia de sección; `toggleExpand` alterna
  `expandedRow` y abrir otra cierra la anterior; el «rastro» sigue funcionando
  tras un `acknowledge` directo.
- Borrar los casos de `renameImage` en `ImagesViewModelTest`.
- `historias-de-usuario.md`: retirar H-\* de undo, cola y renombrar; añadir
  H-\* de fila abierta y de «copiar copia solo el origen».

---

## 9. Fase 4 — Ajustes (aplicar al momento + dos columnas)

**Ficheros:** `ui/settings/SettingsViewModel.kt`,
`ui/settings/SettingsScreen.kt`, `desktopApp/.../Main.kt` (`SettingsPane`).

### 9.1 `SettingsViewModel` — aplicar al momento (D-3, A-1)

**`SettingsUiState`:**

```kotlin
data class SettingsUiState(
    val remoteUrl: String,
    val intervalSeconds: String,
    val ignoreSslErrors: Boolean,      // ⊖ simulationMode
    val theme: ThemePreference,
    val toastsEnabled: Boolean,
    val toastSeconds: String,
    val soundsEnabled: Boolean,
    val soundVolume: Float,
    val mutedAll: Boolean,
    val autostart: Boolean,            // ⊕ leído del AutostartPort
    // Errores por campo, cada uno en su línea de ayuda reservada:
    val urlError: String? = null,      // ⊕
    val intervalError: String? = null, // ⊕
    val toastError: String? = null,    // ⊕
    // ⊖ error: String?  (global)
    // ⊖ savedAt: Long
)
```

**Constructor:** `SettingsViewModel(current: AppConfig, private val apply:
(AppConfig) -> String?, private val autostart: AutostartPort)`.

**Métodos:**

- **Aplican al instante** (toggle/enum/slider): `onIgnoreSslChange`,
  `onThemeChange`, `onToastsChange`, `onSoundsChange`, `onVolumeChange`,
  `onMutedAllChange` → `edit { it.copy(...) }` **y** `apply(configFrom(newState))`.
  Si `apply` devuelve mensaje (fallo de E/S), se muestra… ¿dónde? Estos campos
  no tienen línea de ayuda. **Decisión:** un fallo de E/S al guardar volumen es
  excepcional; se registra en el log (ya lo hace `applyConfig`) y el toggle
  revierte visualmente al valor anterior. Sin banda de error global.
- **`onAutostartChange(value: Boolean)`** → `autostart.setEnabled(value)` +
  `edit { it.copy(autostart = value) }`. No pasa por `apply`.
- **Validan en commit** (blur / Enter):
  - `onUrlChange(value)` solo actualiza el texto y limpia `urlError`.
  - `onUrlCommit()` → construye el `AppConfig` candidato y llama a `apply`; si
    devuelve mensaje, `urlError = message`; si no, `urlError = null`. (El
    Blueprint: «Se comprueba al salir del campo o con Enter.»)
  - `onIntervalChange` / `onIntervalCommit()` — parsea a `Long`, comprueba
    `in 5..3600` (A-11). Fuera de rango → `intervalError = "El intervalo mínimo
    es 5 s."` / `"El intervalo máximo es 3600 s."`. En rango → `apply`.
  - `onToastSecondsChange` / `onToastSecondsCommit()` — `in 3..30` (A-10). Fuera
    → `toastError = "Entre 3 s y 30 s."`.
- Se elimina `save()`.

**Regla de validación:** el mínimo del intervalo lo sigue teniendo el
`PollingController` (núcleo); aquí se comprueba **antes** solo para poder pintar
la línea de ayuda sin lanzar una consulta ni reiniciar el sondeo. Es duplicación
consciente y acotada (dos números), anotada en el KDoc del método.

### 9.2 `SettingsScreen` — layout de dos columnas (A-2 … A-10)

```
Column(fillMaxSize)
├─ TitleRow: [chevron en círculo 32 surfaceVariant] "Ajustes" 16/700 + subtítulo "Los cambios se aplican al momento."
├─ Row(weight(1f), grid 2 col, gap 16, align start)
│  ├─ Column (izquierda)
│  │  ├─ Card "Origen":        URL (mono) + helpLine(urlError ?: "Se comprueba al salir del campo o con Enter.")
│  │  │                        + Toggle "Ignorar errores de TLS" / "Acepta certificados que no se pueden validar"
│  │  └─ Card "Comprobación":  Toggle "Iniciar al encender el equipo" / "La ventana arranca minimizada"
│  │                           + Row [ campo Intervalo (132) + helpLine ]  [ "Estado": botón Detener/Iniciar + caption "comprobando · N s" ]
│  └─ Column (derecha)
│     ├─ Card "Apariencia":    Segmented [Sistema | Claro | Oscuro] + caption "Ahora mismo el sistema pide tema X."
│     └─ Card "Avisos":        Toggle "Silenciar todos los avisos" / "No cambia el silencio de cada imagen"
│                              + campo Duración (132) + helpLine "Entre 3 s y 30 s."
│                              + Toggle "Sonidos" / "Uno por tanda, no uno por tarjeta"
│                              + Row [ "Volumen" 60 ] [ slider ] [ "70 %" 44 tnum ]
└─ FootRow: [ pill "Restablecer ajustes" surfaceVariant ] [ pill "Borrar datos locales" errorContainer ]  … weight …  [ "N imágenes vigiladas" tnum ]
```

**Componentes nuevos en `SettingsScreen.kt`:**

- `SettingsCard(title: String, content)`: `Surface(color = surface, border =
  BorderStroke(1.dp, hair), shape = RoundedCornerShape(Radius.md))` con padding
  16 y `Column(spacedBy(Space.md))`. Reemplaza `Section` (que usaba
  `HorizontalDivider`).
- `HelpLine(text: String, error: Boolean)`: `Box(Modifier.height(Layout.settingsHelpLine))`
  con `Text` de 11 sp, color `error → errt` / `mutedText`. **Altura reservada
  siempre** (A-4): al fallar solo cambian texto y color; nada baja.
- `Segmented(options, selected, onSelect)`: `Row` en `Surface(surfaceVariant,
  pill)` con padding 3; cada segmento `weight(1f)`, el seleccionado sobre
  `primaryContainer` (`accc`/`acct`), texto 12/600 centrado. Sustituye los
  `FilterChip`.
- El campo numérico: un `Box` de 34 dp de alto, borde `Radius.sm`, con el número
  (`tnum`) a la izquierda y la unidad (`s`) fija a la derecha dentro del campo.
  Borde `errt` cuando el campo de ese error `!= null`. (El Blueprint dibuja un
  campo custom, no un `OutlinedTextField`; se puede seguir usando
  `BasicTextField` con esa decoración para conservar edición real.)

**«Iniciar al encender el equipo» (A-5):** el toggle llama a
`onAutostartChange`. El `actual` real llega en la fase 7; en la fase 4 el puerto
puede ser un stub en memoria para no bloquear el layout, **o** se ordena la fase
7 antes de la 4 para esta pieza. **Elegido:** el `AutostartPort` (interfaz +
`actual` Windows) entra en la fase 4 junto con su toggle — es pequeño y evita un
stub. La fase 7 se queda con wipe + reset + `ConfirmDialog`.

**Pie con «Restablecer» y «Borrar» (A-8):** los dos botones abren un
`ConfirmDialog` (fase 7). Hasta que la fase 7 aporte el diálogo y las acciones,
la fase 4 deja los botones **deshabilitados con tooltip «disponible en la
siguiente fase»** — o, mejor, se mueve el `ConfirmDialog` + las dos acciones a
la fase 4 y la fase 7 se queda solo con autostart. **Elegido (revisado):**

> **Reordenación de fases.** Autostart, wipe, reset y `ConfirmDialog` son todos
> «infra que Ajustes necesita». Se juntan en la fase 4. La fase 7 desaparece
> como fase separada y su contenido se reparte: `AutostartPort`, `wipeLocalData`,
> `resetSettings`, `ConfirmDialog` → **fase 4**. Queda el plan en **siete
> fases**. Ver §14.

### 9.3 `SettingsPane` (`Main.kt`)

- `SettingsViewModel` recibe también `wiring.autostart`.
- Se le pasan los nuevos `onUrlCommit` / `onIntervalCommit` /
  `onToastSecondsCommit` / `onAutostartChange` / `onReset` / `onWipe`.
- `onReset` → abre `ConfirmDialog` de restablecer → `wiring.resetSettings()`.
- `onWipe` → abre `ConfirmDialog` de borrar → `wiring.wipeLocalData()`.
- Se quita `onSave` y `onSimulationChange`.
- La `key` del `remember { SettingsViewModel(...) }`: hoy no tiene clave a
  propósito (guardar reconstruía el VM y borraba la confirmación). Sin «Guardar»
  y con aplicar-al-momento, `config` cambia en cada edición y **sí** conviene que
  el VM no se reconstruya en cada cambio: se mantiene sin clave. El VM lee
  `config` una vez al montar; los cambios posteriores los produce él mismo.

### 9.4 `Wiring` — `wipeLocalData` y `resetSettings`

```kotlin
/** Borra todo lo que la app guarda en este equipo y vuelve al estado de primer arranque. */
fun wipeLocalData() {
    controller.stop()
    listOf("config.json", "tracked-images.json", "silenced-images.json")
        .forEach { FileSystem.SYSTEM.delete(seedFile.sibling(it), mustExist = false) }
    FileSystem.SYSTEM.delete(loaded.stateFile.toPath(), mustExist = false)
    // Re-siembra y recarga: mismo camino que el init.
    val fresh = configFromEnvironment()
    configStore = JsonConfigStore(seedFile.sibling("config.json"), fresh)
    config.value = configStore.load()
    // …reconstruir trackedImages, silencedImages, state, source, service, controller…
    controller.start()
}
```

**Riesgo:** `Wiring` fue diseñado para construirse una vez en `init`. Rehacer
sus campos en caliente es delicado (`service` tiene oyentes registrados; el
`ImagesViewModel` guarda una referencia a `service`). **Alternativa más segura:**
`wipeLocalData()` borra los ficheros y luego **fuerza el reinicio de la
aplicación** (`exitApplication()` + relanzar), que es lo que hace un «restablecer
de fábrica» en la mayoría de apps de escritorio. Dado que la app reside en la
bandeja y arranca oculta, el parpadeo es mínimo. **Elegido:** borrar + reiniciar.
Se implementa con un `Runtime.exec` del propio ejecutable y `exitApplication()`,
o —más simple— dejando la app cerrada y avisando «Datos borrados. Vuelve a abrir
ImageWatch». La tarea decide; el spec fija que **no se recablea `Wiring` en
caliente**.

```kotlin
/** Ajustes a valores de fábrica. Imágenes vigiladas y versión vista: intactas. */
fun resetSettings(): String? {
    val factory = configFromEnvironment()
    return applyConfig(
        config.value.copy(
            remoteUrl = factory.remoteUrl,
            pollInterval = factory.pollInterval,
            ignoreSslErrors = factory.ignoreSslErrors,
            theme = factory.theme,
            toastsEnabled = factory.toastsEnabled,
            toastDuration = factory.toastDuration,
            soundsEnabled = factory.soundsEnabled,
            soundVolume = factory.soundVolume,
            mutedAll = factory.mutedAll,
            // stateFile, imageNames, simulationMode: se conservan
        ),
    )
}
```

`resetSettings` **sí** es seguro en caliente: `applyConfig` ya está pensado para
aplicar configuración sobre la app viva.

### 9.5 `ConfirmDialog` (`ui/dialogs/`)

Sustituye a `DeleteDialog`; `NameDialog` se queda (alta).

```kotlin
@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    lost: List<String>,          // «lo que se pierde», un punto de 4 dp por línea
    confirmLabel: String,        // «Borrar todo» / «Restablecer» / «Eliminar»
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
)
```

- Velo `rgba(0,0,0,0.55)`, `Elevation.dialog`.
- Ancho `Layout.dialogWidth` (420).
- Cabecera: círculo de 32 dp `errorContainer` con `AppSvg.ALERT` 16 dp +
  título 13/700.
- Cuerpo 12 sp + caja `background` `Radius.sm` padding 12 con la lista `lost`
  (bullet 4 dp + texto `tnum`).
- Línea reservada de 16 dp: «No se puede deshacer.»
- Acciones a la derecha: «Cancelar» (`surfaceVariant`) + confirmación
  (`errorContainer`, `min-width Layout.dialogConfirmMin`).

**`DeleteDialog` → `ConfirmDialog`:** la llamada de `MainScreen` para «Quitar de
la lista» pasa a:

```kotlin
ConfirmDialog(
    title = "Quitar $name de la lista",
    body = "Deja de vigilarse y desaparece. Las imágenes seguirán en el registry.",
    lost = listOf("El seguimiento de $name", "La versión vista de $name"),
    confirmLabel = "Quitar de la lista",
    onDismiss = { deleting = null },
    onConfirm = { viewModel.removeImage(name); deleting = null },
)
```

### 9.6 Tests de la fase 4

- `SettingsViewModelTest` reescrito:
  - toggles/tema/volumen aplican al instante (el fake `apply` recibe el
    `AppConfig` esperado sin llamar a un `save`).
  - `onIntervalCommit` con 4 → `intervalError` puesto, `apply` **no** llamado.
  - `onIntervalCommit` con 10 → `apply` llamado, `intervalError` nulo.
  - `onToastSecondsCommit` con 40 → `toastError` puesto.
  - `onUrlCommit` con URL sin HTTPS → `urlError` = mensaje del fake.
  - `onAutostartChange(true)` → `autostart.setEnabled(true)` en el fake.
  - borrar los casos de `save()` y `simulationMode`.
- `resetSettings` — test en `ImagesViewModelTest`/`WiringTest` (si existe) o un
  test nuevo del `applyConfig` con la config de fábrica.
- `AutostartPort`: fake en `commonTest`; el `actual` Windows con un test de
  `jvmTest` que escribe y relee una clave `HKCU\...\Run\ImageWatch-test`.

---

## 10. Fase 5 — Aviso

**Ficheros:** `ui/toast/ToastState.kt`, `ui/toast/ToastNotificationPort.kt`,
`ui/toast/ToastWindow.kt` (`jvmMain`), `application/NotificationPort.kt` +
`application/VersionPollingService.kt` (§5.1).

### 10.1 Modelo `Toast`

```kotlin
enum class ToastKind { NUEVA, SALTADAS, ERROR, RESUMEN }

data class Toast(
    val id: Long,
    val kind: ToastKind,
    val title: String,
    val sub: String,          // ⊕ segunda línea del bloque de texto (antes «body»)
    val meta: String,         // ⊕ origen en mono, ≤ Layout.toastMetaMax
    val action: String,       // ⊕ «Visto» / «Reintentar» / «Ver todas»
    val imageName: String?,
    val leaving: Boolean = false,
    val progress: Float = 1f,
    val durationMillis: Long = 0L,
)
```

**`toastOf` (nunca nombra la versión anterior, V-3):**

```kotlin
private fun toastOf(image: ImageState, millis: Long) = Toast(
    id = sequence.updateAndGet { it + 1 },
    kind = ToastKind.NUEVA,
    title = "${image.name} · versión nueva",
    sub = "versión ${image.remote?.value ?: ABSENT}",
    meta = image.registry,
    action = "Visto",
    imageName = image.name,
    durationMillis = millis,
)
```

- **`SALTADAS`:** hoy el núcleo no expone las publicaciones intermedias
  (`skippedCount` es «Derivado» y futuro en el Blueprint 06). **En esta fase
  todos los avisos de versión nueva son `NUEVA`.** El `kind` `SALTADAS` queda
  definido y renderizable, pero sin productor hasta que el núcleo cuente saltos.
  Anotado como límite conocido.
- **`ERROR`:** nuevo `fun showFailures(failures: List<ImageState>)` en
  `ToastState`, análogo a `show`:

  ```kotlin
  fun showFailures(failures: List<ImageState>) {
      // title = "$name · no se pudo verificar"
      // sub   = image.error ?: "sin detalle"   (p. ej. "HTTP 503")
      // meta  = image.registry
      // action = "Reintentar"
      // kind = ERROR
  }
  ```

  `ToastNotificationPort.notifyFailures(failures)` la llama, respetando
  `config().mutedAll` y `config().toastsEnabled` igual que `notifyUpdates`. El
  sonido para fallos ya lo emite `ImagesViewModel.onSnapshot` en la transición a
  «todo falla» — el toast de error **no** añade sonido (evita el doble pitido).
- **`RESUMEN`:** derivación de la capa de UI, **no** un `Toast` en la cola.
  `TOASTS_VISIBLES` baja a **3**. La `StateFlow<List<Toast>>` pública se
  transforma: si `mutableToasts.value.size > 3`, se emiten los 2 primeros + una
  tarjeta sintética `RESUMEN`:

  ```kotlin
  val toasts: StateFlow<List<Toast>> = mutableToasts
      .map { full ->
          if (full.size <= 3) full
          else full.take(2) + Toast(
              id = -1L, kind = ToastKind.RESUMEN,
              title = "y ${full.size - 2} novedades más",
              sub = "", meta = full.drop(2).mapNotNull { it.imageName }.joinToString(", "),
              action = "Ver todas", imageName = null,
          )
      }
      .stateIn(scope, SharingStarted.Eagerly, emptyList())
  ```

  El reloj de descarte sigue corriendo solo para los 2 reales visibles (el
  `RESUMEN` no tiene `id` real, `sincronizarRelojes` lo ignora porque no está en
  `mutableToasts`). Cuando uno de los 2 se va, entra el siguiente real y el
  contador del resumen baja.

  > Alternativa: meter el `RESUMEN` como `Toast` real en la cola. Rechazada:
  > tendría que llevar reloj, y su contenido («N novedades más») cambia cada vez
  > que entra o sale otro — sería un `Toast` que se reescribe. La derivación en
  > el `map` lo recalcula gratis.

### 10.2 `ToastCard` — rejilla 340 × 78 (V-1, V-5, V-8)

```
Surface(width 340, height 78, color = surface, border = tono, Elevation.toast, Radius.md)
└─ Column(space-between)
   ├─ Row(grid  26 · 1fr · 84 · 24,  gap 8,  padding 10/12/0/12)
   │  ├─ Box 26 círculo  background = tonoIconBg   → SvgIcon 14 (tono)
   │  ├─ Column(min-width 0)  [ title 13/600 ellipsis ]  [ sub 11 tx2 ellipsis tnum ]
   │  ├─ Box 84  → si action: pill accc/acct 12/600 centrado, ancho 84
   │  └─ Box 24  → IconButton CLOSE 14 (tx3)
   ├─ Row(space-between, padding 0/12/6/12, height 16)
   │  ├─ Text meta  mono 11 tx3 ellipsis  max-width 200
   │  └─ Text timer 11 tx3 tnum  width 64  align end   ("6 s" … "0 s")
   └─ Box(height 3, background = hair)  → Box(width progreso%, background = tono)
```

- **Tono por `kind`:** `NUEVA/SALTADAS` → paleta PENDING; `ERROR` → paleta
  ERROR; `RESUMEN` → acento. `tonoIconBg` = `statusColors(...).background` (o
  `primaryContainer` para RESUMEN); borde de la tarjeta = ese mismo color.
- **Cuenta atrás:** `timer = "${(progreso.value * toast.durationMillis / 1000f)
  .roundToInt().coerceAtLeast(0)} s"`, leído dentro del `draw`/lambda igual que
  la barra, para no recomponer la lista.
- **Acción:** `NUEVA/SALTADAS` → «Visto» (`onView` pasa a
  `onAction(toast.imageName, kind)` y el `MainScreen` lo mapea a
  `viewModel.acknowledge`); `ERROR` → «Reintentar» → `viewModel.refreshNow(name)`;
  `RESUMEN` → «Ver todas» → trae la ventana y `screen = IMAGES` en la sección
  «Versión nueva» (equivale a `windowVisible = true; traerAlFrente++` +, si hace
  falta, un scroll a la primera pendiente).
- `color = surface` en vez de `surfaceVariant`; `tonalElevation` → `Elevation.toast`.
- `TOASTS_VISIBLES` 4 → 3; `Modifier.width` usa `Layout.toastWidth - 12.dp`.

### 10.3 `ToastLayer`

- `items(toasts.take(3), key = { it.id })` — el `map` ya recorta, pero el
  `take(3)` defiende de un `RESUMEN` + 2.
- Separación entre tarjetas: `Arrangement.spacedBy(Space.sm)` (8 dp) con
  `Arrangement.Bottom`. Hoy es `Arrangement.Bottom` sin `spacedBy`.
- `LAYER_HEIGHT` y `MARGIN` (→ `Layout` no, se queda: `MARGIN` sí a `Layout` no…
  `MARGIN = 16` → 16 dp de margen de pantalla, es dato de diseño → `Layout` lo
  puede tener como `toastScreenMargin`. `LAYER_HEIGHT` se queda literal).

### 10.4 Tests de la fase 5

- `ToastStateTest`:
  - `show` produce `Toast` con `kind = NUEVA`, `sub = "versión X"` (sin flecha,
    sin versión anterior), `meta = registry`.
  - `showFailures` produce `kind = ERROR`, `sub` = motivo, `action = "Reintentar"`.
  - con 5 avisos en cola, `toasts.value` tiene 3 elementos y el 3.º es
    `kind = RESUMEN` con `title = "y 3 novedades más"`; al descartar uno, pasa a
    `"y 2 novedades más"` y entra un real.
  - el reloj no arranca para el `RESUMEN` (no está en `relojes.jobs`).
- `ToastNotificationPortTest`: `notifyFailures` respeta `mutedAll` y
  `toastsEnabled`; no emite sonido.
- `VersionPollingServiceTest`: §5.1.

---

## 11. Fase 6 — Barra de título

**Ficheros:** `ui/components/TitleBar.kt` (`jvmMain`), `desktopApp/.../Main.kt`.

Per D-1: **cerrar + campana**, sin minimizar ni maximizar. Se conserva el KDoc
que explica por qué (una app residente que se minimiza duplica su sitio y deja a
Windows decidiendo cuándo puede volver al frente).

```kotlin
@Composable
fun WindowScope.TitleBar(
    title: String,
    mutedAll: Boolean,
    onToggleMuteAll: () -> Unit,
    onClose: () -> Unit,
)
```

- `WindowDraggableArea` → `Surface(color = surface, height = Layout.titleBarHeight)`
  con **hairline inferior** `Modifier.drawBehind { drawLine(hair, ...) }` o un
  `Box` de 1 dp.
- Contenido: `SvgIcon(LOGO, primary, 16)` + `"  $title"` 11 sp Medium
  `onSurfaceVariant` `weight(1f)` + **campana** + **cerrar**.
- **Campana:** `IconButton(onToggleMuteAll, width = 24.dp)`:
  - `mutedAll` → `SvgIcon(BELL_OFF, onSurface, 16)` sobre
    `Surface(surfaceVariant, CircleShape)`.
  - si no → `SvgIcon(BELL, onSurfaceVariant, 16)`, fondo transparente.
- **Cerrar:** `IconButton(onClose, width = Layout` … `CLOSE_HIT_WIDTH` se queda
  literal, es zona de clic no medida de diseño) → `SvgIcon(CLOSE,
  onSurfaceVariant, 14)`.

**`Main.kt`:** `TitleBar` se llama con `config.mutedAll` y
`{ wiring.applyConfig(config.copy(mutedAll = !config.mutedAll)) }` — la misma
lambda que la campana de la cabecera. Se propaga por `MainScreen` →
la llamada actual `TitleBar("ImageWatch — …") { … }` pasa a
`TitleBar("ImageWatch — …", config.mutedAll, onToggleMuteAll) { … }`.

**Tests:** no hay test de Compose UI; el cambio va al repaso visual. Se
verifica: la campana alterna con `mutedAll`, y `mutedAll` cambiado desde la
barra se refleja en la cabecera y en el pie (misma fuente, `config`).

---

## 12. Fase 7 — Navegación por teclado

**Ficheros:** `ui/theme/` (helper `focusRing`), `ui/images/ImagesScreen.kt` +
`ImageRow.kt`, `ui/settings/SettingsScreen.kt`, `ui/dialogs/`.

Blueprint sección 1h / `rowStates` «Foco de teclado»:

- **`Modifier.focusRing()`** en `ui/theme`: dibuja un contorno de 2 dp
  `MaterialTheme.colorScheme.primary` con `offset 2` **por fuera** del borde del
  componente (`Modifier.border` sobre un `Modifier.padding(-2.dp)` simulado con
  `drawBehind` + `inset`), para no alterar el tamaño. Se aplica cuando
  `interactionSource` reporta foco.
- **Lista (`LazyColumn` de `ImagesScreen`):**
  - `↑` / `↓` → mueven el foco entre filas (`FocusRequester` por fila + un mapa,
    o `Modifier.focusGroup()` + `focusProperties`).
  - `Espacio` sobre una fila pendiente → «Visto» (`onAcknowledge(name)`).
  - `Enter` → `onToggleExpand(name)`.
  - `Escape` → si hay `expandedRow`, `onToggleExpand(expandedRow)` (colapsa).
  - `Tab` → entra en el cluster de acciones de la fila (chip + kebab).
- **Cada control** (chips de cabecera, botones del pie, toggles de Ajustes,
  botones del diálogo) recibe un target de foco real y `focusRing()`.
- **No** el `ToastLayer` — la ventana es `focusable = false` por diseño y no
  debe robar foco.

**Estado:** el foco es estado de Compose, no del view model. Lo único que sube
al view model es lo que ya sube (`acknowledge`, `toggleExpand`, `refreshNow`).

**Tests:** sin test de UI; repaso visual + una lista de comprobación manual en
el PR (mover con flechas, Espacio marca, Enter despliega, Escape pliega, Tab
alcanza las acciones, el anillo se ve y no cambia el tamaño de la tarjeta).

`historias-de-usuario.md`: añadir H-\* de navegación por teclado.

---

## 13. `historias-de-usuario.md` — cambios por fase

El documento `docs/superpowers/specs/2026-09-05-imagewatch-historias-de-usuario.md`
es el contrato contra el que se comprueba que un refactor no cambió nada. Este
rediseño **sí** cambia comportamiento, así que el documento se actualiza en cada
PR:

| Fase | Historias que se retiran | Historias que se añaden |
|---|---|---|
| 3 | «Deshacer un Visto», «Ponerlas arriba», «Renombrar una imagen» | «Abrir una fila muestra su detalle», «Copiar referencia copia solo el origen y avisa con un check» |
| 4 | «Guardar ajustes», «Modo simulación» | «Un ajuste se aplica al momento», «Un valor inválido no mueve nada», «Iniciar al encender el equipo», «Restablecer ajustes», «Borrar datos locales» |
| 5 | — | «El aviso de un fallo», «Cuarta novedad y siguientes se agrupan» |
| 6 | — | «La campana de la barra de título silencia todos los avisos» |
| 7 | — | «Recorrer la lista con el teclado» |

---

## 14. Tabla de fases / PR (siete fases)

| Fase | PR | Núcleo | Infra nueva | Depende de |
|---|---|---|---|---|
| **1** | Tokens (`Elevation`, `Layout`, `emphasisEasing`), color (K-1, K-2), auditoría `tnum` | — | — | — |
| **2** | Bandeja: buscador replegable, «Ver todas» reconoce, umbral «Al día», easing de énfasis, pie a `Layout` | — | — | 1 |
| **3** | Fila abierta; retirar Undo + cola + Renombrar; «copiar referencia» solo origen | `ImagesViewModel` (no `domain`/`application`) | — | 1, 2 |
| **4** | Ajustes: aplicar al momento, dos columnas, líneas de ayuda, segmentado; `AutostartPort`; `wipeLocalData`; `resetSettings`; `ConfirmDialog` | — | `AutostartPort` (expect/actual Windows) | 1 |
| **5** | Aviso 340×78, `kind`, cuenta atrás, resumen; `notifyFailures` | `NotificationPort.notifyFailures` + un bucle en `VersionPollingService` | — | 1 |
| **6** | Barra de título: cerrar + campana, 38 dp, hairline | — | — | 1 |
| **7** | Navegación por teclado + `focusRing` | — | — | 2, 3, 4 |

Fases 2, 4, 5, 6 son independientes entre sí una vez está la 1; se pueden
paralelizar o reordenar. La 3 necesita la 2 (comparte `ImagesScreen`). La 7 va
al final porque toca todos los controles.

---

## 15. No objetivos

- **No** se cambia la arquitectura hexagonal ni se mueve nada de `domain/` o
  `infrastructure/`.
- **No** se añade un objetivo de KMP. El `actual` de `AutostartPort` es solo
  Windows.
- **No** se implementa `skippedCount` real (contar publicaciones intermedias del
  registry). El `kind` `SALTADAS` queda definido sin productor.
- **No** se añade densidad de fila configurable, ni tema de densidad
  (`LocalSpace`). `Tokens` sigue siendo `object`.
- **No** se toca `VersionPollingService.renameImage` ni `ImageStateStore.rename`
  aunque queden sin llamador desde la UI: son del núcleo, tienen tests, y
  borrarlos es un refactor del núcleo aparte de este rediseño.
- **No** se migra el repaso visual T14 a un test automático. Sigue siendo
  manual, una lista de comprobación por PR.

## 16. Riesgos y cuestiones abiertas

1. **`wipeLocalData` en caliente.** Recablear `Wiring` tras borrar los ficheros
   es frágil (`service` con oyentes, `ImagesViewModel` con referencia directa).
   **Mitigación elegida:** borrar + reiniciar la aplicación (o cerrarla y pedir
   reapertura), no recablear. A confirmar en la tarea de la fase 4.
2. **`AutostartPort` y el runtime de jlink.** `java.util.prefs` sobre el
   registro de Windows puede necesitar el módulo `java.prefs`, que hoy no está
   en `nativeDistributions.modules`. Si el `actual` usa `prefs`, hay que añadir
   el módulo (misma lección que `java.naming` en la migración: `packageMsi`
   verde no dice si arranca). Si usa `reg.exe`, no hace falta módulo pero
   depende de un binario del sistema. La tarea prueba las dos y elige.
3. **Buscador replegable y `highlight()`.** `highlight()` limpia el filtro
   (`search = ""`). Con el buscador replegado, tras un `highlight` el buscador
   debe quedar replegado y vacío — el `searchExpanded` local tiene que
   reaccionar a `state.search` volviéndose `""`. Se resuelve con
   `LaunchedEffect(state.search) { if (state.search.isEmpty() && !focused)
   searchExpanded = false }`.
4. **`RESUMEN` con `id = -1`.** El `key` de `LazyColumn` en `ToastLayer` usa
   `it.id`; dos render seguidos con `RESUMEN` comparten `id = -1`, lo cual es
   correcto (es la misma tarjeta lógica), pero hay que asegurarse de que ningún
   `Toast` real use `id` negativo (`sequence` arranca en 0 y sube).
5. **Orden de merge fase 1 → resto.** Todas las demás fases dependen de `Layout`
   y `emphasisEasing`. Si se trabajan en paralelo, la 1 se mergea primero o las
   ramas rebasan sobre ella.
6. **`Bandeja.dc.html` variante `abierta` usa una rejilla de 3 columnas**
   (`minmax(0,1fr) 104px 128px`) en la fila superior de la tarjeta abierta, no
   la de 4 (`… 148px …`) de la fila cerrada. Es decir, la fila abierta **oculta
   la columna `+N`/píldora** y ensancha la de acciones. A verificar en el repaso
   visual de la fase 3: la banda superior de la fila abierta puede diferir de la
   cerrada en el reparto de columnas.
