# Fase 5 — Aviso — Plan de implementación

> **Para quien ejecute:** REQUIRED SUB-SKILL: usar `superpowers:subagent-driven-development`
> (recomendado) o `superpowers:executing-plans` para ejecutar este plan tarea a tarea. Los pasos
> usan casillas (`- [ ]`) para el seguimiento.

**Objetivo:** Rediseñar el sistema de avisos (`Toast`) para que tenga cuatro tipos
(`NUEVA`/`SALTADAS`/`ERROR`/`RESUMEN`), avise también de fallos de verificación, y renderice la
rejilla 340×78 del Blueprint en vez de la tarjeta actual.

**Arquitectura:** Cambio de modelo (`Toast` gana `kind`, `sub`, `meta`, `action`; pierde `body`) +
un método nuevo en el núcleo (`NotificationPort.notifyFailures`) + reescritura de `ToastCard` en
`jvmMain`. `RESUMEN` es una derivación de la capa de UI sobre la cola real, no un `Toast` que se
guarda.

**Tech Stack:** Kotlin Multiplatform (`commonMain`/`jvmMain`/`commonTest`/`jvmTest`), Compose
Desktop, kotlinx-coroutines-test.

**Spec:** `docs/superpowers/specs/2026-09-08-imagewatch-rediseno-design.md`, §5.1 y §10 (líneas
182–227 y 916–1062). El contrato de comportamiento vive en
`docs/superpowers/specs/2026-09-05-imagewatch-historias-de-usuario.md`.

## Restricciones globales

- Commits en español, `<tipo>: <descripción>`, terminando con la atribución vigente del
  `system-reminder` de esta sesión (Claude Sonnet 5, sin `Claude-Session` si el reminder no la trae).
- **Nunca** `git checkout`/`git restore <fichero>`. `git add` solo con rutas explícitas — nunca
  `git add -A` (dos ficheros de otra rama, `2026-09-07-migracion-plantilla-kmp.md` y `ESTADO.md`,
  están modificados y no son de este trabajo).
- ktlint: línea máxima **120**, no 140. `spotlessApply` no envuelve strings largos ni firmas: a
  mano.
- Verificación final igual que en fases anteriores, **solo desde PowerShell** con `JAVA_HOME`
  fijado:
  ```powershell
  $env:JAVA_HOME='C:\Users\shizu\.jdks\ms-21.0.12.1'
  Set-Location C:\Users\shizu\IdeaProjects\ImageWatch
  .\gradlew.bat :shared:jvmTest :shared:koverVerify :desktopApp:compileKotlin spotlessCheck --console=plain
  ```
- `TOASTS_VISIBLES` baja de 4 a 3 (spec §10.1). `RESUMEN` nunca es un `Toast` real en
  `mutableToasts`: es una fila sintética con `id = -1L` que la `StateFlow` pública construye en un
  `.map`.
- El toast de `ERROR` **no** añade sonido: el pitido de «todo falla» ya lo emite
  `ImagesViewModel.onSnapshot` (línea 274 de `ImagesViewModel.kt`, no se toca en esta fase).
- `SALTADAS` queda definido y renderizable pero **sin productor** en esta fase — todo aviso de
  versión nueva usa `kind = NUEVA`.

---

## Fichero por fichero

| Fichero | Qué cambia |
|---|---|
| `application/NotificationPort.kt` | + `fun notifyFailures(failures: List<ImageState>) {}` |
| `application/VersionPollingService.kt` | `notifyTransitions` gana el bucle de fallos |
| `application/VersionPollingServiceTest.kt` | `RecordingNotificationPort` + 2 tests nuevos |
| `ui/toast/ToastState.kt` | `Toast` con `kind/sub/meta/action`, `toastOf`, `showFailures`, `TOASTS_VISIBLES=3`, `toasts` con derivación `RESUMEN` |
| `ui/toast/ToastStateTest.kt` | Reescritura completa sobre el modelo nuevo |
| `ui/toast/ToastNotificationPort.kt` | + `notifyFailures` |
| `ui/toast/ToastNotificationPortTest.kt` | + 2 tests de `notifyFailures` |
| `ui/toast/ToastWindow.kt` (`jvmMain`) | `ToastCard` rehecha a la rejilla 340×78; `ToastLayer` con `onAction` y `take(3)` |
| `Main.kt` (`desktopApp`) | `ToastLayer(onAction = ...)` con el `when(kind)` que reemplaza a `onView` |

---

### Task 1: Núcleo — `NotificationPort.notifyFailures`

**Files:**
- Modify: `shared/src/commonMain/kotlin/io/github/shizukajiku/imagewatch/application/NotificationPort.kt`
- Modify: `shared/src/commonMain/kotlin/io/github/shizukajiku/imagewatch/application/VersionPollingService.kt:139-157` (`notifyTransitions`)
- Test: `shared/src/commonTest/kotlin/io/github/shizukajiku/imagewatch/application/VersionPollingServiceTest.kt`

**Interfaces:**
- Produces: `NotificationPort.notifyFailures(failures: List<ImageState>)` — default no-op, para
  que `ToastNotificationPort` (Task 3) y cualquier otro notificador lo sobrescriban solo si les
  interesa.
- Consumes: `transitionedInto(previous, current, ImageStatus.ERROR)`, ya existente en
  `VersionPollingService.kt:163-172`.

- [ ] **Step 1: Escribir los tests que fallan**

Añadir a `VersionPollingServiceTest.kt`, junto a los demás `@Test` que usan `RecordingNotificationPort`
(por ejemplo tras `anImageThatFailsIsNotNotifiedAsAnUpdate`, línea ~249):

```kotlin
    @Test
    fun unaImagenQuePasaAErrorDisparaNotifyFailuresUnaVez() = runTest {
        val state = FakeImageStateStore()
        state.seed("alpha", "registry.local/alpha:1.0.0")
        val source = FakeImageSource(mutableMapOf("alpha" to "registry.local/alpha:1.0.0"))
        val notifications = RecordingNotificationPort()
        val service = service(source, state, listOf(notifications), listOf("alpha"))

        service.poll() // línea base: alpha al día, sin transición todavía
        source.failOn("alpha", "HTTP 503")
        service.poll() // alpha pasa a ERROR

        assertEquals(listOf("alpha"), notifications.receivedFailures.map { it.name })
    }

    @Test
    fun unaImagenQueYaFallabaNoVuelveADispararNotifyFailures() = runTest {
        val source = FakeImageSource(mutableMapOf())
        source.failOn("alpha", "HTTP 503")
        val notifications = RecordingNotificationPort()
        val service = service(source, FakeImageStateStore(), listOf(notifications), listOf("alpha"))

        service.poll() // alpha ya arranca en ERROR: no hay "antes" que valga
        notifications.receivedFailures.clear()
        service.poll() // sigue en ERROR: no es una transición nueva

        assertTrue(notifications.receivedFailures.isEmpty())
    }
```

Y ampliar `RecordingNotificationPort` (línea ~509) para que registre también los fallos:

```kotlin
    private class RecordingNotificationPort : NotificationPort {
        val received = mutableListOf<ImageState>()
        val receivedFailures = mutableListOf<ImageState>()

        override fun notifyUpdates(updates: List<ImageState>) {
            received.addAll(updates)
        }

        override fun notifyFailures(failures: List<ImageState>) {
            receivedFailures.addAll(failures)
        }
    }
```

- [ ] **Step 2: Ejecutar y comprobar que fallan**

```powershell
$env:JAVA_HOME='C:\Users\shizu\.jdks\ms-21.0.12.1'
.\gradlew.bat :shared:jvmTest --tests "*VersionPollingServiceTest*" --console=plain
```

Esperado: FALLA (`notifyFailures` no existe todavía en `NotificationPort` → error de compilación
de `RecordingNotificationPort`, `override` sin función que sobrescribir).

- [ ] **Step 3: Implementar `notifyFailures` en `NotificationPort`**

```kotlin
package io.github.shizukajiku.imagewatch.application

import io.github.shizukajiku.imagewatch.domain.ImageState

interface NotificationPort {
    /** Recibe únicamente las imágenes que acaban de pasar a tener una versión pendiente. */
    fun notifyUpdates(updates: List<ImageState>)

    /**
     * Recibe las imágenes que acaban de pasar a ERROR: no lo estaban en el ciclo anterior. Por
     * defecto no hace nada, para que un notificador al que no le interese no tenga que
     * implementarlo.
     */
    fun notifyFailures(failures: List<ImageState>) {}
}
```

- [ ] **Step 4: Cablear el bucle en `VersionPollingService.notifyTransitions`**

En `VersionPollingService.kt`, dentro de `notifyTransitions` (línea 139-157), tras el bucle
existente de `newlyPending`, añadir:

```kotlin
    private fun notifyTransitions(previous: PollSnapshot, current: PollSnapshot) {
        if (previous === PollSnapshot.EMPTY) {
            return
        }
        val newlyPending = pendingNews(previous, current)
        if (newlyPending.isNotEmpty()) {
            notifiers.forEach { notifier ->
                try {
                    notifier.notifyUpdates(newlyPending)
                } catch (e: RuntimeException) {
                    log.warn("Un notificador falló al recibir las transiciones", e)
                }
            }
        }
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
    }
```

No cambia el `if (newlyPending.isEmpty()) { return }` original: se sustituye por el `if` que envuelve
solo su propio bucle, para que el segundo bucle (fallos) corra aunque no haya novedades pendientes.

- [ ] **Step 5: Ejecutar y comprobar que pasan**

```powershell
.\gradlew.bat :shared:jvmTest --tests "*VersionPollingServiceTest*" --console=plain
```

Esperado: PASS, todos los tests de la clase (los viejos siguen valiendo: `RecordingNotificationPort`
sin override de `notifyFailures` en ningún otro test usa el default vacío).

- [ ] **Step 6: Commit**

```bash
git add shared/src/commonMain/kotlin/io/github/shizukajiku/imagewatch/application/NotificationPort.kt shared/src/commonMain/kotlin/io/github/shizukajiku/imagewatch/application/VersionPollingService.kt shared/src/commonTest/kotlin/io/github/shizukajiku/imagewatch/application/VersionPollingServiceTest.kt
git commit -m "feat: NotificationPort avisa tambien de imagenes que pasan a error"
```

---

### Task 2: `ToastState` — modelo `Toast` con `kind`/`sub`/`meta`/`action` y `RESUMEN`

**Files:**
- Modify: `shared/src/commonMain/kotlin/io/github/shizukajiku/imagewatch/ui/toast/ToastState.kt`
- Test: `shared/src/commonTest/kotlin/io/github/shizukajiku/imagewatch/ui/toast/ToastStateTest.kt`
  (reescritura completa)

**Interfaces:**
- Consumes: `ImageState` (`domain/ImageState.kt`: `name`, `local: Version?`, `remote: Version?`,
  `registry: String`, `error: String?`).
- Produces:
  - `enum class ToastKind { NUEVA, SALTADAS, ERROR, RESUMEN }`
  - `data class Toast(id: Long, kind: ToastKind, title: String, sub: String, meta: String, action: String, imageName: String?, leaving: Boolean = false, progress: Float = 1f, durationMillis: Long = 0L)`
  - `ToastState.show(updates: List<ImageState>)` (ya existe, cambia su `Toast` interno)
  - `ToastState.showFailures(failures: List<ImageState>)` — nuevo, mismo patrón que `show`
  - `ToastState.toasts: StateFlow<List<Toast>>` — ahora una transformación de la cola real, no la
    cola en crudo
  - `const val TOASTS_VISIBLES = 3` (antes 4)

- [ ] **Step 1: Reescribir `ToastStateTest.kt` (RED)**

El fichero entero cambia porque `Toast.body` desaparece. Reemplazar
`shared/src/commonTest/kotlin/io/github/shizukajiku/imagewatch/ui/toast/ToastStateTest.kt` por:

```kotlin
package io.github.shizukajiku.imagewatch.ui.toast

import io.github.shizukajiku.imagewatch.domain.ImageState
import io.github.shizukajiku.imagewatch.domain.ImageStatus
import io.github.shizukajiku.imagewatch.domain.Version
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private fun pending(name: String, from: String, to: String) = ImageState(
    name,
    Version(from),
    Version(to),
    "registry.local/$name",
    ImageStatus.PENDING,
    null,
    Instant.fromEpochSeconds(0),
)

private fun failed(name: String, message: String) = ImageState(
    name,
    null,
    null,
    "registry.local/$name",
    ImageStatus.ERROR,
    message,
    Instant.fromEpochSeconds(0),
)

/**
 * Duración fija y muy holgada para las pruebas que no ejercitan el reloj: da igual el número
 * mientras sea mayor que lo que tarda la prueba en correr, porque un `CoroutineScope` real —no de
 * tiempo virtual— no la va a agotar en ese rato.
 */
private fun estadoSinReloj() = ToastState(CoroutineScope(Job()), { 60.seconds })

@OptIn(ExperimentalCoroutinesApi::class)
class ToastStateTest {

    @Test
    fun `una imagen produce un toast NUEVA sin nombrar la version anterior`() {
        val state = estadoSinReloj()

        state.show(listOf(pending("alpha", "1.0.0", "1.1.0")))

        val toast = state.toasts.value.single()
        assertEquals("alpha", toast.imageName)
        assertEquals(ToastKind.NUEVA, toast.kind)
        assertEquals("versión 1.1.0", toast.sub)
        assertFalse(toast.sub.contains("1.0.0"), "No nombra la version anterior (V-3)")
        assertEquals("registry.local/alpha", toast.meta)
        assertEquals("Visto", toast.action)
    }

    @Test
    fun `dos imagenes producen dos toasts`() {
        val state = estadoSinReloj()

        state.show(listOf(pending("alpha", "1.0.0", "1.1.0"), pending("beta", "2.0.0", "2.1.0")))

        assertEquals(2, state.toasts.value.size)
    }

    @Test
    fun `descartar quita solo el toast pedido`() {
        val state = estadoSinReloj()
        state.show(listOf(pending("alpha", "1.0.0", "1.1.0"), pending("beta", "2.0.0", "2.1.0")))
        val first = state.toasts.value.first()

        state.dismiss(first.id)

        assertEquals(1, state.toasts.value.size)
        assertEquals("beta", state.toasts.value.single().imageName)
    }

    @Test
    fun `mostrar en paralelo no pierde ningun toast`() {
        // show() corre en el hilo del planificador y dismiss() en el de Compose: un
        // leer-modificar-escribir no atomico sobre mutableToasts.value perderia escrituras aqui.
        val state = estadoSinReloj()
        val total = 50

        val threads = (1..total).map { i ->
            Thread { state.show(listOf(pending("img$i", "1.0.0", "1.1.0"))) }
        }
        threads.forEach(Thread::start)
        threads.forEach(Thread::join)

        // Con mas de 3 en cola, la lista publica recorta a 2 reales + RESUMEN.
        assertEquals(3, state.toasts.value.size)
        assertEquals(ToastKind.RESUMEN, state.toasts.value.last().kind)
    }

    @Test
    fun `descartar en paralelo no pierde ningun descarte`() {
        // Se queda en TOASTS_VISIBLES elementos -no en los 50 de antes de esta fase- porque a
        // partir de ahi la cola publica sintetiza un RESUMEN y sus ids reales dejan de ser
        // visibles desde aqui. Con exactamente TOASTS_VISIBLES no hay RESUMEN y los tres ids son
        // los reales: la carrera que este test vigila -leer-modificar-escribir no atomico sobre
        // mutableToasts.value- se ejercita igual con tres hilos que con cincuenta.
        val state = estadoSinReloj()
        val total = TOASTS_VISIBLES
        (1..total).forEach { i -> state.show(listOf(pending("img$i", "1.0.0", "1.1.0"))) }
        val ids = state.toasts.value.map { it.id }

        val threads = ids.map { id -> Thread { state.dismiss(id) } }
        threads.forEach(Thread::start)
        threads.forEach(Thread::join)

        assertEquals(0, state.toasts.value.size)
    }

    @Test
    fun `los que no caben en la ventana esperan turno en vez de perderse`() {
        // Se muestran los primeros TOASTS_VISIBLES reales; el resto sigue en la cola y entra en
        // cuanto se libera hueco. Ningun aviso se pierde por llegar en mal momento.
        val state = estadoSinReloj()

        repeat(10) { i -> state.show(listOf(pending("img$i", "1.0.0", "1.1.0"))) }

        assertEquals(
            listOf("img0", "img1"),
            state.toasts.value.take(2).map { it.imageName },
            "Se muestran los mas antiguos: son los que llevan mas tiempo esperando",
        )
        assertEquals(ToastKind.RESUMEN, state.toasts.value[2].kind)
    }

    @Test
    fun `descartar por imagen retira sus avisos y deja los demas`() {
        val state = estadoSinReloj()
        state.show(listOf(pending("alpha", "1.0.0", "1.1.0")))
        state.show(listOf(pending("beta", "1.0.0", "1.1.0")))
        state.show(listOf(pending("alpha", "1.1.0", "1.2.0")))

        state.dismissFor("alpha")

        assertEquals(listOf("beta"), state.toasts.value.map { it.imageName })
    }

    @Test
    fun `descartar por una imagen sin avisos no cambia nada`() {
        val state = estadoSinReloj()
        state.show(listOf(pending("alpha", "1.0.0", "1.1.0")))

        state.dismissFor("gamma")

        assertEquals(1, state.toasts.value.size)
    }

    @Test
    fun `showFailures produce un toast ERROR con Reintentar como accion`() {
        val state = estadoSinReloj()

        state.showFailures(listOf(failed("alpha", "HTTP 503")))

        val toast = state.toasts.value.single()
        assertEquals(ToastKind.ERROR, toast.kind)
        assertEquals("HTTP 503", toast.sub)
        assertEquals("Reintentar", toast.action)
        assertEquals("registry.local/alpha", toast.meta)
    }

    @Test
    fun `showFailures sin detalle usa un motivo por defecto`() {
        val state = estadoSinReloj()

        state.showFailures(listOf(failed("alpha", "").copy(error = null)))

        assertEquals("sin detalle", state.toasts.value.single().sub)
    }

    @Test
    fun `con mas de tres en cola se sintetiza un RESUMEN con las dos primeras reales`() {
        val state = estadoSinReloj()

        repeat(5) { i -> state.show(listOf(pending("img$i", "1.0.0", "1.1.0"))) }

        val toasts = state.toasts.value
        assertEquals(3, toasts.size)
        assertEquals(listOf("img0", "img1"), toasts.take(2).map { it.imageName })
        val resumen = toasts[2]
        assertEquals(ToastKind.RESUMEN, resumen.kind)
        assertEquals("y 3 novedades más", resumen.title)
        assertEquals("Ver todas", resumen.action)
        assertNull(resumen.imageName)
        assertEquals(listOf("img2", "img3", "img4").joinToString(", "), resumen.meta)
    }

    @Test
    fun `al descartar uno de los reales el RESUMEN baja su cuenta y entra el siguiente`() {
        val state = estadoSinReloj()
        repeat(5) { i -> state.show(listOf(pending("img$i", "1.0.0", "1.1.0"))) }
        val primero = state.toasts.value.first().id

        state.dismiss(primero)

        val toasts = state.toasts.value
        assertEquals(listOf("img1", "img2"), toasts.take(2).map { it.imageName })
        assertEquals("y 2 novedades más", toasts[2].title)
    }

    @Test
    fun `con tres o menos en cola no hay RESUMEN`() {
        val state = estadoSinReloj()
        repeat(3) { i -> state.show(listOf(pending("img$i", "1.0.0", "1.1.0"))) }

        assertEquals(3, state.toasts.value.size)
        assertTrue(state.toasts.value.none { it.kind == ToastKind.RESUMEN })
    }

    @Test
    fun `el reloj de descarte solo corre para los dos reales visibles, no para el RESUMEN`() = runTest {
        val state = ToastState(backgroundScope, { 5.seconds }, { currentTime })
        repeat(5) { i -> state.show(listOf(pending("img$i", "1.0.0", "1.1.0"))) }

        advanceTimeBy(5_001)

        val toasts = state.toasts.value
        // Los dos reales visibles (img0, img1) expiraron y se marcaron salientes.
        assertTrue(toasts[0].leaving && toasts[1].leaving)
        // El RESUMEN no tiene reloj propio: nunca se marca saliente por si mismo.
        assertFalse(toasts[2].leaving)
    }

    @Test
    fun `un aviso se retira solo al agotarse su tiempo`() = runTest {
        val state = ToastState(backgroundScope, { 5.seconds }, { currentTime })
        state.show(listOf(pending("alpha", "1.0.0", "1.1.0")))

        advanceTimeBy(5_001)

        assertTrue(state.toasts.value.single().leaving, "Se marca saliente, no se borra de golpe")
        state.exitFinished(state.toasts.value.single().id)
        assertTrue(state.toasts.value.isEmpty())
    }

    @Test
    fun `el puntero encima pausa el descarte y al salir sigue con lo que quedaba`() = runTest {
        val state = ToastState(backgroundScope, { 5.seconds }, { currentTime })
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
}
```

Añadir el `import kotlin.test.assertNull` al principio del fichero junto a los demás `kotlin.test.*`.

- [ ] **Step 2: Ejecutar y comprobar que fallan**

```powershell
.\gradlew.bat :shared:jvmTest --tests "*ToastStateTest*" --console=plain
```

Esperado: FALLA en compilación (`ToastKind`, `Toast.kind`/`sub`/`meta`/`action`, `showFailures` no
existen todavía).

- [ ] **Step 3: Reescribir `ToastState.kt`**

Reemplazar el bloque de `Toast` (líneas 30-38), la constante `TOASTS_VISIBLES` (línea 71), el
campo `toasts` y la función `show` (línea 136-147) y `toastOf` (línea 317-323). `dismiss`,
`dismissFor`, `pause`, `resume`, `exitFinished` y todo lo demás de la clase **no cambia**: solo se
añade `showFailures` justo debajo de `show`.

```kotlin
enum class ToastKind { NUEVA, SALTADAS, ERROR, RESUMEN }

/**
 * Un aviso en pantalla. Uno por imagen: los avisos no se agrupan (salvo el `RESUMEN`, que es una
 * derivacion de la capa de UI y no un aviso real — ver [ToastState.toasts]).
 *
 * `leaving` es la señal de `ToastState` de que toca irse: la tarjeta la usa para arrancar su
 * animación de salida, y no para borrarse sola.
 *
 * `progress` y `durationMillis` son la **condición inicial** de la barrita, no su valor cuadro a
 * cuadro: con qué fracción arranca y a cuántos milisegundos equivale esa fracción entera.
 */
data class Toast(
    val id: Long,
    val kind: ToastKind,
    val title: String,
    val sub: String,
    val meta: String,
    val action: String,
    val imageName: String?,
    val leaving: Boolean = false,
    val progress: Float = 1f,
    val durationMillis: Long = 0L,
)

private const val ABSENT = "—"
private const val SIN_DETALLE = "sin detalle"
```

`MIN_VISIBLE_MILLIS` (línea 62) no cambia.

`TOASTS_VISIBLES` (línea 71) pasa de 4 a 3:

```kotlin
const val TOASTS_VISIBLES = 3
```

La clase `ToastState`: el campo `toasts` deja de ser un alias directo de `mutableToasts` y pasa a
transformarla. Reemplazar (línea 130-147):

```kotlin
    /**
     * La cola visible, en orden de llegada: los dos primeros reales más, si hay más de tres en
     * la cola completa, una tarjeta sintética `RESUMEN` en el tercer hueco.
     *
     * El `RESUMEN` **no vive en `mutableToasts`**: `id = -1L` para que ningún `dismiss` real lo
     * alcance nunca (`sequence` arranca en 0 y solo sube), y para que `sincronizarRelojes` -que
     * itera sobre `mutableToasts`, no sobre este `StateFlow`- lo ignore por completo. Su reloj no
     * corre: cuando uno de los dos reales visibles se va, entra el siguiente real y el contador
     * del resumen baja solo, sin que nadie lo reprograme.
     */
    val toasts: StateFlow<List<Toast>> = mutableToasts
        .map { full ->
            if (full.size <= TOASTS_VISIBLES) {
                full
            } else {
                val visibles = TOASTS_VISIBLES - 1
                full.take(visibles) + resumenDe(full.drop(visibles))
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    private fun resumenDe(ocultos: List<Toast>) = Toast(
        id = -1L,
        kind = ToastKind.RESUMEN,
        title = "y ${ocultos.size} novedades más",
        sub = "",
        meta = ocultos.mapNotNull { it.imageName }.joinToString(", "),
        action = "Ver todas",
        imageName = null,
    )

    fun show(updates: List<ImageState>) {
        if (updates.isEmpty()) {
            return
        }
        val millis = duration().inWholeMilliseconds.coerceAtLeast(MIN_VISIBLE_MILLIS)
        val nuevos = updates.map { toastOf(it, millis) }
        relojes.update { s -> s.copy(remaining = s.remaining + nuevos.associate { it.id to millis }) }
        mutableToasts.update { it + nuevos }
        sincronizarRelojes()
    }

    /**
     * Aviso de imágenes que acaban de fallar la verificación. Mismo patrón que [show]: entra en
     * la misma cola, cuenta para el mismo `TOASTS_VISIBLES`, y su reloj lo lleva igual
     * [sincronizarRelojes]. La única diferencia es el contenido de la tarjeta ([failureOf]).
     */
    fun showFailures(failures: List<ImageState>) {
        if (failures.isEmpty()) {
            return
        }
        val millis = duration().inWholeMilliseconds.coerceAtLeast(MIN_VISIBLE_MILLIS)
        val nuevos = failures.map { failureOf(it, millis) }
        relojes.update { s -> s.copy(remaining = s.remaining + nuevos.associate { it.id to millis }) }
        mutableToasts.update { it + nuevos }
        sincronizarRelojes()
    }
```

`import kotlinx.coroutines.flow.map` y `import kotlinx.coroutines.flow.stateIn` y
`import kotlinx.coroutines.flow.SharingStarted` se añaden a las importaciones de arriba.

`sincronizarRelojes()` (línea 187-194) sigue igual: itera `mutableToasts.value.take(TOASTS_VISIBLES)`,
y con `TOASTS_VISIBLES = 3` eso son 3 reales — más de los 2 que se pintan cuando hay `RESUMEN`, así
que **no hace falta tocarla**: sigue arrancando reloj para hasta 3 reales, y el `take(2)` de arriba
(en `resumenDe`) es quien decide cuántos de esos 3 se pintan de verdad cuando hay más de 3 en cola.
Cuando hay 3 o menos en cola, `toasts` los expone todos y los 3 tienen reloj — coherente con "sin
`RESUMEN` con 3 o menos" del test.

Reemplazar `toastOf` (línea 317-323) y añadir `failureOf`:

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

    private fun failureOf(image: ImageState, millis: Long) = Toast(
        id = sequence.updateAndGet { it + 1 },
        kind = ToastKind.ERROR,
        title = "${image.name} · no se pudo verificar",
        sub = image.error ?: SIN_DETALLE,
        meta = image.registry,
        action = "Reintentar",
        imageName = image.name,
        durationMillis = millis,
    )
```

- [ ] **Step 4: Ejecutar y comprobar que pasan**

```powershell
.\gradlew.bat :shared:jvmTest --tests "*ToastStateTest*" --console=plain
```

Esperado: PASS, las 16 pruebas.

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/io/github/shizukajiku/imagewatch/ui/toast/ToastState.kt shared/src/commonTest/kotlin/io/github/shizukajiku/imagewatch/ui/toast/ToastStateTest.kt
git commit -m "feat: Toast gana kind/sub/meta/action, showFailures y resumen de mas de tres avisos"
```

---

### Task 3: `ToastNotificationPort.notifyFailures`

**Files:**
- Modify: `shared/src/commonMain/kotlin/io/github/shizukajiku/imagewatch/ui/toast/ToastNotificationPort.kt`
- Test: `shared/src/commonTest/kotlin/io/github/shizukajiku/imagewatch/ui/toast/ToastNotificationPortTest.kt`

**Interfaces:**
- Consumes: `ToastState.showFailures(List<ImageState>)` (Task 2), `AppConfig.mutedAll`,
  `AppConfig.toastsEnabled` (ya existentes en `config/AppConfig.kt`).
- Produces: `ToastNotificationPort` sigue implementando `NotificationPort` completo (ahora con
  `notifyFailures` real, no el default).

- [ ] **Step 1: Escribir los tests que fallan**

Añadir a `ToastNotificationPortTest.kt`, tras el último test:

```kotlin
    @Test
    fun `notifyFailures respeta el silencio general y no suena`() {
        val reproducidos = mutableListOf<Sound>()
        val sounds = Sounds(
            enabled = { true },
            volume = { 1.0 },
            windowFocused = { false },
            onPlay = { reproducidos.add(it) },
        )
        val toasts = estadoSinReloj()
        val port = ToastNotificationPort(toasts, sounds, FakeSilencedImageStore()) {
            config(toastsEnabled = true, mutedAll = true)
        }

        port.notifyFailures(listOf(pending("alpha").copy(status = ImageStatus.ERROR, error = "HTTP 503")))

        assertTrue(reproducidos.isEmpty())
        assertTrue(toasts.toasts.value.isEmpty())
    }

    @Test
    fun `notifyFailures con toasts desactivados no muestra nada y no suena`() {
        val reproducidos = mutableListOf<Sound>()
        val sounds = Sounds(
            enabled = { true },
            volume = { 1.0 },
            windowFocused = { false },
            onPlay = { reproducidos.add(it) },
        )
        val toasts = estadoSinReloj()
        val port = ToastNotificationPort(toasts, sounds, FakeSilencedImageStore()) {
            config(toastsEnabled = false)
        }

        port.notifyFailures(listOf(pending("alpha").copy(status = ImageStatus.ERROR, error = "HTTP 503")))

        assertTrue(reproducidos.isEmpty(), "El toast de error no añade sonido propio")
        assertTrue(toasts.toasts.value.isEmpty())
    }

    @Test
    fun `notifyFailures con toasts activados muestra el aviso de error sin sonido`() {
        val sounds = Sounds(enabled = { false }, volume = { 1.0 }, windowFocused = { false })
        val toasts = estadoSinReloj()
        val port = ToastNotificationPort(toasts, sounds, FakeSilencedImageStore()) {
            config(toastsEnabled = true)
        }

        port.notifyFailures(listOf(pending("alpha").copy(status = ImageStatus.ERROR, error = "HTTP 503")))

        val toast = toasts.toasts.value.single()
        assertEquals(ToastKind.ERROR, toast.kind)
        assertEquals("alpha", toast.imageName)
    }
```

Import nuevo en la cabecera del fichero: `io.github.shizukajiku.imagewatch.domain.ImageStatus` ya
está importado; hace falta añadir `assertEquals` si no está (ya lo está, línea 14).

- [ ] **Step 2: Ejecutar y comprobar que fallan**

```powershell
.\gradlew.bat :shared:jvmTest --tests "*ToastNotificationPortTest*" --console=plain
```

Esperado: FALLA — los tres tests nuevos ven `toasts.toasts.value` vacío tras `notifyFailures`
porque `ToastNotificationPort` no la implementa (usa el default no-op de `NotificationPort`).

- [ ] **Step 3: Implementar `notifyFailures`**

```kotlin
    override fun notifyFailures(failures: List<ImageState>) {
        if (config().mutedAll) {
            return
        }
        if (!config().toastsEnabled) {
            return
        }
        // Sin sonido: el pitido de "todo falla" ya lo emite ImagesViewModel.onSnapshot en la
        // transicion. Añadir uno aqui duplicaria el aviso sonoro por cada imagen que cae.
        toasts.showFailures(failures)
    }
```

Se añade debajo de `notifyUpdates`, dentro de la clase `ToastNotificationPort`.

- [ ] **Step 4: Ejecutar y comprobar que pasan**

```powershell
.\gradlew.bat :shared:jvmTest --tests "*ToastNotificationPortTest*" --console=plain
```

Esperado: PASS, las 8 pruebas (5 viejas + 3 nuevas).

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/io/github/shizukajiku/imagewatch/ui/toast/ToastNotificationPort.kt shared/src/commonTest/kotlin/io/github/shizukajiku/imagewatch/ui/toast/ToastNotificationPortTest.kt
git commit -m "feat: ToastNotificationPort muestra el aviso de error sin sonido propio"
```

---

### Task 4: `ToastWindow` — rejilla 340×78 y `onAction`

**Files:**
- Modify: `shared/src/jvmMain/kotlin/io/github/shizukajiku/imagewatch/ui/toast/ToastWindow.kt`
- Modify: `desktopApp/src/main/kotlin/io/github/shizukajiku/imagewatch/Main.kt:357-373`

Sin test automatizado: es Compose Desktop puro (`jvmMain`), igual que el resto de `ToastWindow.kt`
hasta ahora. Se verifica con `:desktopApp:run` en el Step final de este task.

**Interfaces:**
- Consumes: `Toast` (Task 2: `kind`, `title`, `sub`, `meta`, `action`), `Layout.toastIcon/toastAction/toastClose/toastTimer/toastBar/toastMetaMax` (`ui/theme/Tokens.kt`, ya existen), `statusColors(status, dark)` (`ui/theme/Colors.kt`) — reutilizado vía un mapeo `ToastKind -> ImageStatus` local, `AppSvg.BELL/WARNING/CHECK_ALL` (`ui/components/SvgIcon.kt`).
- Produces: `ToastLayer(toasts, onPause, onResume, onExitFinished, onAction: (String?, ToastKind) -> Unit)` — `onView` desaparece, lo reemplaza `onAction`.

- [ ] **Step 1: Reescribir `ToastLayer` y `ToastCard`**

Reemplazar la firma de `ToastLayer` (línea 79-130) y todo `ToastCard` (línea 132-257) por:

```kotlin
@Composable
fun ToastLayer(
    toasts: List<Toast>,
    onPause: (Long) -> Unit,
    onResume: (Long) -> Unit,
    onExitFinished: (Long) -> Unit,
    onAction: (String?, ToastKind) -> Unit,
) {
    if (toasts.isEmpty()) {
        return
    }
    val bounds = remember { GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds }
    val position = remember(bounds) {
        WindowPosition(
            x = (bounds.x + bounds.width).dp - Layout.toastWidth - Layout.toastScreenMargin,
            y = (bounds.y + bounds.height - LAYER_HEIGHT).dp - Layout.toastScreenMargin,
        )
    }

    Window(
        onCloseRequest = {},
        state = rememberWindowState(position = position, width = Layout.toastWidth, height = LAYER_HEIGHT.dp),
        undecorated = true,
        transparent = true,
        alwaysOnTop = true,
        focusable = false,
        resizable = false,
        title = "ImageWatch — avisos",
    ) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(Space.sm, Alignment.Bottom),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(toasts.take(TOASTS_VISIBLES), key = { it.id }) { toast ->
                ToastCard(
                    toast = toast,
                    onPause = { onPause(toast.id) },
                    onResume = { onResume(toast.id) },
                    onExitFinished = { onExitFinished(toast.id) },
                    onAction = { onAction(toast.imageName, toast.kind) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ToastCard(
    toast: Toast,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onExitFinished: () -> Unit,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var visible by remember { mutableStateOf(false) }
    var saliendoManual by remember { mutableStateOf(false) }
    val saliendo = toast.leaving || saliendoManual
    var puntero by remember { mutableStateOf(false) }
    val progreso = remember { Animatable(toast.progress) }

    LaunchedEffect(toast.id) { visible = true }

    LaunchedEffect(puntero, saliendo) {
        if (!puntero && !saliendo) {
            progreso.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = (progreso.value * toast.durationMillis).toInt().coerceAtLeast(0),
                    easing = LinearEasing,
                ),
            )
        }
    }

    LaunchedEffect(saliendo) {
        if (saliendo) {
            visible = false
            delay(Motion.QUICK.toLong())
            onExitFinished()
        }
    }

    val dark = isSystemInDarkTheme()
    val tono = tonoDe(toast.kind, dark)

    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally { it } + fadeIn(),
        exit = slideOutHorizontally(tween(Motion.QUICK)) { it } + fadeOut(tween(Motion.QUICK)),
        modifier = modifier,
    ) {
        Surface(
            shape = RoundedCornerShape(Radius.md),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = Elevation.toast,
            border = BorderStroke(1.dp, tono.background),
            modifier = Modifier
                .padding(6.dp)
                .width(Layout.toastWidth - 12.dp)
                .height(Layout.toastHeight)
                .onPointerEvent(PointerEventType.Enter) {
                    puntero = true
                    onPause()
                }
                .onPointerEvent(PointerEventType.Exit) {
                    puntero = false
                    onResume()
                },
        ) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Space.sm),
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier.fillMaxWidth().padding(start = Space.md, top = 10.dp, end = Space.md),
                ) {
                    Box(
                        Modifier.size(Layout.toastIcon).clip(CircleShape).background(tono.background),
                        contentAlignment = Alignment.Center,
                    ) {
                        SvgIcon(iconoDe(toast.kind), tono.foreground, Modifier.size(IconSize.sm))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            toast.title,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = TypeScale.body,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (toast.sub.isNotEmpty()) {
                            Text(
                                toast.sub,
                                fontSize = TypeScale.caption,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (toast.action.isNotEmpty()) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(Radius.pill),
                            onClick = {
                                onAction()
                                saliendoManual = true
                            },
                            modifier = Modifier.width(Layout.toastAction),
                        ) {
                            Text(
                                toast.action,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = TypeScale.caption,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(vertical = Space.xs),
                            )
                        }
                    }
                    IconButton({ saliendoManual = true }, Modifier.size(Layout.toastClose)) {
                        SvgIcon(AppSvg.CLOSE, MaterialTheme.colorScheme.onSurfaceVariant, Modifier.size(IconSize.sm))
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = Space.md, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        toast.meta,
                        fontFamily = FontFamily.Monospace,
                        fontSize = TypeScale.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).widthIn(max = Layout.toastMetaMax),
                    )
                    Text(
                        "${(progreso.value * toast.durationMillis / 1000f).roundToInt().coerceAtLeast(0)} s",
                        fontFamily = FontFamily.Monospace,
                        fontSize = TypeScale.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(Layout.toastTimer),
                    )
                }
                LinearProgressIndicator(
                    progress = { progreso.value },
                    color = tono.background,
                    trackColor = Color.Transparent,
                    modifier = Modifier.fillMaxWidth().height(Layout.toastBar),
                )
            }
        }
    }
}

private fun iconoDe(kind: ToastKind): AppSvg = when (kind) {
    ToastKind.NUEVA, ToastKind.SALTADAS -> AppSvg.BELL
    ToastKind.ERROR -> AppSvg.WARNING
    ToastKind.RESUMEN -> AppSvg.CHECK_ALL
}

/**
 * Tono por tipo. `NUEVA`/`SALTADAS` reutilizan la paleta `PENDING` -misma gama que la insignia de
 * la fila, es el mismo mensaje-, `ERROR` la suya, y `RESUMEN` usa el acento porque no describe un
 * estado de imagen sino una acción de la interfaz ("ver todas").
 */
private fun tonoDe(kind: ToastKind, dark: Boolean): StatusColors = when (kind) {
    ToastKind.NUEVA, ToastKind.SALTADAS -> statusColors(ImageStatus.PENDING, dark)
    ToastKind.ERROR -> statusColors(ImageStatus.ERROR, dark)
    ToastKind.RESUMEN -> if (dark) {
        StatusColors(Color(0xFF2B3557), Color(0xFFD9E0FF), "")
    } else {
        StatusColors(Color(0xFFDDE3FF), Color(0xFF1B2A5C), "")
    }
}
```

**Importaciones nuevas** en la cabecera de `ToastWindow.kt` (añadir a las existentes):

```kotlin
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import io.github.shizukajiku.imagewatch.domain.ImageStatus
import io.github.shizukajiku.imagewatch.ui.components.AppSvg
import io.github.shizukajiku.imagewatch.ui.theme.StatusColors
import io.github.shizukajiku.imagewatch.ui.theme.statusColors
import kotlin.math.roundToInt
```

(`AppSvg` y `SvgIcon` ya estaban importados; revisar duplicados al aplicar el diff.)

`LAYER_HEIGHT` (línea 61) se queda igual, literal, como dice el spec §10.3.

- [ ] **Step 2: Actualizar `Main.kt`**

Reemplazar el bloque `ToastLayer(...)` (línea 363-372):

```kotlin
            ToastLayer(
                toasts = toasts,
                onPause = wiring.toasts::pause,
                onResume = wiring.toasts::resume,
                onExitFinished = wiring.toasts::exitFinished,
                onAction = { name, kind ->
                    when (kind) {
                        ToastKind.NUEVA, ToastKind.SALTADAS -> {
                            windowVisible = true
                            name?.let(viewModel::highlight)
                        }
                        ToastKind.ERROR -> name?.let(viewModel::refreshNow)
                        ToastKind.RESUMEN -> {
                            windowVisible = true
                            traerAlFrente++
                        }
                    }
                },
            )
```

`import io.github.shizukajiku.imagewatch.ui.toast.ToastKind` se añade junto a los demás imports de
`ui.toast` (línea 51-53).

- [ ] **Step 3: Compilar**

```powershell
$env:JAVA_HOME='C:\Users\shizu\.jdks\ms-21.0.12.1'
.\gradlew.bat :desktopApp:compileKotlin --console=plain
```

Esperado: compila sin error.

- [ ] **Step 4: Repaso visual con la app real**

```powershell
.\gradlew.bat :desktopApp:run --console=plain
```

En background (no termina sola: es una app de bandeja). Con `SIMULATION_MODE=true` (por defecto)
las imágenes suben de versión solas cada `SIMULATED_BUMP_EVERY` (20 s) — esperar un par de ciclos y
comprobar en la ventana de avisos:

- La tarjeta mide 340×78, tono rojo/PENDING con icono de campana, título
  `"<imagen> · versión nueva"`, subtítulo `"versión X"` sin flecha ni versión anterior.
- El pill de acción dice "Visto" y al pulsarlo la tarjeta sale y la fila de la ventana principal
  queda reconocida.
- Provocar un error (parar el origen simulado no es trivial desde fuera; alternativa: fijar
  `IMAGE_VERSION_URL` a una URL que no resuelva y `SIMULATION_MODE=false` momentáneamente, o
  aceptar el repaso visual solo de `NUEVA`/`RESUMEN` y dejar constancia de que `ERROR` se revisó
  solo por test, no visualmente, en el punto abierto de abajo).
- Con más de 3 avisos en cola (repetir varios ciclos sin reconocer), la tercera tarjeta dice
  "y N novedades más" y su pill "Ver todas" trae la ventana al frente.

Matar el proceso (`java`/`gradle`/`kotlin`) al terminar, igual que en la fase 4.

- [ ] **Step 5: `spotlessApply` + verificación completa**

```powershell
.\gradlew.bat spotlessApply --console=plain
.\gradlew.bat :shared:jvmTest :shared:koverVerify :desktopApp:compileKotlin spotlessCheck --console=plain
```

Si `spotlessApply` no envuelve alguna línea por encima de 120 columnas (firmas largas, strings),
partirla a mano.

- [ ] **Step 6: Commit**

```bash
git add shared/src/jvmMain/kotlin/io/github/shizukajiku/imagewatch/ui/toast/ToastWindow.kt desktopApp/src/main/kotlin/io/github/shizukajiku/imagewatch/Main.kt
git commit -m "feat: ToastCard usa la rejilla 340x78 del blueprint con icono y accion por tipo"
```

---

## Self-Review

**Cobertura del spec §5.1 y §10:**
- 10.1 modelo `Toast`/`ToastKind`, `toastOf` sin versión anterior, `showFailures`, `RESUMEN`
  derivado, `TOASTS_VISIBLES` 3 → Task 2.
- 10.2 rejilla 340×78, tono por `kind`, `color = surface`, `tonalElevation = Elevation.toast`,
  `TOASTS_VISIBLES` en el ancho → Task 4.
- 10.3 `ToastLayer` con `take(3)` y `spacedBy(Space.sm)` → Task 4.
- 10.4 tests de `ToastStateTest`/`ToastNotificationPortTest`/`VersionPollingServiceTest` → Tasks 1-3.
- 5.1 `NotificationPort.notifyFailures` + bucle en `notifyTransitions` → Task 1.

**Puntos abiertos que quedan para revisión manual del usuario (no bloquean el cierre de fase):**
1. El icono exacto por `ToastKind` (`BELL`/`WARNING`/`CHECK_ALL`) y el tono de `RESUMEN` (acento
   fijo, no derivado de `statusColors`) no estaban explícitos en el spec — es una decisión de esta
   implementación, a validar visualmente.
2. `RESUMEN` "Ver todas" solo trae la ventana al frente; no fuerza `screen = IMAGES` si Ajustes
   está abierto (el estado `screen` vive dentro de `MainScreen`, no en `Wiring`/`main()`) ni hace
   scroll a la primera pendiente — el spec lo marca como "si hace falta".
3. Provocar un `ERROR` real para el repaso visual del Step 4 de Task 4 requiere tocar
   `IMAGE_VERSION_URL`/`SIMULATION_MODE`; si no se hace, esa combinación queda verificada solo por
   test unitario.
