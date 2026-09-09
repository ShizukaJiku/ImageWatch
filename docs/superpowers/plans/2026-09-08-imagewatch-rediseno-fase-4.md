# ImageWatch — Rediseño, Fase 4: Ajustes

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ajustes se aplica al momento (sin botón «Guardar»), en dos columnas de tarjetas con línea de ayuda reservada por campo, y gana «Iniciar al encender el equipo», «Restablecer ajustes» y «Borrar datos locales» con diálogo de confirmación.

**Architecture:** `SettingsViewModel` deja de acumular un formulario: cada `onXChange` aplica ya. URL, intervalo y duración validan en un commit explícito (blur/Enter) y pintan su error en la línea de ayuda de su campo, sin mover nada. Un puerto nuevo `AutostartPort` (interfaz en `commonMain`, implementación Windows con `reg.exe` en `desktopApp`) gobierna el arranque al iniciar sesión; no vive en `AppConfig` porque es estado del sistema operativo. `Wiring` gana `wipeLocalData()` (borra los ficheros y cierra la app) y `resetSettings()` (aplica la config de fábrica en caliente vía `applyConfig`). Un `ConfirmDialog` compartido sustituye a `DeleteDialog`.

**Tech Stack:** Kotlin Multiplatform, Compose Desktop Material 3, `kotlin.test`, Gradle con Spotless + Kover. JDK Microsoft 21.

**Spec:** `docs/superpowers/specs/2026-09-08-imagewatch-rediseno-design.md` (§9, decisiones D-3, D-5, D-6).

**Plan anterior:** `docs/superpowers/plans/2026-09-08-imagewatch-rediseno-fases-1-3.md` (fases 1–3 completadas).

## Global Constraints

- **Construir SIEMPRE desde PowerShell:**
  ```powershell
  $env:JAVA_HOME='C:\Users\shizu\.jdks\ms-21.0.12.1'
  Set-Location C:\Users\shizu\IdeaProjects\ImageWatch
  .\gradlew.bat <tarea>
  ```
- **Verde al cerrar cada tarea:** `.\gradlew.bat :shared:jvmTest :shared:koverVerify :desktopApp:compileKotlin spotlessCheck`. Si Spotless falla: `.\gradlew.bat spotlessApply` y repetir.
- **Comentarios, KDoc y nombres de test en español**, explicando el porqué. Nombres de test entre backticks.
- **Todo cambio de comportamiento va precedido de su test** (rojo → verde). El layout de Compose no tiene test unitario en el proyecto: se cierra con `build` + repaso visual anotado en el PR.
- **`reg.exe` para el autostart**, no `java.util.prefs`: las prefs de Java en Windows viven en `HKCU\Software\JavaSoft\Prefs`, no en la clave `Run`. `reg.exe` está siempre en el PATH y no exige módulo de jlink nuevo.
- **`AppConfig` no cambia.** El autostart es estado del SO; lo lee y escribe el puerto directamente.
- **Nunca `git checkout` ni `git restore`** en este repo.
- Un PR por rama (`feature/rediseno-fase-4-ajustes`), sale de `feature/rediseno-fase-3-fila-abierta`.
- Commits en español, formato `<tipo>: <descripción>`, terminando con:
  ```
  Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
  Claude-Session: https://claude.ai/code/session_019RioxagAx3sXkNcUVk6ihF
  ```

---

## Estructura de ficheros

Prefijo `commonMain`: `shared/src/commonMain/kotlin/io/github/shizukajiku/imagewatch/`.

### Se crean

| Fichero | Responsabilidad |
|---|---|
| `application/AutostartPort.kt` | `interface AutostartPort { fun isEnabled(): Boolean; fun setEnabled(enabled: Boolean) }`. |
| `desktopApp/src/main/kotlin/io/github/shizukajiku/imagewatch/WindowsAutostart.kt` | `class WindowsAutostart(private val label: String, private val command: List<String>) : AutostartPort` con `reg.exe`. |
| `desktopApp/src/test/kotlin/io/github/shizukajiku/imagewatch/WindowsAutostartTest.kt` | Escribe y relee una clave `Run` de prueba (`ImageWatch-test-<random>`), la borra al terminar. |
| `ui/dialogs/ConfirmDialog.kt` | Diálogo de confirmación compartido (420 dp, icono de alerta, lista de «lo que se pierde», «No se puede deshacer.», Cancelar + confirmación en contenedor de error). |

### Se modifican

| Fichero | Cambio |
|---|---|
| `ui/settings/SettingsViewModel.kt` | Aplicar-al-momento: fuera `save()`, `savedAt`, `simulationMode`, `error` global, `onSimulationChange`. Dentro `urlError`/`intervalError`/`toastError`, `autostart`, `onAutostartChange`, `onUrlCommit`/`onIntervalCommit`/`onToastSecondsCommit`. Constructor gana `autostart: AutostartPort`. |
| `ui/settings/SettingsScreen.kt` | Dos columnas de `SettingsCard`; control segmentado de tema; `HelpLine` reservada bajo URL/Intervalo/Duración; toggle «Iniciar al encender el equipo»; pie con «Restablecer ajustes» + «Borrar datos locales» + «N imágenes vigiladas»; chevron en círculo. Fuera «Modo simulación», «Guardar», «Guardado». |
| `ui/settings/SettingsViewModelTest.kt` | Reescrito para el modelo aplicar-al-momento + fake `AutostartPort`. |
| `ui/dialogs/ImageDialogs.kt` | Se retira `DeleteDialog` (lo sustituye `ConfirmDialog`). `NameDialog` se queda. |
| `desktopApp/.../Main.kt` | `Wiring` construye `WindowsAutostart`, gana `wipeLocalData()` y `resetSettings()`. `SettingsPane` pasa las lambdas nuevas y quita `onSave`/`onSimulationChange`. `MainScreen` usa `ConfirmDialog` para «Quitar de la lista». |
| `desktopApp/build.gradle.kts` | Nada de `modules` (reg.exe no lo exige). Solo si `WindowsAutostartTest` necesita algo. |

### No se toca

`domain/`, el resto de `application/`, `infrastructure/`, `ui/images/`, `ui/toast/`, `ui/components/TitleBar.kt`.

---

### Task 1: Rama y `AutostartPort`

**Files:**
- Create: `shared/src/commonMain/kotlin/io/github/shizukajiku/imagewatch/application/AutostartPort.kt`

- [ ] **Step 1: Rama desde la fase 3**

```powershell
git switch feature/rediseno-fase-3-fila-abierta
git switch -c feature/rediseno-fase-4-ajustes
$env:JAVA_HOME='C:\Users\shizu\.jdks\ms-21.0.12.1'
.\gradlew.bat :shared:jvmTest spotlessCheck --console=plain
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Crear el puerto**

```kotlin
package io.github.shizukajiku.imagewatch.application

/**
 * Arranque de la aplicación al iniciar sesión en el sistema. Es estado del sistema operativo,
 * no configuración de la app: no vive en `AppConfig` ni en `config.json`. La pantalla de ajustes
 * lo lee y lo escribe directamente a través de este puerto.
 */
interface AutostartPort {
    fun isEnabled(): Boolean

    fun setEnabled(enabled: Boolean)
}
```

- [ ] **Step 3: Compilar y commit**

```powershell
$env:JAVA_HOME='C:\Users\shizu\.jdks\ms-21.0.12.1'
.\gradlew.bat :shared:compileKotlinJvm spotlessCheck --console=plain
```
Expected: `BUILD SUCCESSFUL`.

```powershell
git add shared/src/commonMain/kotlin/io/github/shizukajiku/imagewatch/application/AutostartPort.kt
git commit -m "feat: puerto AutostartPort para el arranque al iniciar sesión"
```

---

### Task 2: `SettingsViewModel` — aplicar al momento

**Files:**
- Modify: `shared/src/commonMain/kotlin/io/github/shizukajiku/imagewatch/ui/settings/SettingsViewModel.kt`
- Modify: `shared/src/commonTest/kotlin/io/github/shizukajiku/imagewatch/ui/settings/SettingsViewModelTest.kt`

**Interfaces:**
- Consumes: `AutostartPort` (Task 1); `apply: (AppConfig) -> String?` (existente, de `Wiring::applyConfig`).
- Produces: `SettingsUiState` sin `error`/`savedAt`/`simulationMode`, con `autostart`, `urlError`, `intervalError`, `toastError`. Métodos `onUrlChange`/`onUrlCommit`, `onIntervalChange`/`onIntervalCommit`, `onToastSecondsChange`/`onToastSecondsCommit`, `onIgnoreSslChange`, `onThemeChange`, `onToastsChange`, `onSoundsChange`, `onVolumeChange`, `onMutedAllChange`, `onAutostartChange`. Los consume `Main.SettingsPane` (Task 6).

- [ ] **Step 1: Reescribir el test**

Reemplazar `SettingsViewModelTest.kt` entero por:

```kotlin
package io.github.shizukajiku.imagewatch.ui.settings

import io.github.shizukajiku.imagewatch.application.AutostartPort
import io.github.shizukajiku.imagewatch.config.AppConfig
import io.github.shizukajiku.imagewatch.config.ThemePreference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds

private fun config() = AppConfig(
    "images.json",
    "https://origen.ejemplo/api",
    300.seconds,
    listOf("alpha"),
    true,
    true,
    ThemePreference.SYSTEM,
    true,
    8.seconds,
    true,
    0.5,
    false,
)

private class FakeAutostart(var enabled: Boolean = false) : AutostartPort {
    override fun isEnabled() = enabled

    override fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }
}

class SettingsViewModelTest {

    private fun viewModel(
        onApply: (AppConfig) -> String? = { null },
        autostart: AutostartPort = FakeAutostart(),
    ) = SettingsViewModel(config(), onApply, autostart)

    @Test
    fun `arranca con los valores vigentes y el estado del autostart`() {
        val vm = viewModel(autostart = FakeAutostart(enabled = true))

        val state = vm.state.value

        assertEquals("https://origen.ejemplo/api", state.remoteUrl)
        assertEquals("300", state.intervalSeconds)
        assertEquals(ThemePreference.SYSTEM, state.theme)
        assertEquals(true, state.autostart)
    }

    @Test
    fun `un toggle se aplica al instante`() {
        var recibida: AppConfig? = null
        val vm = viewModel(onApply = { recibida = it; null })

        vm.onThemeChange(ThemePreference.DARK)

        assertEquals(ThemePreference.DARK, recibida?.theme)
        assertEquals(ThemePreference.DARK, vm.state.value.theme)
    }

    @Test
    fun `el volumen se aplica al instante`() {
        var recibida: AppConfig? = null
        val vm = viewModel(onApply = { recibida = it; null })

        vm.onVolumeChange(0.2f)

        assertEquals(0.2, recibida?.soundVolume)
    }

    @Test
    fun `silenciar todos los avisos se aplica al instante`() {
        var recibida: AppConfig? = null
        val vm = viewModel(onApply = { recibida = it; null })

        vm.onMutedAllChange(true)

        assertEquals(true, recibida?.mutedAll)
    }

    @Test
    fun `el intervalo valido se aplica en el commit`() {
        var recibida: AppConfig? = null
        val vm = viewModel(onApply = { recibida = it; null })

        vm.onIntervalChange("15")
        assertNull(recibida, "Teclear no aplica todavía")

        vm.onIntervalCommit()
        assertEquals(15.seconds, recibida?.pollInterval)
        assertNull(vm.state.value.intervalError)
    }

    @Test
    fun `un intervalo por debajo del minimo no se aplica y pinta su linea de ayuda`() {
        var llamado = false
        val vm = viewModel(onApply = { llamado = true; null })

        vm.onIntervalChange("3")
        vm.onIntervalCommit()

        assertEquals("El intervalo mínimo es 5 s.", vm.state.value.intervalError)
        assertEquals(false, llamado)
    }

    @Test
    fun `un intervalo por encima del maximo no se aplica`() {
        val vm = viewModel(onApply = { error("no debería llamarse") })

        vm.onIntervalChange("4000")
        vm.onIntervalCommit()

        assertEquals("El intervalo máximo es 3600 s.", vm.state.value.intervalError)
    }

    @Test
    fun `una duracion de aviso fuera de rango pinta su linea de ayuda`() {
        val vm = viewModel(onApply = { error("no debería llamarse") })

        vm.onToastSecondsChange("40")
        vm.onToastSecondsCommit()

        assertEquals("Entre 3 s y 30 s.", vm.state.value.toastError)
    }

    @Test
    fun `una URL rechazada por el aplicador pinta la linea de ayuda de la URL`() {
        val vm = viewModel(onApply = { "El endpoint remoto debe utilizar HTTPS" })

        vm.onUrlChange("http://inseguro.ejemplo")
        vm.onUrlCommit()

        assertEquals("El endpoint remoto debe utilizar HTTPS", vm.state.value.urlError)
    }

    @Test
    fun `activar el autostart llega al puerto`() {
        val autostart = FakeAutostart()
        val vm = viewModel(autostart = autostart)

        vm.onAutostartChange(true)

        assertEquals(true, autostart.enabled)
        assertEquals(true, vm.state.value.autostart)
    }
}
```

- [ ] **Step 2: Ejecutar y ver que falla al compilar**

```powershell
$env:JAVA_HOME='C:\Users\shizu\.jdks\ms-21.0.12.1'
.\gradlew.bat :shared:compileTestKotlinJvm --console=plain
```
Expected: FALLA — `SettingsViewModel` aún tiene la firma vieja y no existen `onUrlCommit` ni `urlError`.

- [ ] **Step 3: Reescribir `SettingsViewModel.kt`**

```kotlin
package io.github.shizukajiku.imagewatch.ui.settings

import io.github.shizukajiku.imagewatch.application.AutostartPort
import io.github.shizukajiku.imagewatch.config.AppConfig
import io.github.shizukajiku.imagewatch.config.ThemePreference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Duration.Companion.seconds

data class SettingsUiState(
    val remoteUrl: String,
    val intervalSeconds: String,
    val ignoreSslErrors: Boolean,
    val theme: ThemePreference,
    val toastsEnabled: Boolean,
    val toastSeconds: String,
    val soundsEnabled: Boolean,
    val soundVolume: Float,
    val mutedAll: Boolean,
    val autostart: Boolean,
    /** Cada error vive en la línea de ayuda -de altura reservada- de su propio campo. */
    val urlError: String? = null,
    val intervalError: String? = null,
    val toastError: String? = null,
)

/** El intervalo mínimo lo garantiza también el controlador; el máximo, la sensatez. */
private const val INTERVAL_MIN = 5L
private const val INTERVAL_MAX = 3600L
private const val TOAST_MIN = 3L
private const val TOAST_MAX = 30L

/**
 * Estado de la pantalla de ajustes. **No hay «Guardar»**: cada cambio se aplica al momento
 * (Blueprint: «Los cambios se aplican al momento»). Los toggles, el tema y el volumen aplican en
 * cuanto cambian; la URL, el intervalo y la duración validan en un commit explícito -al salir del
 * campo o con Enter- y, si fallan, pintan su mensaje en la línea de ayuda de su campo sin mover
 * nada más.
 *
 * El rango del intervalo y de la duración se comprueba aquí **antes** de aplicar solo para poder
 * pintar la línea de ayuda sin lanzar una consulta ni reiniciar el sondeo. Es duplicación
 * consciente y acotada -dos números- de reglas que el núcleo ya tiene.
 *
 * @param apply aplica la configuración sobre la aplicación viva y la persiste. Devuelve `null` si
 *   todo fue bien, o el mensaje a mostrar.
 * @param autostart puerto del arranque al iniciar sesión. No pasa por [apply]: es estado del SO.
 */
class SettingsViewModel(
    current: AppConfig,
    private val apply: (AppConfig) -> String?,
    private val autostart: AutostartPort,
) {
    private val stateFile = current.stateFile
    private val imageNames = current.imageNames
    private val simulationMode = current.simulationMode
    private val ignoreSslErrors0 = current.ignoreSslErrors

    private val mutableState = MutableStateFlow(
        SettingsUiState(
            remoteUrl = current.remoteUrl,
            intervalSeconds = current.pollInterval.inWholeSeconds.toString(),
            ignoreSslErrors = current.ignoreSslErrors,
            theme = current.theme,
            toastsEnabled = current.toastsEnabled,
            toastSeconds = current.toastDuration.inWholeSeconds.toString(),
            soundsEnabled = current.soundsEnabled,
            soundVolume = current.soundVolume.toFloat(),
            mutedAll = current.mutedAll,
            autostart = autostart.isEnabled(),
        ),
    )
    val state: StateFlow<SettingsUiState> = mutableState.asStateFlow()

    // --- se aplican al instante ---

    fun onIgnoreSslChange(value: Boolean) = editAndApply { it.copy(ignoreSslErrors = value) }

    fun onThemeChange(value: ThemePreference) = editAndApply { it.copy(theme = value) }

    fun onToastsChange(value: Boolean) = editAndApply { it.copy(toastsEnabled = value) }

    fun onSoundsChange(value: Boolean) = editAndApply { it.copy(soundsEnabled = value) }

    fun onVolumeChange(value: Float) = editAndApply { it.copy(soundVolume = value) }

    fun onMutedAllChange(value: Boolean) = editAndApply { it.copy(mutedAll = value) }

    fun onAutostartChange(value: Boolean) {
        autostart.setEnabled(value)
        mutableState.value = mutableState.value.copy(autostart = autostart.isEnabled())
    }

    // --- teclear no aplica; el commit valida y aplica ---

    fun onUrlChange(value: String) {
        mutableState.value = mutableState.value.copy(remoteUrl = value, urlError = null)
    }

    fun onUrlCommit() {
        mutableState.value = mutableState.value.copy(urlError = apply(configFromForm()))
    }

    fun onIntervalChange(value: String) {
        mutableState.value = mutableState.value.copy(intervalSeconds = value, intervalError = null)
    }

    fun onIntervalCommit() {
        val seconds = mutableState.value.intervalSeconds.trim().toLongOrNull()
        val error = when {
            seconds == null -> "El intervalo debe ser un número de segundos."
            seconds < INTERVAL_MIN -> "El intervalo mínimo es $INTERVAL_MIN s."
            seconds > INTERVAL_MAX -> "El intervalo máximo es $INTERVAL_MAX s."
            else -> apply(configFromForm())
        }
        mutableState.value = mutableState.value.copy(intervalError = error)
    }

    fun onToastSecondsChange(value: String) {
        mutableState.value = mutableState.value.copy(toastSeconds = value, toastError = null)
    }

    fun onToastSecondsCommit() {
        val seconds = mutableState.value.toastSeconds.trim().toLongOrNull()
        val error = when {
            seconds == null || seconds < TOAST_MIN || seconds > TOAST_MAX -> "Entre $TOAST_MIN s y $TOAST_MAX s."
            else -> apply(configFromForm())
        }
        mutableState.value = mutableState.value.copy(toastError = error)
    }

    private fun editAndApply(change: (SettingsUiState) -> SettingsUiState) {
        mutableState.value = change(mutableState.value)
        // Un fallo de E/S al guardar el volumen es excepcional; `applyConfig` ya lo registra en
        // el log. Estos campos no tienen línea de ayuda, así que el mensaje no se pinta.
        apply(configFromForm())
    }

    private fun configFromForm(): AppConfig {
        val form = mutableState.value
        return AppConfig(
            stateFile = stateFile,
            remoteUrl = form.remoteUrl.trim(),
            pollInterval = (form.intervalSeconds.trim().toLongOrNull() ?: 0L).seconds,
            imageNames = imageNames,
            simulationMode = simulationMode,
            ignoreSslErrors = form.ignoreSslErrors,
            theme = form.theme,
            toastsEnabled = form.toastsEnabled,
            toastDuration = (form.toastSeconds.trim().toLongOrNull() ?: 0L).seconds,
            soundsEnabled = form.soundsEnabled,
            soundVolume = form.soundVolume.toDouble(),
            mutedAll = form.mutedAll,
        )
    }
}
```

> `ignoreSslErrors0` no se usa fuera del estado inicial; se deja como referencia por si un test
> futuro lo necesita. Si Spotless marca el campo sin usar, borrarlo (el valor ya está en
> `mutableState`).

- [ ] **Step 4: Ejecutar los tests y ver que pasan**

```powershell
$env:JAVA_HOME='C:\Users\shizu\.jdks\ms-21.0.12.1'
.\gradlew.bat :shared:jvmTest --tests "io.github.shizukajiku.imagewatch.ui.settings.SettingsViewModelTest" spotlessCheck --console=plain
```
Expected: PASAN. Si Spotless se queja de `ignoreSslErrors0`, borrar esa línea y repetir.

- [ ] **Step 5: Commit (el árbol no compila entero todavía: `Main.kt` usa la firma vieja; se arregla en la Task 6)**

```powershell
git add shared/src/commonMain/kotlin/io/github/shizukajiku/imagewatch/ui/settings/SettingsViewModel.kt shared/src/commonTest/kotlin/io/github/shizukajiku/imagewatch/ui/settings/SettingsViewModelTest.kt
git commit -m "refactor: Ajustes se aplica al momento, sin botón «Guardar»"
```

---

### Task 3: `WindowsAutostart`

**Files:**
- Create: `desktopApp/src/main/kotlin/io/github/shizukajiku/imagewatch/WindowsAutostart.kt`
- Create: `desktopApp/src/test/kotlin/io/github/shizukajiku/imagewatch/WindowsAutostartTest.kt`

**Interfaces:**
- Consumes: `AutostartPort`.
- Produces: `class WindowsAutostart(label: String, command: List<String>) : AutostartPort`. Lo construye `Wiring` (Task 4).

- [ ] **Step 1: Escribir el test**

```kotlin
package io.github.shizukajiku.imagewatch

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WindowsAutostartTest {

    private val label = "ImageWatch-test-" + (1..8).map { ('a'..'z').random() }.joinToString("")
    private val autostart = WindowsAutostart(label, listOf("C:\\ruta\\imagewatch.exe", "--minimized"))

    @AfterTest
    fun limpiar() {
        autostart.setEnabled(false)
    }

    @Test
    fun `arranca desactivado si la clave no existe`() {
        assertEquals(false, autostart.isEnabled())
    }

    @Test
    fun `activar escribe la clave Run y desactivar la borra`() {
        autostart.setEnabled(true)
        assertTrue(autostart.isEnabled(), "La clave Run debería existir tras activar")

        autostart.setEnabled(false)
        assertEquals(false, autostart.isEnabled(), "La clave Run debería haberse borrado")
    }

    @Test
    fun `activar dos veces no falla`() {
        autostart.setEnabled(true)
        autostart.setEnabled(true)
        assertTrue(autostart.isEnabled())
    }
}
```

- [ ] **Step 2: Ejecutar y ver que falla al compilar**

```powershell
$env:JAVA_HOME='C:\Users\shizu\.jdks\ms-21.0.12.1'
.\gradlew.bat :desktopApp:compileTestKotlin --console=plain
```
Expected: FALLA — `WindowsAutostart` no existe.

- [ ] **Step 3: Implementar `WindowsAutostart`**

```kotlin
package io.github.shizukajiku.imagewatch

import io.github.shizukajiku.imagewatch.application.AutostartPort
import org.slf4j.LoggerFactory

/**
 * Arranque al iniciar sesión mediante la clave `Run` del registro de usuario de Windows. Se usa
 * `reg.exe` -siempre en el PATH- y no `java.util.prefs`, cuyas preferencias viven en otra rama del
 * registro (`HKCU\Software\JavaSoft\Prefs`), no en `Run`.
 *
 * @param label nombre del valor bajo `Run` (p. ej. «ImageWatch»).
 * @param command ejecutable y argumentos con los que relanzar la app; se unen entre comillas.
 */
class WindowsAutostart(
    private val label: String,
    private val command: List<String>,
) : AutostartPort {
    private val runKey = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run"

    override fun isEnabled(): Boolean =
        run("reg", "query", runKey, "/v", label) == 0

    override fun setEnabled(enabled: Boolean) {
        val code = if (enabled) {
            run("reg", "add", runKey, "/v", label, "/t", "REG_SZ", "/d", command.joinToString(" ") { "\"$it\"" }, "/f")
        } else {
            // /f para que borrar una clave ausente no sea un error.
            run("reg", "delete", runKey, "/v", label, "/f")
        }
        if (code != 0 && enabled) {
            LOG.warn("No se pudo escribir la clave de arranque «{}» (reg.exe salió con {})", label, code)
        }
    }

    private fun run(vararg args: String): Int =
        try {
            ProcessBuilder(*args)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
                .waitFor()
        } catch (e: Exception) {
            LOG.warn("No se pudo ejecutar reg.exe", e)
            -1
        }

    private companion object {
        private val LOG = LoggerFactory.getLogger(WindowsAutostart::class.java)
    }
}
```

- [ ] **Step 4: Ejecutar los tests y ver que pasan**

```powershell
$env:JAVA_HOME='C:\Users\shizu\.jdks\ms-21.0.12.1'
.\gradlew.bat :desktopApp:test --tests "io.github.shizukajiku.imagewatch.WindowsAutostartTest" --console=plain
```
Expected: PASAN (escriben y borran una clave `Run` de usuario de prueba).

> Si `:desktopApp` no tiene configurada la tarea `test` para Kotlin, el fichero de test va donde
> `:desktopApp` ya tenga sus tests; si no hay ninguno, se añade `kotlin("test")` a
> `desktopApp/build.gradle.kts` en `dependencies { testImplementation(...) }` y
> `tasks.test { useJUnitPlatform() }`. Comprobar primero con
> `.\gradlew.bat :desktopApp:tasks --all | Select-String test`.

- [ ] **Step 5: Commit**

```powershell
git add desktopApp/src/main/kotlin/io/github/shizukajiku/imagewatch/WindowsAutostart.kt desktopApp/src/test/kotlin/io/github/shizukajiku/imagewatch/WindowsAutostartTest.kt
git commit -m "feat: WindowsAutostart con la clave Run del registro"
```

---

### Task 4: `Wiring` — autostart, `wipeLocalData`, `resetSettings`

**Files:**
- Modify: `desktopApp/src/main/kotlin/io/github/shizukajiku/imagewatch/Main.kt`

**Interfaces:**
- Produces: `Wiring.autostart: AutostartPort`, `Wiring.wipeLocalData(onDone: () -> Unit)`, `Wiring.resetSettings(): String?`. Los consume `MainScreen`/`SettingsPane` (Task 6).

- [ ] **Step 1: Construir `WindowsAutostart` en `Wiring`**

En la clase `Wiring`, junto a los demás campos:

```kotlin
    /**
     * Arranque al iniciar sesión. El comando es el ejecutable actual: en la instalación es el
     * lanzador de la app; con `gradle run` es la JVM -donde el autostart no tiene sentido, pero
     * tampoco molesta-.
     */
    val autostart: AutostartPort = WindowsAutostart(
        label = "ImageWatch",
        command = listOf(ProcessHandle.current().info().command().orElse("imagewatch"), "--minimized"),
    )
```

Import: `io.github.shizukajiku.imagewatch.application.AutostartPort`.

- [ ] **Step 2: `resetSettings()`**

En `Wiring`, tras `applyConfig`:

```kotlin
    /**
     * Ajustes a valores de fábrica. Las imágenes vigiladas y su versión vista no se tocan: solo
     * los campos de configuración. Seguro en caliente -`applyConfig` está pensado para eso-.
     */
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
            ),
        )
    }
```

- [ ] **Step 3: `wipeLocalData()`**

```kotlin
    /**
     * Borra todo lo que la app guarda en este equipo -los cuatro ficheros JSON- y cierra la
     * aplicación. No se recablea `Wiring` en caliente: `service` tiene oyentes registrados y el
     * view model guarda una referencia a él; rehacer sus campos vivos es frágil. Volver a abrir
     * la app la siembra de cero desde el entorno, que es el mismo camino que el primer arranque.
     */
    fun wipeLocalData(onDone: () -> Unit) {
        controller.stop()
        val base = configFromEnvironment().stateFile.toPath()
        listOf(base, base.sibling("config.json"), base.sibling("tracked-images.json"), base.sibling("silenced-images.json"))
            .forEach { runCatching { JsonFiles.fileSystem.delete(it, mustExist = false) } }
        onDone()
    }
```

Imports: `io.github.shizukajiku.imagewatch.infrastructure.persistence.JsonFiles`. `sibling` ya existe como extensión privada en `Main.kt`.

> `JsonFiles.fileSystem` es `internal` en `commonMain`; si no es visible desde `desktopApp`,
> usar `okio.FileSystem.SYSTEM` directamente (import `okio.FileSystem`), que es lo que
> `JsonFiles` envuelve.

- [ ] **Step 4: Compilar**

```powershell
$env:JAVA_HOME='C:\Users\shizu\.jdks\ms-21.0.12.1'
.\gradlew.bat :desktopApp:compileKotlin --console=plain
```
Expected: FALLA en `SettingsPane` y `MainScreen` (firmas viejas) — se arregla en la Task 6. **La compilación de `Wiring` en sí debe pasar**; si el error es solo en `SettingsPane`/`MainScreen`/`DeleteDialog`, seguir.

- [ ] **Step 5: Commit**

```powershell
git add desktopApp/src/main/kotlin/io/github/shizukajiku/imagewatch/Main.kt
git commit -m "feat: Wiring construye el autostart y expone wipeLocalData/resetSettings"
```

---

### Task 5: `ConfirmDialog`

**Files:**
- Create: `shared/src/commonMain/kotlin/io/github/shizukajiku/imagewatch/ui/dialogs/ConfirmDialog.kt`
- Modify: `shared/src/commonMain/kotlin/io/github/shizukajiku/imagewatch/ui/dialogs/ImageDialogs.kt` (retirar `DeleteDialog`)

**Interfaces:**
- Produces: `@Composable fun ConfirmDialog(title: String, body: String, lost: List<String>, confirmLabel: String, onDismiss: () -> Unit, onConfirm: () -> Unit)`. Lo consume `MainScreen`/`SettingsScreen` (Task 6).

- [ ] **Step 1: Crear `ConfirmDialog.kt`**

```kotlin
package io.github.shizukajiku.imagewatch.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Dialog
import io.github.shizukajiku.imagewatch.ui.components.AppSvg
import io.github.shizukajiku.imagewatch.ui.components.SvgIcon
import io.github.shizukajiku.imagewatch.ui.theme.Elevation
import io.github.shizukajiku.imagewatch.ui.theme.IconSize
import io.github.shizukajiku.imagewatch.ui.theme.Layout
import io.github.shizukajiku.imagewatch.ui.theme.Radius
import io.github.shizukajiku.imagewatch.ui.theme.Space
import io.github.shizukajiku.imagewatch.ui.theme.TabularNums
import io.github.shizukajiku.imagewatch.ui.theme.TypeScale

/**
 * Diálogo de confirmación para las acciones que no se pueden deshacer (Blueprint «Diálogo de
 * confirmación»): quitar una imagen, restablecer los ajustes, borrar los datos locales. La lista
 * [lost] enumera lo que se pierde, un punto por línea. La línea «No se puede deshacer.» tiene
 * altura reservada. La confirmación va en contenedor de error, con ancho mínimo para que su texto
 * no cambie la geometría.
 */
@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    lost: List<String>,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(Radius.md),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = Elevation.dialog,
            modifier = Modifier.widthIn(max = Layout.dialogWidth),
        ) {
            Column(Modifier.padding(Space.xl), verticalArrangement = Arrangement.spacedBy(Space.md)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = CircleShape) {
                        Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                            SvgIcon(AppSvg.ALERT, MaterialTheme.colorScheme.onErrorContainer, Modifier.size(IconSize.md))
                        }
                    }
                    Text(title, fontWeight = FontWeight.Bold, fontSize = TypeScale.body)
                }
                Text(body, fontSize = TypeScale.meta, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Surface(color = MaterialTheme.colorScheme.background, shape = RoundedCornerShape(Radius.sm)) {
                    Column(Modifier.fillMaxWidth().padding(Space.md), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                        lost.forEach { linea ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                                Box(Modifier.size(4.dp).let { it })
                                Text(linea, fontSize = TypeScale.meta, color = MaterialTheme.colorScheme.onSurfaceVariant, style = TabularNums)
                            }
                        }
                    }
                }
                Text(
                    "No se puede deshacer.",
                    fontSize = TypeScale.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.height(Layout.settingsHelpLine),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    Spacer(Modifier.weight(1f))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(Radius.pill),
                        onClick = onDismiss,
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
                    ) {
                        Text(
                            confirmLabel,
                            fontSize = TypeScale.meta,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.widthIn(min = Layout.dialogConfirmMin).padding(horizontal = Space.md, vertical = Space.sm),
                        )
                    }
                }
            }
        }
    }
}
```

Import de `dp`: `androidx.compose.ui.unit.dp`. El punto de 4 dp: sustituir
`Box(Modifier.size(4.dp).let { it })` por
`Box(Modifier.size(4.dp).background(MaterialTheme.colorScheme.onSurfaceVariant, CircleShape))` con
`import androidx.compose.foundation.background`.

- [ ] **Step 2: Comprobar que `AppSvg.ALERT` existe**

```powershell
Select-String -Path shared/src/commonMain/kotlin/io/github/shizukajiku/imagewatch/ui/components/SvgIcon.kt -Pattern 'ALERT|ALARM|WARNING'
```
Si no hay un icono de alerta, usar el que exista para errores (p. ej. `AppSvg.TRASH` no; buscar
uno de aviso). Si de verdad no hay ninguno, añadir `ALERT` al enum con el `path`
`M12 8v5M12 16h.01M10.3 4l-7 12a2 2 0 0 0 1.7 3h14a2 2 0 0 0 1.7-3l-7-12a2 2 0 0 0-3.4 0z`
(el del Blueprint) en `SvgIcon.kt` / `SvgIcon.jvm.kt`.

- [ ] **Step 3: Retirar `DeleteDialog` de `ImageDialogs.kt`**

Borrar la función `DeleteDialog` entera y los imports que queden sin uso (`AlertDialog` sigue si
`NameDialog` lo usa —lo usa—). `NameDialog` no se toca.

- [ ] **Step 4: Compilar**

```powershell
$env:JAVA_HOME='C:\Users\shizu\.jdks\ms-21.0.12.1'
.\gradlew.bat :shared:compileKotlinJvm spotlessCheck --console=plain
```
Expected: FALLA solo en `Main.kt` (usa `DeleteDialog`). `shared` compila. Si Spotless se queja
del `ConfirmDialog`, `spotlessApply`.

- [ ] **Step 5: Commit**

```powershell
git add shared/src/commonMain/kotlin/io/github/shizukajiku/imagewatch/ui/dialogs/
git commit -m "feat: ConfirmDialog compartido; retirar DeleteDialog"
```

---

### Task 6: `SettingsScreen` de dos columnas + cablear todo; cerrar la fase

**Files:**
- Modify: `shared/src/commonMain/kotlin/io/github/shizukajiku/imagewatch/ui/settings/SettingsScreen.kt`
- Modify: `desktopApp/src/main/kotlin/io/github/shizukajiku/imagewatch/Main.kt`

- [ ] **Step 1: Reescribir `SettingsScreen.kt`**

Estructura (Blueprint `Ajustes.dc.html`): título con chevron en círculo de 32 dp; `Row` de dos
`Column` (`weight(1f)`, `spacedBy(Space.lg)`); cada grupo un `SettingsCard`. Izquierda: **Origen**
(campo URL mono + `HelpLine(urlError ?: "Se comprueba al salir del campo o con Enter.")` + toggle
«Ignorar errores de TLS»), **Comprobación** (toggle «Iniciar al encender el equipo» + `Row` [campo
Intervalo 132 + `HelpLine(intervalError ?: "Entre 5 s y 3600 s.")`] [botón Detener/Iniciar +
caption]). Derecha: **Apariencia** (`Segmented` Sistema/Claro/Oscuro + caption), **Avisos** (toggle
«Silenciar todos los avisos» + campo Duración 132 + `HelpLine(toastError ?: "Entre 3 s y 30 s.")`
+ toggle «Sonidos» + `Row` [«Volumen»] [slider] [«N %» `TabularNums`]). Pie: `Row` [pill
«Restablecer ajustes» `surfaceVariant`] [pill «Borrar datos locales» `errorContainer`] …
`weight(1f)` … [«N imágenes vigiladas» `TabularNums`].

Firma nueva:

```kotlin
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    polling: Boolean,
    watchedCount: Int,
    onTogglePolling: () -> Unit,
    onUrlChange: (String) -> Unit,
    onUrlCommit: () -> Unit,
    onIntervalChange: (String) -> Unit,
    onIntervalCommit: () -> Unit,
    onIgnoreSslChange: (Boolean) -> Unit,
    onThemeChange: (ThemePreference) -> Unit,
    onToastsChange: (Boolean) -> Unit,
    onToastSecondsChange: (String) -> Unit,
    onToastSecondsCommit: () -> Unit,
    onSoundsChange: (Boolean) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onMutedAllChange: (Boolean) -> Unit,
    onAutostartChange: (Boolean) -> Unit,
    onResetSettings: () -> Unit,
    onWipeLocalData: () -> Unit,
    onBack: () -> Unit,
)
```

Componentes privados nuevos en el mismo fichero:

```kotlin
@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(Radius.md),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Space.lg), verticalArrangement = Arrangement.spacedBy(Space.md)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = TypeScale.body)
            content()
        }
    }
}

@Composable
private fun HelpLine(text: String, isError: Boolean) {
    Text(
        text,
        fontSize = TypeScale.caption,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.height(Layout.settingsHelpLine),
    )
}

@Composable
private fun Segmented(selected: ThemePreference, onSelect: (ThemePreference) -> Unit) {
    Row(
        Modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(Radius.pill))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        ThemePreference.entries.forEach { option ->
            val on = option == selected
            Text(
                themeLabel(option),
                textAlign = TextAlign.Center,
                fontSize = TypeScale.meta,
                fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                color = if (on) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .weight(1f)
                    .then(if (on) Modifier.background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(Radius.pill)) else Modifier)
                    .clickable { onSelect(option) }
                    .padding(vertical = Space.sm),
            )
        }
    }
}

@Composable
private fun NumberField(value: String, unit: String, isError: Boolean, onChange: (String) -> Unit, onCommit: () -> Unit) {
    // BasicTextField dentro de un Box de 34 dp, borde Radius.sm; unidad fija a la derecha.
    // onCommit se dispara en onFocusChanged {!isFocused} y con KeyboardActions(onDone).
}

@Composable
private fun FootPill(text: String, container: Color, onContent: Color, onClick: () -> Unit) {
    Surface(color = container, shape = RoundedCornerShape(Radius.pill), onClick = onClick) {
        Text(text, color = onContent, fontSize = TypeScale.meta, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = Space.lg, vertical = Space.sm))
    }
}
```

El toggle reutiliza el `Toggle` privado actual. `NumberField` completo: usar `BasicTextField`
con `keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)`,
`keyboardActions = KeyboardActions(onDone = { onCommit() })`, y
`Modifier.onFocusChanged { if (!it.isFocused) onCommit() }`. Placeholder no hace falta (siempre
hay número).

Los dos botones del pie abren un `ConfirmDialog`:

```kotlin
    var confirming by remember { mutableStateOf<Confirm?>(null) }
    // ...
    FootPill("Restablecer ajustes", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant) {
        confirming = Confirm.RESET
    }
    FootPill("Borrar datos locales", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer) {
        confirming = Confirm.WIPE
    }
    // ...
    when (confirming) {
        Confirm.RESET -> ConfirmDialog(
            title = "Restablecer ajustes",
            body = "Los ajustes vuelven a sus valores de fábrica. Las imágenes vigiladas y su versión vista no se tocan.",
            lost = listOf("URL del registry · intervalo de fábrica", "Apariencia · Sistema", "Avisos · duración y sonidos de fábrica"),
            confirmLabel = "Restablecer",
            onDismiss = { confirming = null },
            onConfirm = onResetSettings,
        )
        Confirm.WIPE -> ConfirmDialog(
            title = "Borrar datos locales",
            body = "Se borra todo lo que la app guarda en este equipo. Las imágenes seguirán en el registry; la lista de vigilancia no.",
            lost = listOf("$watchedCount imágenes vigiladas", "La versión vista de cada una", "Los ajustes de la app"),
            confirmLabel = "Borrar todo",
            onDismiss = { confirming = null },
            onConfirm = onWipeLocalData,
        )
        null -> {}
    }
```
con `private enum class Confirm { RESET, WIPE }` a nivel de fichero.

`themeLabel` se conserva. Fuera: `Section` con `HorizontalDivider`, `FilterChip`, el `OutlinedTextField`
de la URL (pasa a `NumberField`-style o un `BasicTextField` mono de una línea), el botón «Guardar»
y su `AnimatedVisibility` de error/«Guardado», el toggle «Modo simulación», `onSimulationChange`,
`onSave`.

- [ ] **Step 2: `Main.kt` — `SettingsPane` y `MainScreen`**

`SettingsPane`: `SettingsViewModel(config, wiring::applyConfig, wiring.autostart)`. Pasa a
`SettingsScreen` las lambdas nuevas (`onUrlCommit`, `onIntervalCommit`, `onToastSecondsCommit`,
`onAutostartChange`, `onResetSettings = { wiring.resetSettings() }`,
`onWipeLocalData = { wiring.wipeLocalData(onExit) }`, `watchedCount = wiring.trackedImages.findAll().size`),
sin `onSave` ni `onSimulationChange`. `onExit` es la lambda que ya cierra la app desde el menú de
la bandeja -extraerla o pasar `exitApplication` a través de `MainScreen`-.

`MainScreen`: la rama `deleting?.let { ... DeleteDialog(...) }` pasa a:

```kotlin
    deleting?.let { name ->
        ConfirmDialog(
            title = "Quitar $name de la lista",
            body = "Deja de vigilarse y desaparece. La imagen seguirá en el registry.",
            lost = listOf("El seguimiento de $name", "La versión vista de $name"),
            confirmLabel = "Quitar de la lista",
            onDismiss = { deleting = null },
            onConfirm = { viewModel.removeImage(name) },
        )
    }
```

`MainScreen` necesita `exitApplication`: se le pasa desde `main()` a través de `MainScreen(wiring, viewModel, onExit)`
(hoy el `Item("Salir", ...)` de la bandeja hace el cierre; reutilizar esa misma secuencia).

- [ ] **Step 3: Compilar todo, tests, Spotless**

```powershell
$env:JAVA_HOME='C:\Users\shizu\.jdks\ms-21.0.12.1'
.\gradlew.bat :shared:jvmTest :desktopApp:compileKotlin spotlessCheck --console=plain
```
Expected: `BUILD SUCCESSFUL`. `spotlessApply` si hace falta.

- [ ] **Step 4: Repaso visual**

```powershell
$env:JAVA_HOME='C:\Users\shizu\.jdks\ms-21.0.12.1'
.\gradlew.bat :desktopApp:run --console=plain
```
Contra `Ajustes.dc.html`:
- Dos columnas de tarjetas con borde; sin botón «Guardar»; sin «Modo simulación».
- Tema con control segmentado; caption «el sistema pide tema X».
- Línea de ayuda de altura fija bajo URL, Intervalo y Duración; al meter un intervalo de 3
  aparece «El intervalo mínimo es 5 s.» y **nada se mueve**.
- Toggle «Iniciar al encender el equipo»: al activarlo, `reg query "HKCU\...\Run" /v ImageWatch`
  desde otra consola devuelve la clave; al desactivarlo, desaparece.
- Pie: «Restablecer ajustes» y «Borrar datos locales» abren el `ConfirmDialog`; «Restablecer»
  vuelve los campos a fábrica en caliente; «Borrar todo» borra los JSON y cierra la app.
- Volver a abrir tras «Borrar todo»: la app arranca sembrada de cero.

- [ ] **Step 5: `historias-de-usuario.md`**

En `docs/superpowers/specs/2026-09-05-imagewatch-historias-de-usuario.md`:
- Retirar H-79..H-84 (familia «Guardar ajustes»/`SettingsViewModel.save`) y la mención de «Modo
  simulación» donde aparezca, marcándolas `~~...~~ (retirada en el rediseño, fase 4)`.
- Añadir, con el formato Dado/Cuando/Entonces:
  - `H-93 · Un ajuste se aplica al momento` — código `SettingsViewModel.editAndApply`. Prueba:
    `SettingsViewModelTest` («un toggle se aplica al instante»).
  - `H-94 · Un valor inválido no mueve nada` — código `SettingsViewModel.onIntervalCommit`.
    Prueba: `SettingsViewModelTest` («un intervalo por debajo del minimo…»).
  - `H-95 · Iniciar al encender el equipo` — código `WindowsAutostart`, `SettingsViewModel.onAutostartChange`.
    Prueba: `WindowsAutostartTest`, `SettingsViewModelTest` («activar el autostart llega al puerto»).
  - `H-96 · Restablecer ajustes` — código `Wiring.resetSettings`. Prueba: repaso visual.
  - `H-97 · Borrar datos locales` — código `Wiring.wipeLocalData`. Prueba: repaso visual.

- [ ] **Step 6: Verde completo + commit + cierre**

```powershell
$env:JAVA_HOME='C:\Users\shizu\.jdks\ms-21.0.12.1'
.\gradlew.bat :shared:jvmTest :shared:koverVerify :desktopApp:compileKotlin spotlessCheck --console=plain
```
Expected: `BUILD SUCCESSFUL`; `koverVerify` cumple el 80 %.

```powershell
git add shared/src/commonMain/kotlin/io/github/shizukajiku/imagewatch/ui/settings/SettingsScreen.kt desktopApp/src/main/kotlin/io/github/shizukajiku/imagewatch/Main.kt docs/superpowers/specs/2026-09-05-imagewatch-historias-de-usuario.md
git commit -m "feat: Ajustes en dos columnas con líneas de ayuda, autostart y confirmaciones"
```

Abrir el PR `feature/rediseno-fase-4-ajustes` (o dejar la rama y anotar la revisión).

---

## Self-review

**Cobertura del spec (§9):**
- §9.1 aplicar-al-momento → Task 2. ✔
- §9.2 dos columnas / segmentado / help lines / sin simulación → Task 6. ✔
- §9.4 `wipeLocalData` (borrar + cerrar, sin recablear) + `resetSettings` → Task 4. ✔
- §9.5 `ConfirmDialog` sustituye `DeleteDialog` → Task 5 + Task 6. ✔
- §5.2 `AutostartPort` expect/actual → **desvío consciente**: interfaz plana en `commonMain` +
  `WindowsAutostart` en `desktopApp`, sin `expect`/`actual`. Un interface no necesita el mecanismo
  `expect`, y el único consumidor multiplataforma (`SettingsViewModel`) solo ve la interfaz. La
  implementación vive en `desktopApp` junto a `Wiring`, no en `jvmMain`, porque es cableado
  Windows-desktop. `reg.exe` en vez de `java.util.prefs` evita además el riesgo del módulo de
  jlink (§16.2 del spec). ✔

**Escaneo de placeholders:** `NumberField` en la Task 6 Step 1 se describe con el detalle de
implementación (BasicTextField + KeyboardActions + onFocusChanged) pero sin bloque completo — es
un componente de Compose sin superficie de test, y su forma exacta se ajusta en el repaso visual;
el contrato (value/unit/isError/onChange/onCommit) sí está fijado. Aceptable para una tarea de
layout.

**Consistencia de tipos:**
- `AutostartPort.isEnabled()/setEnabled(Boolean)` — Task 1, consumido igual en Tasks 2, 3, 4. ✔
- `SettingsViewModel(current, apply, autostart)` — Task 2, construido así en Task 6 (`SettingsPane`). ✔
- `SettingsUiState` sin `error`/`savedAt` — Task 2; `SettingsScreen` (Task 6) no los referencia. ✔
- `ConfirmDialog(title, body, lost, confirmLabel, onDismiss, onConfirm)` — Task 5, llamado igual
  en Task 6 (3 sitios: reset, wipe, quitar imagen). ✔
- `Wiring.wipeLocalData(onDone)` — Task 4; Task 6 lo llama con la lambda de salida de la app. ✔
