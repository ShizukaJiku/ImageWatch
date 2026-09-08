# ImageWatch Fase 7 — Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Separar la lógica de la interfaz de su apariencia —sin cambiar nada visible— y, encima de esa separación, refinar la configuración, la capa de avisos y los ajustes.

**Architecture:** Tres PRs en orden. El primero solo escribe las historias de usuario del comportamiento actual, que son el contrato. El segundo mueve las decisiones (temporizadores, reglas de disponibilidad, medidas) de los composables al view model y a `Tokens.kt`, dejando los composables como función de estado a píxeles. El tercero añade lo nuevo ya sobre esa arquitectura.

**Tech Stack:** Kotlin + Compose Desktop (Material 3), núcleo en Java 25, JUnit 5, AssertJ (Java), `kotlin.test` (Kotlin), `kotlinx-coroutines-test`, Gradle con Spotless y Kover.

**Spec:** `docs/superpowers/specs/2026-09-05-imagewatch-fase-7-design.md`

## Global Constraints

- **Construir siempre desde PowerShell**, no desde Bash: `$env:JAVA_HOME = "C:\Users\shizu\.jdks\ms-25.0.4.1"; .\gradlew.bat build --console=plain`. Desde Bash el wrapper sale con 127 y sin mensaje.
- Si `spotlessJavaCheck` o `spotlessKotlinCheck` fallan: `.\gradlew.bat spotlessApply` y volver a construir.
- Toolchain Java **25** (`gradle/libs.versions.toml`). Kover tiene umbral sobre el núcleo: no bajar cobertura.
- Comentarios y documentación **en español**, explicando el *por qué*, como el resto del proyecto. Los nombres de test en Kotlin van en backticks y en español.
- Mensajes de commit en español, formato `<tipo>: <descripción>`, terminando con:
  ```
  Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
  Claude-Session: https://claude.ai/code/session_01QwtEJRYy6cUvZhoC6rUPRr
  ```
- Ramas: `feature/fase-7-historias` (Tarea 1), `feature/fase-7-separacion` (Tareas 2-6), `feature/fase-7-refinamiento` (Tareas 7-13). Un PR por rama, en ese orden.
- **El PR 2 no puede cambiar nada visible.** Cualquier diferencia de aspecto es un fallo del PR, no una mejora.

---

## PR 1 — Historias de usuario

### Task 1: Escribir las historias de usuario y el hueco de casos de uso

**Files:**
- Create: `docs/superpowers/specs/2026-09-05-imagewatch-historias-de-usuario.md`

**Interfaces:**
- Consumes: nada.
- Produces: el identificador de cada historia (`H-01`…) y la lista «Casos de uso sin cubrir». Las tareas 3, 5 y 9 citan historias por su identificador.

- [ ] **Step 1: Leer el comportamiento que se va a transcribir**

Leer estos ficheros enteros antes de escribir nada. Las historias tienen que describir lo que el código hace hoy, no lo que debería hacer:

```
src/main/java/io/github/shizukajiku/imagewatch/application/VersionPollingService.java
src/main/java/io/github/shizukajiku/imagewatch/application/PollingController.java
src/main/kotlin/io/github/shizukajiku/imagewatch/ui/images/ImagesViewModel.kt
src/main/kotlin/io/github/shizukajiku/imagewatch/ui/images/ImagesScreen.kt
src/main/kotlin/io/github/shizukajiku/imagewatch/ui/images/ImageRow.kt
src/main/kotlin/io/github/shizukajiku/imagewatch/ui/toast/ToastState.kt
src/main/kotlin/io/github/shizukajiku/imagewatch/ui/toast/ToastWindow.kt
src/main/kotlin/io/github/shizukajiku/imagewatch/ui/sound/Sounds.kt
src/main/kotlin/io/github/shizukajiku/imagewatch/ui/settings/SettingsViewModel.kt
src/main/kotlin/io/github/shizukajiku/imagewatch/Main.kt
```

Y los tests, que son donde el comportamiento ya está escrito en frases:

```
src/test/java/io/github/shizukajiku/imagewatch/application/VersionPollingServiceTest.java
src/test/kotlin/io/github/shizukajiku/imagewatch/ui/images/ImagesViewModelTest.kt
src/test/kotlin/io/github/shizukajiku/imagewatch/ui/toast/ToastStateTest.kt
src/test/kotlin/io/github/shizukajiku/imagewatch/ui/sound/SoundsTest.kt
src/test/kotlin/io/github/shizukajiku/imagewatch/ui/settings/SettingsViewModelTest.kt
```

- [ ] **Step 2: Escribir el documento**

Encabezado, y después una sección por área. Las áreas, en este orden: Vigilar imágenes · Sondeo · Estados de una imagen · Reconocer · Avisos · Sonidos · Ventana y bandeja · Configuración.

Cada historia sigue exactamente este formato:

```markdown
### H-07 · Reconocer una imagen retira su aviso

Como usuario que ya vio la versión nueva
quiero que al marcarla como vista desaparezca su aviso
para que la pantalla no siga anunciando algo que ya atendí.

**Dado** un aviso en pantalla para `alpha`
**Cuando** marco `alpha` como vista, en su fila o con el botón de todas
**Entonces** el aviso de `alpha` desaparece, incluso si aún esperaba turno en la cola
**Y** los avisos de las demás imágenes siguen donde estaban.

Código: `ToastState.dismissFor`, `ImagesViewModel.acknowledge` / `acknowledgeAll`
Prueba: `ToastStateTest` («descartar por imagen retira sus avisos y deja los demas»), `ImagesViewModelTest` («reconocer una imagen retira su aviso en pantalla»)
```

Reglas al escribirlas:

- Numeración correlativa `H-01`, `H-02`… sin agujeros. No se renumera después: si una historia se parte, la nueva va al final.
- **Una historia por comportamiento observable**, no por método. «El primer ciclo de la sesión no avisa» es una historia; `notifyTransitions` no lo es.
- La traza a código apunta a la clase y al método, no a números de línea, que se mueven.
- Si no existe prueba que la cubra, escribir literalmente `Prueba: —`. No inventar la referencia ni marcar como cubierta una prueba que comprueba otra cosa.

Cobertura mínima que el documento debe tener, para que el PR no se dé por hecho a medias: las cuatro peticiones de la fase 6, los cuatro estados de imagen, las tres reglas de sonido de `Sounds`, el turno de la cola de avisos, la pausa al pasar el puntero, el descarte automático, y la persistencia de la configuración.

- [ ] **Step 3: Cerrar el documento con las dos listas de salida**

Al final del documento, dos secciones:

```markdown
## Historias sin prueba

Las que quedaron con `Prueba: —`. Son el trabajo de la Tarea 3.

## Casos de uso sin cubrir

Comportamiento que la aplicación no define hoy y que habría que decidir. Cada uno con
lo que ocurre ahora y por qué es dudoso. No se implementa nada aquí.
```

En «Casos de uso sin cubrir» hay que responder al menos a estas preguntas, y añadir las que aparezcan al escribir:

- ¿Qué ve el usuario si borra todas las imágenes vigiladas?
- ¿Qué ocurre si `images.json`, `tracked-images.json` o `config.json` están corruptos o vacíos?
- ¿Qué pasa si el usuario da de alta dos veces el mismo nombre con distinta caja (`Alpha` y `alpha`)?
- ¿Qué ocurre con los avisos en cola si se borra la imagen que los produjo?
- ¿Qué debería pasar al cambiar el origen mientras hay imágenes pendientes de reconocer?

- [ ] **Step 4: Comprobar el documento contra sí mismo**

Releerlo entero comprobando cuatro cosas: la numeración no salta; ninguna historia describe algo que el código no hace; cada `Prueba:` que no sea `—` apunta a un test que existe (abrirlo y confirmar que comprueba eso); ninguna historia contradice a otra. Corregir lo que falle.

- [ ] **Step 5: Commit y PR**

```bash
git add docs/superpowers/specs/2026-09-05-imagewatch-historias-de-usuario.md
git commit -m "docs: historias de usuario del comportamiento actual"
```

Abrir el PR contra `main` describiendo cuántas historias hay, cuántas sin prueba y qué casos de uso aparecieron.

---

## PR 2 — Separación de la lógica y la apariencia

### Task 2: Crear `Tokens.kt` y migrar `ImageRow`

**Files:**
- Create: `src/main/kotlin/io/github/shizukajiku/imagewatch/ui/theme/Tokens.kt`
- Modify: `src/main/kotlin/io/github/shizukajiku/imagewatch/ui/images/ImageRow.kt`

**Interfaces:**
- Consumes: nada.
- Produces: `Space.xs/sm/md/lg/xl`, `Radius.sm/md/pill`, `TypeScale.title/body/meta/caption`, `Motion.QUICK/NORMAL/EMPHASIS/PULSE/BUMP`, `Dwell.HIGHLIGHT_MILLIS/BUMP_MILLIS`. Las tareas 4, 5, 6 y 11 leen de aquí.

- [ ] **Step 1: Escribir `Tokens.kt` con los valores actuales**

Los valores son exactamente los que ya hay en el código: esto no cambia nada visible.

```kotlin
package io.github.shizukajiku.imagewatch.ui.theme

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Las medidas y los tiempos de la interfaz, en un solo sitio, igual que el color vive en
 * `Colors.kt`. Cambiar el ritmo o la densidad no debe obligar a abrir cinco ficheros de lógica.
 *
 * Son `object` y no `CompositionLocal` a proposito: un local permitiria temas de densidad
 * distintos —compacto, comodo— y hoy no hay ninguno. Cuando exista un segundo, la migracion es
 * mecanica: `Space.md` pasa a `LocalSpace.current.md`.
 */
object Space {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
}

/** La pildora es la forma de marca: versiones, insignias, boton de agregar, buscador. */
object Radius {
    val sm = 6.dp
    val md = 10.dp
    val pill = 999.dp
}

/** Cuatro tamanos, cada uno con un oficio. Un quinto tamano es una decision nueva. */
object TypeScale {
    val title = 16.sp
    val body = 13.sp
    val meta = 12.sp
    val caption = 11.sp
}

/** Ritmo de las animaciones, en milisegundos. */
object Motion {
    const val QUICK = 220
    const val NORMAL = 320
    const val EMPHASIS = 400
    const val PULSE = 700
    const val BUMP = 600
}

/**
 * Cuanto vive un aviso visual antes de apagarse solo. Es UX y no animacion: dice cuanto dura el
 * mensaje, no como se mueve.
 */
object Dwell {
    const val HIGHLIGHT_MILLIS = 4000L
    const val BUMP_MILLIS = 3000L
}
```

- [ ] **Step 2: Migrar `ImageRow.kt` a los tokens**

Sustituir en `ImageRow.kt`, sin cambiar ningún valor:

- `private const val PULSE_MILLIS = 700` y `BUMP_MILLIS = 600` se borran; sus usos pasan a `Motion.PULSE` y `Motion.BUMP`.
- `RoundedCornerShape(6.dp)` del tooltip pasa a `RoundedCornerShape(Radius.sm)`.
- Los `fontSize = 13.sp` pasan a `TypeScale.body`, `11.sp` a `TypeScale.caption`.
- Los paddings: `horizontal = 20.dp` a `Space.xl`, `vertical = 12.dp` a `Space.md`, `top = 4.dp` a `Space.xs`, `start = 16.dp` a `Space.lg`, `spacedBy(2.dp)` se queda con su literal y un comentario: es una separación óptica entre iconos, no un escalón de la escala.
- `Modifier.size(16.dp)` y compañía **no** se tocan en esta tarea: los tamaños de icono son otra escala y entran en la Tarea 6.

Dejar `PULSE_MIN_ALPHA` donde está: es un valor de la animación concreta, no de la escala.

- [ ] **Step 3: Construir y comprobar que nada cambió**

```powershell
$env:JAVA_HOME = "C:\Users\shizu\.jdks\ms-25.0.4.1"; .\gradlew.bat build --console=plain
```

Esperado: `BUILD SUCCESSFUL`. Ningún test debería cambiar de resultado: no se ha tocado comportamiento.

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/io/github/shizukajiku/imagewatch/ui/theme/Tokens.kt src/main/kotlin/io/github/shizukajiku/imagewatch/ui/images/ImageRow.kt
git commit -m "refactor: extraer las medidas y los tiempos de la interfaz a Tokens.kt"
```

---

### Task 3: Escribir las pruebas que faltan, antes de mover nada

**Files:**
- Modify: `src/test/kotlin/io/github/shizukajiku/imagewatch/ui/toast/ToastStateTest.kt`
- Modify: `src/test/kotlin/io/github/shizukajiku/imagewatch/ui/images/ImagesViewModelTest.kt`

**Interfaces:**
- Consumes: la lista «Historias sin prueba» de la Tarea 1.
- Produces: pruebas que fijan el comportamiento actual. Las tareas 4 y 5 no pueden cambiarlas: si una falla después de mover código, el movimiento está mal.

- [ ] **Step 1: Escribir una prueba por historia sin cubrir**

Para cada entrada de «Historias sin prueba», escribir la prueba **del comportamiento de hoy**, en la clase de test que corresponda a la clase que lo implementa hoy. Si el comportamiento vive dentro de un composable y por tanto no se puede probar todavía —el temporizador del toast, los dos temporizadores de `ImagesScreen`—, **no** se escribe aquí: se escribe en la tarea que lo mueve (4 y 5), que es cuando pasa a ser probable.

Anotar en el PR cuáles quedaron para 4 y 5, para que no se pierdan.

Ejemplo del tipo de prueba que sale de aqui, para la historia «el primer ciclo de la sesion no
avisa» si hubiera quedado sin cubrir:

```kotlin
@Test
fun `una imagen sin linea base no puede reconocerse`() {
    val fixture = fixture(listOf("alpha"))
    fixture.service.poll()

    fixture.viewModel.acknowledge("alpha")

    assertEquals(ImageStatus.UNKNOWN, fixture.viewModel.state.value.rows.single().status)
}
```

- [ ] **Step 2: Ejecutar y ver pasar**

```powershell
$env:JAVA_HOME = "C:\Users\shizu\.jdks\ms-25.0.4.1"; .\gradlew.bat test --console=plain
```

Esperado: PASS. Estas pruebas describen lo que ya funciona; si alguna falla, o la historia está mal escrita o hay un fallo que hay que reportar antes de seguir.

- [ ] **Step 3: Commit**

```bash
git add src/test
git commit -m "test: cubrir las historias que no tenian prueba"
```

---

### Task 4: Subir el reloj del aviso a `ToastState`

**Files:**
- Modify: `src/main/kotlin/io/github/shizukajiku/imagewatch/ui/toast/ToastState.kt`
- Modify: `src/main/kotlin/io/github/shizukajiku/imagewatch/ui/toast/ToastWindow.kt`
- Modify: `src/main/kotlin/io/github/shizukajiku/imagewatch/Main.kt:~90` (construcción de `ToastState` en `Wiring`)
- Test: `src/test/kotlin/io/github/shizukajiku/imagewatch/ui/toast/ToastStateTest.kt`

**Interfaces:**
- Consumes: `Dwell`, `Motion` (Tarea 2).
- Produces: `ToastState(scope: CoroutineScope, duration: () -> Duration)`; `Toast(id, title, body, imageName, leaving: Boolean)`; `ToastState.pause(id)`, `resume(id)`, `exitFinished(id)`. La Tarea 6 y el PR 3 los usan.

**Frontera:** `ToastState` decide **cuándo** un aviso se va —temporizador, pausa, tiempo restante—; la tarjeta decide **cómo** se va y avisa al terminar con `exitFinished`. El reloj arriba, la animación abajo.

- [ ] **Step 1: Escribir las pruebas del reloj (fallan)**

```kotlin
@Test
fun `un aviso se retira solo al agotarse su tiempo`() = runTest {
    val state = ToastState(backgroundScope) { Duration.ofSeconds(5) }
    state.show(listOf(pending("alpha", "1.0.0", "1.1.0")))

    advanceTimeBy(5_001)

    assertTrue(state.toasts.value.single().leaving, "Se marca saliente, no se borra de golpe")
    state.exitFinished(state.toasts.value.single().id)
    assertTrue(state.toasts.value.isEmpty())
}

@Test
fun `el puntero encima pausa el descarte y al salir sigue con lo que quedaba`() = runTest {
    val state = ToastState(backgroundScope) { Duration.ofSeconds(5) }
    state.show(listOf(pending("alpha", "1.0.0", "1.1.0")))
    val id = state.toasts.value.single().id

    advanceTimeBy(3_000)
    state.pause(id)
    advanceTimeBy(60_000)
    assertFalse(state.toasts.value.single().leaving, "Con el puntero encima no se va")

    state.resume(id)
    advanceTimeBy(1_500)
    assertFalse(state.toasts.value.single().leaving, "Quedaban 2 s, no 0")
    advanceTimeBy(600)
    assertTrue(state.toasts.value.single().leaving)
}

@Test
fun `el que espera turno no gasta su tiempo hasta que se pinta`() = runTest {
    val state = ToastState(backgroundScope) { Duration.ofSeconds(5) }
    repeat(TOASTS_VISIBLES + 1) { i -> state.show(listOf(pending("img$i", "1.0.0", "1.1.0"))) }
    val ultimo = state.toasts.value.last().id

    advanceTimeBy(5_001)

    assertTrue(state.toasts.value.any { it.id == ultimo && !it.leaving }, "Entra ahora, no se pierde")
}
```

Añadir los imports: `kotlinx.coroutines.test.runTest`, `kotlinx.coroutines.test.advanceTimeBy`, `kotlin.test.assertFalse`, `java.time.Duration`.

- [ ] **Step 2: Ejecutar y ver fallar**

```powershell
$env:JAVA_HOME = "C:\Users\shizu\.jdks\ms-25.0.4.1"; .\gradlew.bat test --tests "*ToastStateTest*" --console=plain
```

Esperado: no compila —`ToastState` no acepta esos parámetros y `Toast` no tiene `leaving`—. Ese es el fallo esperado.

- [ ] **Step 3: Implementar el reloj en `ToastState`**

`Toast` gana `leaving: Boolean = false`. `ToastState` recibe el scope y la duración vigente como función —la duración se cambia en Ajustes mientras la aplicación corre, así que capturarla congelaría el ajuste—. Un job por aviso **visible**: los que esperan turno no gastan su tiempo, que es la regla que ya existía por vivir el temporizador dentro de la tarjeta pintada.

```kotlin
class ToastState(
    private val scope: CoroutineScope,
    private val duration: () -> Duration,
) {
    private val sequence = AtomicLong()
    private val mutableToasts = MutableStateFlow<List<Toast>>(emptyList())
    private val jobs = ConcurrentHashMap<Long, Job>()
    private val remaining = ConcurrentHashMap<Long, Long>()

    val toasts: StateFlow<List<Toast>> = mutableToasts.asStateFlow()

    fun show(updates: List<ImageState>) { /* como hoy, y despues */ sincronizarRelojes() }

    fun dismiss(id: Long) { detener(id); mutableToasts.update { l -> l.filterNot { it.id == id } }; sincronizarRelojes() }

    fun dismissFor(imageName: String) { /* como hoy, deteniendo los jobs de los retirados */ }

    /** El puntero encima: se detiene el reloj y se conserva lo que quedaba. */
    fun pause(id: Long) { detener(id) }

    /** El puntero fuera: sigue con lo que quedaba, no con el total. */
    fun resume(id: Long) { sincronizarRelojes() }

    /** La tarjeta terminó su animación de salida: ahora sí se saca de la cola. */
    fun exitFinished(id: Long) { dismiss(id) }
}
```

Detalles que la implementación debe respetar, y que las pruebas del paso 1 comprueban:

- `remaining[id]` se inicializa con `duration().toMillis()` al mostrarse y se descuenta al pausar.
- `sincronizarRelojes()` arranca un job para cada uno de los `TOASTS_VISIBLES` primeros que no tenga job ni esté pausado ni saliente, y no toca los demás.
- Al agotarse, el job marca `leaving = true` y **no** borra: borra `exitFinished`.
- Mínimo de un segundo visible, como hoy: si `duration()` viene a 0 o negativa —`AppConfig.fromEnvironment()` no la valida—, se usa 1000 ms.

- [ ] **Step 4: Ejecutar y ver pasar**

```powershell
$env:JAVA_HOME = "C:\Users\shizu\.jdks\ms-25.0.4.1"; .\gradlew.bat test --tests "*ToastStateTest*" --console=plain
```

Esperado: PASS, incluidas las pruebas antiguas de cola y turno, que no se tocan.

- [ ] **Step 5: Adelgazar `ToastCard`**

En `ToastWindow.kt`: quitar de `ToastCard` el `Animatable` del progreso, el cálculo de `remaining` y los `LaunchedEffect` del temporizador. Lo que queda:

- `hovered` sigue siendo estado local, pero ahora llama a `onPause` / `onResume` en vez de cancelar un efecto.
- La visibilidad de salida se ata a `toast.leaving`; al terminar la animación, la tarjeta llama a `onExitFinished(toast.id)`.
- La barrita de progreso se pinta desde el estado que publica `ToastState`; si eso obliga a exponer el progreso, exponerlo como fracción (`0f..1f`) en `Toast`, no como milisegundos: cuánto queda es del reloj, cómo se dibuja es de la tarjeta.
- `ToastLayer` pasa `onPause`, `onResume` y `onExitFinished` hacia abajo.

En `Main.kt`, `Wiring` construye `ToastState(scope, { config.value.toastDuration() })` con un scope propio (`CoroutineScope(SupervisorJob() + Dispatchers.Default)`), y lo cancela donde ya se cierran `controller` y `sounds` al salir.

- [ ] **Step 6: Construir, ejecutar la aplicación y mirar los avisos**

```powershell
$env:JAVA_HOME = "C:\Users\shizu\.jdks\ms-25.0.4.1"; .\gradlew.bat build --console=plain
$env:JAVA_HOME = "C:\Users\shizu\.jdks\ms-25.0.4.1"; .\gradlew.bat run --console=plain
```

Comprobar con la aplicación delante, porque es la pieza que la spec señala como delicada: el aviso se desliza al salir —no se esfuma—, el puntero encima lo detiene, al retirarlo sigue con lo que quedaba, y el quinto entra cuando se va uno.

- [ ] **Step 7: Commit**

```bash
git add src/main/kotlin/io/github/shizukajiku/imagewatch/ui/toast src/main/kotlin/io/github/shizukajiku/imagewatch/Main.kt src/test/kotlin/io/github/shizukajiku/imagewatch/ui/toast
git commit -m "refactor: el reloj del aviso vive en ToastState y la animacion en la tarjeta"
```

---

### Task 5: Estado de intención y temporizadores en `ImagesViewModel`

**Files:**
- Modify: `src/main/kotlin/io/github/shizukajiku/imagewatch/ui/images/ImagesViewModel.kt`
- Modify: `src/main/kotlin/io/github/shizukajiku/imagewatch/ui/images/ImagesScreen.kt`
- Modify: `src/main/kotlin/io/github/shizukajiku/imagewatch/ui/images/ImageRow.kt`
- Modify: `src/main/kotlin/io/github/shizukajiku/imagewatch/Main.kt`
- Test: `src/test/kotlin/io/github/shizukajiku/imagewatch/ui/images/ImagesViewModelTest.kt`

**Interfaces:**
- Consumes: `Dwell.HIGHLIGHT_MILLIS`, `Dwell.BUMP_MILLIS` (Tarea 2).
- Produces: `enum class RowEmphasis { NINGUNO, SENALADA, NOVEDAD }`; `ImageRowState.canAcknowledge: Boolean`; `ImageRowState.emphasis: RowEmphasis`; `ImagesUiState.canAcknowledgeAll: Boolean`; `ImagesViewModel(…, scope: CoroutineScope)`. Desaparecen `ImagesUiState.highlighted` y `ImagesUiState.bumped`, y con ellos `clearHighlight()` y `clearBumps()` como API pública de la pantalla.

- [ ] **Step 1: Escribir las pruebas (fallan)**

```kotlin
@Test
fun `solo se puede reconocer lo que esta pendiente`() {
    val fixture = fixture(listOf("alpha"), localVersion = "1.0.0", remoteVersion = "2.0.0")
    fixture.service.poll()

    val fila = fixture.viewModel.state.value.rows.single()

    assertTrue(fila.canAcknowledge)
    assertTrue(fixture.viewModel.state.value.canAcknowledgeAll)
}

@Test
fun `el resaltado se apaga solo al cumplirse su tiempo`() = runTest {
    val fixture = fixture(listOf("alpha"), scope = backgroundScope)
    fixture.viewModel.highlight("alpha")
    assertEquals(RowEmphasis.SENALADA, fixture.viewModel.state.value.rows.single().emphasis)

    advanceTimeBy(Dwell.HIGHLIGHT_MILLIS + 100)

    assertEquals(RowEmphasis.NINGUNO, fixture.viewModel.state.value.rows.single().emphasis)
}

@Test
fun `una novedad late su tiempo aunque entre otro ciclo`() = runTest {
    val fixture = fixture(
        listOf("alpha", "beta"),
        localVersion = "1.0.0",
        remoteVersion = "2.0.0",
        scope = backgroundScope,
    )
    fixture.service.poll()
    fixture.source.version = "3.0.0"
    fixture.service.poll()
    assertEquals(RowEmphasis.NOVEDAD, fixture.viewModel.state.value.rows.first().emphasis)

    // Un ciclo sin novedades no puede apagar el latido del anterior antes de tiempo.
    advanceTimeBy(500)
    fixture.service.poll()

    assertEquals(RowEmphasis.NOVEDAD, fixture.viewModel.state.value.rows.first().emphasis)
}
```

El `fixture` gana un parámetro `scope: CoroutineScope = CoroutineScope(Dispatchers.Unconfined)` que se pasa al `ImagesViewModel`.

- [ ] **Step 2: Ejecutar y ver fallar**

```powershell
$env:JAVA_HOME = "C:\Users\shizu\.jdks\ms-25.0.4.1"; .\gradlew.bat test --tests "*ImagesViewModelTest*" --console=plain
```

Esperado: no compila; `RowEmphasis`, `canAcknowledge` y el parámetro `scope` no existen.

- [ ] **Step 3: Implementar en el view model**

```kotlin
/** Que quiere decir la fila, no como se pinta. La capa visual elige el latido o el borde. */
enum class RowEmphasis { NINGUNO, SENALADA, NOVEDAD }
```

- `ImageRowState` gana `canAcknowledge: Boolean` —`status == ImageStatus.PENDING`— y `emphasis: RowEmphasis`, que `derive()` calcula: `SENALADA` si el nombre es el resaltado, `NOVEDAD` si está entre las novedades, y `NINGUNO` en otro caso. `SENALADA` gana cuando coinciden: el resaltado responde a un clic del usuario.
- `ImagesUiState` gana `canAcknowledgeAll` —`pending > 0`— y pierde `highlighted` y `bumped`, que pasan a ser privados del view model.
- El constructor recibe `private val scope: CoroutineScope`. `highlight(name)` cancela el job anterior de resaltado, publica y lanza uno que espera `Dwell.HIGHLIGHT_MILLIS` y limpia. `onSnapshot` **acumula** las novedades —`current.bumped + nuevas`, que es el hallazgo LOW de la revisión de la fase 6— y por cada tanda lanza un job que espera `Dwell.BUMP_MILLIS` y retira **solo las de esa tanda**.
- `close()` cancela los jobs pendientes además de desengancharse del servicio.

- [ ] **Step 4: Ejecutar y ver pasar**

```powershell
$env:JAVA_HOME = "C:\Users\shizu\.jdks\ms-25.0.4.1"; .\gradlew.bat test --tests "*ImagesViewModelTest*" --console=plain
```

Esperado: PASS, incluidas las de la fase 6, que siguen valiendo.

- [ ] **Step 5: Adelgazar la pantalla y la fila**

- `ImagesScreen`: se van los dos `LaunchedEffect` con `delay` y las constantes `HIGHLIGHT_DURATION_MILLIS` y `BUMP_DURATION_MILLIS`; se van los parámetros `onClearHighlight` y `onClearBumps`. El desplazamiento a la fila señalada se queda —es reacción a estado, no una regla— y busca `rows.indexOfFirst { it.emphasis == RowEmphasis.SENALADA }`. El botón de reconocer todas se dibuja con `state.canAcknowledgeAll`, no con `state.pending > 0`.
- `ImageRow`: recibe `emphasis` en lugar de `highlighted` y `bumped`, y decide el fondo con un `when` sobre el enum. El botón de reconocer se dibuja con `row.canAcknowledge`, no con `row.status == ImageStatus.PENDING`.
- `Main.kt`: construye el view model con un scope propio y quita las lambdas que ya no existen.

- [ ] **Step 6: Construir y comprobar que no cambió nada visible**

```powershell
$env:JAVA_HOME = "C:\Users\shizu\.jdks\ms-25.0.4.1"; .\gradlew.bat build --console=plain
$env:JAVA_HOME = "C:\Users\shizu\.jdks\ms-25.0.4.1"; .\gradlew.bat run --console=plain
```

Con la aplicación delante: «Ver» sigue resaltando la fila y apagándose a los 4 s, el latido de una versión sobre otra pendiente sigue durando 3 s, y los botones aparecen y desaparecen igual que antes.

- [ ] **Step 7: Commit**

```bash
git add src/main/kotlin/io/github/shizukajiku/imagewatch/ui src/main/kotlin/io/github/shizukajiku/imagewatch/Main.kt src/test/kotlin/io/github/shizukajiku/imagewatch/ui/images
git commit -m "refactor: la fila recibe intencion y el view model mide el tiempo"
```

---

### Task 6: Terminar la migración a tokens y verificar los tres criterios

**Files:**
- Modify: `src/main/kotlin/io/github/shizukajiku/imagewatch/ui/images/ImagesScreen.kt`
- Modify: `src/main/kotlin/io/github/shizukajiku/imagewatch/ui/settings/SettingsScreen.kt`
- Modify: `src/main/kotlin/io/github/shizukajiku/imagewatch/ui/toast/ToastWindow.kt`
- Modify: `src/main/kotlin/io/github/shizukajiku/imagewatch/ui/components/VersionPill.kt`
- Modify: `src/main/kotlin/io/github/shizukajiku/imagewatch/ui/components/StatusBadge.kt`
- Modify: `src/main/kotlin/io/github/shizukajiku/imagewatch/ui/components/TitleBar.kt`
- Modify: `src/main/kotlin/io/github/shizukajiku/imagewatch/ui/theme/Tokens.kt`

**Interfaces:**
- Consumes: los objetos de la Tarea 2.
- Produces: `IconSize.sm/md/lg` en `Tokens.kt`. La Tarea 11 lo usa.

- [ ] **Step 1: Añadir la escala de iconos a `Tokens.kt`**

```kotlin
/** Tres tamanos de icono: en linea con el texto, de accion y de cabecera. */
object IconSize {
    val sm = 14.dp
    val md = 16.dp
    val lg = 18.dp
}
```

Los tamaños de hoy —13, 14, 15, 16, 17 y 18 dp— se reparten en esos tres. Es el único cambio visible que este PR admite, de un dp o dos por icono, y hay que decirlo en el PR.

- [ ] **Step 2: Migrar los ficheros que quedan**

Sustituir literales por tokens en los seis ficheros. `SLIDE_MILLIS` de `VersionPill` pasa a `Motion.NORMAL`, `TRANSITION_MILLIS` de `StatusBadge` a `Motion.EMPHASIS`, `EXIT_MILLIS` de `ToastWindow` a `Motion.QUICK`. `RoundedCornerShape(999.dp)` a `Radius.pill` en sus cuatro sitios, `RoundedCornerShape(10.dp)` del toast a `Radius.md`.

Se quedan con su literal y un comentario que lo explique: `STATUS_WIDTH`, `TOAST_WIDTH`, `LAYER_HEIGHT` y `MARGIN`. No son estilo: son compensaciones de composición, y `LAYER_HEIGHT` además desaparece en la Tarea 9.

- [ ] **Step 3: Verificar los tres criterios de la separación**

```bash
grep -rn "delay(" src/main/kotlin --include=*.kt | grep -v "/toast/ToastState.kt" | grep -v "/images/ImagesViewModel.kt"
grep -rn "ImageStatus\." src/main/kotlin/io/github/shizukajiku/imagewatch/ui --include=*.kt | grep -v ViewModel
grep -rnE "[0-9]+\.(dp|sp)" src/main/kotlin --include=*.kt | grep -v "/theme/"
```

Los tres deben salir vacíos salvo las excepciones anotadas —los anchos de composición del paso 2 y `PULSE_MIN_ALPHA`—. Lo que aparezca de más, se migra; lo que se deje, se justifica con un comentario en el propio código.

- [ ] **Step 4: Construir, ejecutar y comparar**

```powershell
$env:JAVA_HOME = "C:\Users\shizu\.jdks\ms-25.0.4.1"; .\gradlew.bat build --console=plain
$env:JAVA_HOME = "C:\Users\shizu\.jdks\ms-25.0.4.1"; .\gradlew.bat run --console=plain
```

Recorrer la aplicación entera: lista, ajustes, diálogos, avisos, tema claro y oscuro. Nada debe verse distinto salvo el ajuste de uno o dos dp en algunos iconos.

- [ ] **Step 5: Commit y PR**

```bash
git add src/main/kotlin
git commit -m "refactor: completar la migracion de medidas y tiempos a Tokens.kt"
```

Abrir el PR contra `main`. En la descripción: los tres `grep` de verificación con su salida, y la lista de iconos cuyo tamaño se ajustó.

---

## PR 3 — Refinamiento

### Task 7: Un solo cálculo de «pendiente con novedad» en el núcleo

**Files:**
- Modify: `src/main/java/io/github/shizukajiku/imagewatch/application/VersionPollingService.java`
- Modify: `src/main/kotlin/io/github/shizukajiku/imagewatch/ui/images/ImagesViewModel.kt`
- Test: `src/test/java/io/github/shizukajiku/imagewatch/application/VersionPollingServiceTest.java`

**Interfaces:**
- Consumes: nada.
- Produces: `public static List<ImageState> pendingNews(PollSnapshot previous, PollSnapshot current)` en `VersionPollingService`. La Tarea 8 y `ImagesViewModel` leen de ahí.

Es el hallazgo MEDIUM de la revisión de la fase 6: hoy la regla está escrita dos veces, en `VersionPollingService.pendingNews` y en `ImagesViewModel.remoteBumps`, con distinto filtro y dos suites de test independientes.

- [ ] **Step 1: Escribir la prueba de la regla única (falla)**

```java
@Test
void pendingNewsDistingueLaPrimeraPendienteDeLaSegundaVersion() {
    var previous = snapshotDe(imagen("alpha", "1.0.0", "2.0.0", ImageStatus.PENDING));
    var current = snapshotDe(imagen("alpha", "1.0.0", "3.0.0", ImageStatus.PENDING));

    assertThat(VersionPollingService.pendingNews(previous, current))
        .extracting(ImageState::name)
        .containsExactly("alpha");
    assertThat(VersionPollingService.pendingNews(current, current)).isEmpty();
}
```

Con dos ayudantes privados en el test, `imagen(nombre, local, remota, estado)` y `snapshotDe(vararg)`, que construyen `ImageState` y `PollSnapshot` directamente.

- [ ] **Step 2: Ejecutar y ver fallar**

Run: `.\gradlew.bat test --tests "*VersionPollingServiceTest*" --console=plain`
Expected: no compila; `pendingNews` es privado.

- [ ] **Step 3: Hacerlo público y estático, y que el view model lo use**

`pendingNews` pasa a `public static`; su cuerpo no cambia. En `ImagesViewModel`, `remoteBumps` se borra y `onSnapshot` usa:

```kotlin
val bumped = VersionPollingService.pendingNews(snapshot, received)
    .filter { image -> snapshot.find(image.name()).map { it.status() == ImageStatus.PENDING }.orElse(false) }
    .map { it.name() }
    .toSet()
```

El filtro de la interfaz —solo las que **ya** estaban pendientes— se queda aquí y a la vista, porque es una decisión de presentación: una imagen que acaba de pasar a pendiente ya se anuncia con su insignia y su aviso, y señalarla además como «otra más» diría algo que no ocurrió. Lo que deja de estar duplicado es la comparación de versiones.

- [ ] **Step 4: Ejecutar y ver pasar**

Run: `.\gradlew.bat test --console=plain`
Expected: PASS, incluidas las pruebas de novedad del view model de la Tarea 5.

- [ ] **Step 5: Commit**

```bash
git add src/main/java src/main/kotlin src/test/java
git commit -m "refactor: una sola regla de pendiente con novedad, en el nucleo"
```

---

### Task 8: Cerrar el TOCTOU de `acknowledgeAll`

**Files:**
- Modify: `src/main/java/io/github/shizukajiku/imagewatch/application/VersionPollingService.java`
- Modify: `src/main/kotlin/io/github/shizukajiku/imagewatch/ui/images/ImagesViewModel.kt`
- Test: `src/test/kotlin/io/github/shizukajiku/imagewatch/ui/images/ImagesViewModelTest.kt`

**Interfaces:**
- Consumes: nada.
- Produces: `public List<String> acknowledgeAll()` —antes `void`— devolviendo los nombres realmente reconocidos.

Hallazgo MEDIUM de la revisión: el view model lee las pendientes **antes** de reconocer; si el planificador publica un ciclo justo en medio, esa imagen se reconoce pero su aviso se queda en pantalla anunciando algo ya atendido.

- [ ] **Step 1: Escribir la prueba (falla)**

```kotlin
@Test
fun `reconocer todas retira tambien el aviso de la que entro durante el reconocimiento`() {
    val retirados = mutableListOf<String>()
    val fixture = fixture(
        listOf("alpha", "beta"),
        localVersion = "1.0.0",
        remoteVersion = "2.0.0",
        dismissToastsFor = retirados::add,
    )
    fixture.service.poll()

    fixture.viewModel.acknowledgeAll()

    // Los nombres salen de lo que el servicio reconocio de verdad, no de lo leido antes.
    assertEquals(listOf("alpha", "beta"), retirados.sorted())
}
```

- [ ] **Step 2: Ejecutar y ver fallar**

Run: `.\gradlew.bat test --tests "*ImagesViewModelTest*" --console=plain`
Expected: la prueba compila pero no distingue nada todavía; falla en cuanto `acknowledgeAll()` devuelva `List<String>` y el view model deje de usar la lista previa. Escribir primero el cambio de firma en el test hace visible la intención.

- [ ] **Step 3: Implementar**

En `VersionPollingService.acknowledgeAll()`: devolver `releases.stream().map(ImageRelease::name).toList()` —ya se calcula ahí dentro— y `List.of()` en la salida temprana. En `ImagesViewModel.acknowledgeAll()`: borrar la lectura previa de `snapshot.pending()` y usar lo devuelto:

```kotlin
val reconocidas = service.acknowledgeAll()
snapshot = service.lastSnapshot()
reconocidas.forEach(dismissToastsFor)
```

- [ ] **Step 4: Ejecutar y ver pasar**

Run: `.\gradlew.bat test --console=plain`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java src/main/kotlin src/test/kotlin
git commit -m "fix: reconocer todas retira los avisos de lo que de verdad se reconocio"
```

---

### Task 9: Geometría de la capa de avisos

**Files:**
- Create: `src/main/kotlin/io/github/shizukajiku/imagewatch/ui/toast/ToastPlacement.kt`
- Create: `src/test/kotlin/io/github/shizukajiku/imagewatch/ui/toast/ToastPlacementTest.kt`
- Modify: `src/main/kotlin/io/github/shizukajiku/imagewatch/ui/toast/ToastWindow.kt`
- Modify: `src/main/kotlin/io/github/shizukajiku/imagewatch/ui/theme/Tokens.kt`

**Interfaces:**
- Consumes: `TOASTS_VISIBLES` (`ToastState.kt`), `Space` (Tarea 2).
- Produces: `data class ToastPlacement(val xDp: Float, val yDp: Float, val heightDp: Float)` y `fun toastLayerPlacement(bounds: Rectangle, escala: Float, avisos: Int, anchoDp: Float = TOAST_WIDTH_DP, altoTarjetaDp: Float = TOAST_CARD_HEIGHT_DP): ToastPlacement`.

Tres síntomas, una raíz: `ToastWindow.kt` calcula la posición con píxeles de AWT y los pasa como `dp`, y fija el alto en 420. Con escalado al 125 % o 150 % la ventana baja de donde debería y la última tarjeta queda bajo la barra de tareas; y en 420 dp no caben cuatro tarjetas, así que solo se pintan tres.

**Observado por el usuario con la aplicación delante (2026-09-05):** la barrita de progreso del
aviso de más abajo **no se ve, y solo cuando hay tres avisos a la vez**. Con uno o dos no ocurre.
Encaja con las dos causas: con tres tarjetas el contenido llega al borde inferior de una ventana de
420 dp que además está colocada más abajo de lo que debería, y esa franja se la come la barra de
tareas. Es el caso de reproducción que esta tarea tiene que cerrar.

**Confirmado con la aplicación delante (verificación del PR 2):** solo se ven **tres** avisos a la
vez aunque `TOASTS_VISIBLES` sea 4, y **la capa se puede desplazar con la rueda**. Ese scroll es la
prueba del diagnóstico: las cuatro tarjetas están compuestas, pero no caben en los 420 dp de la
ventana, así que el `LazyColumn` las deja desplazar en vez de mostrarlas. Una capa de avisos que se
desplaza es un fallo en sí misma —nadie va a hacer scroll en un aviso que se va solo en ocho
segundos—, así que el arreglo tiene que dejarla sin scroll posible: si se puede desplazar, el alto
sigue siendo insuficiente.

- [ ] **Step 1: Escribir las pruebas de la función pura (fallan)**

```kotlin
class ToastPlacementTest {

    // 1920x1080 sin escalado, barra de tareas abajo de 40 px: el area util empieza en 0,0.
    private val pantallaHd = Rectangle(0, 0, 1920, 1040)

    @Test
    fun `sin escalado la capa se ancla abajo a la derecha dentro del area util`() {
        val p = toastLayerPlacement(pantallaHd, escala = 1f, avisos = 4)

        assertEquals(1920f - 340f - 16f, p.xDp)
        assertEquals(1040f - p.heightDp - 16f, p.yDp)
    }

    @Test
    fun `con escalado al 150 por ciento la posicion se expresa en dp, no en pixeles`() {
        // 2880x1620 fisicos al 150% son 1920x1080 en dp: la capa debe caer donde caeria sin escalar.
        val p = toastLayerPlacement(Rectangle(0, 0, 2880, 1560), escala = 1.5f, avisos = 4)

        assertEquals(1920f - 340f - 16f, p.xDp)
        assertEquals(1040f - p.heightDp - 16f, p.yDp)
    }

    @Test
    fun `el alto crece con el numero de avisos y cabe el cuarto`() {
        val uno = toastLayerPlacement(pantallaHd, escala = 1f, avisos = 1)
        val cuatro = toastLayerPlacement(pantallaHd, escala = 1f, avisos = 4)

        assertTrue(cuatro.heightDp > uno.heightDp)
        assertEquals(4 * TOAST_CARD_HEIGHT_DP, cuatro.heightDp)
    }

    @Test
    fun `con tres avisos la ultima tarjeta cabe entera por encima del borde inferior`() {
        // El caso que el usuario reproduce: con tres avisos, la barrita del de mas abajo quedaba
        // tapada por la barra de tareas.
        val p = toastLayerPlacement(pantallaHd, escala = 1f, avisos = 3)

        assertEquals(3 * TOAST_CARD_HEIGHT_DP, p.heightDp)
        // El borde inferior de la capa queda dentro del area util, con su margen.
        assertEquals(1040f - 16f, p.yDp + p.heightDp)
    }

    @Test
    fun `con la barra de tareas arriba la capa no se mete debajo`() {
        // maximumWindowBounds devuelve y=40 cuando la barra esta arriba.
        val p = toastLayerPlacement(Rectangle(0, 40, 1920, 1040), escala = 1f, avisos = 2)

        assertTrue(p.yDp >= 40f, "La capa empieza dentro del area util")
        assertEquals(40f + 1040f - p.heightDp - 16f, p.yDp)
    }

    @Test
    fun `nunca pide mas alto del que hay`() {
        val p = toastLayerPlacement(Rectangle(0, 0, 1920, 200), escala = 1f, avisos = 4)

        assertTrue(p.heightDp <= 200f - 16f)
        assertTrue(p.yDp >= 0f)
    }
}
```

- [ ] **Step 2: Ejecutar y ver fallar**

Run: `.\gradlew.bat test --tests "*ToastPlacementTest*" --console=plain`
Expected: no compila; no existe `ToastPlacement.kt`.

- [ ] **Step 3: Implementar la función pura**

```kotlin
package io.github.shizukajiku.imagewatch.ui.toast

import java.awt.Rectangle

/** Ancho de la capa de avisos, en dp. */
const val TOAST_WIDTH_DP = 340f

/**
 * Alto de una tarjeta con su separacion, en dp. Titulo y cuerpo van a una linea con puntos
 * suspensivos, asi que la tarjeta no crece con el texto y el alto de la capa se puede calcular.
 */
const val TOAST_CARD_HEIGHT_DP = 104f

private const val MARGIN_DP = 16f

/** Donde y de que tamano va la capa de avisos. En dp: quien la pinta no vuelve a ver pixeles. */
data class ToastPlacement(val xDp: Float, val yDp: Float, val heightDp: Float)

/**
 * Traduce el area util de la pantalla —que ya descuenta la barra de tareas— a la posicion y el
 * alto de la capa.
 *
 * `bounds` viene en pixeles fisicos y Compose posiciona en dp: dividir por la escala es lo que
 * faltaba. Sin eso, en una pantalla al 150 % la capa caia un tercio mas abajo y a la derecha de
 * donde debia, y la ultima tarjeta —con su barrita— se metia debajo de la barra de tareas.
 *
 * El alto sale del numero de avisos, no de un literal: con 420 dp fijos no cabia el cuarto y por
 * eso solo se veian tres.
 */
fun toastLayerPlacement(
    bounds: Rectangle,
    escala: Float,
    avisos: Int,
    anchoDp: Float = TOAST_WIDTH_DP,
    altoTarjetaDp: Float = TOAST_CARD_HEIGHT_DP,
): ToastPlacement {
    val xDp = bounds.x / escala
    val yDp = bounds.y / escala
    val anchoUtilDp = bounds.width / escala
    val altoUtilDp = bounds.height / escala
    val alto = (avisos * altoTarjetaDp).coerceAtMost(altoUtilDp - MARGIN_DP)
    return ToastPlacement(
        xDp = xDp + anchoUtilDp - anchoDp - MARGIN_DP,
        yDp = (yDp + altoUtilDp - alto - MARGIN_DP).coerceAtLeast(yDp),
        heightDp = alto,
    )
}
```

- [ ] **Step 4: Ejecutar y ver pasar**

Run: `.\gradlew.bat test --tests "*ToastPlacementTest*" --console=plain`
Expected: PASS.

- [ ] **Step 5: Usarla en `ToastWindow`**

- Se van `TOAST_WIDTH`, `LAYER_HEIGHT` y `MARGIN`.
- La escala sale de la configuración gráfica del dispositivo por defecto: `GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration.defaultTransform.scaleX.toFloat()`.
- `remember(bounds, escala, visibles)` recalcula el `WindowPosition` y el tamaño cuando cambia el número de avisos visibles, para que la capa crezca y mengüe con la cola.
- La tarjeta fija su alto en `TOAST_CARD_HEIGHT_DP` y pone `maxLines = 1` con `TextOverflow.Ellipsis` en título y cuerpo.

- [ ] **Step 6: Ejecutar la aplicación y contar los avisos**

```powershell
$env:JAVA_HOME = "C:\Users\shizu\.jdks\ms-25.0.4.1"; .\gradlew.bat run --console=plain
```

Con seis imágenes en simulación, tres comprobaciones y las tres obligatorias:

1. **Con tres avisos a la vez** —el caso que el usuario reprodujo—: el de más abajo se ve entero,
   con su barrita de progreso visible de principio a fin.
2. Con la cola llena aparecen **cuatro** avisos a la vez, no tres, y ninguno queda debajo de la
   barra de tareas.
3. **La capa no se puede desplazar** con la rueda del ratón en ninguno de los dos casos: si se
   desplaza, es que el alto calculado sigue sin dar para lo que se está pintando.
4. Repetir la primera con la barra de tareas movida a otro borde de la pantalla.

- [ ] **Step 7: Commit**

```bash
git add src/main/kotlin/io/github/shizukajiku/imagewatch/ui/toast src/test/kotlin/io/github/shizukajiku/imagewatch/ui/toast
git commit -m "fix: la capa de avisos se coloca en dp y su alto sale del numero de avisos"
```

---

### Task 10: Configuración en tiempo real en el view model

**Files:**
- Modify: `src/main/kotlin/io/github/shizukajiku/imagewatch/ui/settings/SettingsViewModel.kt`
- Test: `src/test/kotlin/io/github/shizukajiku/imagewatch/ui/settings/SettingsViewModelTest.kt`

**Interfaces:**
- Consumes: `Wiring.applyConfig` a través de la lambda `apply` que ya recibe.
- Produces: `commitUrl()`, `commitInterval()`, `commitToastSeconds()`, `commitVolume()`; `onSimulationChange`, `onIgnoreSslChange`, `onThemeChange`, `onToastsChange`, `onSoundsChange` pasan a aplicar al instante. Desaparece `save()`; `SettingsUiState.savedAt` desaparece y `error` pasa a `urlError`, `intervalError`, `toastSecondsError`.

- [ ] **Step 1: Escribir las pruebas (fallan)**

```kotlin
@Test
fun `un interruptor se aplica al pulsarlo`() {
    val aplicados = mutableListOf<AppConfig>()
    val viewModel = SettingsViewModel(config()) { aplicados.add(it); null }

    viewModel.onSoundsChange(false)

    assertEquals(1, aplicados.size)
    assertFalse(aplicados.single().soundsEnabled())
}

@Test
fun `teclear en un campo no aplica nada hasta confirmarlo`() {
    val aplicados = mutableListOf<AppConfig>()
    val viewModel = SettingsViewModel(config()) { aplicados.add(it); null }

    viewModel.onIntervalChange("4")
    viewModel.onIntervalChange("45")

    assertTrue(aplicados.isEmpty(), "Un 4 a medio teclear no es un intervalo de 4 segundos")

    viewModel.commitInterval()

    assertEquals(45, aplicados.single().pollInterval().seconds)
}

@Test
fun `un intervalo no numerico marca el campo y no llega al aplicador`() {
    val aplicados = mutableListOf<AppConfig>()
    val viewModel = SettingsViewModel(config()) { aplicados.add(it); null }

    viewModel.onIntervalChange("cada rato")
    viewModel.commitInterval()

    assertTrue(aplicados.isEmpty())
    assertNotNull(viewModel.state.value.intervalError)
}

@Test
fun `el error del aplicador se ancla al campo que lo causo`() {
    val viewModel = SettingsViewModel(config()) { "La URL debe usar HTTPS" }

    viewModel.onUrlChange("http://inseguro.local/versions.json")
    viewModel.commitUrl()

    assertEquals("La URL debe usar HTTPS", viewModel.state.value.urlError)
    assertNull(viewModel.state.value.intervalError)
}

@Test
fun `editar un campo limpia su error anterior`() {
    val viewModel = SettingsViewModel(config()) { "fallo" }
    viewModel.onUrlChange("http://malo")
    viewModel.commitUrl()

    viewModel.onUrlChange("https://bueno.local/v.json")

    assertNull(viewModel.state.value.urlError)
}
```

Con un ayudante `config()` en el test que devuelva un `AppConfig` de partida.

- [ ] **Step 2: Ejecutar y ver fallar**

Run: `.\gradlew.bat test --tests "*SettingsViewModelTest*" --console=plain`
Expected: no compila; no existen `commitInterval`, `commitUrl` ni los campos de error por campo.

- [ ] **Step 3: Implementar**

- Los `onXChange` de interruptores, tema y toasts pasan a `editYAplica { it.copy(...) }`: actualizan el formulario y llaman a `aplicar()` en el acto.
- Los `onXChange` de campos de texto solo editan el formulario y limpian **su** error.
- `commitUrl()`, `commitInterval()`, `commitToastSeconds()` validan el formato de su campo —el intervalo y la duración tienen que ser números; el resto de reglas ya viven en el núcleo y duplicarlas aquí sería mantener dos versiones— y llaman a `aplicar()`.
- `commitVolume()` existe para el `onValueChangeFinished` del slider: mientras se arrastra solo se actualiza el formulario, y se persiste al soltar. El volumen en vivo lo lee `Sounds` de la configuración vigente, así que hasta soltarlo se oye el anterior; es el precio de no escribir el fichero sesenta veces por arrastre, y se anota como tal en el código.
- `aplicar()` construye el `AppConfig` con el formulario entero, llama a `apply` y coloca el mensaje devuelto en el error del campo que se acaba de confirmar, o en `urlError` si el cambio venía de un interruptor —el único que puede fallar por su culpa es la URL, cuando el modo simulación se apaga con una URL inválida guardada—.

- [ ] **Step 4: Ejecutar y ver pasar**

Run: `.\gradlew.bat test --tests "*SettingsViewModelTest*" --console=plain`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/io/github/shizukajiku/imagewatch/ui/settings src/test/kotlin/io/github/shizukajiku/imagewatch/ui/settings
git commit -m "feat: la configuracion se aplica sin boton de guardar"
```

---

### Task 11: La pantalla de ajustes sin botón de guardar

**Files:**
- Modify: `src/main/kotlin/io/github/shizukajiku/imagewatch/ui/settings/SettingsScreen.kt`
- Modify: `src/main/kotlin/io/github/shizukajiku/imagewatch/Main.kt`

**Interfaces:**
- Consumes: los `commitX()` de la Tarea 10, `Space`, `TypeScale`, `IconSize` (Tareas 2 y 6).
- Produces: `SettingsScreen` sin los parámetros `onSave`; con `onUrlCommit`, `onIntervalCommit`, `onToastSecondsCommit`, `onVolumeCommit`.

- [ ] **Step 1: Cambiar los campos de texto para que confirmen al salir o con Enter**

Cada `OutlinedTextField` de texto gana:

```kotlin
modifier = Modifier
    .width(220.dp)
    .onFocusChanged { if (!it.isFocused) onIntervalCommit() }
    .onKeyEvent { evento ->
        if (evento.type == KeyEventType.KeyDown && evento.key == Key.Enter) {
            onIntervalCommit()
            true
        } else {
            false
        }
    },
isError = state.intervalError != null,
supportingText = state.intervalError?.let { { Text(it, fontSize = TypeScale.caption) } },
```

El `220.dp` sale a `Tokens.kt` como parte de la Tarea 6 si sobrevive; si no, se queda con comentario de compensación.

- [ ] **Step 2: Quitar el botón y la marca de guardado**

Se va la `Row` final entera con `Button(onSave)`, «Guardado» y el error general. El `Slider` del volumen gana `onValueChangeFinished = onVolumeCommit`.

- [ ] **Step 3: Ajustar `Main.kt`**

`SettingsPane` deja de pasar `onSave` y pasa los cuatro `commitX`. El `remember` sin clave del `SettingsViewModel` se queda como está, y su comentario se actualiza: ya no es que guardar reconstruiría el view model borrando la confirmación —no hay confirmación—, sino que el formulario debe nacer con la configuración vigente al entrar en la pantalla y no reconstruirse con cada cambio que él mismo provoca.

- [ ] **Step 4: Construir y probar a mano**

```powershell
$env:JAVA_HOME = "C:\Users\shizu\.jdks\ms-25.0.4.1"; .\gradlew.bat build --console=plain
$env:JAVA_HOME = "C:\Users\shizu\.jdks\ms-25.0.4.1"; .\gradlew.bat run --console=plain
```

Comprobar: un interruptor se aplica al pulsarlo; el intervalo se aplica al pulsar Tab o Enter y no antes; un intervalo inválido marca el campo en rojo y el sondeo sigue con el anterior; el volumen se oye al arrastrar y se guarda al soltar; salir de Ajustes y volver enseña lo guardado.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin
git commit -m "feat: ajustes en tiempo real, con el error anclado a su campo"
```

---

### Task 12: Restablecer ajustes y borrar datos locales

**Files:**
- Create: `src/main/java/io/github/shizukajiku/imagewatch/application/LocalData.java`
- Create: `src/test/java/io/github/shizukajiku/imagewatch/application/LocalDataTest.java`
- Modify: `src/main/kotlin/io/github/shizukajiku/imagewatch/ui/settings/SettingsScreen.kt`
- Modify: `src/main/kotlin/io/github/shizukajiku/imagewatch/ui/dialogs/ImageDialogs.kt`
- Modify: `src/main/kotlin/io/github/shizukajiku/imagewatch/Main.kt`

**Interfaces:**
- Consumes: `ConfigStore`, `TrackedImageStore`, `PollingController`.
- Produces: `LocalData(Path stateFile, Path configFile, Path trackedFile)` con `void wipe()`; `Wiring.resetSettings(): String?` y `Wiring.wipeLocalData(): String?`; `ConfirmDialog` en `ImageDialogs.kt`.

- [ ] **Step 1: Escribir la prueba del borrado (falla)**

```java
@Test
void wipeBorraLosTresFicherosYNoFallaSiFaltaAlguno(@TempDir Path dir) throws Exception {
    var estado = Files.writeString(dir.resolve("images.json"), "[]");
    var config = Files.writeString(dir.resolve("config.json"), "{}");
    var vigiladas = dir.resolve("tracked-images.json"); // no existe a proposito

    new LocalData(estado, config, vigiladas).wipe();

    assertThat(estado).doesNotExist();
    assertThat(config).doesNotExist();
    assertThat(vigiladas).doesNotExist();
}
```

- [ ] **Step 2: Ejecutar y ver fallar**

Run: `.\gradlew.bat test --tests "*LocalDataTest*" --console=plain`
Expected: no compila; no existe `LocalData`.

- [ ] **Step 3: Implementar `LocalData`**

```java
/**
 * Los ficheros que la aplicacion escribe en el disco del usuario, juntos en un sitio para poder
 * borrarlos de una vez. Que falte alguno no es un error: borrar dos veces tiene que poder hacerse.
 */
public record LocalData(Path stateFile, Path configFile, Path trackedFile) {
  public void wipe() throws IOException {
    Files.deleteIfExists(stateFile);
    Files.deleteIfExists(configFile);
    Files.deleteIfExists(trackedFile);
  }
}
```

- [ ] **Step 4: Ejecutar y ver pasar**

Run: `.\gradlew.bat test --tests "*LocalDataTest*" --console=plain`
Expected: PASS.

- [ ] **Step 5: Añadir las dos acciones a `Wiring`**

```kotlin
/** Devuelve la configuracion a los valores de fabrica. Las imagenes y el historial siguen. */
fun resetSettings(): String? = applyConfig(AppConfig.fromEnvironment())

/**
 * Deja la aplicacion como recien instalada sin cerrarla: para el sondeo, borra los tres ficheros,
 * vacia lo que quedaba vivo en memoria y lo reanuda. Sin parar antes, un ciclo en vuelo volveria a
 * escribir el fichero que se acaba de borrar.
 */
fun wipeLocalData(): String? = runCatching {
    controller.stop()
    localData.wipe()
    trackedImages.save(AppConfig.fromEnvironment().imageNames())
    config.value = AppConfig.fromEnvironment()
    controller.start()
    sounds.play(Sound.SUCCESS)
}.fold(onSuccess = { null }, onFailure = {
    LOG.error("No se pudieron borrar los datos locales", it)
    it.message ?: "No se pudieron borrar los datos locales"
})
```

`resetSettings` también suena, por `applyConfig`. Ojo con el orden en `wipeLocalData`: parar, borrar, repoblar, arrancar.

`Wiring` construye el `LocalData` en su `init`, junto a los stores, con los tres caminos que ya
calcula ahi: `loaded.stateFile()`, su hermano `config.json` y su hermano `tracked-images.json`.
Calcularlos por segunda vez en otro sitio es la forma de que un dia dejen de coincidir.

- [ ] **Step 6: Añadir el diálogo de confirmación**

En `ImageDialogs.kt`, junto a `DeleteDialog`, un `ConfirmDialog(titulo, cuerpo, etiquetaAccion, onDismiss, onConfirm)` con el botón de acción en `MaterialTheme.colorScheme.error`. Los textos:

- Restablecer: «Los ajustes vuelven a sus valores de fábrica. Las imágenes vigiladas y lo que ya diste por visto no se tocan.» Acción: «Restablecer».
- Borrar: «Se borran los ajustes, las imágenes vigiladas y el historial de versiones vistas. La aplicación queda como recién instalada.» Acción: «Borrar todo».

- [ ] **Step 7: Añadir la sección «Datos» a `SettingsScreen`**

Al final de la pantalla, después de «Avisos». Dos `TextButton` en color de error, cada uno abriendo su `ConfirmDialog`. El mensaje de fallo, si lo hay, debajo de los botones.

- [ ] **Step 8: Construir y probar a mano**

```powershell
$env:JAVA_HOME = "C:\Users\shizu\.jdks\ms-25.0.4.1"; .\gradlew.bat build --console=plain
$env:JAVA_HOME = "C:\Users\shizu\.jdks\ms-25.0.4.1"; .\gradlew.bat run --console=plain
```

Con la aplicación delante: restablecer ajustes deja las imágenes; borrar datos deja la aplicación en la lista sembrada por defecto sin cerrarse, y el sondeo sigue corriendo después. Comprobar en `%USERPROFILE%\.notifier` que los ficheros se han ido y se vuelven a escribir solos.

- [ ] **Step 9: Commit**

```bash
git add src/main/java src/main/kotlin src/test/java
git commit -m "feat: restablecer los ajustes y borrar los datos locales desde Ajustes"
```

---

### Task 13: «Ver» trae la ventana al frente

**Files:**
- Modify: `src/main/kotlin/io/github/shizukajiku/imagewatch/Main.kt`
- Modify: `docs/superpowers/specs/2026-09-05-imagewatch-historias-de-usuario.md`

**Interfaces:**
- Consumes: nada.
- Produces: nada que otra tarea use.

Lo encontro la Tarea 1 al escribir las historias: `Main.kt` comenta que el contador `traerAlFrente`
existe para arreglar que «Ver» no traia la ventana delante, pero el `onView` que recibe `ToastLayer`
no lo incrementa. Solo pone `windowVisible = true` y resalta la fila, asi que con la ventana ya
visible detras de otra aplicacion «Ver» no la trae al frente. El comentario promete lo que el codigo
no hace.

- [ ] **Step 1: Incrementar el contador en `onView`**

```kotlin
onView = { name ->
    windowVisible = true
    // Sin esto, «Ver» solo servia con la ventana cerrada: visible pero detras de otra
    // aplicacion, resaltaba una fila que el usuario no podia ver. `traerAlFrente` es lo unico
    // que dispara toFront() y requestFocus().
    traerAlFrente++
    name?.let(viewModel::highlight)
},
```

- [ ] **Step 2: Verificar a mano**

Es cableado de ventana, no logica: no hay prueba automatica que lo cubra, y montar una
infraestructura de interfaz para un `++` costaria mas de lo que protege.

```powershell
$env:JAVA_HOME = "C:\Users\shizu\.jdks\ms-25.0.4.1"; .\gradlew.bat run --console=plain
```

Dos casos, los dos obligatorios:

1. Ventana **abierta** y otra aplicacion delante. Al llegar un aviso, pulsar «Ver»: la ventana pasa
   al frente, con el foco, y la fila queda resaltada.
2. Ventana **cerrada**. Pulsar «Ver»: se abre igual que antes, con la fila resaltada. Este caso ya
   funcionaba y no puede romperse.

- [ ] **Step 3: Actualizar las historias**

H-55 y H-75 vuelven a decir que «Ver» trae la ventana al frente —ahora es cierto— y el caso de uso
«Ver no trae la ventana al frente» sale de la lista de casos sin cubrir, con una nota de que se
resolvio en esta fase.

- [ ] **Step 4: Commit**

Mensaje: `fix: «Ver» trae la ventana al frente, no solo la hace visible`, anadiendo `Main.kt` y el
documento de historias.

---

### Task 14: Cierre del PR 3

**Files:**
- Modify: `README.md`
- Modify: `docs/superpowers/specs/2026-09-05-imagewatch-historias-de-usuario.md`

- [ ] **Step 1: Actualizar las historias de usuario**

Añadir las historias de lo que esta fase incorporó —configuración en tiempo real, restablecer, borrar datos— y actualizar la traza de las que cambiaron de sitio: el temporizador del aviso ahora es `ToastState`, los de la fila son `ImagesViewModel`. La lista «Historias sin prueba» debería quedar vacía o justificar cada resto.

- [ ] **Step 2: Actualizar el README**

La sección de configuración: ya no hay botón de guardar, y hay dos acciones destructivas. Mencionar qué borra cada una.

- [ ] **Step 3: Construir entero y verificación visual final**

```powershell
$env:JAVA_HOME = "C:\Users\shizu\.jdks\ms-25.0.4.1"; .\gradlew.bat build --console=plain
$env:JAVA_HOME = "C:\Users\shizu\.jdks\ms-25.0.4.1"; .\gradlew.bat run --console=plain
```

Recorrer las historias de usuario del documento una por una con la aplicación delante. Es la última comprobación de que la separación no cambió nada y de que lo nuevo hace lo que dice.

- [ ] **Step 4: Commit y PR**

```bash
git add README.md docs
git commit -m "docs: actualizar historias y README con el refinamiento de la fase 7"
```

Abrir el PR contra `main` con el resumen de las tres partes: configuración en tiempo real, capa de avisos y ajustes ampliados, más la deuda de la revisión de la fase 6.

---

## Verificación de cobertura del plan

| Requisito de la spec | Tarea |
|---|---|
| Historias de usuario con traza a código y prueba | 1 |
| Casos de uso sin cubrir | 1 |
| Sin `delay` en composables | 4, 5, 6 |
| Sin reglas de dominio en composables | 5, 6 |
| Sin literales de medida fuera de `Tokens.kt` | 2, 6 |
| Estado que expresa intención | 5 |
| Reloj del aviso arriba, animación abajo | 4 |
| Pruebas que faltaban, antes de mover | 3, 4, 5 |
| Configuración en tiempo real | 10, 11 |
| Geometría de la capa de avisos | 9 |
| Cuatro avisos visibles | 9 |
| Restablecer ajustes y borrar datos | 12 |
| TOCTOU de `acknowledgeAll` | 8 |
| Regla duplicada `pendingNews` / `remoteBumps` | 7 |
| `bumped` acumulativo | 5 |
| «Ver» trae la ventana al frente (hallazgo de la Tarea 1) | 13 |
