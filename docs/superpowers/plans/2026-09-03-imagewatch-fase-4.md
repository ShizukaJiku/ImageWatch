# ImageWatch — Plan de implementación, Fase 4: Compose Desktop

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Sustituir las 1.022 líneas de interfaz Swing por Compose Desktop en Kotlin, con paridad funcional y las siete animaciones que el diseño pide.

**Architecture:** El núcleo Java no se toca. La interfaz pasa a consumir el `StateFlow` que envuelve al `PollListener` ya existente, de modo que la recomposición sustituye al repintado manual. El composition root se traslada a Kotlin para poder abrir `application { Tray(...) }`. Cada composable recibe estado y lambdas, nunca el servicio: eso los hace testeables sin arrancar la aplicación.

**Tech Stack:** Kotlin 2.3.0 · Compose Multiplatform 1.12.0 · Material 3 · kotlinx-coroutines · Kover · detekt · Java 25 (núcleo intacto)

**Spec:** `docs/superpowers/specs/2026-09-03-imagewatch-design.md`

**Planes anteriores:** fases 0–2 y fase 3, ambos completados e integrados en `main`.

## Global Constraints

- **El núcleo Java no se modifica.** `domain/`, `application/` e `infrastructure/` quedan intactos salvo el borrado de `WindowsNotificationAdapter`, que es presentación disfrazada de infraestructura. **Si un test del núcleo se rompe, la migración se salió de su capa: párate.**
- **Kotlin 2.3.0** y **Compose Multiplatform 1.12.0**. Son las versiones verificadas por la prueba de concepto.
- **`jvmToolchain(25)`**; `jvmTarget` de Kotlin y `release` de Java deben coincidir en 25.
- **Hace falta `google()` en los repositorios.** Compose 1.12 arrastra artefactos `androidx` que no están en Maven Central. Sin él, once dependencias quedan sin resolver. *(Verificado en la prueba de concepto.)*
- **Material 3 se declara aparte.** `compose.desktop.currentOs` no lo incluye, y el alias `compose.material3` está marcado como obsoleto: usa la coordenada directa. *(Verificado.)*
- **`--enable-native-access=ALL-UNNAMED` se mantiene.** Skiko carga su motor igual que lo hacía FlatLaf; el flag no era una curita para FlatLaf. *(Verificado.)*
- **Paquete raíz:** `io.github.shizukajiku.imagewatch`.
- **Ningún identificador corporativo** puede entrar en el repositorio; el hook `pre-commit` lo verifica.
- **Los 59 tests existentes deben seguir verdes en cada tarea.**
- Mensajes de commit en formato convencional.

---

## Estructura de ficheros

### Se crean

| Fichero | Responsabilidad |
|---|---|
| `ui/theme/Colors.kt` | Los dos `ColorScheme`, claro y oscuro. Única sede del color. |
| `ui/theme/Theme.kt` | `ImageWatchTheme` — envuelve `MaterialTheme` y resuelve claro/oscuro. |
| `ui/images/ImagesViewModel.kt` | Adapta `PollListener` a `StateFlow`; expone el filtro y las acciones. |
| `ui/images/ImagesScreen.kt` | Cabecera, buscador, barra de sondeo y lista. |
| `ui/images/ImageRow.kt` | Una fila, con sus animaciones. |
| `ui/components/StatusBadge.kt` | Píldora de estado con transición de color. |
| `ui/components/VersionPill.kt` | Versión con rotación vertical al cambiar. |
| `ui/components/SvgIcon.kt` | Carga y cachea los SVG de `resources/icons/`. |
| `ui/dialogs/ImageDialogs.kt` | Alta, edición y confirmación de borrado. |
| `ui/AppIcon.kt` | El icono, como `Painter`, para bandeja y ventana. |
| `Main.kt` | Composition root y `application { Tray + Window }`. |

### Se eliminan

| Fichero | Motivo |
|---|---|
| `ui/ImageManagerFrame.java` (830 líneas) | Sustituido por seis composables. |
| `ui/TrayUi.java` (132) | Sustituido por el composable `Tray`. |
| `ui/AppIcon.java` (60) | Reescrito en Kotlin como `Painter`. |
| `Main.java` (52) | El composition root pasa a Kotlin. |
| `infrastructure/notification/WindowsNotificationAdapter.java` | El globo del sistema desaparece; los toasts llegan en la fase 5. |
| Dependencias `flatlaf`, `flatlaf-extras`, `modal-dialog` | Solo las usaba la interfaz borrada. |

### Comportamientos que deben sobrevivir

Inventario tomado de la interfaz actual. **Ninguno puede perderse:**

1. Listar las imágenes vigiladas con su estado
2. Filtrar por texto
3. Agregar imagen (con validación de nombre y de duplicado)
4. Editar el nombre de una imagen
5. Eliminar imagen, con confirmación
6. Marcar una imagen como vista
7. Arrancar y detener el sondeo
8. Cambiar el intervalo, con validación
9. Ver el estado del sondeo (activo/detenido) y el intervalo vigente
10. Resumen: cuántas imágenes y cuántas pendientes
11. Abrir la ventana desde la bandeja
12. Cerrar la ventana sin matar el proceso
13. Salir desde el menú de la bandeja
14. Actualizarse sola al llegar un snapshot

---

### Task 1: Rama y andamiaje del build

**Files:**
- Modify: `gradle/libs.versions.toml`, `build.gradle.kts`, `settings.gradle.kts`

**Interfaces:**
- Produces: `./gradlew compileKotlin` operativo; los alias `libs.compose.*`, `libs.kotlinx.coroutines.swing` y los plugins `libs.plugins.kotlin.jvm`, `libs.plugins.kotlin.compose`, `libs.plugins.compose`.

- [ ] **Step 1: Crear la rama**

```bash
git switch -c feature/fase-4-compose
./gradlew check
```

Expected: `BUILD SUCCESSFUL`, 59 tests. Es la línea base que no puede empeorar.

- [ ] **Step 2: Añadir versiones y plugins al catálogo**

En `gradle/libs.versions.toml`, dentro de `[versions]`:

```toml
kotlin = "2.3.0"
compose = "1.12.0"
coroutines = "1.10.2"
```

En `[libraries]`:

```toml
compose-material3 = { module = "org.jetbrains.compose.material3:material3", version.ref = "compose" }
kotlinx-coroutines-swing = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-swing", version.ref = "coroutines" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines" }
```

En `[plugins]`:

```toml
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
compose = { id = "org.jetbrains.compose", version.ref = "compose" }
```

`compose.desktop.currentOs` no lleva alias: lo aporta el propio plugin como extensión.

- [ ] **Step 3: Declarar el repositorio de plugins de Compose**

En `settings.gradle.kts`, **antes** de `rootProject.name`:

```kotlin
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

rootProject.name = "imagewatch"
```

- [ ] **Step 4: Aplicar los plugins y las dependencias**

En `build.gradle.kts`, la sección `plugins`:

```kotlin
plugins {
    application
    checkstyle
    alias(libs.plugins.spotless)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose)
}
```

Repositorios:

```kotlin
repositories {
    // Compose 1.12 arrastra artefactos androidx que NO están publicados en Maven Central.
    // Sin este repositorio, once dependencias quedan sin resolver.
    google()
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}
```

Toolchain de Kotlin, junto al de Java:

```kotlin
kotlin {
    jvmToolchain(libs.versions.java.get().toInt())
}
```

Dependencias nuevas:

```kotlin
    implementation(compose.desktop.currentOs)
    // Material 3 NO viene en desktop.currentOs, y el alias compose.material3 está obsoleto.
    implementation(libs.compose.material3)
    // El despachador Swing es lo que permite a las coroutines saltar al hilo de la interfaz.
    implementation(libs.kotlinx.coroutines.swing)

    testImplementation(libs.kotlinx.coroutines.test)
```

- [ ] **Step 5: Añadir ktlint a spotless**

```kotlin
spotless {
    java {
        googleJavaFormat(libs.versions.googleJavaFormat.get())
        removeUnusedImports()
    }
    kotlin {
        ktlint()
        target("src/**/*.kt")
    }
    kotlinGradle {
        ktlint()
    }
}
```

- [ ] **Step 6: Verificar que el andamiaje resuelve**

```bash
mkdir -p src/main/kotlin/io/github/shizukajiku/imagewatch
cat > src/main/kotlin/io/github/shizukajiku/imagewatch/Scaffold.kt <<'EOF'
package io.github.shizukajiku.imagewatch

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
internal fun scaffoldProbe() = Text("compose resuelve")
EOF
./gradlew compileKotlin
```

Expected: `BUILD SUCCESSFUL`. Si falla por dependencias sin resolver, revisa que `google()` esté en `repositories`. Si falla por `Text` no resuelto, falta `libs.compose.material3`.

- [ ] **Step 7: Confirmar que el núcleo sigue intacto**

```bash
./gradlew check
```

Expected: los 59 tests siguen pasando. Añadir Kotlin no debe alterar el núcleo Java.

- [ ] **Step 8: Commit**

```bash
rm src/main/kotlin/io/github/shizukajiku/imagewatch/Scaffold.kt
./gradlew spotlessApply && ./gradlew check
git add -A
git commit -m "build: anadir Kotlin y Compose Desktop al proyecto

Tres requisitos que la prueba de concepto destapo y que no son evidentes:
google() es obligatorio porque Compose arrastra artefactos androidx que no
estan en Maven Central; Material 3 se declara aparte de desktop.currentOs; y
el repositorio de plugins de JetBrains hace falta en pluginManagement."
```

---

### Task 2: Paleta y tema

**Files:**
- Create: `src/main/kotlin/io/github/shizukajiku/imagewatch/ui/theme/Colors.kt`
- Create: `src/main/kotlin/io/github/shizukajiku/imagewatch/ui/theme/Theme.kt`
- Create: `src/test/kotlin/io/github/shizukajiku/imagewatch/ui/theme/ColorsTest.kt`

**Interfaces:**
- Produces: `LightColors: ColorScheme`, `DarkColors: ColorScheme`, `statusColors(status: ImageStatus, dark: Boolean): StatusColors` con `StatusColors(background: Color, foreground: Color, label: String)`, y `@Composable ImageWatchTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit)`.

- [ ] **Step 1: Escribir el test**

La paleta tiene una regla verificable: **los cuatro estados deben producir etiquetas distintas**. Es lo que se rompió en la fase 3, cuando `ERROR` y `UNKNOWN` compartían etiqueta y una imagen caída decía «Sin verificar».

```kotlin
package io.github.shizukajiku.imagewatch.ui.theme

import io.github.shizukajiku.imagewatch.domain.ImageStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ColorsTest {

    @Test
    fun `cada estado tiene su propia etiqueta`() {
        val labels = ImageStatus.entries.map { statusColors(it, dark = true).label }

        assertEquals(labels.size, labels.toSet().size, "Dos estados comparten etiqueta: $labels")
    }

    @Test
    fun `el estado de error no se confunde con el de sin verificar`() {
        assertNotEquals(
            statusColors(ImageStatus.ERROR, dark = true).label,
            statusColors(ImageStatus.UNKNOWN, dark = true).label,
        )
    }

    @Test
    fun `la paleta responde en claro y en oscuro`() {
        ImageStatus.entries.forEach { status ->
            assertNotEquals(
                statusColors(status, dark = true).background,
                statusColors(status, dark = false).background,
                "El estado $status usa el mismo fondo en claro y en oscuro",
            )
        }
    }
}
```

Añade la dependencia de test de Kotlin en `build.gradle.kts`:

```kotlin
    testImplementation(kotlin("test"))
```

- [ ] **Step 2: Ejecutar y comprobar que no compila**

```bash
./gradlew test --tests "*ColorsTest*"
```

Expected: FALLA — `statusColors` no existe.

- [ ] **Step 3: Escribir la paleta**

```kotlin
package io.github.shizukajiku.imagewatch.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import io.github.shizukajiku.imagewatch.domain.ImageStatus

// El acento no es el color de marca de ningún proveedor: es un índigo neutro.
private val Accent = Color(0xFF4C6EF5)
private val AccentDark = Color(0xFF3B5BDB)

val DarkColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    background = Color(0xFF1B1B1D),
    onBackground = Color(0xFFE7E7EA),
    surface = Color(0xFF232326),
    onSurface = Color(0xFFE7E7EA),
    surfaceVariant = Color(0xFF2A2A2E),
    onSurfaceVariant = Color(0xFF9A9AA2),
)

val LightColors = lightColorScheme(
    primary = AccentDark,
    onPrimary = Color.White,
    background = Color(0xFFF7F7F9),
    onBackground = Color(0xFF1B1B1D),
    surface = Color.White,
    onSurface = Color(0xFF1B1B1D),
    surfaceVariant = Color(0xFFECECF0),
    onSurfaceVariant = Color(0xFF5C5C66),
)

/** Colores y texto de la píldora de estado. */
data class StatusColors(val background: Color, val foreground: Color, val label: String)

/**
 * ERROR reutiliza la gama roja de PENDING: son los dos estados que piden atención, y el texto
 * ya los distingue. Lo que no puede repetirse es la etiqueta.
 */
fun statusColors(status: ImageStatus, dark: Boolean): StatusColors = when (status) {
    ImageStatus.PENDING ->
        if (dark) StatusColors(Color(0xFF3A1414), Color(0xFFFF8F8F), "Nueva versión")
        else StatusColors(Color(0xFFFFE3E3), Color(0xFFC92A2A), "Nueva versión")

    ImageStatus.ERROR ->
        if (dark) StatusColors(Color(0xFF3A1414), Color(0xFFFFC078), "Error")
        else StatusColors(Color(0xFFFFF0E0), Color(0xFFD9480F), "Error")

    ImageStatus.OK ->
        if (dark) StatusColors(Color(0xFF14321A), Color(0xFF8FD792), "Al día")
        else StatusColors(Color(0xFFE6F4EA), Color(0xFF2B7A39), "Al día")

    ImageStatus.UNKNOWN ->
        if (dark) StatusColors(Color(0xFF313136), Color(0xFF9A9AA2), "Sin verificar")
        else StatusColors(Color(0xFFECECF0), Color(0xFF5C5C66), "Sin verificar")
}
```

- [ ] **Step 4: Escribir el tema**

```kotlin
package io.github.shizukajiku.imagewatch.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

/** Si el tema activo es oscuro. Lo consultan los composables que eligen color por estado. */
val LocalIsDark = staticCompositionLocalOf { true }

@Composable
fun ImageWatchTheme(
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalIsDark provides dark) {
        MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
    }
}
```

- [ ] **Step 5: Ejecutar**

```bash
./gradlew test --tests "*ColorsTest*"
```

Expected: los tres pasan.

- [ ] **Step 6: Commit**

```bash
./gradlew spotlessApply && ./gradlew check
git add -A
git commit -m "feat: paleta y tema de Compose

Una sola sede para el color, con tema claro ademas del oscuro, que la interfaz
anterior no tenia. La paleta se definia dos veces en la ventana Swing.

El test fija que los cuatro estados tengan etiquetas distintas: en la fase 3,
ERROR y UNKNOWN compartian etiqueta y una imagen caida decia 'Sin verificar'."
```

---

### Task 3: Carga de iconos SVG

**Files:**
- Create: `src/main/kotlin/io/github/shizukajiku/imagewatch/ui/components/SvgIcon.kt`
- Create: `src/test/kotlin/io/github/shizukajiku/imagewatch/ui/components/SvgIconTest.kt`

**Interfaces:**
- Produces: `enum class AppSvg { PLUS, PENCIL, TRASH, SEARCH, WARNING }` con `AppSvg.resourcePath: String`; `loadAppSvg(svg: AppSvg, density: Density): Painter`; `@Composable SvgIcon(svg: AppSvg, tint: Color, modifier: Modifier)`.

- [ ] **Step 1: Escribir el test**

La prueba de concepto confirmó que los cinco cargan. Este test lo convierte en garantía permanente: si alguien renombra o borra un SVG, salta aquí y no en pantalla.

```kotlin
package io.github.shizukajiku.imagewatch.ui.components

import androidx.compose.ui.unit.Density
import kotlin.test.Test
import kotlin.test.assertTrue

class SvgIconTest {

    @Test
    fun `los cinco iconos del proyecto se cargan`() {
        val density = Density(1f)

        AppSvg.entries.forEach { svg ->
            val painter = loadAppSvg(svg, density)
            assertTrue(
                painter.intrinsicSize.width > 0f,
                "${svg.resourcePath} cargó con tamaño nulo",
            )
        }
    }

    @Test
    fun `cada icono apunta a un recurso existente`() {
        AppSvg.entries.forEach { svg ->
            val stream = SvgIconTest::class.java.getResourceAsStream(svg.resourcePath)
            assertTrue(stream != null, "No existe el recurso ${svg.resourcePath}")
            stream?.close()
        }
    }
}
```

- [ ] **Step 2: Ejecutar y comprobar que no compila**

```bash
./gradlew test --tests "*SvgIconTest*"
```

Expected: FALLA — `AppSvg` no existe.

- [ ] **Step 3: Implementar**

```kotlin
package io.github.shizukajiku.imagewatch.ui.components

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.loadSvgPainter
import androidx.compose.ui.unit.Density

/** Los iconos vectoriales que la aplicación empaqueta en `resources/icons/`. */
enum class AppSvg(private val file: String) {
    PLUS("plus"),
    PENCIL("pencil"),
    TRASH("trash"),
    SEARCH("search"),
    WARNING("warning"),
    ;

    val resourcePath: String get() = "/icons/$file.svg"
}

/**
 * Carga un icono desde el classpath. Lanza si el recurso falta: un icono ausente es un error de
 * empaquetado, y fallar en el arranque avisa antes que un hueco en la pantalla.
 */
fun loadAppSvg(svg: AppSvg, density: Density): Painter {
    val stream = requireNotNull(AppSvg::class.java.getResourceAsStream(svg.resourcePath)) {
        "Falta el icono ${svg.resourcePath} en los recursos"
    }
    return stream.use { loadSvgPainter(it, density) }
}

@Composable
fun SvgIcon(svg: AppSvg, tint: Color, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    // remember evita releer y reparsear el SVG en cada recomposición.
    val painter = remember(svg, density) { loadAppSvg(svg, density) }
    Image(painter, contentDescription = svg.name, modifier = modifier, colorFilter = ColorFilter.tint(tint))
}
```

- [ ] **Step 4: Ejecutar**

```bash
./gradlew test --tests "*SvgIconTest*"
```

Expected: los dos pasan.

- [ ] **Step 5: Commit**

```bash
./gradlew spotlessApply && ./gradlew check
git add -A
git commit -m "feat: carga de iconos SVG en Compose

Un enum en lugar de rutas sueltas: si alguien renombra o borra un icono, falla
la compilacion o el test, no la pantalla.

El test convierte en garantia permanente lo que la prueba de concepto verifico
una vez."
```

---

### Task 4: El view model

**Files:**
- Create: `src/main/kotlin/io/github/shizukajiku/imagewatch/ui/images/ImagesViewModel.kt`
- Create: `src/test/kotlin/io/github/shizukajiku/imagewatch/ui/images/ImagesViewModelTest.kt`

**Interfaces:**
- Consumes: `VersionPollingService.addListener/removeListener/lastSnapshot/acknowledge`, `TrackedImageStore`, `PollingController`.
- Produces:
  - `data class ImageRowState(name: String, registry: String, local: String, remote: String, status: ImageStatus, detail: String)`
  - `data class ImagesUiState(rows: List<ImageRowState>, total: Int, pending: Int, polling: Boolean, intervalSeconds: Long, search: String)`
  - `class ImagesViewModel(service, trackedImages, controller)` con `state: StateFlow<ImagesUiState>`, y los métodos `onSearchChange(String)`, `acknowledge(String)`, `togglePolling()`, `applyInterval(String): String?`, `addImage(String): String?`, `renameImage(String, String): String?`, `removeImage(String)`, `close()`.

Los métodos que validan devuelven `null` si todo fue bien, o el mensaje de error a mostrar. Es lo que permite probar la validación sin pintar nada.

- [ ] **Step 1: Escribir el test**

```kotlin
package io.github.shizukajiku.imagewatch.ui.images

import io.github.shizukajiku.imagewatch.application.ImageResult
import io.github.shizukajiku.imagewatch.application.ImageSource
import io.github.shizukajiku.imagewatch.application.ImageStateStore
import io.github.shizukajiku.imagewatch.application.PollingController
import io.github.shizukajiku.imagewatch.application.TrackedImageStore
import io.github.shizukajiku.imagewatch.application.VersionPollingService
import io.github.shizukajiku.imagewatch.domain.ImageRelease
import io.github.shizukajiku.imagewatch.domain.ImageStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import java.time.Duration
import java.time.LocalDateTime
import java.util.Optional
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ImagesViewModelTest {

    @Test
    fun `el estado refleja el snapshot tras un ciclo`() = runTest {
        val fixture = fixture(listOf("alpha", "beta"))
        fixture.service.poll()

        val state = fixture.viewModel.state.value

        assertEquals(2, state.rows.size)
        assertEquals(2, state.total)
    }

    @Test
    fun `el filtro deja solo las coincidencias sin perder el total`() = runTest {
        val fixture = fixture(listOf("alpha", "beta"))
        fixture.service.poll()

        fixture.viewModel.onSearchChange("alph")

        val state = fixture.viewModel.state.value
        assertEquals(listOf("alpha"), state.rows.map { it.name })
        assertEquals(2, state.total, "El total cuenta imágenes vigiladas, no filtradas")
    }

    @Test
    fun `un nombre invalido se rechaza con mensaje`() = runTest {
        val fixture = fixture(listOf("alpha"))

        val error = fixture.viewModel.addImage("no vale/esto")

        assertNotNull(error)
        assertTrue(fixture.names.findAll().none { it == "no vale/esto" })
    }

    @Test
    fun `un nombre duplicado se rechaza con mensaje`() = runTest {
        val fixture = fixture(listOf("alpha"))

        val error = fixture.viewModel.addImage("alpha")

        assertNotNull(error)
        assertEquals(1, fixture.names.findAll().size)
    }

    @Test
    fun `un nombre valido se agrega`() = runTest {
        val fixture = fixture(listOf("alpha"))

        val error = fixture.viewModel.addImage("beta")

        assertNull(error)
        assertTrue(fixture.names.findAll().contains("beta"))
    }

    @Test
    fun `un intervalo no numerico se rechaza y no cambia el vigente`() = runTest {
        val fixture = fixture(listOf("alpha"))

        val error = fixture.viewModel.applyInterval("no soy un numero")

        assertNotNull(error)
        assertEquals(30, fixture.controller.interval().seconds)
    }

    @Test
    fun `un intervalo por debajo del minimo se rechaza`() = runTest {
        val fixture = fixture(listOf("alpha"))

        val error = fixture.viewModel.applyInterval("0")

        assertNotNull(error)
        assertEquals(30, fixture.controller.interval().seconds)
    }

    @Test
    fun `reconocer una imagen la deja al dia sin esperar al siguiente ciclo`() = runTest {
        val fixture = fixture(listOf("alpha"), localVersion = "1.0.0", remoteVersion = "2.0.0")
        fixture.service.poll()
        assertEquals(ImageStatus.PENDING, fixture.viewModel.state.value.rows.first().status)

        fixture.viewModel.acknowledge("alpha")

        assertEquals(ImageStatus.OK, fixture.viewModel.state.value.rows.first().status)
    }

    @Test
    fun `cerrar el view model lo desengancha del servicio`() = runTest {
        val fixture = fixture(listOf("alpha"))
        fixture.service.poll()
        val before = fixture.viewModel.state.value

        fixture.viewModel.close()
        fixture.service.poll()

        assertEquals(before, fixture.viewModel.state.value)
    }

    // --- andamiaje ---

    private class Fixture(
        val service: VersionPollingService,
        val names: TrackedImageStore,
        val controller: PollingController,
        val viewModel: ImagesViewModel,
    )

    private fun fixture(
        images: List<String>,
        localVersion: String? = null,
        remoteVersion: String = "1.0.0",
    ): Fixture {
        val names = FakeTrackedImageStore(images.toMutableList())
        val store = FakeImageStateStore()
        localVersion?.let { version ->
            images.forEach { store.save(listOf(release(it, version))) }
        }
        val source = ImageSource { requested ->
            requested.map { ImageResult.found(release(it, remoteVersion)) }
        }
        val service = VersionPollingService(source, store, emptyList(), names)
        val controller = PollingController(service, Duration.ofSeconds(30))
        return Fixture(service, names, controller, ImagesViewModel(service, names, controller))
    }

    private fun release(name: String, version: String) =
        ImageRelease(name, "registry.local/$name:$version", LocalDateTime.now())

    private class FakeTrackedImageStore(private val names: MutableList<String>) : TrackedImageStore {
        override fun findAll(): List<String> = names.toList()
        override fun save(updated: List<String>) {
            names.clear()
            names.addAll(updated)
        }
    }

    private class FakeImageStateStore : ImageStateStore {
        private val byName = mutableMapOf<String, ImageRelease>()
        override fun find(name: String): Optional<ImageRelease> =
            Optional.ofNullable(byName[name])

        override fun save(releases: List<ImageRelease>) {
            releases.forEach { byName[it.name()] = it }
        }
    }
}
```

- [ ] **Step 2: Ejecutar y comprobar que no compila**

```bash
./gradlew test --tests "*ImagesViewModelTest*"
```

Expected: FALLA — `ImagesViewModel` no existe.

- [ ] **Step 3: Implementar**

```kotlin
package io.github.shizukajiku.imagewatch.ui.images

import io.github.shizukajiku.imagewatch.application.PollListener
import io.github.shizukajiku.imagewatch.application.PollingController
import io.github.shizukajiku.imagewatch.application.TrackedImageStore
import io.github.shizukajiku.imagewatch.application.VersionPollingService
import io.github.shizukajiku.imagewatch.domain.ImageState
import io.github.shizukajiku.imagewatch.domain.ImageStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.Duration
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val VALID_NAME = Regex("[A-Za-z0-9._-]+")
private val WHEN_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

/** Una fila de la tabla, ya lista para pintar: sin `Optional`, sin fechas, sin lógica. */
data class ImageRowState(
    val name: String,
    val registry: String,
    val local: String,
    val remote: String,
    val status: ImageStatus,
    val detail: String,
)

data class ImagesUiState(
    val rows: List<ImageRowState> = emptyList(),
    val total: Int = 0,
    val pending: Int = 0,
    val polling: Boolean = false,
    val intervalSeconds: Long = 0,
    val search: String = "",
)

/**
 * Adapta el núcleo Java a un `StateFlow` que Compose puede observar.
 *
 * <p>No es un ViewModel de AndroidX: es una clase plana que recibe sus dependencias por
 * constructor, de modo que se puede probar sin arrancar la aplicación ni pintar nada.
 *
 * <p>Los métodos que validan devuelven `null` cuando todo fue bien, o el mensaje a mostrar. Así
 * la validación se prueba sin interfaz.
 */
class ImagesViewModel(
    private val service: VersionPollingService,
    private val trackedImages: TrackedImageStore,
    private val controller: PollingController,
) {
    private val snapshot = MutableStateFlow(service.lastSnapshot())
    private val search = MutableStateFlow("")
    private val _state = MutableStateFlow(ImagesUiState())
    val state: StateFlow<ImagesUiState> = _state.asStateFlow()

    // El oyente se invoca en el hilo del planificador. MutableStateFlow es seguro entre hilos,
    // y Compose recolecta desde el suyo, así que no hace falta saltar de hilo aquí.
    private val listener = PollListener { received -> snapshot.value = received }

    init {
        service.addListener(listener)
        recompute()
    }

    fun onSearchChange(text: String) {
        search.value = text
        recompute()
    }

    fun acknowledge(name: String) {
        service.acknowledge(name)
        snapshot.value = service.lastSnapshot()
        recompute()
    }

    fun togglePolling() {
        if (controller.status() == PollingController.Status.RUNNING) {
            controller.stop()
        } else {
            controller.start()
        }
        recompute()
    }

    /** Devuelve el mensaje de error, o `null` si el intervalo se aplicó. */
    fun applyInterval(text: String): String? {
        val seconds = text.trim().toLongOrNull()
            ?: return "El intervalo debe ser un número de segundos"
        return runCatching { controller.updateInterval(Duration.ofSeconds(seconds)) }
            .fold(onSuccess = { recompute(); null }, onFailure = { it.message })
    }

    fun addImage(name: String): String? = saveName(editing = null, candidate = name)

    fun renameImage(previous: String, candidate: String): String? =
        saveName(editing = previous, candidate = candidate)

    fun removeImage(name: String) {
        trackedImages.save(trackedImages.findAll() - name)
        recompute()
    }

    /** Se desengancha del servicio. Sin esto, el servicio retendría una interfaz ya cerrada. */
    fun close() {
        service.removeListener(listener)
    }

    private fun saveName(editing: String?, candidate: String): String? {
        val value = candidate.trim()
        if (value.isEmpty() || !VALID_NAME.matches(value)) {
            return "Usa solo letras, números, '.', '_' o '-'"
        }
        val current = trackedImages.findAll()
        if (current.any { it == value && it != editing }) {
            return "Ya existe una imagen con ese nombre"
        }
        val updated = when (editing) {
            null -> current + value
            else -> current.map { if (it == editing) value else it }
        }
        trackedImages.save(updated)
        recompute()
        return null
    }

    private fun recompute() {
        val byName = snapshot.value.images().associateBy { it.name() }
        val names = trackedImages.findAll()
        val all = names.map { toRow(it, byName[it]) }
        val term = search.value.trim().lowercase()
        _state.update {
            ImagesUiState(
                rows = if (term.isEmpty()) all else all.filter { it.name.lowercase().contains(term) },
                total = all.size,
                pending = all.count { it.status == ImageStatus.PENDING },
                polling = controller.status() == PollingController.Status.RUNNING,
                intervalSeconds = controller.interval().seconds,
                search = search.value,
            )
        }
    }

    private fun toRow(name: String, image: ImageState?): ImageRowState {
        if (image == null) {
            return ImageRowState(name, "—", "—", "—", ImageStatus.UNKNOWN, "Sin verificar todavía")
        }
        val detail = image.error().orElseGet {
            "Verificado " + WHEN_FORMAT.format(
                image.lastCheckedAt().atZone(ZoneId.systemDefault()),
            )
        }
        return ImageRowState(
            name = name,
            registry = image.registry(),
            local = image.local().map { it.value() }.orElse("—"),
            remote = image.remote().map { it.value() }.orElse("—"),
            status = image.status(),
            detail = detail,
        )
    }
}
```

- [ ] **Step 4: Ejecutar**

```bash
./gradlew test --tests "*ImagesViewModelTest*"
```

Expected: los nueve pasan.

- [ ] **Step 5: Confirmar que el núcleo sigue intacto**

```bash
./gradlew check
```

Expected: los 59 tests del núcleo siguen verdes, más los nuevos de Kotlin.

- [ ] **Step 6: Commit**

```bash
./gradlew spotlessApply && ./gradlew check
git add -A
git commit -m "feat: view model que adapta el nucleo a un StateFlow

Envuelve el PollListener de la fase 3 sin tocar el nucleo. Es una clase plana
con dependencias por constructor, no un ViewModel de AndroidX: se prueba sin
arrancar la aplicacion ni pintar nada.

Los metodos que validan devuelven el mensaje de error o null, lo que permite
cubrir la validacion de nombres y de intervalo con tests normales. En la
interfaz Swing esa logica vivia dentro de los dialogos y no habia forma de
probarla."
```

---

### Task 5: Componentes con animación

**Files:**
- Create: `src/main/kotlin/io/github/shizukajiku/imagewatch/ui/components/StatusBadge.kt`
- Create: `src/main/kotlin/io/github/shizukajiku/imagewatch/ui/components/VersionPill.kt`

**Interfaces:**
- Consumes: `statusColors`, `LocalIsDark` de la Task 2.
- Produces: `@Composable StatusBadge(status: ImageStatus, modifier: Modifier)`, `@Composable VersionPill(version: String, highlight: Boolean, modifier: Modifier)`.

Estos dos componentes concentran las animaciones 1 y 2 de las siete que pide el diseño.

- [ ] **Step 1: La píldora de estado, con transición de color**

```kotlin
package io.github.shizukajiku.imagewatch.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.shizukajiku.imagewatch.domain.ImageStatus
import io.github.shizukajiku.imagewatch.ui.theme.LocalIsDark
import io.github.shizukajiku.imagewatch.ui.theme.statusColors

/**
 * Animación 1 de las siete: el cambio de estado funde el color en lugar de saltar.
 *
 * <p>Los 400 ms son deliberados: por debajo de ~250 ms el ojo no registra la transición y el
 * efecto es idéntico a no animar.
 */
@Composable
fun StatusBadge(status: ImageStatus, modifier: Modifier = Modifier) {
    val palette = statusColors(status, LocalIsDark.current)
    val background by animateColorAsState(palette.background, tween(400), label = "badgeBg")
    val foreground by animateColorAsState(palette.foreground, tween(400), label = "badgeFg")

    Surface(color = background, shape = RoundedCornerShape(999.dp), modifier = modifier) {
        Text(
            text = palette.label,
            color = foreground,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}
```

- [ ] **Step 2: La versión, con rotación vertical**

```kotlin
package io.github.shizukajiku.imagewatch.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.shizukajiku.imagewatch.domain.ImageStatus
import io.github.shizukajiku.imagewatch.ui.theme.LocalIsDark
import io.github.shizukajiku.imagewatch.ui.theme.statusColors

/**
 * Animación 2 de las siete: la versión anterior sale hacia arriba y la nueva entra desde abajo,
 * de modo que el cambio se ve ocurrir en lugar de aparecer.
 */
@Composable
fun VersionPill(version: String, highlight: Boolean, modifier: Modifier = Modifier) {
    val palette = statusColors(ImageStatus.PENDING, LocalIsDark.current)
    val background =
        if (highlight) palette.background else MaterialTheme.colorScheme.surfaceVariant
    val foreground =
        if (highlight) palette.foreground else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(color = background, shape = RoundedCornerShape(999.dp), modifier = modifier) {
        AnimatedContent(
            targetState = version,
            transitionSpec = {
                slideInVertically(tween(320)) { it } togetherWith
                    slideOutVertically(tween(320)) { -it }
            },
            label = "version",
        ) { value ->
            Text(
                text = value,
                color = foreground,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}
```

- [ ] **Step 3: Compilar**

```bash
./gradlew compileKotlin
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
./gradlew spotlessApply && ./gradlew check
git add -A
git commit -m "feat: componentes de estado y version con animacion

Las dos primeras animaciones de las siete que pide el diseno: transicion de
color al cambiar de estado, y rotacion vertical del numero de version.

La pildora se pintaba a mano con Graphics2D porque un JLabel no puede tener
border-radius. Aqui es un Surface con forma."
```

---

### Task 6: La fila y la lista

**Files:**
- Create: `src/main/kotlin/io/github/shizukajiku/imagewatch/ui/images/ImageRow.kt`
- Create: `src/main/kotlin/io/github/shizukajiku/imagewatch/ui/images/ImagesScreen.kt`

**Interfaces:**
- Consumes: `ImageRowState`, `ImagesUiState` de la Task 4; `StatusBadge`, `VersionPill`, `SvgIcon` de las Tasks 3 y 5.
- Produces: `@Composable ImageRow(row, onAcknowledge, onEdit, onDelete)`, `@Composable ImagesScreen(state, callbacks…)`.

Los composables reciben estado y lambdas, nunca el view model. Es lo que permite previsualizarlos y probarlos aislados.

- [ ] **Step 1: La fila**

```kotlin
package io.github.shizukajiku.imagewatch.ui.images

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.shizukajiku.imagewatch.domain.ImageStatus
import io.github.shizukajiku.imagewatch.ui.components.AppSvg
import io.github.shizukajiku.imagewatch.ui.components.StatusBadge
import io.github.shizukajiku.imagewatch.ui.components.SvgIcon
import io.github.shizukajiku.imagewatch.ui.components.VersionPill

@Composable
fun ImageRow(
    row: ImageRowState,
    onAcknowledge: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(row.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(
                row.registry,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        VersionPill(row.local, highlight = false, modifier = Modifier.width(110.dp))
        Row(Modifier.width(12.dp)) {}
        VersionPill(row.remote, highlight = row.status == ImageStatus.PENDING)

        Column(Modifier.width(190.dp).padding(start = 14.dp)) {
            StatusBadge(row.status)
            Text(
                row.detail,
                fontSize = 10.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            // Solo se ofrece reconocer lo que está pendiente: un botón que no hace nada
            // enseña al usuario a desconfiar de los botones.
            if (row.status == ImageStatus.PENDING) {
                IconButton(onAcknowledge) {
                    SvgIcon(AppSvg.PLUS, MaterialTheme.colorScheme.primary, Modifier.size(16.dp))
                }
            }
            IconButton(onEdit) {
                SvgIcon(AppSvg.PENCIL, MaterialTheme.colorScheme.onSurfaceVariant, Modifier.size(16.dp))
            }
            IconButton(onDelete) {
                SvgIcon(AppSvg.TRASH, MaterialTheme.colorScheme.onSurfaceVariant, Modifier.size(16.dp))
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
}
```

- [ ] **Step 2: La pantalla**

```kotlin
package io.github.shizukajiku.imagewatch.ui.images

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.shizukajiku.imagewatch.domain.ImageStatus
import io.github.shizukajiku.imagewatch.ui.components.AppSvg
import io.github.shizukajiku.imagewatch.ui.components.SvgIcon

@Composable
fun ImagesScreen(
    state: ImagesUiState,
    onSearchChange: (String) -> Unit,
    onAdd: () -> Unit,
    onAcknowledge: (String) -> Unit,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit,
    onTogglePolling: () -> Unit,
    onApplyInterval: (String) -> Unit,
) {
    var interval by remember(state.intervalSeconds) {
        mutableStateOf(state.intervalSeconds.toString())
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Imágenes monitoreadas", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(
                        "${state.total} imágenes registradas · ${state.pending} con actualización pendiente",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(onAdd, shape = RoundedCornerShape(999.dp)) {
                    SvgIcon(AppSvg.PLUS, MaterialTheme.colorScheme.onPrimary, Modifier.size(13.dp))
                    Text("  Agregar imagen")
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = state.search,
                    onValueChange = onSearchChange,
                    placeholder = { Text("Buscar imagen…") },
                    leadingIcon = {
                        SvgIcon(
                            AppSvg.SEARCH,
                            MaterialTheme.colorScheme.onSurfaceVariant,
                            Modifier.size(14.dp),
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier.width(260.dp),
                )

                Row(Modifier.weight(1f)) {}

                // Animación 6: el indicador cruza entre activo y detenido.
                Crossfade(state.polling, label = "pollingStatus") { running ->
                    Text(
                        if (running) "● Activo" else "● Detenido",
                        fontSize = 11.sp,
                        color = if (running) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                OutlinedTextField(
                    value = interval,
                    onValueChange = { interval = it },
                    label = { Text("Intervalo (s)") },
                    singleLine = true,
                    modifier = Modifier.width(120.dp).padding(start = 8.dp),
                )
                TextButton({ onApplyInterval(interval) }) { Text("Aplicar") }
                TextButton(onTogglePolling) {
                    Text(if (state.polling) "Detener" else "Iniciar")
                }
            }
        }

        // Animación 5: el aviso de que el origen no responde entra desplegándose.
        val allFailing = state.total > 0 && state.rows.isNotEmpty() &&
            state.rows.all { it.status == ImageStatus.ERROR }
        AnimatedVisibility(allFailing, enter = expandVertically(), exit = shrinkVertically()) {
            Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Sin conexión con el origen: ninguna imagen pudo verificarse.",
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                )
            }
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Top,
        ) {
            items(state.rows, key = { it.name }) { row ->
                // Animación 3: alta, baja y reordenación de filas se animan.
                ImageRow(
                    row = row,
                    onAcknowledge = { onAcknowledge(row.name) },
                    onEdit = { onEdit(row.name) },
                    onDelete = { onDelete(row.name) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}
```

- [ ] **Step 3: Compilar**

```bash
./gradlew compileKotlin
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
./gradlew spotlessApply && ./gradlew check
git add -A
git commit -m "feat: fila y pantalla de imagenes en Compose

Sustituye la JTable con cuatro renderers y un editor de celda por una
LazyColumn y un composable de fila.

Los composables reciben estado y lambdas, nunca el view model: eso los hace
previsualizables y probables aislados.

El boton de reconocer solo aparece en filas pendientes. Un boton que no hace
nada ensena al usuario a desconfiar de los botones."
```

---

### Task 7: Diálogos

**Files:**
- Create: `src/main/kotlin/io/github/shizukajiku/imagewatch/ui/dialogs/ImageDialogs.kt`

**Interfaces:**
- Produces: `@Composable NameDialog(title, initial, onDismiss, onConfirm: (String) -> String?)`, `@Composable DeleteDialog(name, onDismiss, onConfirm)`.

`onConfirm` devuelve el mensaje de error o `null`: el diálogo se cierra solo cuando la validación pasa, y esa validación vive en el view model, ya probada.

- [ ] **Step 1: Implementar**

```kotlin
package io.github.shizukajiku.imagewatch.ui.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Alta y edición comparten diálogo. `onConfirm` devuelve el mensaje de error o `null`; el
 * diálogo solo se cierra cuando la validación pasa, y esa validación vive en el view model.
 */
@Composable
fun NameDialog(
    title: String,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> String?,
) {
    var value by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it; error = null },
                    label = { Text("Nombre de imagen") },
                    isError = error != null,
                    singleLine = true,
                )
                error?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton({ error = onConfirm(value); if (error == null) onDismiss() }) {
                Text("Guardar")
            }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancelar") } },
    )
}

@Composable
fun DeleteDialog(name: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Eliminar imagen") },
        text = {
            Text(
                "Se dejará de monitorear $name.\n" +
                    "Esta acción no elimina el historial ya notificado.",
            )
        },
        confirmButton = {
            TextButton({ onConfirm(); onDismiss() }) { Text("Eliminar") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancelar") } },
    )
}
```

- [ ] **Step 2: Compilar y commitear**

```bash
./gradlew spotlessApply && ./gradlew check
git add -A
git commit -m "feat: dialogos de alta, edicion y borrado en Material 3

Sustituyen a la libreria externa de dialogos modales, que sale del proyecto.

onConfirm devuelve el mensaje de error o null: el dialogo se cierra solo
cuando la validacion pasa, y esa validacion vive en el view model, donde ya
tiene tests."
```

---

### Task 8: Icono, composition root y retirada de Swing

Es la tarea que enciende la aplicación nueva y apaga la vieja. Va al final porque hasta aquí nada estaba conectado.

**Files:**
- Create: `src/main/kotlin/io/github/shizukajiku/imagewatch/ui/AppIcon.kt`
- Create: `src/main/kotlin/io/github/shizukajiku/imagewatch/Main.kt`
- Delete: `ui/ImageManagerFrame.java`, `ui/TrayUi.java`, `ui/AppIcon.java`, `Main.java`, `infrastructure/notification/WindowsNotificationAdapter.java`
- Modify: `build.gradle.kts`, `gradle/libs.versions.toml`

**Interfaces:**
- Consumes: todo lo anterior.
- Produces: `MainKt` como `mainClass`.

- [ ] **Step 1: El icono, como Painter**

```kotlin
package io.github.shizukajiku.imagewatch.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter

/** La marca: una cuadrícula 2×2 sobre fondo redondeado. Compartida por bandeja y ventana. */
object AppIconPainter : Painter() {
    private val background = Color(0xFF1B1B1D)
    private val accent = Color(0xFF4C6EF5)

    override val intrinsicSize = Size(64f, 64f)

    override fun DrawScope.onDraw() {
        val side = size.minDimension
        drawRoundRect(
            color = background,
            size = Size(side, side),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(side * 0.3f),
        )
        val pad = side * 0.22f
        val gap = side * 0.12f
        val cell = (side - pad * 2 - gap) / 2
        listOf(0f to 0f, 1f to 0f, 0f to 1f, 1f to 1f).forEach { (column, rowIndex) ->
            drawRoundRect(
                color = accent,
                topLeft = Offset(pad + column * (cell + gap), pad + rowIndex * (cell + gap)),
                size = Size(cell, cell),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(cell * 0.4f),
            )
        }
    }
}
```

- [ ] **Step 2: El composition root**

```kotlin
package io.github.shizukajiku.imagewatch

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberTrayState
import io.github.shizukajiku.imagewatch.application.NotificationPort
import io.github.shizukajiku.imagewatch.application.PollingController
import io.github.shizukajiku.imagewatch.application.TrackedImageStore
import io.github.shizukajiku.imagewatch.application.VersionPollingService
import io.github.shizukajiku.imagewatch.config.AppConfig
import io.github.shizukajiku.imagewatch.infrastructure.notification.TeamsNotificationAdapter
import io.github.shizukajiku.imagewatch.infrastructure.persistence.JsonImageStateStore
import io.github.shizukajiku.imagewatch.infrastructure.persistence.JsonTrackedImageStore
import io.github.shizukajiku.imagewatch.infrastructure.remote.HttpClientFactory
import io.github.shizukajiku.imagewatch.infrastructure.remote.HttpImageSource
import io.github.shizukajiku.imagewatch.infrastructure.remote.SimulatedImageSource
import io.github.shizukajiku.imagewatch.ui.AppIconPainter
import io.github.shizukajiku.imagewatch.ui.dialogs.DeleteDialog
import io.github.shizukajiku.imagewatch.ui.dialogs.NameDialog
import io.github.shizukajiku.imagewatch.ui.images.ImagesScreen
import io.github.shizukajiku.imagewatch.ui.images.ImagesViewModel
import io.github.shizukajiku.imagewatch.ui.theme.ImageWatchTheme
import java.time.Clock
import java.time.Duration

/** Cada cuánto sube de versión el origen simulado. Solo aplica en modo simulación. */
private val SIMULATED_BUMP_EVERY: Duration = Duration.ofSeconds(20)

private class Wiring {
    val config: AppConfig = AppConfig.fromEnvironment()
    val trackedImages: TrackedImageStore
    val service: VersionPollingService
    val controller: PollingController

    init {
        require(config.simulationMode() || config.remoteUrl().isNotBlank()) {
            "Falta la URL del origen remoto. Define IMAGE_VERSION_URL " +
                "o deja SIMULATION_MODE=true para usar el origen simulado."
        }
        val source = if (config.simulationMode()) {
            SimulatedImageSource(Clock.systemUTC(), SIMULATED_BUMP_EVERY)
        } else {
            HttpImageSource(
                HttpClientFactory.create(config.ignoreSslErrors()),
                config.remoteUrl(),
            )
        }
        val state = JsonImageStateStore(config.stateFile())
        trackedImages = JsonTrackedImageStore(
            config.stateFile().resolveSibling("tracked-images.json"),
            config.imageNames(),
        )
        val notifiers = buildList<NotificationPort> {
            if (config.teamsEnabled()) add(TeamsNotificationAdapter())
        }
        service = VersionPollingService(source, state, notifiers, trackedImages)
        controller = PollingController(service, config.pollInterval())
    }
}

fun main() {
    val wiring = Wiring()
    wiring.controller.start()

    application {
        val trayState = rememberTrayState()
        var windowVisible by remember { mutableStateOf(false) }

        Tray(
            state = trayState,
            icon = AppIconPainter,
            tooltip = "ImageWatch",
            onAction = { windowVisible = true },
            menu = {
                Item("Abrir", onClick = { windowVisible = true })
                Item(
                    "Salir",
                    onClick = {
                        wiring.controller.close()
                        exitApplication()
                    },
                )
            },
        )

        if (windowVisible) {
            Window(
                onCloseRequest = { windowVisible = false },
                title = "ImageWatch — imágenes monitoreadas",
                icon = AppIconPainter,
            ) {
                ImageWatchTheme {
                    Surface(Modifier.fillMaxSize()) { MainScreen(wiring) }
                }
            }
        }
    }
}

@Composable
private fun MainScreen(wiring: Wiring) {
    val viewModel = remember {
        ImagesViewModel(wiring.service, wiring.trackedImages, wiring.controller)
    }
    // Sin esto, cerrar y reabrir la ventana acumularía oyentes en el servicio.
    DisposableEffect(viewModel) { onDispose { viewModel.close() } }

    val state by viewModel.state.collectAsState()
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<String?>(null) }

    ImagesScreen(
        state = state,
        onSearchChange = viewModel::onSearchChange,
        onAdd = { adding = true },
        onAcknowledge = viewModel::acknowledge,
        onEdit = { editing = it },
        onDelete = { deleting = it },
        onTogglePolling = viewModel::togglePolling,
        onApplyInterval = { viewModel.applyInterval(it) },
    )

    if (adding) {
        NameDialog("Agregar imagen", "", { adding = false }) { viewModel.addImage(it) }
    }
    editing?.let { previous ->
        NameDialog("Editar imagen", previous, { editing = null }) {
            viewModel.renameImage(previous, it)
        }
    }
    deleting?.let { name ->
        DeleteDialog(name, { deleting = null }) { viewModel.removeImage(name) }
    }
}
```

- [ ] **Step 3: Retirar la interfaz anterior**

```bash
git rm src/main/java/io/github/shizukajiku/imagewatch/ui/ImageManagerFrame.java \
       src/main/java/io/github/shizukajiku/imagewatch/ui/TrayUi.java \
       src/main/java/io/github/shizukajiku/imagewatch/ui/AppIcon.java \
       src/main/java/io/github/shizukajiku/imagewatch/Main.java \
       src/main/java/io/github/shizukajiku/imagewatch/infrastructure/notification/WindowsNotificationAdapter.java
```

`WindowsNotificationAdapter` era presentación disfrazada de infraestructura: manipulaba un `TrayIcon` de AWT. El globo del sistema desaparece; los toasts propios llegan en la fase 5. Hasta entonces, con Teams desactivado, no hay notificaciones — es una regresión **temporal y consciente**, y la fase 5 la cierra.

- [ ] **Step 4: Retirar las dependencias de Swing**

En `build.gradle.kts`, elimina:

```kotlin
    implementation(libs.flatlaf)
    implementation(libs.flatlaf.extras)
    implementation(libs.modal.dialog)
```

y en `gradle/libs.versions.toml`, las entradas `flatlaf`, `flatlaf-extras`, `modal-dialog` y sus versiones `flatlaf` y `modalDialog`.

Cambia el punto de entrada:

```kotlin
application {
    mainClass = "io.github.shizukajiku.imagewatch.MainKt"
    applicationDefaultJvmArgs = listOf("--enable-native-access=ALL-UNNAMED")
}
```

El flag se mantiene: Skiko carga su motor nativo igual que lo hacía FlatLaf.

- [ ] **Step 5: Verificar que el núcleo sobrevivió**

```bash
./gradlew clean check
```

Expected: los 59 tests del núcleo siguen verdes, más los de Kotlin. **Si alguno del núcleo falla, la migración se salió de su capa: revísalo antes de seguir.**

- [ ] **Step 6: Arrancar**

```bash
IMAGE_NAMES=alpha,beta,gamma,delta-fail POLL_INTERVAL_SECONDS=5 ./gradlew run
```

Verifica los catorce comportamientos de la lista de la cabecera, uno a uno. Con especial atención a:

- Que la tabla cambie sola con la ventana abierta y sin tocarla.
- Que `delta-fail` diga **«Error»**, no «Sin verificar».
- Que reconocer una imagen la deje al día **al instante** y no altere a las demás.
- Que cerrar la ventana no mate el proceso, y reabrirla desde la bandeja funcione.

- [ ] **Step 7: Commit**

```bash
./gradlew spotlessApply && ./gradlew check
git add -A
git commit -m "feat: sustituir la interfaz Swing por Compose Desktop

Se retiran 1.022 lineas de Swing -una clase de 830 lineas con tema, tabla,
cuatro renderers, un editor de celda y dos dialogos- y sus tres dependencias.

El composition root pasa a Kotlin para poder abrir application { Tray }. El
nucleo Java no se toca: la interfaz consume el PollListener de la fase 3 a
traves de un StateFlow.

Regresion temporal y consciente: al retirar WindowsNotificationAdapter, que
era presentacion disfrazada de infraestructura, la aplicacion se queda sin
notificaciones hasta que la fase 5 traiga los toasts propios."
```

---

### Task 9: Cobertura y análisis estático

**Files:**
- Modify: `gradle/libs.versions.toml`, `build.gradle.kts`
- Create: `config/detekt/detekt.yml`

- [ ] **Step 1: Añadir Kover y detekt**

En `[versions]`:

```toml
kover = "0.9.1"
detekt = "1.23.7"
```

En `[plugins]`:

```toml
kover = { id = "org.jetbrains.kotlinx.kover", version.ref = "kover" }
detekt = { id = "io.gitlab.arturbosch.detekt", version.ref = "detekt" }
```

En `build.gradle.kts`, dentro de `plugins`:

```kotlin
    alias(libs.plugins.kover)
    alias(libs.plugins.detekt)
```

- [ ] **Step 2: Fijar el umbral solo sobre el núcleo**

```kotlin
kover {
    reports {
        verify {
            rule {
                // El umbral se aplica al nucleo, no a la interfaz: la cobertura de composables
                // mide recomposiciones, no comportamiento, y poner un numero ahi es teatro.
                filters {
                    includes { classes("io.github.shizukajiku.imagewatch.domain.*") }
                    includes { classes("io.github.shizukajiku.imagewatch.application.*") }
                }
                minBound(80)
            }
        }
    }
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(files("config/detekt/detekt.yml"))
}
```

- [ ] **Step 3: Crear la configuración de detekt**

```yaml
# Solo las desviaciones respecto al perfil por defecto. El resto se hereda.
style:
  MaxLineLength:
    maxLineLength: 120
  # Los composables de pantalla reciben muchas lambdas por diseño: es lo que los mantiene
  # desacoplados del view model.
  FunctionMaxLength:
    active: false

complexity:
  LongParameterList:
    functionThreshold: 10
    constructorThreshold: 8

naming:
  FunctionNaming:
    # Los composables van en PascalCase por convención de Compose.
    ignoreAnnotated: ['Composable']
```

- [ ] **Step 4: Ejecutar**

```bash
./gradlew koverVerify detekt
```

Expected: ambos pasan. Si `koverVerify` falla, informa del porcentaje real antes de tocar el umbral: bajarlo para que pase es falsear la medida.

- [ ] **Step 5: Commit**

```bash
./gradlew spotlessApply && ./gradlew check
git add -A
git commit -m "build: anadir Kover y detekt

El umbral del 80 por ciento se aplica a domain/ y application/, no a la
interfaz: la cobertura de composables mide recomposiciones, no comportamiento.

detekt hereda el perfil por defecto y solo declara las desviaciones, entre
ellas que los composables van en PascalCase."
```

---

### Task 10: Cierre de la fase

- [ ] **Step 1: Batería completa**

```bash
./gradlew clean check koverVerify detekt
```

- [ ] **Step 2: Comprobar que no queda rastro de Swing**

```bash
grep -rn "javax.swing\|java.awt\|flatlaf\|raven.modal" src/main/ && echo "QUEDAN RESTOS" || echo "sin restos"
```

Expected: `sin restos`. La bandeja ahora la gestiona Compose, no AWT.

- [ ] **Step 3: Actualizar el README**

Ajusta la sección de estructura: `ui/` pasa a ser Kotlin y Compose. Añade a la lista de órdenes:

```
| `./gradlew koverVerify` | Comprueba el umbral de cobertura del núcleo |
| `./gradlew detekt`      | Análisis estático de Kotlin |
```

Y anota la regresión temporal: **sin notificaciones hasta la fase 5**.

- [ ] **Step 4: Barrido de denylist**

```bash
while IFS= read -r p; do
  [ -z "$p" ] && continue
  case "$p" in \#*) continue ;; esac
  if git grep -qiE -- "$p" HEAD 2>/dev/null; then echo "RESIDUO: $p"; fi
done < .denylist.local
echo "barrido completo"
```

- [ ] **Step 5: Integrar**

```bash
git push -u origin feature/fase-4-compose
```

Usa la skill `superpowers:finishing-a-development-branch`.

---

## Criterio de finalización

- [ ] `./gradlew clean check koverVerify detekt` en verde.
- [ ] Los 59 tests del núcleo **siguen pasando sin modificarse**.
- [ ] Los catorce comportamientos del inventario funcionan.
- [ ] Cero referencias a Swing, AWT, FlatLaf o la librería de diálogos en `src/main/`.
- [ ] Las animaciones 1, 2, 3, 5 y 6 son visibles.
- [ ] Barrido de denylist limpio.

## Fuera de esta fase

Dos de las siete animaciones dependen de piezas que llegan en la fase 5, y por eso no están aquí:

- **Animación 4** (pulso mientras se verifica) necesita saber que hay una consulta en vuelo, y el núcleo no lo expone todavía.
- **Animación 7** (toasts) es la fase 5 entera.

También quedan para la fase 5: la pantalla de ajustes, `ConfigStore`, `ReloadableImageSource`, el sonido, y **restaurar las notificaciones**, que esta fase deja temporalmente ausentes.
