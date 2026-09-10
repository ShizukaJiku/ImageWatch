# Estado del rediseño de la interfaz — completado el 2026-09-09

Documento de continuidad. Las 7 fases están hechas; queda la revisión final del usuario sobre
`feature/rediseno-fase-4-ajustes`, que las acumula todas.

- **Spec:** `docs/superpowers/specs/2026-09-08-imagewatch-rediseno-design.md` (7 fases, decisiones D-1..D-7).
- **Plan fases 1–3:** `docs/superpowers/plans/2026-09-08-imagewatch-rediseno-fases-1-3.md` (completado).
- **Plan fase 4:** `docs/superpowers/plans/2026-09-08-imagewatch-rediseno-fase-4.md` (completado).
- **Plan fase 5:** `docs/superpowers/plans/2026-09-09-imagewatch-rediseno-fase-5.md` (completado).
- **Plan fase 6:** `docs/superpowers/plans/2026-09-09-imagewatch-rediseno-fase-6.md` (completado).
- **Plan fase 7:** `docs/superpowers/plans/2026-09-09-imagewatch-rediseno-fase-7.md` (completado). Última fase.
- **Contrato de comportamiento:** `docs/superpowers/specs/2026-09-05-imagewatch-historias-de-usuario.md`, actualizado hasta H-98.

## Dónde estamos

**7 de 7 fases hechas — el rediseño está completo.** Cada fase fue un PR / rama apilada sobre la
anterior. **No hay remoto**: la revisión es de rama, no de PR de GitHub. Las fases 5, 6 y 7 se
hicieron cada una en su propia rama y se fusionaron (fast-forward) de vuelta a
`feature/rediseno-fase-4-ajustes` al terminar; ninguna de esas tres ramas existe ya.

| Fase | Rama | Estado |
|---|---|---|
| 1 Tokens/color/motion | `feature/rediseno-fase-1-tokens` | Hecha |
| 2 Bandeja (armazón) | `feature/rediseno-fase-2-bandeja` | Hecha |
| 3 Fila abierta + quitar undo/cola/rename | `feature/rediseno-fase-3-fila-abierta` | Hecha |
| 4 Ajustes | `feature/rediseno-fase-4-ajustes` | Hecha; **el usuario la está revisando** |
| 5 Aviso | fusionada en `feature/rediseno-fase-4-ajustes` | Hecha; **el usuario la está revisando** |
| 6 Barra de título | fusionada en `feature/rediseno-fase-4-ajustes` | Hecha; **el usuario la está revisando** |
| 7 Teclado | fusionada en `feature/rediseno-fase-4-ajustes` | Hecha; **el usuario la está revisando** |

**Rama actual:** `feature/rediseno-fase-4-ajustes` (tip `bc03c40`, incluye las fases 5, 6 y 7 —
todo el rediseño).

## Puntos abiertos de la fase 7 (el usuario los está revisando)

1. Alcance de `focusRing()` deliberadamente recortado: los `IconButton` sueltos de la cabecera
   (refrescar, ajustes, buscador, agregar, campana de la cabecera) y los `BasicTextField` de
   Ajustes/buscador no lo llevan — ya son foco real por `Tab` y el spec no los nombra
   explícitamente. Solo llevan el anillo los controles que el spec sí nombra (chips de cabecera,
   botones del pie de Ajustes, toggles, botones del diálogo) más la fila entera y sus dos acciones
   (chip + kebab).
2. `CheckingRow` (fila con una comprobación individual en vuelo) no participa en la navegación por
   teclado: no recibe foco ni anillo mientras dura la comprobación (unos segundos).
3. `Espacio` sobre una fila **no pendiente** (Error/Al día) no está interceptado a propósito —
   cae hacia el `clickable` de `RowCard` sin forzar ningún comportamiento adicional; el contrato
   del spec solo define Espacio para «Versión nueva».
4. `↑`/`↓` mueven el foco vía `LocalFocusManager.moveFocus`, que solo encuentra objetivos ya
   compuestos por la `LazyColumn` — en listas muy largas con saltos grandes fuera del margen de
   composición perezosa, una fila muy lejana no es alcanzable hasta que se compone. No es el caso
   de uso principal de esta app.
5. El repaso visual (`:desktopApp:run`) confirmó arranque sin excepción con toda la navegación
   nueva, pero no se pudo recorrer a ojo la lista de comprobación manual del spec §12 (flechas,
   Espacio, Enter, Escape, Tab, anillo visible) porque no hay herramienta de captura/automatización
   para una ventana nativa de Compose Desktop en este entorno — queda pendiente de un repaso manual
   del usuario, con la lista completa en
   `docs/superpowers/plans/2026-09-09-imagewatch-rediseno-fase-7.md` (Task 5, Step 4).

## Instalador, autostart y pipelines (2026-09-09, posterior al refactor de componentes)

Trabajo de entrega, no de rediseño visual. **Sin commitear todavía** (rama
`feature/rediseno-fase-4-ajustes`).

- **Ventana**: arranca a 1120×720 (antes el default 800×600 de Compose; nunca se configuró).
- **Autostart**: `main(args)` ahora lee `--minimized`. Arranque normal (atajo, `gradlew run`) abre
  la ventana; arranque por la clave `Run` del registro (que añade `--minimized`) va a la bandeja.
  Antes el flag era código muerto y la app arrancaba siempre oculta. Subtítulo del toggle de
  Ajustes reescrito.
- **Icono del instalador**: `desktopApp/icons/ImageWatch.ico` (7 tamaños, PNG embebido) generado
  de `AppIconPainter` con `./gradlew :desktopApp:generateIcon` (tarea fuera del build; el `.ico` se
  commitea). `build.gradle.kts` → `windows { iconFile }`.
- **Formatos**: `targetFormats(Msi, AppImage)`. El portable es la carpeta app-image, se zipea.
- **Versión de entrega**: `gradle.properties` → `imagewatch.version` (hoy `1.0.0`), la lee
  `packageVersion`. Subir ahí en el PR que cierra cada versión.
- **Instalación limpia, sin datos de prueba**: la app empaquetada detecta `jpackage.app-path` y
  arranca sin simulación y sin imágenes sembradas. `EmptyImageSource` (nuevo, `commonMain`) cubre
  "sin URL todavía" sin construir un `HttpImageSource("")`. Se quitó el `check(...)` de arranque
  que exigía URL o simulación. En `gradlew run` sigue arrancando en simulación con las 4 imágenes.
- **Actualización conserva datos**: ya era cierto (`~/.notifier/` en el perfil, el MSI no lo toca);
  documentado en README.
- **Hooks pre-commit + `.denylist.local` eliminados**: `scripts/` borrado, `.gitignore` sin
  `*.local`, README sin las secciones de hooks/secretos. `.denylist.local` queda en disco como
  untracked — borrarlo a mano.
- **`tests.yml`**: `push` solo en `main` (+ PR), y añade `:desktopApp:compileKotlin` (antes CI
  nunca compilaba el módulo de escritorio).
- **`release.yml`**: dispara al fusionar a `main`. Job `tag` lee `imagewatch.version`; si el tag
  `vX.Y.Z` no existe, lo crea y `build` publica un Release con el MSI y el zip portable. Fusionar
  sin subir la versión no hace nada. Paso de firma opcional (`SIGN_PFX_BASE64` /
  `SIGN_PFX_PASSWORD`), no-op sin los secretos.

## Refactor de componentes reutilizables (2026-09-09, posterior a las 7 fases)

Tras el repaso visual del usuario con capturas comparadas contra el diseño real (vía `DesignSync`
MCP sobre `Blueprint.dc.html`, `Bandeja.dc.html`, `Ajustes.dc.html`, `Aviso.dc.html`,
`Medidas.dc.html`), salieron defectos de fidelidad que un parche suelto no iba a resolver bien:
iconos de más/de menos, píldoras ovaladas en vez de con esquinas de píldora real, `hairline` vs
`surfaceVariant` confundidos, texto "Agregar Imagen" que debía ser solo "Agregar". El usuario pidió
explícitamente un plan completo con componentes reutilizables/configurables en vez de arreglos
puntuales.

- **Spec:** `docs/superpowers/specs/2026-09-09-imagewatch-componentes-reutilizables-design.md`.
- **Plan:** `docs/superpowers/plans/2026-09-09-imagewatch-componentes-reutilizables.md` (10 tareas,
  hecho inline, sin subagentes).
- **Rama:** `feature/componentes-reutilizables`, fusionada (fast-forward) a
  `feature/rediseno-fase-4-ajustes` y borrada.

**Componentes nuevos** (`ui/components/`): `Pill.kt`, `IwIconButton.kt`, `SurfaceCard.kt`.
Reemplazan 7+ píldoras ad-hoc (`HeaderChip`, `ActionChip`, `DetailPill`, botones sueltos de
Ajustes/diálogo/toast) y 4+ botones circulares (`IconButton` sueltos con fondo/anillo manual).

**Bugs de fidelidad corregidos de paso:**
- Anillo de foco ovalado sobre chips no cuadrados (`FocusRing.kt`): `drawRoundRect` clampa el radio
  por eje, a diferencia de `RoundedCornerShape`; ahora se clampa a mano a `min(ancho,alto)/2`.
- Token `hairline()` nuevo (`Colors.kt`): separa borde/divisor (`--hair`) de fondo de píldora
  (`--surfv`) — antes ambos usaban `surfaceVariant` y en tema claro salía el color equivocado.
- Icono de doble-check que sobraba y de reload que faltaba: "Ver todas"/"Visto" ahora llevan
  `CHECK_ALL`/`CHECK`; "Reintentar" (cabecera de sección de error) ahora lleva `REFRESH`, que
  faltaba; "Silenciar/Reactivar avisos" del detalle de fila ahora lleva `BELL`/`BELL_OFF`, que
  faltaba. Icono nuevo `SPINNER` (arco simple) para la fila en comprobación, sustituye al
  `REFRESH` (flecha completa) que no es el que pide el diseño.
- Texto de la cabecera corregido a "Agregar" (sin "imagen") — el de `EmptyState` sí era "Agregar
  imagen" y se queda igual, son botones distintos.

**Excepciones deliberadas, documentadas y de bajo riesgo** (no migradas a los componentes nuevos):
1. Botón "Quitar de la lista" del detalle de fila (`ImageRow.kt`): combinación icono+texto que no
   encaja en la API simple de `Pill`; se queda como `Surface` propio, pero ganó `.focusRing(...)`
   que no tenía.
2. Botón de cerrar de `TitleBar.kt`: no migrado a `IwIconButton` porque su ancho (46dp) no coincide
   con la altura de la barra (38dp) — saldría una elipse, no un círculo.
3. Botón de confirmar de `ConfirmDialog.kt`: pasó de ancho mínimo (`widthIn`) a ancho fijo
   (`Layout.dialogConfirmMin`, 132dp) — solo importa si algún día una etiqueta más larga se trunca.

**Verificación:** `:shared:jvmTest :shared:koverVerify :desktopApp:compileKotlin spotlessCheck`
verde en cada tarea y en la rama fusionada. `:desktopApp:run` arrancó sin excepción (~90s, matado
limpio). Sin test de UI automatizado (ver más abajo) — falta confirmación visual del usuario.

**Pendiente de confirmación visual del usuario** sobre la ventana real (no se puede capturar/
automatizar una ventana nativa de Compose Desktop en este entorno):
1. Cabecera: ya no hay icono de refrescar ni de doble-check sueltos; quedan buscar, campana,
   ajustes, "Agregar" (sin "imagen").
2. Chip "Visto" de una fila pendiente: lleva el check antes del texto.
3. Enfocar con `Tab` el chip "Visto": el anillo es una píldora limpia, no un óvalo.
4. Pill "Reintentar" de la cabecera de la sección de error: lleva icono de refrescar.
5. Detalle de una fila → "Silenciar avisos": lleva icono de campana tachada.
6. Una fila con comprobación en vuelo: el icono que gira es un arco simple, no una flecha completa.
7. Tema claro: bordes/divisores (fila «Al día», línea de Ajustes, hairline de la barra de título) se
   ven ligeramente distintos del fondo de los chips.

**Investigación pendiente, pedida por el usuario, sin empezar:** evaluar Compose Screenshot Testing
(https://developer.android.com/studio/preview/compose-screenshot-testing) y Roborazzi
(https://github.com/takahirom/roborazzi) como alternativas para pruebas visuales automatizadas de
Compose Desktop — hoy no hay ninguna en el proyecto.

## Puntos abiertos de la fase 6 (el usuario los está revisando)

1. `BELL_HIT_WIDTH = 24.dp` es nombre propio de esta implementación (el spec solo daba el número);
   la campana usa `IconSize.md` (16 dp), distinto a `IconSize.lg` (18 dp) de la campana de la
   cabecera — intencional, así lo pide el spec para esta barra en concreto.
2. El repaso visual (`:desktopApp:run`) confirmó arranque sin excepción con el hairline y la
   campana nuevos, pero no se verificó a ojo el toggle bidireccional (barra ↔ cabecera) porque no
   hay herramienta de captura para una ventana nativa de Compose Desktop en este entorno — queda
   pendiente de un vistazo manual del usuario.

## Puntos abiertos de la fase 5 (el usuario los está revisando)

1. Icono por `ToastKind` no estaba explícito en el spec: se eligió `BELL` para `NUEVA`/`SALTADAS`,
   `WARNING` para `ERROR`, `CHECK_ALL` para `RESUMEN`. El tono de `RESUMEN` es un acento fijo
   (`primaryContainer`/`onPrimaryContainer` en claro, su par en oscuro), no derivado de
   `statusColors`, porque no describe un estado de imagen sino una acción de la interfaz.
2. `toasts` (`ToastState.kt`) dejó de derivarse con `mutableToasts.map{}.stateIn(scope, Eagerly, ...)`
   —como sugería el spec— y pasó a un `MutableStateFlow` que se recalcula de forma síncrona
   (`publicar()`) justo después de cada escritura en la cola completa. Motivo: `show()`/`dismiss()`
   llegan de hilos reales que leen `toasts.value` inmediatamente después de escribir sin ceder el
   hilo, y la tubería reactiva publicaba el recorte en una corrutina aparte que podía llegar tarde
   —los tests de `ToastStateTest` lo detectaron como fallos intermitentes durante la implementación.
3. ~~`RESUMEN` "Ver todas" no fuerza `screen = IMAGES` si Ajustes está abierto~~ **Resuelto en el
   repaso** (2026-09-09): `screen` se subió de `MainScreen` a `main()` (mismo sitio que
   `windowVisible`/`traerAlFrente`), y el `onAction` de `RESUMEN` en `Main.kt` ahora hace también
   `screen = Screen.IMAGES`. Sigue sin hacer scroll a la primera pendiente — el spec lo marcaba
   como "si hace falta", y no se ha pedido.
4. `ERROR` no se verificó visualmente y **se acepta así** (decisión del repaso, 2026-09-09): para
   ver el toast en pantalla hace falta una imagen que pase de OK/UNKNOWN a ERROR **dentro del mismo
   arranque** del proceso — la regla "el primer ciclo no avisa" bloquea el aviso si arranca ya en
   error, y ni `SIMULATION_MODE` (nunca falla) ni un registry real inalcanzable (falla ya en el
   primer ciclo, que no cuenta) pueden producir esa transición sin tocar código de infraestructura
   solo para esta prueba puntual. Cobertura por `ToastStateTest`/`ToastNotificationPortTest`
   aceptada como suficiente.

## Verde al cerrar la fase 4

```
:shared:jvmTest       171 tests en 20 ficheros, todos pasan
:shared:koverVerify   cumple el 80 %
:desktopApp:compileKotlin  compila
spotlessCheck         limpio
:desktopApp:run       arranca sin excepción (comprobado lanzando y matando el proceso)
```

## Cómo se compila

**Solo desde PowerShell**, con `JAVA_HOME` fijado. Desde Bash el wrapper sale con 127 sin mensaje.

```powershell
$env:JAVA_HOME='C:\Users\shizu\.jdks\ms-21.0.12.1'
Set-Location C:\Users\shizu\IdeaProjects\ImageWatch
.\gradlew.bat :shared:jvmTest :shared:koverVerify :desktopApp:compileKotlin spotlessCheck --console=plain
```

Si Spotless se queja: `.\gradlew.bat spotlessApply` y repetir. **Ojo: la línea máxima de ktlint aquí
es 120, no 140** (el `ESTADO.md` de la migración decía 140; en la práctica salta a 120). `spotlessApply`
no envuelve literales de string largos ni firmas: hay que partirlas a mano.

`:desktopApp:run` no termina solo (app de bandeja). Para el repaso: lanzar en background, esperar
~75 s, comprobar que no hay excepción en stderr, matar `java`/`gradle`/`kotlin`.

## Reglas del repo que siguen vigentes

- **Nunca `git checkout` ni `git restore <fichero>`**: el índice guarda la plantilla y el árbol lleva
  encima el renombrado de paquete. `git switch -c`, `git stash`, `git worktree`, `git reset --soft`
  son seguros.
- **`git add` con la ruta correcta.** `desktopApp/.../Main.kt` estuvo tracked como `main.kt` (minúscula)
  hasta la fase 4; `git add Main.kt` NO casaba y el commit `26355d6` se quedó sin el fichero. Se
  arregló en `325840e` y se renombró a `Main.kt` de verdad en `0d0f914`. Ahora ya es PascalCase.
- **Dos ficheros modificados que NO son de este trabajo**, dejarlos así (venían de antes de la sesión):
  `docs/superpowers/plans/2026-09-07-migracion-plantilla-kmp.md` y `docs/superpowers/plans/ESTADO.md`.
  Nunca los metas en un commit de código. Usar `git add <rutas explícitas>`, nunca `git add -A`.
- Commits en español, `<tipo>: <descripción>`, terminando con:
  ```
  Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
  Claude-Session: https://claude.ai/code/session_019RioxagAx3sXkNcUVk6ihF
  ```
  (la sesión cambiará; usar la del `system-reminder` de attribution vigente).

## Decisiones tomadas (sesión de brainstorming, valen para las fases 5–7)

| # | Decisión |
|---|---|
| D-1 | Barra de título: **solo cerrar + campana**. Sin minimizar ni maximizar (se conserva el rationale de app de bandeja de `TitleBar.kt`). |
| D-2 | Quitada toda la mecánica de Undo y cola «Ponerlas arriba». Hecho en fase 3. |
| D-3 | Ajustes se aplica al momento, sin «Guardar». Hecho en fase 4. |
| D-4 | Alcance: rediseño completo, incluida infra. |
| D-5 | Renombrar de imágenes: quitado del todo. `VersionPollingService.renameImage` y `ImageStateStore.rename` quedan en el núcleo SIN llamador — no se tocan en este rediseño. |
| D-6 | «Modo simulación» fuera de la UI de Ajustes. Sigue por variable de entorno `SIMULATION_MODE` en `Main.kt`. `AppConfig.simulationMode` se mantiene. |
| D-7 | Troceado por capa transversal, un PR/rama por fase. |

## Fase 5 — Aviso (hecha; ver spec §10 y los puntos abiertos arriba)

**Ficheros:** `ui/toast/ToastState.kt`, `ui/toast/ToastNotificationPort.kt`, `ui/toast/ToastWindow.kt`
(`jvmMain`), `application/NotificationPort.kt` + `application/VersionPollingService.kt`.

- `Toast` gana `kind: ToastKind` (`NUEVA`/`SALTADAS`/`ERROR`/`RESUMEN`), `sub`, `meta` (origen mono),
  `action` (`"Visto"`/`"Reintentar"`/`"Ver todas"`).
- `toastOf` **deja de nombrar la versión anterior**: `body`/`sub` = «versión X», no «X → Y».
- `TOASTS_VISIBLES` 4 → **3**; con >3 en cola, la `StateFlow` pública sintetiza una tarjeta `RESUMEN`
  («y N novedades más», nombres en `meta`, acción «Ver todas»). Derivación en un `.map`, no un `Toast`
  real en la cola (id = -1).
- `ToastCard` rejilla fija 340×78: `26 icono · 1fr texto · 84 acción · 24 cerrar`, 2.ª línea
  `meta(mono ≤200) · cuenta atrás(64, tnum "6 s"→"0 s")`, barra 3 dp abajo. Tono por `kind` tiñe icono
  y borde. `color = surface` (no `surfaceVariant`), `tonalElevation = Elevation.toast` (ya existe el
  token). Anchos ya en `Layout` (`toastIcon`, `toastAction`, `toastClose`, `toastTimer`, `toastBar`,
  `toastMetaMax`, `toastWidth`, `toastScreenMargin`).
- **Núcleo (mínimo):** `NotificationPort` gana `fun notifyFailures(failures: List<ImageState>) {}`
  (default vacío). En `VersionPollingService.notifyTransitions`, tras el bucle de `notifyUpdates`,
  un segundo bucle protegido con `transitionedInto(previous, current, ImageStatus.ERROR)` que ya
  existe. `ToastNotificationPort.notifyFailures` respeta `mutedAll`/`toastsEnabled`, **sin sonido**
  (el pitido de «todo falla» ya lo emite `ImagesViewModel.onSnapshot`). Test en
  `VersionPollingServiceTest` + `ToastNotificationPortTest`.
- `SALTADAS` queda definido pero **sin productor**: el núcleo no cuenta publicaciones intermedias
  (`skippedCount` es futuro). En esta fase todos los avisos de versión nueva son `NUEVA`.
- `ToastLayer.onView` pasa a `onAction(name, kind)`; `Main.kt` lo mapea: `NUEVA/SALTADAS`→`acknowledge`,
  `ERROR`→`refreshNow(name)`, `RESUMEN`→ventana al frente + sección «Versión nueva».

## Fase 6 — Barra de título (hecha; ver spec §11 y los puntos abiertos arriba)

**Ficheros:** `ui/components/TitleBar.kt` (`jvmMain`), `Main.kt`.

- `TitleBar(title, mutedAll, onToggleMuteAll, onClose)`: campana (24 dp) a la izquierda de cerrar.
  `mutedAll` → `BELL_OFF` tint `onSurface` sobre círculo `surfaceVariant`; si no → `BELL` tint
  `onSurfaceVariant` transparente. Misma lambda que la campana de la cabecera.
- Alto fijo `Layout.titleBarHeight` (38, ya en `Layout`), hairline inferior 1 dp `surfaceVariant`.
- `Main.kt`: `TitleBar("…", config.mutedAll, onToggleMuteAll) { … }` — propagar `onToggleMuteAll`
  (= `{ wiring.applyConfig(config.copy(mutedAll = !config.mutedAll)) }`) por `MainScreen` a `TitleBar`.
- Sin test de UI; repaso visual.

## Fase 7 — Teclado (hecha; ver spec §12 y los puntos abiertos arriba)

**Ficheros:** `ui/theme/FocusRing.kt` (nuevo), `ImagesScreen.kt` + `ImageRow.kt`,
`SettingsScreen.kt`, `ui/dialogs/ConfirmDialog.kt`.

- `Modifier.focusRing()`: contorno 2 dp `primary`, offset 2, **por fuera** del borde (no cambia
  tamaño), dibujado con `drawWithContent` — no añade un segundo objetivo de foco.
- Lista: `↑/↓` mueven foco entre filas (`LocalFocusManager.moveFocus`), `Espacio` = Visto (solo
  pendientes), `Enter` = `toggleExpand`, `Escape` = colapsa `expandedRow`. Todo interceptado en un
  único `Modifier.onPreviewKeyEvent` en el `LazyColumn`. `Tab` entra en las acciones de la fila
  gratis: la fila, el chip y el kebab ya eran focos reales por `clickable`/`IconButton`.
- Anillo aplicado a: la fila entera, `ActionChip`, el kebab, `HeaderChip`, `OkSectionHeader`, el
  «Reintentar ahora» del aviso contextual, `FootPill`, el `Switch` de `Toggle`, `Segmented`, el
  botón de volver de Ajustes, y los dos botones de `ConfirmDialog`.
- H-98 añadida a `historias-de-usuario.md`.

## Puntos abiertos de la fase 4 (el usuario los está revisando)

1. `AutostartPort` es interfaz plana + impl JVM (`WindowsAutostart` en `jvmMain/infrastructure/os/`),
   **no `expect`/`actual`** como pedía el spec §5.2. Justificado: un interface no necesita el
   mecanismo, y el único consumidor multiplataforma (`SettingsViewModel`) solo ve la interfaz.
2. `reg.exe` en vez de `java.util.prefs` (las prefs de Java viven en `HKCU\Software\JavaSoft\Prefs`,
   no en `Run`). Evita además añadir módulo a jlink.
3. ~~`wipeLocalData` cierra la app sin que el `ConfirmDialog` lo avise~~ **Resuelto en el repaso**
   (2026-09-09): el `body` de «Borrar datos locales» ahora dice explícitamente que la aplicación se
   cierra y que hay que volver a abrirla.
4. `NumberField`/`UrlField` son `BasicTextField` con decoración propia a 34 dp, no `OutlinedTextField`.
   Commit en blur y en Enter (`onFocusChanged { !isFocused }` + `KeyboardActions(onDone)`).
5. Caption del segmentado usa `isSystemInDarkTheme()` — dice el tema que pide el SO aunque el usuario
   fuerce Claro/Oscuro. El Blueprint lo dibuja así igual.
6. El buscador replegable de la fase 2 quedó como `BasicTextField` pill de 34 dp tras un bug (se
   colapsaba antes de recibir el foco). Arreglado en `7a95423` con un flag `hasHadFocus`.

## Gotchas ya resueltos, por si reaparecen

- **Test flaky `comprobar una sola fila…`** (`ImagesViewModelTest`): `PollingController` consume en
  `Dispatchers.Default`; su consumidor adelantaba a las aserciones. Se arregló cerrando el controlador
  ANTES de `refreshNow`. Si otro test con el mismo patrón flakea, misma solución.
- **`Platform declaration clash`** en fakes de `AutostartPort`: `var enabled: Boolean` genera
  `setEnabled(Z)V` que choca con el método del interface. Usar un nombre de campo distinto (`on`).
- **`Float.toDouble()`** no da el literal exacto (`0.2f` → `0.20000000298023224`). En tests comparar
  de vuelta en `Float`.

## Repaso del usuario (2026-09-09)

Se repasaron los puntos abiertos de las 4 fases uno a uno. Resultado:

- **Arreglados:** fase 4 punto 3 (aviso de cierre en «Borrar datos locales»), fase 5 punto 3
  (`RESUMEN` fuerza `Screen.IMAGES`).
- **Aceptados tal cual, sin cambio:** fase 4 puntos 1, 2, 4, 5, 6; fase 5 puntos 1, 2, 4 (ver el
  detalle de por qué el 4 no se puede verificar sin tocar infraestructura); fase 6 punto 1; fase 7
  puntos 1-4.
- **Pendientes de repaso manual del propio usuario** (no automatizables desde aquí): fase 6 punto 2
  (toggle bidireccional de la campana) y fase 7 punto 5 (lista de comprobación de teclado completa).

## Qué pedir al retomar

El rediseño está completo y repasado. Al retomar, lo único que queda es:

1. El repaso manual pendiente de fase 6/fase 7 (arriba), si no se hizo ya.
2. Decidir qué hacer con `feature/rediseno-fase-4-ajustes` (las 7 fases juntas, más las dos
   correcciones de este repaso): sin remoto en este repo, la opción natural es fusionarla a `main`
   cuando el usuario dé el visto bueno — no se ha hecho todavía.
