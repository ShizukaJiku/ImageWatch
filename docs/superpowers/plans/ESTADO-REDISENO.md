# Estado del rediseño de la interfaz — pausa el 2026-09-08

Documento de continuidad para retomar en otra sesión. Se lee **antes** que los planes.

- **Spec:** `docs/superpowers/specs/2026-09-08-imagewatch-rediseno-design.md` (7 fases, decisiones D-1..D-7).
- **Plan fases 1–3:** `docs/superpowers/plans/2026-09-08-imagewatch-rediseno-fases-1-3.md` (completado).
- **Plan fase 4:** `docs/superpowers/plans/2026-09-08-imagewatch-rediseno-fase-4.md` (completado).
- **Plan fase 5:** `docs/superpowers/plans/2026-09-09-imagewatch-rediseno-fase-5.md` (escrito, sin ejecutar).
- **Planes fases 6–7:** aún NO escritos. Se escriben al retomar, uno por fase, con el skill `superpowers:writing-plans`.
- **Contrato de comportamiento:** `docs/superpowers/specs/2026-09-05-imagewatch-historias-de-usuario.md`, actualizado hasta H-97.

## Dónde estamos

**4 de 7 fases hechas.** Cada fase es un PR / rama apilada sobre la anterior. **No hay remoto**: la
revisión es de rama, no de PR de GitHub.

| Fase | Rama | Estado |
|---|---|---|
| 1 Tokens/color/motion | `feature/rediseno-fase-1-tokens` | Hecha |
| 2 Bandeja (armazón) | `feature/rediseno-fase-2-bandeja` | Hecha |
| 3 Fila abierta + quitar undo/cola/rename | `feature/rediseno-fase-3-fila-abierta` | Hecha |
| 4 Ajustes | `feature/rediseno-fase-4-ajustes` | Hecha; **el usuario la está revisando** |
| 5 Aviso | `feature/rediseno-fase-5-aviso` (sin crear) | Pendiente, sin plan |
| 6 Barra de título | `feature/rediseno-fase-6-barra-titulo` (sin crear) | Pendiente, sin plan |
| 7 Teclado | `feature/rediseno-fase-7-teclado` (sin crear) | Pendiente, sin plan |

**Rama actual:** `feature/rediseno-fase-4-ajustes` (tip `0d0f914`).

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

## Fase 5 — Aviso (lo que toca, ver spec §10)

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

## Fase 6 — Barra de título (ver spec §11)

**Ficheros:** `ui/components/TitleBar.kt` (`jvmMain`), `Main.kt`.

- `TitleBar(title, mutedAll, onToggleMuteAll, onClose)`: campana (24 dp) a la izquierda de cerrar.
  `mutedAll` → `BELL_OFF` tint `onSurface` sobre círculo `surfaceVariant`; si no → `BELL` tint
  `onSurfaceVariant` transparente. Misma lambda que la campana de la cabecera.
- Alto fijo `Layout.titleBarHeight` (38, ya en `Layout`), hairline inferior 1 dp `surfaceVariant`.
- `Main.kt`: `TitleBar("…", config.mutedAll, onToggleMuteAll) { … }` — propagar `onToggleMuteAll`
  (= `{ wiring.applyConfig(config.copy(mutedAll = !config.mutedAll)) }`) por `MainScreen` a `TitleBar`.
- Sin test de UI; repaso visual.

## Fase 7 — Teclado (ver spec §12)

**Ficheros:** `ui/theme/` (helper `focusRing`), `ImagesScreen.kt` + `ImageRow.kt`, `SettingsScreen.kt`,
`ui/dialogs/`.

- `Modifier.focusRing()`: contorno 2 dp `primary`, offset 2, **por fuera** del borde (no cambia
  tamaño). Se aplica con `interactionSource` de foco.
- Lista: `↑/↓` mueven foco entre filas, `Espacio` = Visto (solo pendientes), `Enter` = `toggleExpand`,
  `Escape` = colapsa `expandedRow`, `Tab` entra en las acciones de la fila.
- Foco visible en todos los controles de Bandeja, Ajustes y diálogos. **No** el `ToastLayer`
  (`focusable = false` por diseño).
- Añadir H-98 (o el número que toque) de navegación por teclado a las historias.

## Puntos abiertos de la fase 4 (el usuario los está revisando)

1. `AutostartPort` es interfaz plana + impl JVM (`WindowsAutostart` en `jvmMain/infrastructure/os/`),
   **no `expect`/`actual`** como pedía el spec §5.2. Justificado: un interface no necesita el
   mecanismo, y el único consumidor multiplataforma (`SettingsViewModel`) solo ve la interfaz.
2. `reg.exe` en vez de `java.util.prefs` (las prefs de Java viven en `HKCU\Software\JavaSoft\Prefs`,
   no en `Run`). Evita además añadir módulo a jlink.
3. `wipeLocalData` **cierra la app** (no recablea `Wiring`). El `ConfirmDialog` no dice que se
   cerrará — el usuario lo descubre al confirmar. Pendiente: ¿añadir ese texto al `body`?
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

## Qué pedir al retomar

1. Confirmar si la revisión de la fase 4 dejó cambios que aplicar.
2. `superpowers:writing-plans` → plan de la fase 5 (Aviso) → ejecutar con `superpowers:executing-plans`.
3. Igual para 6 y 7.
