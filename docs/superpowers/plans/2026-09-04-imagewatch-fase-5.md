# ImageWatch — Plan de implementación, Fase 5: Ajustes, errores y toasts

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Cerrar el TO-BE: configuración persistida y editable en caliente, errores visibles en los tres niveles, toasts propios con sonido, y un instalador de Windows.

**Architecture:** La configuración deja de ser un `record` inmutable capturado en el arranque y pasa a vivir en `~/.notifier/config.json` detrás de un puerto `ConfigStore`; las variables de entorno solo siembran el fichero la primera vez. Guardar surte efecto sin reiniciar porque el origen deja de ser un campo final: `ReloadableImageSource` delega en un campo `volatile` intercambiable. Los toasts son ventanas propias de Compose montadas en el scope de `application { }`, alimentadas por un adaptador del `NotificationPort` que ya existe, de modo que el núcleo no se entera de que ha cambiado el canal de aviso.

**Tech Stack:** Kotlin 2.3.0 · Compose Multiplatform 1.12.0 · Material 3 · kotlinx-coroutines · Kover · `javax.sound.sampled` (JDK, sin dependencias) · jpackage vía el plugin de Compose · Java 25

**Spec:** `docs/superpowers/specs/2026-09-03-imagewatch-design.md` — secciones 6 (Toast), 7 (Configuración, errores y simulación) y 8 (Sonido).

**Planes anteriores:** fases 0–2, fase 3 y fase 4, todos completados e integrados en `main`.

## Global Constraints

- **Paquete raíz:** `io.github.shizukajiku.imagewatch`.
- **El núcleo Java se toca solo donde el plan lo dice.** Esta fase sí modifica `application/` y `config/`, a diferencia de la fase 4, pero cada cambio está enumerado: `ConfigStore`, el método `onPollStarted` de `PollListener` y la eliminación del vestigio de Teams. **Cualquier otro test del núcleo que se rompa significa que el cambio se salió de su alcance: párate.**
- **No se añade ninguna dependencia nueva.** El sonido usa `javax.sound.sampled`, que forma parte del JDK; el empaquetado usa el plugin de Compose, ya instalado.
- **Formato de audio: WAV PCM 16 bits 44,1 kHz.** `javax.sound.sampled` no reproduce MP3 ni OGG sin un SPI adicional.
- **Ningún binario de audio entra en el repositorio sin aprobación previa del usuario.** La Task 8 se detiene explícitamente a pedirla.
- **`--enable-native-access=ALL-UNNAMED` se mantiene**, también en la configuración del instalador.
- **`jvmToolchain(25)`**; el umbral de Kover (80 % sobre `domain/` y `application/`) debe seguir verde.
- **Ningún identificador corporativo** puede entrar en el repositorio; el hook `pre-commit` lo verifica.
- **Los 77 tests existentes deben seguir verdes en cada tarea.**
- **`JAVA_HOME` no está definido en el entorno del usuario.** Todas las órdenes de Gradle se ejecutan con `JAVA_HOME=~/.jdks/ms-25.0.4.1` por delante, o desde un shell que ya lo exporte.
- Mensajes de commit en formato convencional, en español y sin tildes en el asunto.

---

## Estructura de ficheros

### Se crean

| Fichero | Responsabilidad |
|---|---|
| `application/ConfigStore.java` | Puerto: `load()` / `save(AppConfig)`. |
| `config/ThemePreference.java` | `SYSTEM`, `LIGHT`, `DARK`. Vive en `config/` porque se persiste. |
| `infrastructure/persistence/ConfigDto.java` | Formato de cable de la configuración. Aísla el JSON del record de dominio. |
| `infrastructure/persistence/JsonConfigStore.java` | `ConfigStore` sobre `~/.notifier/config.json`, sembrado una vez desde el entorno. |
| `infrastructure/remote/ReloadableImageSource.java` | Delega en un `ImageSource` `volatile` intercambiable en caliente. |
| `ui/settings/SettingsViewModel.kt` | Estado del formulario de ajustes y su validación. |
| `ui/settings/SettingsScreen.kt` | El formulario. |
| `ui/toast/ToastState.kt` | Cola de toasts observable, viva fuera de la ventana principal. |
| `ui/toast/ToastWindow.kt` | La ventana sin decoración y su animación. |
| `ui/toast/ToastNotificationPort.kt` | Adaptador: `NotificationPort` → cola de toasts + sonido. |
| `ui/sound/Sounds.kt` | Los cuatro clips, su precarga y las tres reglas de reproducción. |
| `src/main/resources/sounds/*.wav` | Los cuatro sonidos (CC0, aprobados en la Task 8). |
| `src/main/resources/sounds/NOTICE` | Procedencia y licencia de los WAV. |
| `src/main/resources/icons/gear.svg`, `bell.svg`, `close.svg` | Iconos nuevos. |

### Se modifican

| Fichero | Cambio |
|---|---|
| `config/AppConfig.java` | Cinco campos nuevos; desaparece `teamsEnabled`. |
| `application/PollListener.java` | Método `default onPollStarted()`. |
| `application/VersionPollingService.java` | Avisa del comienzo del ciclo. |
| `ui/images/ImagesViewModel.kt` | `verifying`, `highlighted`, y el detalle de error por fila. |
| `ui/images/ImagesScreen.kt` | Aviso global con antigüedad; botón de ajustes. |
| `ui/images/ImageRow.kt` | Pulso en `UNKNOWN`, resaltado, versión conocida atenuada en `ERROR`. |
| `ui/components/SvgIcon.kt` | Tres entradas nuevas en `AppSvg`. |
| `Main.kt` | `Wiring` mutable, navegación con `Crossfade`, ventana de toasts, tema desde configuración. |
| `build.gradle.kts` | Bloque `compose.desktop` con el instalador. |
| `README.md` | Ajustes, sonido, instalador; se retira el aviso de regresión. |

### Se eliminan

| Fichero | Por qué |
|---|---|
| `infrastructure/notification/TeamsNotificationAdapter.java` | Vestigio sin implementación. El spec lo declara fuera de alcance y los toasts ocupan su sitio. |

---

### Task 1: Configuración persistida

La configuración deja de nacer y morir en el arranque. El entorno **siembra** el fichero la primera vez y después deja de tener efecto: si el entorno prevaleciera siempre, la pantalla de ajustes no podría cambiar nada. Es el mismo patrón que ya usa `JsonTrackedImageStore` con su lista de valores por defecto.

**Files:**
- Create: `src/main/java/io/github/shizukajiku/imagewatch/application/ConfigStore.java`
- Create: `src/main/java/io/github/shizukajiku/imagewatch/config/ThemePreference.java`
- Create: `src/main/java/io/github/shizukajiku/imagewatch/infrastructure/persistence/ConfigDto.java`
- Create: `src/main/java/io/github/shizukajiku/imagewatch/infrastructure/persistence/JsonConfigStore.java`
- Modify: `src/main/java/io/github/shizukajiku/imagewatch/config/AppConfig.java`
- Delete: `src/main/java/io/github/shizukajiku/imagewatch/infrastructure/notification/TeamsNotificationAdapter.java`
- Modify: `src/main/kotlin/io/github/shizukajiku/imagewatch/Main.kt`
- Test: `src/test/java/io/github/shizukajiku/imagewatch/infrastructure/persistence/JsonConfigStoreTest.java`

**Interfaces:**
- Consumes: `AppConfig.fromEnvironment()`, `JsonFiles.read/write` (package-private en `infrastructure.persistence`).
- Produces: `ConfigStore.load(): AppConfig`, `ConfigStore.save(AppConfig)`; `AppConfig` con `theme()`, `toastsEnabled()`, `toastDuration()`, `soundsEnabled()`, `soundVolume()` y sin `teamsEnabled()`; `ThemePreference.{SYSTEM,LIGHT,DARK}`.

- [ ] **Step 1: Escribir el test que falla**

`src/test/java/io/github/shizukajiku/imagewatch/infrastructure/persistence/JsonConfigStoreTest.java`:

```java
package io.github.shizukajiku.imagewatch.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.shizukajiku.imagewatch.config.AppConfig;
import io.github.shizukajiku.imagewatch.config.ThemePreference;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JsonConfigStoreTest {

  private static AppConfig seed(Path stateFile) {
    return new AppConfig(
        stateFile,
        "https://origen.ejemplo/api",
        Duration.ofSeconds(300),
        List.of("alpha", "beta"),
        true,
        true,
        ThemePreference.SYSTEM,
        true,
        Duration.ofSeconds(8),
        true,
        0.5);
  }

  @Test
  void siembra_el_fichero_en_el_primer_arranque(@TempDir Path dir) {
    var file = dir.resolve("config.json");
    var store = new JsonConfigStore(file, seed(dir.resolve("images.json")));

    var loaded = store.load();

    assertThat(file).exists();
    assertThat(loaded.pollInterval()).isEqualTo(Duration.ofSeconds(300));
    assertThat(loaded.imageNames()).containsExactly("alpha", "beta");
  }

  @Test
  void el_fichero_manda_sobre_la_semilla(@TempDir Path dir) {
    var file = dir.resolve("config.json");
    var stateFile = dir.resolve("images.json");
    new JsonConfigStore(file, seed(stateFile)).load();

    var guardado =
        new AppConfig(
            stateFile,
            "https://otro.ejemplo/api",
            Duration.ofSeconds(15),
            List.of("gamma"),
            false,
            false,
            ThemePreference.DARK,
            false,
            Duration.ofSeconds(4),
            false,
            0.25);
    new JsonConfigStore(file, seed(stateFile)).save(guardado);

    // Instancia nueva: lo que se comprueba es el fichero, no el estado en memoria.
    var releido = new JsonConfigStore(file, seed(stateFile)).load();

    assertThat(releido.remoteUrl()).isEqualTo("https://otro.ejemplo/api");
    assertThat(releido.pollInterval()).isEqualTo(Duration.ofSeconds(15));
    assertThat(releido.simulationMode()).isFalse();
    assertThat(releido.theme()).isEqualTo(ThemePreference.DARK);
    assertThat(releido.soundVolume()).isEqualTo(0.25);
  }

  @Test
  void la_ruta_del_estado_no_se_persiste_y_llega_desde_la_semilla(@TempDir Path dir) {
    var file = dir.resolve("config.json");
    new JsonConfigStore(file, seed(dir.resolve("images.json"))).load();

    var otraRuta = dir.resolve("otro/images.json");
    var releido = new JsonConfigStore(file, seed(otraRuta)).load();

    assertThat(releido.stateFile()).isEqualTo(otraRuta);
  }
}
```

- [ ] **Step 2: Ejecutar el test para verificar que falla**

```bash
JAVA_HOME=~/.jdks/ms-25.0.4.1 ./gradlew test --tests "*JsonConfigStoreTest*"
```

Expected: FAIL — no compila: `JsonConfigStore`, `ThemePreference` y los campos nuevos de `AppConfig` no existen.

- [ ] **Step 3: Ampliar `AppConfig` y crear `ThemePreference`**

`config/ThemePreference.java`:

```java
package io.github.shizukajiku.imagewatch.config;

/** Preferencia de tema. Se persiste, y por eso vive junto a la configuración y no en `ui/`. */
public enum ThemePreference {
  SYSTEM,
  LIGHT,
  DARK
}
```

`config/AppConfig.java` completo:

```java
package io.github.shizukajiku.imagewatch.config;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

public record AppConfig(
    Path stateFile,
    String remoteUrl,
    Duration pollInterval,
    List<String> imageNames,
    boolean simulationMode,
    boolean ignoreSslErrors,
    ThemePreference theme,
    boolean toastsEnabled,
    Duration toastDuration,
    boolean soundsEnabled,
    double soundVolume) {

  /**
   * Copia defensiva de la lista: la configuración cruza hilos —el planificador la lee mientras la
   * interfaz guarda una versión nueva— y un record con una lista mutable dentro no es inmutable en
   * la práctica.
   */
  public AppConfig {
    imageNames = List.copyOf(imageNames);
  }

  /**
   * Los valores con los que se siembra el fichero de configuración la primera vez. A partir de ahí
   * manda el fichero: si el entorno prevaleciera siempre, la pantalla de ajustes no podría
   * modificar nada.
   */
  public static AppConfig fromEnvironment() {
    var home = Path.of(System.getenv().getOrDefault("USERPROFILE", "."));
    var names = System.getenv().getOrDefault("IMAGE_NAMES", "alpha,beta,gamma,delta");
    return new AppConfig(
        Path.of(
            System.getenv()
                .getOrDefault(
                    "NOTIFIER_STATE_FILE", home.resolve(".notifier/images.json").toString())),
        System.getenv().getOrDefault("IMAGE_VERSION_URL", ""),
        Duration.ofSeconds(
            Long.parseLong(System.getenv().getOrDefault("POLL_INTERVAL_SECONDS", "300"))),
        Arrays.stream(names.split(",")).map(String::trim).filter(s -> !s.isBlank()).toList(),
        Boolean.parseBoolean(System.getenv().getOrDefault("SIMULATION_MODE", "true")),
        Boolean.parseBoolean(System.getenv().getOrDefault("IGNORE_SSL_ERRORS", "true")),
        ThemePreference.valueOf(System.getenv().getOrDefault("THEME", "SYSTEM")),
        Boolean.parseBoolean(System.getenv().getOrDefault("TOASTS_ENABLED", "true")),
        Duration.ofSeconds(Long.parseLong(System.getenv().getOrDefault("TOAST_SECONDS", "8"))),
        Boolean.parseBoolean(System.getenv().getOrDefault("SOUNDS_ENABLED", "true")),
        Double.parseDouble(System.getenv().getOrDefault("SOUND_VOLUME", "0.5")));
  }
}
```

- [ ] **Step 4: Crear el puerto y el adaptador**

`application/ConfigStore.java`:

```java
package io.github.shizukajiku.imagewatch.application;

import io.github.shizukajiku.imagewatch.config.AppConfig;

/** Configuración persistida. La siembra el entorno una vez; después manda el fichero. */
public interface ConfigStore {
  AppConfig load();

  void save(AppConfig config);
}
```

`infrastructure/persistence/ConfigDto.java`:

```java
package io.github.shizukajiku.imagewatch.infrastructure.persistence;

import java.util.List;

/**
 * Formato de cable de la configuración. Existe por la misma razón que {@code ReleaseDto}: el JSON
 * se escribe con tipos que Jackson maneja sin módulos añadidos —números, cadenas y listas— y la
 * traducción a {@code Duration}, {@code Path} y enumeraciones queda en un único sitio.
 *
 * <p>{@code stateFile} no está aquí a propósito: no es editable y llega desde el entorno, así que
 * persistirlo permitiría que un fichero viejo apuntara el estado a una ruta que ya no existe.
 */
record ConfigDto(
    String remoteUrl,
    long pollIntervalSeconds,
    List<String> imageNames,
    boolean simulationMode,
    boolean ignoreSslErrors,
    String theme,
    boolean toastsEnabled,
    long toastSeconds,
    boolean soundsEnabled,
    double soundVolume) {}
```

`infrastructure/persistence/JsonConfigStore.java`:

```java
package io.github.shizukajiku.imagewatch.infrastructure.persistence;

import io.github.shizukajiku.imagewatch.application.ConfigStore;
import io.github.shizukajiku.imagewatch.config.AppConfig;
import io.github.shizukajiku.imagewatch.config.ThemePreference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/** {@link ConfigStore} respaldado por un fichero JSON, sembrado una vez desde el entorno. */
public final class JsonConfigStore implements ConfigStore {

  private static final String DESCRIPTION = "la configuración";

  private final Path file;
  private final AppConfig seed;

  public JsonConfigStore(Path file, AppConfig seed) {
    this.file = file;
    this.seed = seed;
  }

  @Override
  public synchronized AppConfig load() {
    if (!Files.exists(file)) {
      save(seed);
      return seed;
    }
    return toConfig(JsonFiles.read(file, ConfigDto.class, DESCRIPTION));
  }

  @Override
  public synchronized void save(AppConfig config) {
    JsonFiles.write(file, toDto(config), DESCRIPTION);
  }

  private AppConfig toConfig(ConfigDto dto) {
    return new AppConfig(
        seed.stateFile(),
        dto.remoteUrl(),
        Duration.ofSeconds(dto.pollIntervalSeconds()),
        dto.imageNames(),
        dto.simulationMode(),
        dto.ignoreSslErrors(),
        ThemePreference.valueOf(dto.theme()),
        dto.toastsEnabled(),
        Duration.ofSeconds(dto.toastSeconds()),
        dto.soundsEnabled(),
        dto.soundVolume());
  }

  private ConfigDto toDto(AppConfig config) {
    return new ConfigDto(
        config.remoteUrl(),
        config.pollInterval().toSeconds(),
        config.imageNames(),
        config.simulationMode(),
        config.ignoreSslErrors(),
        config.theme().name(),
        config.toastsEnabled(),
        config.toastDuration().toSeconds(),
        config.soundsEnabled(),
        config.soundVolume());
  }
}
```

- [ ] **Step 5: Borrar el vestigio de Teams y adaptar el cableado**

```bash
git rm src/main/java/io/github/shizukajiku/imagewatch/infrastructure/notification/TeamsNotificationAdapter.java
```

En `Main.kt`, dentro de `Wiring.init`, la configuración pasa a leerse del almacén:

```kotlin
        val seed = AppConfig.fromEnvironment()
        val configStore = JsonConfigStore(seed.stateFile().resolveSibling("config.json"), seed)
        val config = configStore.load()
```

Y donde estaba la lista de notificadores con Teams:

```kotlin
        // Sin notificadores todavía: los toasts entran en la Task 7 de esta fase.
        service = VersionPollingService(source, state, emptyList(), trackedImages)
```

Elimina los imports de `NotificationPort` y `TeamsNotificationAdapter`, y añade el de `JsonConfigStore`.

- [ ] **Step 6: Ejecutar los tests**

```bash
JAVA_HOME=~/.jdks/ms-25.0.4.1 ./gradlew test
```

Expected: PASS — los tres tests nuevos y los 77 anteriores.

- [ ] **Step 7: Commit**

```bash
JAVA_HOME=~/.jdks/ms-25.0.4.1 ./gradlew spotlessApply && JAVA_HOME=~/.jdks/ms-25.0.4.1 ./gradlew check
git add -A
git commit -m "feat: persistir la configuracion en config.json

El entorno siembra el fichero en el primer arranque y despues deja de tener
efecto: si prevaleciera siempre, la pantalla de ajustes no podria cambiar nada.

La ruta del estado no se persiste. Es lo unico que sigue viniendo del entorno,
porque un fichero viejo podria apuntar a una ruta que ya no existe.

Se elimina el adaptador de Teams, vestigio sin implementacion que el diseno
declara fuera de alcance."
```

---

### Task 2: Origen intercambiable en caliente

Hoy el origen es un campo final capturado en el arranque: cambiar la URL o el modo simulación obliga a reiniciar. `ReloadableImageSource` lo resuelve delegando en un campo `volatile`. Es `volatile` y no `synchronized` porque la escritura ocurre en el hilo de la interfaz y la lectura en el del planificador: lo que hace falta es visibilidad, no exclusión mutua, y un `synchronized` en `findByNames` bloquearía el guardado de ajustes durante un ciclo entero de red.

**Files:**
- Create: `src/main/java/io/github/shizukajiku/imagewatch/infrastructure/remote/ReloadableImageSource.java`
- Test: `src/test/java/io/github/shizukajiku/imagewatch/infrastructure/remote/ReloadableImageSourceTest.java`

**Interfaces:**
- Consumes: `ImageSource.findByNames(List<String>): List<ImageResult>`.
- Produces: `ReloadableImageSource(ImageSource initial)`, `swap(ImageSource replacement)`, y `findByNames` heredado de `ImageSource`.

- [ ] **Step 1: Escribir el test que falla**

`src/test/java/io/github/shizukajiku/imagewatch/infrastructure/remote/ReloadableImageSourceTest.java`:

```java
package io.github.shizukajiku.imagewatch.infrastructure.remote;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.shizukajiku.imagewatch.application.ImageResult;
import io.github.shizukajiku.imagewatch.application.ImageSource;
import io.github.shizukajiku.imagewatch.domain.ImageRelease;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReloadableImageSourceTest {

  private static ImageSource sourceNamed(String tag) {
    return names ->
        names.stream()
            .map(name -> ImageResult.found(new ImageRelease(name, tag + "/" + name + ":1.0.0")))
            .toList();
  }

  @Test
  void delega_en_el_origen_inicial() {
    var source = new ReloadableImageSource(sourceNamed("antes"));

    var results = source.findByNames(List.of("alpha"));

    assertThat(results).singleElement().satisfies(
        result -> assertThat(result.release().orElseThrow().reference()).startsWith("antes/"));
  }

  @Test
  void tras_el_cambio_delega_en_el_nuevo() {
    var source = new ReloadableImageSource(sourceNamed("antes"));

    source.swap(sourceNamed("despues"));
    var results = source.findByNames(List.of("alpha"));

    assertThat(results).singleElement().satisfies(
        result -> assertThat(result.release().orElseThrow().reference()).startsWith("despues/"));
  }

  @Test
  void rechaza_un_reemplazo_nulo() {
    var source = new ReloadableImageSource(sourceNamed("antes"));

    // Un null aquí dejaría la aplicación sin origen y el fallo aparecería un ciclo después,
    // lejos de su causa.
    assertThatThrownBy(() -> source.swap(null)).isInstanceOf(NullPointerException.class);
  }
}
```

- [ ] **Step 2: Ejecutar el test para verificar que falla**

```bash
JAVA_HOME=~/.jdks/ms-25.0.4.1 ./gradlew test --tests "*ReloadableImageSourceTest*"
```

Expected: FAIL — no compila: `ReloadableImageSource` no existe.

- [ ] **Step 3: Escribir la implementación mínima**

```java
package io.github.shizukajiku.imagewatch.infrastructure.remote;

import io.github.shizukajiku.imagewatch.application.ImageResult;
import io.github.shizukajiku.imagewatch.application.ImageSource;
import java.util.List;
import java.util.Objects;

/**
 * Origen que puede sustituirse sin reiniciar la aplicación.
 *
 * <p>El campo es {@code volatile} y no hay bloqueo: la escritura ocurre en el hilo de la interfaz y
 * la lectura en el del planificador, así que lo que hace falta es visibilidad. Un {@code
 * synchronized} en {@code findByNames} dejaría el guardado de ajustes esperando a que terminase un
 * ciclo de red entero.
 *
 * <p>Un ciclo ya en vuelo termina con el origen anterior. Es aceptable: dura un ciclo y el
 * siguiente ya usa el nuevo.
 */
public final class ReloadableImageSource implements ImageSource {

  private volatile ImageSource delegate;

  public ReloadableImageSource(ImageSource initial) {
    this.delegate = Objects.requireNonNull(initial, "El origen inicial no puede ser nulo");
  }

  public void swap(ImageSource replacement) {
    this.delegate = Objects.requireNonNull(replacement, "El origen de reemplazo no puede ser nulo");
  }

  @Override
  public List<ImageResult> findByNames(List<String> names) {
    return delegate.findByNames(names);
  }
}
```

- [ ] **Step 4: Ejecutar los tests**

```bash
JAVA_HOME=~/.jdks/ms-25.0.4.1 ./gradlew test --tests "*ReloadableImageSourceTest*"
```

Expected: PASS, los tres.

- [ ] **Step 5: Commit**

```bash
JAVA_HOME=~/.jdks/ms-25.0.4.1 ./gradlew spotlessApply && JAVA_HOME=~/.jdks/ms-25.0.4.1 ./gradlew check
git add -A
git commit -m "feat: anadir un origen intercambiable en caliente

Cambiar la URL o el modo simulacion obligaba a reiniciar, porque el origen era
un campo final capturado en el arranque.

El delegado es volatile y no hay bloqueo: se escribe desde el hilo de la
interfaz y se lee desde el del planificador, asi que hace falta visibilidad, no
exclusion. Un synchronized dejaria el guardado de ajustes esperando a que
terminara un ciclo de red entero."
```

---

### Task 3: Aplicación en caliente de la configuración

El `Wiring` deja de ser una caja que solo se construye una vez y gana un método que aplica una configuración nueva sobre la aplicación viva. Ese método es también el que valida: **no duplica reglas**, sino que invoca las que ya existen —el constructor de `HttpImageSource` exige HTTPS y `PollingController.updateInterval` exige al menos un segundo— y devuelve el mensaje que ellas producen.

**Files:**
- Modify: `src/main/kotlin/io/github/shizukajiku/imagewatch/Main.kt`
- Create: `src/main/kotlin/io/github/shizukajiku/imagewatch/ui/settings/SettingsViewModel.kt`
- Test: `src/test/kotlin/io/github/shizukajiku/imagewatch/ui/settings/SettingsViewModelTest.kt`

**Interfaces:**
- Consumes: `ConfigStore`, `AppConfig`, `ThemePreference`, `ReloadableImageSource.swap`, `PollingController.updateInterval`.
- Produces: `SettingsUiState` (data class con `remoteUrl`, `intervalSeconds`, `simulationMode`, `ignoreSslErrors`, `theme`, `toastsEnabled`, `toastSeconds`, `soundsEnabled`, `soundVolume`, `error`, `savedAt`), `SettingsViewModel(current: AppConfig, apply: (AppConfig) -> String?)` con `state: StateFlow<SettingsUiState>`, los `onXChange`, y `save()`.
- El `Wiring` produce `fun applyConfig(config: AppConfig): String?` — `null` si se aplicó, o el mensaje a mostrar.

- [ ] **Step 1: Escribir el test que falla**

`src/test/kotlin/io/github/shizukajiku/imagewatch/ui/settings/SettingsViewModelTest.kt`:

```kotlin
package io.github.shizukajiku.imagewatch.ui.settings

import io.github.shizukajiku.imagewatch.config.AppConfig
import io.github.shizukajiku.imagewatch.config.ThemePreference
import java.nio.file.Path
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun config() = AppConfig(
    Path.of("images.json"),
    "https://origen.ejemplo/api",
    Duration.ofSeconds(300),
    listOf("alpha"),
    true,
    true,
    ThemePreference.SYSTEM,
    true,
    Duration.ofSeconds(8),
    true,
    0.5,
)

class SettingsViewModelTest {

    @Test
    fun `arranca con los valores vigentes`() {
        val viewModel = SettingsViewModel(config()) { null }

        val state = viewModel.state.value

        assertEquals("https://origen.ejemplo/api", state.remoteUrl)
        assertEquals("300", state.intervalSeconds)
        assertEquals(ThemePreference.SYSTEM, state.theme)
    }

    @Test
    fun `entrega al aplicador la configuracion editada`() {
        var recibida: AppConfig? = null
        val viewModel = SettingsViewModel(config()) { recibida = it; null }

        viewModel.onIntervalChange("15")
        viewModel.onSimulationChange(false)
        viewModel.onThemeChange(ThemePreference.DARK)
        viewModel.save()

        assertEquals(Duration.ofSeconds(15), recibida?.pollInterval())
        assertEquals(false, recibida?.simulationMode())
        assertEquals(ThemePreference.DARK, recibida?.theme())
        assertNull(viewModel.state.value.error)
        assertTrue(viewModel.state.value.savedAt > 0)
    }

    @Test
    fun `un intervalo no numerico se rechaza sin llegar al aplicador`() {
        var llamado = false
        val viewModel = SettingsViewModel(config()) { llamado = true; null }

        viewModel.onIntervalChange("cada rato")
        viewModel.save()

        assertEquals("El intervalo debe ser un número de segundos", viewModel.state.value.error)
        assertEquals(false, llamado)
    }

    @Test
    fun `el mensaje del aplicador se muestra tal cual`() {
        // La pantalla no reimplementa la validacion: ensena lo que devuelven las reglas que ya
        // existen en el nucleo.
        val viewModel = SettingsViewModel(config()) { "El endpoint remoto debe utilizar HTTPS" }

        viewModel.onUrlChange("http://inseguro.ejemplo")
        viewModel.save()

        assertEquals("El endpoint remoto debe utilizar HTTPS", viewModel.state.value.error)
        assertEquals(0L, viewModel.state.value.savedAt)
    }
}
```

- [ ] **Step 2: Ejecutar el test para verificar que falla**

```bash
JAVA_HOME=~/.jdks/ms-25.0.4.1 ./gradlew test --tests "*SettingsViewModelTest*"
```

Expected: FAIL — no compila: `SettingsViewModel` no existe.

- [ ] **Step 3: Escribir el view model**

`src/main/kotlin/io/github/shizukajiku/imagewatch/ui/settings/SettingsViewModel.kt`:

```kotlin
package io.github.shizukajiku.imagewatch.ui.settings

import io.github.shizukajiku.imagewatch.config.AppConfig
import io.github.shizukajiku.imagewatch.config.ThemePreference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Duration

data class SettingsUiState(
    val remoteUrl: String,
    val intervalSeconds: String,
    val simulationMode: Boolean,
    val ignoreSslErrors: Boolean,
    val theme: ThemePreference,
    val toastsEnabled: Boolean,
    val toastSeconds: String,
    val soundsEnabled: Boolean,
    val soundVolume: Float,
    val error: String? = null,
    /** Marca de tiempo del último guardado correcto. Cero mientras no haya ninguno. */
    val savedAt: Long = 0,
)

/**
 * Estado del formulario de ajustes.
 *
 * No valida por su cuenta más que el formato de los dos campos numéricos: el resto de reglas ya
 * viven en el núcleo —HTTPS obligatorio en el adaptador HTTP, intervalo mínimo en el controlador— y
 * duplicarlas aquí significaría mantener dos versiones de la misma regla.
 *
 * @param apply aplica la configuración sobre la aplicación viva y la persiste. Devuelve `null` si
 *   todo fue bien, o el mensaje a mostrar.
 */
class SettingsViewModel(
    current: AppConfig,
    private val apply: (AppConfig) -> String?,
) {
    private val stateFile = current.stateFile()
    private val imageNames = current.imageNames()
    private val mutableState = MutableStateFlow(
        SettingsUiState(
            remoteUrl = current.remoteUrl(),
            intervalSeconds = current.pollInterval().seconds.toString(),
            simulationMode = current.simulationMode(),
            ignoreSslErrors = current.ignoreSslErrors(),
            theme = current.theme(),
            toastsEnabled = current.toastsEnabled(),
            toastSeconds = current.toastDuration().seconds.toString(),
            soundsEnabled = current.soundsEnabled(),
            soundVolume = current.soundVolume().toFloat(),
        ),
    )
    val state: StateFlow<SettingsUiState> = mutableState.asStateFlow()

    fun onUrlChange(value: String) = edit { it.copy(remoteUrl = value) }

    fun onIntervalChange(value: String) = edit { it.copy(intervalSeconds = value) }

    fun onSimulationChange(value: Boolean) = edit { it.copy(simulationMode = value) }

    fun onIgnoreSslChange(value: Boolean) = edit { it.copy(ignoreSslErrors = value) }

    fun onThemeChange(value: ThemePreference) = edit { it.copy(theme = value) }

    fun onToastsChange(value: Boolean) = edit { it.copy(toastsEnabled = value) }

    fun onToastSecondsChange(value: String) = edit { it.copy(toastSeconds = value) }

    fun onSoundsChange(value: Boolean) = edit { it.copy(soundsEnabled = value) }

    fun onVolumeChange(value: Float) = edit { it.copy(soundVolume = value) }

    fun save() {
        val form = mutableState.value
        val interval = form.intervalSeconds.trim().toLongOrNull()
        if (interval == null) {
            mutableState.value = form.copy(error = "El intervalo debe ser un número de segundos")
            return
        }
        val toastSeconds = form.toastSeconds.trim().toLongOrNull()
        if (toastSeconds == null || toastSeconds <= 0) {
            mutableState.value = form.copy(error = "La duración del toast debe ser un número de segundos")
            return
        }
        val message = apply(
            AppConfig(
                stateFile,
                form.remoteUrl.trim(),
                Duration.ofSeconds(interval),
                imageNames,
                form.simulationMode,
                form.ignoreSslErrors,
                form.theme,
                form.toastsEnabled,
                Duration.ofSeconds(toastSeconds),
                form.soundsEnabled,
                form.soundVolume.toDouble(),
            ),
        )
        mutableState.value = when (message) {
            null -> form.copy(error = null, savedAt = System.currentTimeMillis())
            else -> form.copy(error = message)
        }
    }

    /** Editar limpia el error anterior: dejarlo en pantalla mientras se corrige es ruido. */
    private fun edit(change: (SettingsUiState) -> SettingsUiState) {
        mutableState.value = change(mutableState.value).copy(error = null)
    }
}
```

- [ ] **Step 4: Ejecutar los tests**

```bash
JAVA_HOME=~/.jdks/ms-25.0.4.1 ./gradlew test --tests "*SettingsViewModelTest*"
```

Expected: PASS, los cuatro.

- [ ] **Step 5: Enseñar al `Wiring` a aplicar en caliente**

En `Main.kt`, `Wiring` pasa a guardar lo que necesita para reconstruir y expone `applyConfig`. Sustituye la clase entera por:

```kotlin
private class Wiring {
    private val configStore: ConfigStore
    private val source: ReloadableImageSource
    val trackedImages: TrackedImageStore
    val service: VersionPollingService
    val controller: PollingController

    /** La configuración vigente. La interfaz la observa para el tema y los ajustes. */
    val config = MutableStateFlow(AppConfig.fromEnvironment())

    init {
        val seed = AppConfig.fromEnvironment()
        configStore = JsonConfigStore(seed.stateFile().resolveSibling("config.json"), seed)
        val loaded = configStore.load()
        check(loaded.simulationMode() || loaded.remoteUrl().isNotBlank()) {
            "Falta la URL del origen remoto. Define IMAGE_VERSION_URL " +
                "o deja SIMULATION_MODE=true para usar el origen simulado."
        }
        config.value = loaded
        source = ReloadableImageSource(sourceFor(loaded))
        val state = JsonImageStateStore(loaded.stateFile())
        trackedImages =
            JsonTrackedImageStore(
                loaded.stateFile().resolveSibling("tracked-images.json"),
                loaded.imageNames(),
            )
        service = VersionPollingService(source, state, emptyList(), trackedImages)
        controller = PollingController(service, loaded.pollInterval())
    }

    /**
     * Aplica una configuración sobre la aplicación viva y la persiste. Devuelve el mensaje a
     * mostrar, o `null` si todo fue bien.
     *
     * El orden importa: primero se construye el origen —cuyo constructor es quien valida la URL— y
     * después se toca nada más. Así una URL inválida no deja el intervalo cambiado a medias.
     */
    fun applyConfig(candidate: AppConfig): String? = runCatching {
        val replacement = sourceFor(candidate)
        controller.updateInterval(candidate.pollInterval())
        source.swap(replacement)
        configStore.save(candidate)
        config.value = candidate
    }.fold(onSuccess = { null }, onFailure = { it.message ?: "No se pudo aplicar la configuración" })

    private fun sourceFor(config: AppConfig): ImageSource =
        if (config.simulationMode()) {
            SimulatedImageSource(Clock.systemUTC(), SIMULATED_BUMP_EVERY)
        } else {
            HttpImageSource(
                HttpClientFactory.create(config.ignoreSslErrors()),
                config.remoteUrl(),
            )
        }
}
```

Imports que hay que añadir a `Main.kt`: `io.github.shizukajiku.imagewatch.application.ConfigStore`, `io.github.shizukajiku.imagewatch.application.ImageSource`, `io.github.shizukajiku.imagewatch.infrastructure.persistence.JsonConfigStore`, `io.github.shizukajiku.imagewatch.infrastructure.remote.ReloadableImageSource` y `kotlinx.coroutines.flow.MutableStateFlow`.

- [ ] **Step 6: Ejecutar la batería completa**

```bash
JAVA_HOME=~/.jdks/ms-25.0.4.1 ./gradlew check
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
JAVA_HOME=~/.jdks/ms-25.0.4.1 ./gradlew spotlessApply && JAVA_HOME=~/.jdks/ms-25.0.4.1 ./gradlew check
git add -A
git commit -m "feat: aplicar la configuracion sin reiniciar

El cableado gana applyConfig: reconstruye el origen, propaga el intervalo al
controlador y persiste, en ese orden. Primero se construye el origen porque su
constructor es quien valida la URL: asi una URL invalida no deja el intervalo
cambiado a medias.

El formulario no reimplementa ninguna regla. Solo comprueba que los dos campos
numericos lo sean y ensena el mensaje que devuelven las reglas del nucleo."
```

---

### Task 4: Pantalla de ajustes y navegación

Dos pantallas con `Crossfade` y sin librería de navegación: para dos destinos, una enumeración y un `when` bastan, y una dependencia de navegación aquí sería infraestructura para un problema que no existe.

**Files:**
- Create: `src/main/resources/icons/gear.svg`, `src/main/resources/icons/arrow-left.svg`
- Modify: `src/main/kotlin/io/github/shizukajiku/imagewatch/ui/components/SvgIcon.kt`
- Modify: `src/main/kotlin/io/github/shizukajiku/imagewatch/ui/theme/Theme.kt`
- Create: `src/main/kotlin/io/github/shizukajiku/imagewatch/ui/settings/SettingsScreen.kt`
- Modify: `src/main/kotlin/io/github/shizukajiku/imagewatch/ui/images/ImagesScreen.kt`
- Modify: `src/main/kotlin/io/github/shizukajiku/imagewatch/Main.kt`
- Test: `src/test/kotlin/io/github/shizukajiku/imagewatch/ui/components/SvgIconTest.kt` (ampliar)

**Interfaces:**
- Consumes: `SettingsUiState`, `SettingsViewModel`, `ThemePreference`, `Wiring.config`, `Wiring.applyConfig`.
- Produces: `AppSvg.GEAR`, `AppSvg.BACK`; `ImageWatchTheme(preference: ThemePreference, content: @Composable () -> Unit)`; `SettingsScreen(state, onUrlChange, onIntervalChange, onSimulationChange, onIgnoreSslChange, onThemeChange, onToastsChange, onToastSecondsChange, onSoundsChange, onVolumeChange, onSave, onBack)`; `ImagesScreen` gana el parámetro `onOpenSettings: () -> Unit`.

- [ ] **Step 1: Crear los dos iconos**

`src/main/resources/icons/gear.svg`:

```xml
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="#9a9aa2" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 1 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 1 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 1 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 1 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"/></svg>
```

`src/main/resources/icons/arrow-left.svg`:

```xml
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="#9a9aa2" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="19" y1="12" x2="5" y2="12"/><polyline points="12 19 5 12 12 5"/></svg>
```

- [ ] **Step 2: Registrarlos y ampliar el test de iconos**

En `ui/components/SvgIcon.kt`, dentro de `enum class AppSvg`, añade dos entradas antes del `;`:

```kotlin
    GEAR("gear"),
    BACK("arrow-left"),
```

El test existente `SvgIconTest` recorre `AppSvg.entries`, así que cubre los nuevos sin tocarlo. Verifícalo leyéndolo antes de continuar; si estuviera escrito con una lista fija de iconos, añade allí los dos.

- [ ] **Step 3: Ejecutar el test de iconos**

```bash
JAVA_HOME=~/.jdks/ms-25.0.4.1 ./gradlew test --tests "*SvgIconTest*"
```

Expected: PASS. Si falla con «Falta el icono», el SVG no acabó en `src/main/resources/icons/`.

- [ ] **Step 4: Enseñar al tema a obedecer la preferencia**

Añade a `ui/theme/Theme.kt`, sin tocar la función existente:

```kotlin
/**
 * La preferencia persistida decide el tema. `SYSTEM` delega en el escritorio, que es lo que
 * quiere quien no ha elegido: si el usuario cambia el suyo a oscuro por la noche, la aplicación
 * lo sigue sin que él vuelva aquí.
 */
@Composable
fun ImageWatchTheme(preference: ThemePreference, content: @Composable () -> Unit) {
    val dark = when (preference) {
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
        ThemePreference.LIGHT -> false
        ThemePreference.DARK -> true
    }
    ImageWatchTheme(dark = dark, content = content)
}
```

Import nuevo: `io.github.shizukajiku.imagewatch.config.ThemePreference`.

- [ ] **Step 5: Escribir la pantalla de ajustes**

`src/main/kotlin/io/github/shizukajiku/imagewatch/ui/settings/SettingsScreen.kt`:

```kotlin
package io.github.shizukajiku.imagewatch.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.shizukajiku.imagewatch.config.ThemePreference
import io.github.shizukajiku.imagewatch.ui.components.AppSvg
import io.github.shizukajiku.imagewatch.ui.components.SvgIcon

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onUrlChange: (String) -> Unit,
    onIntervalChange: (String) -> Unit,
    onSimulationChange: (Boolean) -> Unit,
    onIgnoreSslChange: (Boolean) -> Unit,
    onThemeChange: (ThemePreference) -> Unit,
    onToastsChange: (Boolean) -> Unit,
    onToastSecondsChange: (String) -> Unit,
    onSoundsChange: (Boolean) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 12.dp, top = 16.dp, end = 20.dp, bottom = 8.dp),
        ) {
            IconButton(onBack) {
                SvgIcon(AppSvg.BACK, MaterialTheme.colorScheme.onSurfaceVariant, Modifier.size(18.dp))
            }
            Text("Ajustes", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }

        Section("Origen") {
            OutlinedTextField(
                value = state.remoteUrl,
                onValueChange = onUrlChange,
                label = { Text("URL del origen") },
                singleLine = true,
                enabled = !state.simulationMode,
                modifier = Modifier.fillMaxWidth(),
            )
            Toggle("Modo simulación", state.simulationMode, onSimulationChange)
            Toggle("Ignorar errores de TLS", state.ignoreSslErrors, onIgnoreSslChange)
            OutlinedTextField(
                value = state.intervalSeconds,
                onValueChange = onIntervalChange,
                label = { Text("Intervalo de sondeo (s)") },
                singleLine = true,
                modifier = Modifier.width(220.dp),
            )
        }

        Section("Apariencia") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemePreference.entries.forEach { option ->
                    FilterChip(
                        selected = state.theme == option,
                        onClick = { onThemeChange(option) },
                        label = { Text(themeLabel(option)) },
                    )
                }
            }
        }

        Section("Avisos") {
            Toggle("Mostrar toasts", state.toastsEnabled, onToastsChange)
            OutlinedTextField(
                value = state.toastSeconds,
                onValueChange = onToastSecondsChange,
                label = { Text("Duración del toast (s)") },
                singleLine = true,
                enabled = state.toastsEnabled,
                modifier = Modifier.width(220.dp),
            )
            Toggle("Sonidos", state.soundsEnabled, onSoundsChange)
            Text("Volumen", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Slider(
                value = state.soundVolume,
                onValueChange = onVolumeChange,
                enabled = state.soundsEnabled,
                modifier = Modifier.width(260.dp),
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Button(onSave) { Text("Guardar") }
            Spacer(Modifier.width(16.dp))
            // El error del guardado aparece aqui, junto al boton que lo provoco, y no arriba:
            // un mensaje lejos de su causa obliga a buscarlo.
            AnimatedVisibility(state.error != null) {
                Text(
                    state.error.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                )
            }
            AnimatedVisibility(state.error == null && state.savedAt > 0) {
                Text(
                    "Guardado",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

private fun themeLabel(preference: ThemePreference) = when (preference) {
    ThemePreference.SYSTEM -> "Sistema"
    ThemePreference.LIGHT -> "Claro"
    ThemePreference.DARK -> "Oscuro"
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(
            title,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        content()
        HorizontalDivider(Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Text(label, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
```

- [ ] **Step 6: Añadir el acceso desde la pantalla de imágenes**

En `ImagesScreen.kt`, añade el parámetro `onOpenSettings: () -> Unit` a `ImagesScreen` y a `Header`, y dentro de la fila del encabezado, justo antes del botón «Agregar imagen»:

```kotlin
            IconButton(onOpenSettings) {
                SvgIcon(AppSvg.GEAR, MaterialTheme.colorScheme.onSurfaceVariant, Modifier.size(18.dp))
            }
```

Import nuevo en ese fichero: `androidx.compose.material3.IconButton`.

- [ ] **Step 7: Conectar la navegación y el tema en `Main.kt`**

En `MainScreen`, sustituye el cuerpo por la versión con dos pantallas:

```kotlin
private enum class Screen { IMAGES, SETTINGS }

@Composable
private fun MainScreen(wiring: Wiring) {
    val viewModel =
        remember { ImagesViewModel(wiring.service, wiring.trackedImages, wiring.controller) }
    DisposableEffect(viewModel) { onDispose { viewModel.close() } }

    val state by viewModel.state.collectAsState()
    val config by wiring.config.collectAsState()
    var screen by remember { mutableStateOf(Screen.IMAGES) }
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<String?>(null) }

    Crossfade(screen, label = "screen") { current ->
        when (current) {
            Screen.IMAGES ->
                ImagesScreen(
                    state = state,
                    onSearchChange = viewModel::onSearchChange,
                    onAdd = { adding = true },
                    onAcknowledge = viewModel::acknowledge,
                    onEdit = { editing = it },
                    onDelete = { deleting = it },
                    onTogglePolling = viewModel::togglePolling,
                    onApplyInterval = { viewModel.applyInterval(it) },
                    onOpenSettings = { screen = Screen.SETTINGS },
                )

            Screen.SETTINGS -> SettingsPane(wiring, config) { screen = Screen.IMAGES }
        }
    }

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

@Composable
private fun SettingsPane(wiring: Wiring, config: AppConfig, onBack: () -> Unit) {
    // La clave es la configuracion vigente: si se aplica una nueva, el formulario se reconstruye
    // con ella en lugar de seguir mostrando lo que el usuario tecleo hace dos pantallas.
    val viewModel = remember(config) { SettingsViewModel(config, wiring::applyConfig) }
    val state by viewModel.state.collectAsState()

    SettingsScreen(
        state = state,
        onUrlChange = viewModel::onUrlChange,
        onIntervalChange = viewModel::onIntervalChange,
        onSimulationChange = viewModel::onSimulationChange,
        onIgnoreSslChange = viewModel::onIgnoreSslChange,
        onThemeChange = viewModel::onThemeChange,
        onToastsChange = viewModel::onToastsChange,
        onToastSecondsChange = viewModel::onToastSecondsChange,
        onSoundsChange = viewModel::onSoundsChange,
        onVolumeChange = viewModel::onVolumeChange,
        onSave = viewModel::save,
        onBack = onBack,
    )
}
```

Y en `main()`, el tema pasa a leer la preferencia. Dentro de `application { }`, antes del `Tray`:

```kotlin
        val config by wiring.config.collectAsState()
```

y la ventana envuelve con `ImageWatchTheme(config.theme()) { ... }`.

Imports nuevos en `Main.kt`: `androidx.compose.animation.Crossfade`, `io.github.shizukajiku.imagewatch.ui.settings.SettingsScreen`, `io.github.shizukajiku.imagewatch.ui.settings.SettingsViewModel`.

- [ ] **Step 8: Comprobarlo a ojo**

```bash
JAVA_HOME=~/.jdks/ms-25.0.4.1 ./gradlew run
```

Comprueba, y **no sigas hasta que las cuatro cosas ocurran**: el engranaje abre los ajustes con un fundido; cambiar el tema a claro repinta la ventana; guardar un intervalo de 10 s hace que la barra de sondeo lo refleje; y reabrir la aplicación conserva ambos.

- [ ] **Step 9: Commit**

```bash
JAVA_HOME=~/.jdks/ms-25.0.4.1 ./gradlew spotlessApply && JAVA_HOME=~/.jdks/ms-25.0.4.1 ./gradlew check
git add -A
git commit -m "feat: anadir la pantalla de ajustes

Dos pantallas con Crossfade y sin libreria de navegacion: para dos destinos, una
enumeracion y un when bastan.

El formulario se reconstruye cuando cambia la configuracion vigente, para no
seguir mostrando lo que el usuario tecleo antes de guardar."
```

---

### Task 5: Animación 4 — pulso mientras se verifica

La fase 4 dejó esta animación fuera porque el núcleo no exponía que hubiera una consulta en vuelo. Se resuelve con un método `default` en `PollListener`: la interfaz es un oyente más y se entera igual que se entera del resultado. Sigue siendo `@FunctionalInterface` porque solo hay un método abstracto.

**Files:**
- Modify: `src/main/java/io/github/shizukajiku/imagewatch/application/PollListener.java`
- Modify: `src/main/java/io/github/shizukajiku/imagewatch/application/VersionPollingService.java`
- Modify: `src/main/kotlin/io/github/shizukajiku/imagewatch/ui/images/ImagesViewModel.kt`
- Modify: `src/main/kotlin/io/github/shizukajiku/imagewatch/ui/images/ImagesScreen.kt`
- Modify: `src/main/kotlin/io/github/shizukajiku/imagewatch/ui/images/ImageRow.kt`
- Test: `src/test/java/io/github/shizukajiku/imagewatch/application/VersionPollingServiceTest.java` (añadir un test)
- Test: `src/test/kotlin/io/github/shizukajiku/imagewatch/ui/images/ImagesViewModelTest.kt` (añadir un test)

**Interfaces:**
- Consumes: `PollListener.onSnapshot(PollSnapshot)`.
- Produces: `PollListener.onPollStarted()` (método `default`, cuerpo vacío); `ImagesUiState.verifying: Boolean`; `ImageRow(..., verifying: Boolean, ...)`.

- [ ] **Step 1: Escribir el test del núcleo que falla**

Añade a `VersionPollingServiceTest`:

```java
  @Test
  void elOyenteSeEnteraDeQueElCicloEmpiezaAntesDeRecibirElResultado() {
    var names = new FakeTrackedImageStore(List.of("alpha"));
    var state = new FakeImageStateStore();
    var source = new FakeImageSource(Map.of("alpha", "registry.local/alpha:1.0.0"));
    var service = new VersionPollingService(source, state, List.of(), names);
    var orden = new ArrayList<String>();
    service.addListener(
        new PollListener() {
          @Override
          public void onPollStarted() {
            orden.add("inicio");
          }

          @Override
          public void onSnapshot(PollSnapshot snapshot) {
            orden.add("resultado");
          }
        });

    service.poll();

    assertThat(orden).containsExactly("inicio", "resultado");
  }
```

- [ ] **Step 2: Ejecutar para verificar que falla**

```bash
JAVA_HOME=~/.jdks/ms-25.0.4.1 ./gradlew test --tests "*VersionPollingServiceTest*"
```

Expected: FAIL — no compila: `onPollStarted` no existe.

- [ ] **Step 3: Añadir el aviso de comienzo**

`application/PollListener.java` gana el método:

```java
  /**
   * Aviso de que un ciclo acaba de empezar. Vacío por defecto: la mayoría de oyentes solo quiere el
   * resultado, y obligarles a implementar esto convertiría cada uno en una clase anónima.
   *
   * <p>Es lo que permite a la interfaz distinguir «todavía no se ha comprobado» de «se está
   * comprobando ahora mismo», que para el usuario son dos cosas distintas.
   */
  default void onPollStarted() {}
```

En `VersionPollingService.poll()`, como primera línea del método:

```java
    publishStart();
```

y el método privado, junto a `publish`:

```java
  /** Igual que {@link #publish}: un oyente que lanza no puede dejar a los demás sin aviso. */
  private void publishStart() {
    for (var listener : listeners) {
      try {
        listener.onPollStarted();
      } catch (RuntimeException e) {
        LOG.warn("Un oyente falló al recibir el comienzo del ciclo", e);
      }
    }
  }
```

- [ ] **Step 4: Ejecutar el test del núcleo**

```bash
JAVA_HOME=~/.jdks/ms-25.0.4.1 ./gradlew test --tests "*VersionPollingServiceTest*"
```

Expected: PASS.

- [ ] **Step 5: Escribir el test del view model que falla**

Añade a `ImagesViewModelTest`:

```kotlin
    @Test
    fun `verificando se enciende al empezar el ciclo y se apaga al terminar`() {
        val fixture = fixture(listOf("alpha"))

        assertEquals(false, fixture.viewModel.state.value.verifying)

        fixture.service.poll()

        // Cuando poll() retorna, el snapshot ya llegó: lo que queda es el estado de reposo.
        assertEquals(false, fixture.viewModel.state.value.verifying)
    }

    @Test
    fun `el aviso de comienzo enciende la verificacion`() {
        val fixture = fixture(listOf("alpha"))

        // Se invoca el puerto directamente porque poll() enciende y apaga en la misma llamada:
        // el estado intermedio no es observable desde fuera de otro modo.
        fixture.viewModel.onPollStarted()

        assertEquals(true, fixture.viewModel.state.value.verifying)
    }
```

El segundo test solo compila si `ImagesViewModel` implementa `PollListener` en lugar de registrar una lambda: es lo que hace el paso siguiente.

- [ ] **Step 6: Llevar el estado al view model**

En `ImagesViewModel.kt`, la clase pasa a implementar `PollListener` —lo que además elimina la lambda y el campo `listener`—:

```kotlin
class ImagesViewModel(
    private val service: VersionPollingService,
    private val trackedImages: TrackedImageStore,
    private val controller: PollingController,
) : PollListener {
```

Sustituye el campo `listener` por los dos métodos del puerto y añade el estado:

```kotlin
    private var verifying = false

    // Llegan desde el hilo del planificador. MutableStateFlow es seguro entre hilos y Compose
    // recolecta desde el suyo, así que no hace falta saltar de hilo aquí.
    override fun onPollStarted() {
        verifying = true
        recompute()
    }

    override fun onSnapshot(received: PollSnapshot) {
        snapshot = received
        verifying = false
        recompute()
    }
```

En `init`, `service.addListener(listener)` pasa a `service.addListener(this)`, y en `close()`, `service.removeListener(this)`.

Añade el campo a `ImagesUiState`:

```kotlin
    val verifying: Boolean = false,
```

y en `recompute()`, dentro de la construcción del estado:

```kotlin
            verifying = verifying,
```

Import nuevo: `io.github.shizukajiku.imagewatch.domain.PollSnapshot`.

- [ ] **Step 7: Pintar el pulso**

En `ImagesScreen.kt`, pasa el dato a cada fila:

```kotlin
                ImageRow(
                    row = row,
                    verifying = state.verifying,
```

En `ImageRow.kt`, añade el parámetro y el pulso. La función gana `verifying: Boolean` tras `row`, y dentro:

```kotlin
    // Animacion 4: mientras hay una consulta en vuelo, lo que nunca se ha comprobado late. Solo
    // las filas UNKNOWN: hacer latir una fila que ya tiene dato sugeriria que su dato es dudoso.
    val pulsing = verifying && row.status == ImageStatus.UNKNOWN
    val transition = rememberInfiniteTransition(label = "pulse")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.45f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "pulseAlpha",
    )

    Column(modifier.alpha(if (pulsing) pulse else 1f)) {
```

Imports nuevos en `ImageRow.kt`: `androidx.compose.animation.core.RepeatMode`, `androidx.compose.animation.core.animateFloat`, `androidx.compose.animation.core.infiniteRepeatable`, `androidx.compose.animation.core.rememberInfiniteTransition`, `androidx.compose.animation.core.tween`, `androidx.compose.runtime.getValue`, `androidx.compose.ui.draw.alpha`.

- [ ] **Step 8: Ejecutar la batería completa**

```bash
JAVA_HOME=~/.jdks/ms-25.0.4.1 ./gradlew check
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 9: Verlo en marcha**

```bash
JAVA_HOME=~/.jdks/ms-25.0.4.1 IMAGE_NAMES=alpha,beta,gamma POLL_INTERVAL_SECONDS=10 ./gradlew run
```

Borra antes `~/.notifier/config.json` si la configuración guardada pisa esas variables: ahora el fichero manda sobre el entorno, que es justo lo que la Task 1 introdujo.

Expected: en el primer ciclo, las filas sin verificar laten; al llegar el resultado, dejan de hacerlo.

- [ ] **Step 10: Commit**

```bash
JAVA_HOME=~/.jdks/ms-25.0.4.1 ./gradlew spotlessApply && JAVA_HOME=~/.jdks/ms-25.0.4.1 ./gradlew check
git add -A
git commit -m "feat: latir las filas sin verificar mientras se consulta

El nucleo no exponia que hubiera una consulta en vuelo, y por eso la fase 4
dejo esta animacion fuera. Se resuelve con un metodo default en PollListener:
la interfaz se entera del comienzo igual que se entera del resultado, y el
puerto sigue siendo funcional porque solo hay un metodo abstracto.

Solo laten las filas sin verificar: hacer latir una que ya tiene dato sugeriria
que su dato es dudoso."
```

---

### Task 6: Errores visibles en los tres niveles

El spec pide tres niveles y hoy hay uno y medio: la fila ya muestra `ERROR`, pero pierde la última versión conocida, el aviso global no dice cuánto hace de la última verificación correcta, y la bandeja no distingue un fallo persistente.

**Files:**
- Modify: `src/main/kotlin/io/github/shizukajiku/imagewatch/ui/images/ImagesViewModel.kt`
- Modify: `src/main/kotlin/io/github/shizukajiku/imagewatch/ui/images/ImagesScreen.kt`
- Modify: `src/main/kotlin/io/github/shizukajiku/imagewatch/ui/images/ImageRow.kt`
- Modify: `src/main/kotlin/io/github/shizukajiku/imagewatch/ui/AppIconPainter.kt`
- Modify: `src/main/kotlin/io/github/shizukajiku/imagewatch/Main.kt`
- Test: `src/test/kotlin/io/github/shizukajiku/imagewatch/ui/images/ImagesViewModelTest.kt`

**Interfaces:**
- Consumes: `ImageState.error(): Optional<String>`, `ImageState.local()`, `PollSnapshot.at()`.
- Produces: `ImagesUiState.allFailing: Boolean`, `ImagesUiState.lastSuccessAt: Instant?`; `ImageRowState.error: String?`; `AlertIconPainter` (object `Painter`); `VersionPill(..., dimmed: Boolean = false)`.

- [ ] **Step 1: Escribir los tests que fallan**

Añade a `ImagesViewModelTest`:

```kotlin
    @Test
    fun `una fila con error conserva la ultima version conocida`() {
        val fixture = fixture(listOf("alpha"))
        fixture.state.seed("alpha", "registry.local/alpha:1.0.0")
        fixture.source.failOn("alpha", "HTTP 503")

        fixture.service.poll()

        val row = fixture.viewModel.state.value.rows.single()
        assertEquals(ImageStatus.ERROR, row.status)
        assertEquals("HTTP 503", row.error)
        assertEquals("1.0.0", row.local, "La version conocida no desaparece porque falle la consulta")
    }

    @Test
    fun `si fallan todas se marca el aviso global`() {
        val fixture = fixture(listOf("alpha", "beta"))
        fixture.source.failOn("alpha", "HTTP 503")
        fixture.source.failOn("beta", "HTTP 503")

        fixture.service.poll()

        assertTrue(fixture.viewModel.state.value.allFailing)
    }

    @Test
    fun `una sola imagen caida no dispara el aviso global`() {
        // "El origen no responde" y "esta imagen no existe" son problemas distintos y el aviso
        // solo cubre el primero.
        val fixture = fixture(listOf("alpha", "beta"))
        fixture.source.failOn("alpha", "HTTP 404")

        fixture.service.poll()

        assertEquals(false, fixture.viewModel.state.value.allFailing)
    }
```

Si el `fixture` existente no expone `state` y `source`, amplíalo para que lo haga: son los dobles que ya construye internamente.

- [ ] **Step 2: Ejecutar para verificar que falla**

```bash
JAVA_HOME=~/.jdks/ms-25.0.4.1 ./gradlew test --tests "*ImagesViewModelTest*"
```

Expected: FAIL — `row.error` y `state.allFailing` no existen.

- [ ] **Step 3: Ampliar el estado**

En `ImagesViewModel.kt`:

```kotlin
data class ImageRowState(
    val name: String,
    val registry: String,
    val local: String,
    val remote: String,
    val status: ImageStatus,
    val detail: String,
    /** Mensaje del fallo, si esta fila falló. Es lo que se enseña en el tooltip. */
    val error: String? = null,
)
```

y en `ImagesUiState`:

```kotlin
    /** Todas las imágenes vigiladas fallaron en el último ciclo: el origen no responde. */
    val allFailing: Boolean = false,
    /** Cuándo terminó el último ciclo en el que algo se verificó. Nulo si nunca ocurrió. */
    val lastSuccessAt: Instant? = null,
```

En `toRow`, el `ImageRowState` que se construye con una imagen presente pasa a llevar `error = image.error().orElse(null)`.

En `recompute()`, junto al resto de campos:

```kotlin
            allFailing = all.isNotEmpty() && all.all { it.status == ImageStatus.ERROR },
            lastSuccessAt = lastSuccessAt,
```

y el campo que lo sostiene, junto a `verifying`:

```kotlin
    private var lastSuccessAt: Instant? = null
```

que se actualiza en `onSnapshot`, antes de `recompute()`:

```kotlin
        // La marca solo avanza si algo se verificó. Si avanzara en cada ciclo, el aviso diría
        // "hace 10 segundos" mientras el origen lleva una hora caído.
        if (received.images().any { it.status() != ImageStatus.ERROR }) {
            lastSuccessAt = received.at()
        }
```

Import nuevo: `java.time.Instant`.

- [ ] **Step 4: Ejecutar los tests**

```bash
JAVA_HOME=~/.jdks/ms-25.0.4.1 ./gradlew test --tests "*ImagesViewModelTest*"
```

Expected: PASS.

- [ ] **Step 5: Enseñarlo en pantalla**

En `ImagesScreen.kt`, `ConnectionBanner` deja de deducir el fallo y usa el estado, y dice la antigüedad:

```kotlin
@Composable
private fun ConnectionBanner(state: ImagesUiState) {
    AnimatedVisibility(state.allFailing, enter = expandVertically(), exit = shrinkVertically()) {
        Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            ) {
                SvgIcon(
                    AppSvg.WARNING,
                    MaterialTheme.colorScheme.onErrorContainer,
                    Modifier.size(16.dp),
                )
                Text(
                    "  Sin conexión con el origen: ninguna imagen pudo verificarse. " +
                        lastSuccessLabel(state.lastSuccessAt),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}

private fun lastSuccessLabel(at: Instant?): String {
    if (at == null) {
        return "No se ha verificado nada desde que arrancó."
    }
    val minutes = Duration.between(at, Instant.now()).toMinutes()
    return when {
        minutes < 1 -> "Última verificación correcta: hace menos de un minuto."
        minutes < 60 -> "Última verificación correcta: hace $minutes min."
        else -> "Última verificación correcta: hace ${minutes / 60} h."
    }
}
```

Imports nuevos: `java.time.Duration`, `java.time.Instant`.

En `ImageRow.kt`, la fila con error atenúa la versión conocida y enseña el mensaje. Envuelve el `Text(row.detail, ...)` en un `TooltipArea` cuando hay error:

```kotlin
                if (row.error != null) {
                    TooltipArea(tooltip = {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(6.dp),
                        ) {
                            Text(
                                row.error,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(8.dp),
                            )
                        }
                    }) {
                        Text(
                            row.detail,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                } else {
                    Text(
                        row.detail,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
```

Y la píldora de la versión conocida se atenúa en lugar de desaparecer:

```kotlin
            // La fila no miente: en ERROR sigue enseñando lo ultimo que se supo, atenuado.
            VersionPill(row.local, highlight = false, dimmed = row.status == ImageStatus.ERROR)
```

En `VersionPill.kt`, la firma gana el parámetro y el color se atenúa:

```kotlin
private const val DIMMED_ALPHA = 0.45f

@Composable
fun VersionPill(
    version: String,
    highlight: Boolean,
    modifier: Modifier = Modifier,
    dimmed: Boolean = false,
) {
    val palette = statusColors(ImageStatus.PENDING, LocalIsDark.current)
    val background = if (highlight) palette.background else MaterialTheme.colorScheme.surfaceVariant
    val base = if (highlight) palette.foreground else MaterialTheme.colorScheme.onSurfaceVariant
    // Atenuar y no ocultar: el dato sigue siendo cierto, lo que ya no se sabe es si sigue vigente.
    val foreground = if (dimmed) base.copy(alpha = DIMMED_ALPHA) else base
```

El resto del composable no cambia.

Imports nuevos en `ImageRow.kt`: `androidx.compose.foundation.TooltipArea`, `androidx.compose.foundation.shape.RoundedCornerShape`, `androidx.compose.material3.Surface`.

- [ ] **Step 6: El tercer nivel — la bandeja**

En `ui/AppIconPainter.kt`, el dibujo se extrae a una función que acepta el color de las celdas, y aparece una segunda variante. Ambas viven en el mismo fichero para que no se les separe la geometría:

```kotlin
private val Alert = Color(0xFFE5484D)
private const val DOT_FACTOR = 0.16f

/** La marca: una cuadrícula 2×2 sobre fondo redondeado. Compartida por bandeja y ventana. */
object AppIconPainter : Painter() {
    override val intrinsicSize = Size(64f, 64f)

    override fun DrawScope.onDraw() = drawIcon(Accent, alert = false)
}

/**
 * La misma marca en rojo y con un punto en la esquina. Es el único nivel de error visible cuando
 * la ventana está cerrada, así que se distingue de un vistazo y sin leer nada.
 */
object AlertIconPainter : Painter() {
    override val intrinsicSize = Size(64f, 64f)

    override fun DrawScope.onDraw() = drawIcon(Alert, alert = true)
}

private fun DrawScope.drawIcon(cellColor: Color, alert: Boolean) {
    val side = size.minDimension
    drawRoundRect(
        color = Background,
        size = Size(side, side),
        cornerRadius = CornerRadius(side * CORNER_FACTOR),
    )

    val padding = side * PADDING_FACTOR
    val gap = side * GAP_FACTOR
    val cell = (side - padding * 2 - gap) / 2
    val positions = listOf(0f to 0f, 1f to 0f, 0f to 1f, 1f to 1f)

    positions.forEach { (column, row) ->
        drawRoundRect(
            color = cellColor,
            topLeft = Offset(padding + column * (cell + gap), padding + row * (cell + gap)),
            size = Size(cell, cell),
            cornerRadius = CornerRadius(cell * CELL_CORNER_FACTOR),
        )
    }

    if (alert) {
        val radius = side * DOT_FACTOR
        drawCircle(color = Alert, radius = radius, center = Offset(side - radius, radius))
    }
}
```

En `Main.kt`, el `Tray` elige icono según el estado. Como el `Tray` vive fuera de la ventana, necesita el estado aunque la ventana esté cerrada: crea el `ImagesViewModel` **una sola vez en `application { }`** y pásalo a `MainScreen`, en lugar de crearlo dentro:

```kotlin
        val viewModel = remember {
            ImagesViewModel(wiring.service, wiring.trackedImages, wiring.controller)
        }
        val state by viewModel.state.collectAsState()

        Tray(
            state = rememberTrayState(),
            icon = if (state.allFailing) AlertIconPainter else AppIconPainter,
            tooltip = if (state.allFailing) "ImageWatch — sin conexión" else "ImageWatch",
```

Con ello desaparece el `DisposableEffect` que cerraba el view model al cerrar la ventana: ahora vive lo que vive la aplicación, y se cierra en el `Item("Salir")` junto al controlador.

- [ ] **Step 7: Ejecutar y ver los tres niveles**

```bash
JAVA_HOME=~/.jdks/ms-25.0.4.1 IMAGE_NAMES=alpha-fail,beta-fail POLL_INTERVAL_SECONDS=10 ./gradlew run
```

Expected: las dos filas en `ERROR` con su mensaje en el tooltip, la barra roja con la antigüedad y el icono de bandeja en su variante de alerta. Después, con `IMAGE_NAMES=alpha,beta-fail`, la barra **no** aparece: un fallo aislado no es una caída del origen.

- [ ] **Step 8: Commit**

```bash
JAVA_HOME=~/.jdks/ms-25.0.4.1 ./gradlew spotlessApply && JAVA_HOME=~/.jdks/ms-25.0.4.1 ./gradlew check
git add -A
git commit -m "feat: completar los tres niveles de error

La fila conserva la ultima version conocida atenuada y ensena el mensaje en el
tooltip: una fila que se queda en blanco al fallar la consulta borra informacion
que si se tenia.

El aviso global dice cuanto hace de la ultima verificacion correcta, y esa marca
solo avanza cuando algo se verifico: si avanzara en cada ciclo diria 'hace diez
segundos' con el origen caido desde hace una hora.

La bandeja cambia de icono ante un fallo persistente, que es el unico nivel
visible cuando la ventana esta cerrada."
```

---

### Task 7: Toasts propios

Devuelve las notificaciones que la fase 4 se llevó por delante, pero como ventanas propias de Compose y no como globos del sistema. El adaptador implementa el `NotificationPort` que ya existe: el núcleo no se entera de que ha cambiado el canal.

Una sola ventana apila todos los toasts, en lugar de una ventana por toast. Con una ventana por toast habría que recalcular la posición de todas cada vez que una desaparece, y eso es exactamente el trabajo que un `Column` hace solo.

**Files:**
- Create: `src/main/kotlin/io/github/shizukajiku/imagewatch/ui/toast/ToastState.kt`
- Create: `src/main/kotlin/io/github/shizukajiku/imagewatch/ui/toast/ToastWindow.kt`
- Create: `src/main/kotlin/io/github/shizukajiku/imagewatch/ui/toast/ToastNotificationPort.kt`
- Create: `src/main/resources/icons/close.svg`
- Modify: `src/main/kotlin/io/github/shizukajiku/imagewatch/ui/components/SvgIcon.kt`
- Modify: `src/main/kotlin/io/github/shizukajiku/imagewatch/ui/images/ImagesViewModel.kt`
- Modify: `src/main/kotlin/io/github/shizukajiku/imagewatch/ui/images/ImagesScreen.kt`
- Modify: `src/main/kotlin/io/github/shizukajiku/imagewatch/ui/images/ImageRow.kt`
- Modify: `src/main/kotlin/io/github/shizukajiku/imagewatch/Main.kt`
- Test: `src/test/kotlin/io/github/shizukajiku/imagewatch/ui/toast/ToastStateTest.kt`

**Interfaces:**
- Consumes: `NotificationPort.notifyUpdates(List<ImageState>)`, `AppConfig.toastsEnabled()`, `AppConfig.toastDuration()`.
- Produces: `Toast(id: Long, title: String, body: String, imageName: String?)`; `ToastState()` con `toasts: StateFlow<List<Toast>>`, `show(updates: List<ImageState>)`, `dismiss(id: Long)`; `ToastNotificationPort(toasts: ToastState, config: () -> AppConfig) : NotificationPort`; `ApplicationScope.ToastLayer(toasts, durationSeconds, onDismiss, onView)`; `ImagesUiState.highlighted: String?` y `ImagesViewModel.highlight(name)` / `clearHighlight()`.

- [ ] **Step 1: Escribir el test que falla**

`src/test/kotlin/io/github/shizukajiku/imagewatch/ui/toast/ToastStateTest.kt`:

```kotlin
package io.github.shizukajiku.imagewatch.ui.toast

import io.github.shizukajiku.imagewatch.domain.ImageState
import io.github.shizukajiku.imagewatch.domain.ImageStatus
import io.github.shizukajiku.imagewatch.domain.Version
import java.time.Instant
import java.util.Optional
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun pending(name: String, from: String, to: String) = ImageState(
    name,
    Optional.of(Version(from)),
    Optional.of(Version(to)),
    "registry.local/$name",
    ImageStatus.PENDING,
    Optional.empty(),
    Instant.EPOCH,
)

class ToastStateTest {

    @Test
    fun `una imagen produce un toast con su transicion`() {
        val state = ToastState()

        state.show(listOf(pending("alpha", "1.0.0", "1.1.0")))

        val toast = state.toasts.value.single()
        assertEquals("alpha", toast.imageName)
        assertTrue(toast.body.contains("1.0.0"))
        assertTrue(toast.body.contains("1.1.0"))
    }

    @Test
    fun `dos imagenes producen dos toasts`() {
        val state = ToastState()

        state.show(listOf(pending("alpha", "1.0.0", "1.1.0"), pending("beta", "2.0.0", "2.1.0")))

        assertEquals(2, state.toasts.value.size)
    }

    @Test
    fun `tres o mas se resumen en uno solo`() {
        // Tres ventanas apiladas tapan media pantalla por algo que se lee de un vistazo en la
        // tabla. El resumen lleva a ella; el detalle vive alli.
        val state = ToastState()

        state.show(
            listOf(
                pending("alpha", "1.0.0", "1.1.0"),
                pending("beta", "2.0.0", "2.1.0"),
                pending("gamma", "3.0.0", "3.1.0"),
            ),
        )

        val toast = state.toasts.value.single()
        assertNull(toast.imageName, "El resumen no lleva a ninguna fila concreta")
        assertTrue(toast.title.contains("3"))
    }

    @Test
    fun `descartar quita solo el toast pedido`() {
        val state = ToastState()
        state.show(listOf(pending("alpha", "1.0.0", "1.1.0"), pending("beta", "2.0.0", "2.1.0")))
        val first = state.toasts.value.first()

        state.dismiss(first.id)

        assertEquals(1, state.toasts.value.size)
        assertEquals("beta", state.toasts.value.single().imageName)
    }
}
```

- [ ] **Step 2: Ejecutar para verificar que falla**

```bash
JAVA_HOME=~/.jdks/ms-25.0.4.1 ./gradlew test --tests "*ToastStateTest*"
```

Expected: FAIL — `ToastState` no existe.

- [ ] **Step 3: Escribir la cola de toasts**

`src/main/kotlin/io/github/shizukajiku/imagewatch/ui/toast/ToastState.kt`:

```kotlin
package io.github.shizukajiku.imagewatch.ui.toast

import io.github.shizukajiku.imagewatch.domain.ImageState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

/** Un aviso en pantalla. `imageName` es nulo cuando resume varias imágenes. */
data class Toast(
    val id: Long,
    val title: String,
    val body: String,
    val imageName: String?,
)

/** A partir de este número de imágenes se resume en lugar de apilar. */
private const val SUMMARY_THRESHOLD = 3
private const val ABSENT = "—"

/**
 * Cola de toasts. Vive fuera de la ventana principal: los avisos tienen que aparecer justo cuando
 * la ventana está cerrada, que es cuando el usuario no está mirando la tabla.
 */
class ToastState {

    private val sequence = AtomicLong()
    private val mutableToasts = MutableStateFlow<List<Toast>>(emptyList())
    val toasts: StateFlow<List<Toast>> = mutableToasts.asStateFlow()

    fun show(updates: List<ImageState>) {
        if (updates.isEmpty()) {
            return
        }
        val nuevos = if (updates.size >= SUMMARY_THRESHOLD) {
            listOf(summaryOf(updates))
        } else {
            updates.map(::toastOf)
        }
        mutableToasts.value = mutableToasts.value + nuevos
    }

    fun dismiss(id: Long) {
        mutableToasts.value = mutableToasts.value.filterNot { it.id == id }
    }

    private fun toastOf(image: ImageState) = Toast(
        id = sequence.incrementAndGet(),
        title = "Nueva versión de ${image.name()}",
        body = "${image.local().map { it.value() }.orElse(ABSENT)} → " +
            image.remote().map { it.value() }.orElse(ABSENT),
        imageName = image.name(),
    )

    private fun summaryOf(updates: List<ImageState>) = Toast(
        id = sequence.incrementAndGet(),
        title = "${updates.size} imágenes con nueva versión",
        body = updates.joinToString(", ") { it.name() },
        imageName = null,
    )
}
```

- [ ] **Step 4: Ejecutar los tests**

```bash
JAVA_HOME=~/.jdks/ms-25.0.4.1 ./gradlew test --tests "*ToastStateTest*"
```

Expected: PASS, los cuatro.

- [ ] **Step 5: Crear el adaptador del puerto**

`src/main/kotlin/io/github/shizukajiku/imagewatch/ui/toast/ToastNotificationPort.kt`:

```kotlin
package io.github.shizukajiku.imagewatch.ui.toast

import io.github.shizukajiku.imagewatch.application.NotificationPort
import io.github.shizukajiku.imagewatch.config.AppConfig
import io.github.shizukajiku.imagewatch.domain.ImageState

/**
 * Presentación implementando un puerto de la aplicación: el núcleo sigue avisando de transiciones
 * sin saber que ahora el canal es una ventana propia.
 *
 * La configuración llega como función y no como valor porque puede cambiar mientras la aplicación
 * corre: capturarla aquí congelaría el ajuste en el que estuviera al arrancar.
 */
class ToastNotificationPort(
    private val toasts: ToastState,
    private val config: () -> AppConfig,
) : NotificationPort {

    override fun notifyUpdates(updates: List<ImageState>) {
        if (!config().toastsEnabled()) {
            return
        }
        toasts.show(updates)
    }
}
```

- [ ] **Step 6: Crear el icono de cierre y registrarlo**

`src/main/resources/icons/close.svg`:

```xml
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="#9a9aa2" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
```

Y en `AppSvg`: `CLOSE("close"),`.

- [ ] **Step 7: Escribir la ventana de toasts**

`src/main/kotlin/io/github/shizukajiku/imagewatch/ui/toast/ToastWindow.kt`:

```kotlin
package io.github.shizukajiku.imagewatch.ui.toast

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import io.github.shizukajiku.imagewatch.ui.components.AppSvg
import io.github.shizukajiku.imagewatch.ui.components.SvgIcon
import java.awt.GraphicsEnvironment

private const val TOAST_WIDTH = 340
private const val LAYER_HEIGHT = 420
private const val MARGIN = 16

/**
 * Capa de toasts: una única ventana sin decoración anclada abajo a la derecha.
 *
 * Tres banderas no son negociables. `transparent` **exige** `undecorated`, o Compose lanza. Sin
 * `focusable = false` la ventana roba el foco mientras el usuario escribe en otra aplicación. Y
 * `alwaysOnTop` es lo único que hace que un aviso sirva de algo cuando la ventana principal está
 * cerrada y hay otra encima.
 *
 * La posición se calcula con `getMaximumWindowBounds`, que descuenta la barra de tareas. El tamaño
 * de pantalla crudo no lo hace y colocaría los toasts por debajo de ella.
 */
@Composable
fun ApplicationScope.ToastLayer(
    toasts: List<Toast>,
    durationSeconds: Long,
    onDismiss: (Long) -> Unit,
    onView: (String?) -> Unit,
) {
    if (toasts.isEmpty()) {
        return
    }
    val bounds = remember { GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds }
    val position = remember(bounds) {
        WindowPosition(
            x = (bounds.x + bounds.width - TOAST_WIDTH - MARGIN).dp,
            y = (bounds.y + bounds.height - LAYER_HEIGHT - MARGIN).dp,
        )
    }

    Window(
        onCloseRequest = {},
        state = rememberWindowState(position = position, width = TOAST_WIDTH.dp, height = LAYER_HEIGHT.dp),
        undecorated = true,
        transparent = true,
        alwaysOnTop = true,
        focusable = false,
        resizable = false,
        title = "ImageWatch — avisos",
    ) {
        LazyColumn(
            verticalArrangement = Arrangement.Bottom,
            modifier = Modifier.fillMaxSize(),
        ) {
            items(toasts, key = { it.id }) { toast ->
                // animateItem cierra el hueco cuando uno desaparece; sin el, los de abajo
                // saltarian de golpe.
                ToastCard(
                    toast = toast,
                    durationSeconds = durationSeconds,
                    onDismiss = { onDismiss(toast.id) },
                    onView = { onView(toast.imageName) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

@Composable
private fun ToastCard(
    toast: Toast,
    durationSeconds: Long,
    onDismiss: () -> Unit,
    onView: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var visible by remember { mutableStateOf(false) }
    var hovered by remember { mutableStateOf(false) }
    val progress = remember { Animatable(1f) }

    LaunchedEffect(toast.id) { visible = true }

    // El puntero encima pausa el descarte: cancelar el efecto detiene la animacion donde este, y
    // al salir se reanuda con el tiempo que quedaba, no con el total.
    LaunchedEffect(toast.id, hovered) {
        if (!hovered) {
            val remaining = (progress.value * durationSeconds * 1000).toInt()
            progress.animateTo(0f, tween(durationMillis = remaining, easing = LinearEasing))
            onDismiss()
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally { it } + fadeIn(),
        exit = slideOutHorizontally { it } + fadeOut(),
        modifier = modifier,
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 6.dp,
            modifier = Modifier
                .padding(6.dp)
                .width(TOAST_WIDTH.dp - 12.dp)
                .onPointerEvent(PointerEventType.Enter) { hovered = true }
                .onPointerEvent(PointerEventType.Exit) { hovered = false },
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 10.dp, end = 4.dp),
                ) {
                    Text(toast.title, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    IconButton(onDismiss) {
                        SvgIcon(AppSvg.CLOSE, MaterialTheme.colorScheme.onSurfaceVariant, Modifier.size(13.dp))
                    }
                }
                Text(
                    toast.body,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.weight(1f))
                    TextButton(onView) { Text("Ver") }
                }
                LinearProgressIndicator(
                    progress = { progress.value },
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.Transparent,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
```

- [ ] **Step 8: Resaltar la fila desde el toast**

En `ImagesViewModel.kt`, añade a `ImagesUiState`:

```kotlin
    /** Fila señalada desde un toast. Se limpia sola en la pantalla. */
    val highlighted: String? = null,
```

el campo y los dos métodos:

```kotlin
    private var highlighted: String? = null

    fun highlight(name: String) {
        highlighted = name
        recompute()
    }

    fun clearHighlight() {
        highlighted = null
        recompute()
    }
```

y `highlighted = highlighted,` en `recompute()`.

En `ImagesScreen.kt`, pasa `highlighted` a la fila y añade el olvido automático:

```kotlin
    LaunchedEffect(state.highlighted) {
        if (state.highlighted != null) {
            // Un resaltado permanente deja de señalar nada: al cuarto toast la tabla entera
            // estaria resaltada.
            delay(4000)
            onClearHighlight()
        }
    }
```

`ImagesScreen` gana el parámetro `onClearHighlight: () -> Unit`, y `ImageRow` el parámetro `highlighted: Boolean`, que pinta el fondo de la fila con `MaterialTheme.colorScheme.primaryContainer` mediante `Modifier.background(...)` cuando es cierto.

En la llamada a `ImagesScreen` dentro de `MainScreen` (`Main.kt`), añade las dos líneas que faltan:

```kotlin
                    onClearHighlight = viewModel::clearHighlight,
```

y en la construcción de cada fila dentro de `ImagesScreen`:

```kotlin
                    highlighted = row.name == state.highlighted,
```

Imports nuevos en `ImagesScreen.kt`: `androidx.compose.runtime.LaunchedEffect`, `kotlinx.coroutines.delay`.

- [ ] **Step 9: Cablearlo todo en `Main.kt`**

En `Wiring`, el puerto entra en el servicio. Añade el campo y pásalo:

```kotlin
    val toasts = ToastState()
    ...
        service =
            VersionPollingService(
                source,
                state,
                // La configuración se pasa como lambda y no como valor: puede cambiar mientras la
                // aplicación corre, y `config::value` capturaría la propiedad, no su lectura.
                listOf(ToastNotificationPort(toasts) { config.value }),
                trackedImages,
            )
```

Con ello, `ToastNotificationPort` se declara con la configuración como último parámetro, para que la lambda quede fuera del paréntesis.

En `application { }`, junto al `Tray`:

```kotlin
        val toasts by wiring.toasts.toasts.collectAsState()

        ToastLayer(
            toasts = toasts,
            durationSeconds = config.toastDuration().seconds,
            onDismiss = wiring.toasts::dismiss,
            onView = { name ->
                windowVisible = true
                name?.let(viewModel::highlight)
            },
        )
```

- [ ] **Step 10: Verlo**

```bash
JAVA_HOME=~/.jdks/ms-25.0.4.1 IMAGE_NAMES=alpha,beta POLL_INTERVAL_SECONDS=10 ./gradlew run
```

Con el origen simulado subiendo de versión cada 20 s, el segundo ciclo produce toasts. **Comprueba las cinco cosas**: entran deslizándose desde la derecha; la barra de progreso baja; el puntero encima la detiene; «Ver» abre la ventana con la fila resaltada; y con tres imágenes a la vez sale un resumen en lugar de tres ventanas.

- [ ] **Step 11: Commit**

```bash
JAVA_HOME=~/.jdks/ms-25.0.4.1 ./gradlew spotlessApply && JAVA_HOME=~/.jdks/ms-25.0.4.1 ./gradlew check
git add -A
git commit -m "feat: restaurar las notificaciones con toasts propios

Devuelve el aviso que la fase 4 se llevo al borrar el adaptador de Windows, pero
como ventana propia: el adaptador implementa el NotificationPort que ya existia,
asi que el nucleo no se entera de que cambio el canal.

Una sola ventana apila los toasts. Con una ventana por toast habria que
recalcular la posicion de todas cada vez que una desaparece, que es justo el
trabajo que hace solo un Column.

transparent exige undecorated, y sin focusable=false la ventana roba el foco
mientras el usuario escribe en otra aplicacion."
```

---

### Task 8: Sonido

`javax.sound.sampled` forma parte del JDK y reproduce WAV/PCM sin nada más: **no se añade ninguna dependencia**. El sonido no define un puerto en `application/`: es presentación, y un `NotificationPort` que además reprodujera audio mezclaría capas. Vive en `ui/sound/` y lo invocan tanto el adaptador de toasts como los controles de la interfaz.

**Files:**
- Create: `src/main/kotlin/io/github/shizukajiku/imagewatch/ui/sound/Sounds.kt`
- Create: `src/main/resources/sounds/{update,error,success,toggle}.wav`
- Create: `src/main/resources/sounds/NOTICE`
- Modify: `src/main/kotlin/io/github/shizukajiku/imagewatch/ui/toast/ToastNotificationPort.kt`
- Modify: `src/main/kotlin/io/github/shizukajiku/imagewatch/ui/images/ImagesViewModel.kt`
- Modify: `src/main/kotlin/io/github/shizukajiku/imagewatch/Main.kt`
- Test: `src/test/kotlin/io/github/shizukajiku/imagewatch/ui/sound/SoundsTest.kt`

**Interfaces:**
- Consumes: `AppConfig.soundsEnabled()`, `AppConfig.soundVolume()`.
- Produces: `enum class Sound { UPDATE, ERROR, SUCCESS, TOGGLE }`; `Sounds(enabled: () -> Boolean, volume: () -> Double, windowFocused: () -> Boolean)` con `preload()`, `play(sound: Sound)` y `close()`.

- [ ] **Step 0: PARADA OBLIGATORIA — aprobación de los WAV**

**No escribas código de esta tarea hasta que el usuario apruebe los cuatro ficheros.** El spec lo exige: ningún binario de audio entra en el repositorio sin aprobación previa.

Busca cuatro WAV de dominio público (CC0, sin atribución obligatoria) que encajen con la paleta —`update` claro y ascendente, `error` grave y distinto, `success` breve y suave, `toggle` un clic muy sutil—, comprueba que sean **WAV PCM 16 bits 44,1 kHz** y de menos de un segundo cada uno, y preséntaselos al usuario con su procedencia y su licencia. Espera su aprobación explícita.

Con los cuatro aprobados, colócalos en `src/main/resources/sounds/` y escribe `src/main/resources/sounds/NOTICE` con la procedencia y la licencia de cada uno, aunque la CC0 no lo exija.

- [ ] **Step 1: Escribir el test que falla**

`src/test/kotlin/io/github/shizukajiku/imagewatch/ui/sound/SoundsTest.kt`:

```kotlin
package io.github.shizukajiku.imagewatch.ui.sound

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SoundsTest {

    @Test
    fun `los cuatro recursos estan empaquetados`() {
        // Un recurso que falta es un fallo de empaquetado y no se nota al ejecutar, porque el
        // subsistema de sonido calla ante cualquier problema por diseno.
        Sound.entries.forEach { sound ->
            val stream = Sounds::class.java.getResourceAsStream(sound.resourcePath)
            assertNotNull(stream, "Falta ${sound.resourcePath}")
            stream.close()
        }
    }

    @Test
    fun `reproducir sin dispositivo de audio no lanza`() {
        // En integracion continua y por escritorio remoto no hay linea de audio. Una aplicacion
        // de escritorio no puede caerse porque la maquina no tenga altavoces.
        val sounds = Sounds(enabled = { true }, volume = { 1.0 }, windowFocused = { false })
        sounds.preload()

        Sound.entries.forEach { sounds.play(it) }
        sounds.close()

        assertTrue(true, "Llegar aquí sin excepción es el aserto")
    }

    @Test
    fun `no reproduce si la ventana tiene el foco`() {
        var reproducciones = 0
        val sounds = Sounds(
            enabled = { true },
            volume = { 1.0 },
            windowFocused = { true },
            onPlay = { reproducciones++ },
        )

        sounds.play(Sound.UPDATE)

        assertTrue(reproducciones == 0, "Si el usuario ya está mirando, el toast basta")
    }

    @Test
    fun `no reproduce si el sonido esta desactivado`() {
        var reproducciones = 0
        val sounds = Sounds(
            enabled = { false },
            volume = { 1.0 },
            windowFocused = { false },
            onPlay = { reproducciones++ },
        )

        sounds.play(Sound.UPDATE)

        assertTrue(reproducciones == 0)
    }
}
```

- [ ] **Step 2: Ejecutar para verificar que falla**

```bash
JAVA_HOME=~/.jdks/ms-25.0.4.1 ./gradlew test --tests "*SoundsTest*"
```

Expected: FAIL — `Sounds` y `Sound` no existen.

- [ ] **Step 3: Escribir el subsistema de sonido**

`src/main/kotlin/io/github/shizukajiku/imagewatch/ui/sound/Sounds.kt`:

```kotlin
package io.github.shizukajiku.imagewatch.ui.sound

import org.slf4j.LoggerFactory
import java.io.BufferedInputStream
import java.util.concurrent.ConcurrentHashMap
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip
import javax.sound.sampled.FloatControl
import kotlin.math.log10
import kotlin.math.max

private val LOG = LoggerFactory.getLogger(Sounds::class.java)

/** Atenuación base. Un aviso a volumen completo sobresalta. */
private const val BASE_ATTENUATION_DB = -12f

enum class Sound(private val file: String) {
    UPDATE("update"),
    ERROR("error"),
    SUCCESS("success"),
    TOGGLE("toggle"),
    ;

    val resourcePath: String get() = "/sounds/$file.wav"
}

/**
 * Los cuatro avisos sonoros y las tres reglas que los gobiernan.
 *
 * @param onPlay gancho de prueba: recibe el sonido que se va a reproducir justo antes de hacerlo.
 *   Existe porque los tests no pueden comprobar que algo sonó, solo que se decidió reproducirlo.
 */
class Sounds(
    private val enabled: () -> Boolean,
    private val volume: () -> Double,
    private val windowFocused: () -> Boolean,
    private val onPlay: (Sound) -> Unit = {},
) {
    private val clips = ConcurrentHashMap<Sound, Clip>()

    /**
     * Precarga en un hilo de fondo. `getClip()` y `open()` bloquean —unos 100 ms la primera vez— y
     * `start()` no: sin precarga, el primer aviso llega tarde y con un tirón visible en la
     * interfaz.
     */
    fun preload() {
        Thread.ofVirtual().name("sound-preload").start {
            Sound.entries.forEach { sound ->
                load(sound)?.let { clips[sound] = it }
            }
        }
    }

    fun play(sound: Sound) {
        // Regla 1: si el usuario ya está mirando la tabla, el toast basta. El sonido existe para
        // cuando no mira.
        if (windowFocused() || !enabled()) {
            return
        }
        onPlay(sound)
        val clip = clips[sound] ?: return
        // Regla 2: un Clip no puede sonar dos veces a la vez. Ante dos avisos seguidos se
        // reinicia en lugar de encolar, que es lo que un aviso debe hacer.
        clip.stop()
        clip.framePosition = 0
        applyVolume(clip)
        clip.start()
    }

    fun close() {
        clips.values.forEach { runCatching { it.close() } }
        clips.clear()
    }

    /**
     * Regla 3: degradación silenciosa. Por escritorio remoto, en máquinas sin dispositivo de audio
     * y en integración continua, esto lanza. Se registra una vez y el subsistema queda mudo.
     */
    private fun load(sound: Sound): Clip? = runCatching {
        val resource = requireNotNull(Sounds::class.java.getResourceAsStream(sound.resourcePath)) {
            "Falta el recurso ${sound.resourcePath}"
        }
        BufferedInputStream(resource).use { stream ->
            AudioSystem.getAudioInputStream(stream).use { audio ->
                AudioSystem.getClip().apply { open(audio) }
            }
        }
    }.onFailure {
        LOG.info("Sonido desactivado para {}: {}", sound, it.message)
    }.getOrNull()

    private fun applyVolume(clip: Clip) {
        if (!clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            return
        }
        val control = clip.getControl(FloatControl.Type.MASTER_GAIN) as FloatControl
        // El volumen del ajuste es lineal y el control va en decibelios: sin la conversión, la
        // mitad del deslizador no suena a la mitad.
        val decibels = (20 * log10(max(volume(), 0.0001))).toFloat() + BASE_ATTENUATION_DB
        control.value = decibels.coerceIn(control.minimum, control.maximum)
    }
}
```

- [ ] **Step 4: Ejecutar los tests**

```bash
JAVA_HOME=~/.jdks/ms-25.0.4.1 ./gradlew test --tests "*SoundsTest*"
```

Expected: PASS, los cuatro. El segundo pasa tanto con audio como sin él, que es justo lo que comprueba.

- [ ] **Step 5: Conectar los cuatro disparadores**

`ToastNotificationPort` gana el sonido:

```kotlin
class ToastNotificationPort(
    private val toasts: ToastState,
    private val sounds: Sounds,
    private val config: () -> AppConfig,
) : NotificationPort {

    override fun notifyUpdates(updates: List<ImageState>) {
        if (!config().toastsEnabled()) {
            return
        }
        toasts.show(updates)
        sounds.play(Sound.UPDATE)
    }
}
```

El nuevo parámetro va antes de la configuración para que la lambda siga siendo la última, y la llamada en `Wiring` queda `ToastNotificationPort(toasts, sounds) { config.value }`.

`ImagesViewModel` recibe `private val sounds: Sounds` como cuarto parámetro del constructor y lo usa en tres sitios:

- en `togglePolling()`, tras cambiar el estado: `sounds.play(Sound.TOGGLE)`;
- en `removeImage(name)` y en `saveName` cuando termina bien: `sounds.play(Sound.SUCCESS)`;
- en `onSnapshot`, cuando el ciclo pasa de tener alguna imagen verificada a no tener ninguna:

```kotlin
        // Solo en la transicion: repetir el sonido de error en cada ciclo mientras el origen
        // sigue caido es exactamente lo que hace que el usuario apague todos los sonidos.
        val ahoraTodoFalla = received.images().isNotEmpty() &&
            received.images().all { it.status() == ImageStatus.ERROR }
        if (ahoraTodoFalla && !allFailing) {
            sounds.play(Sound.ERROR)
        }
        allFailing = ahoraTodoFalla
```

con el campo `private var allFailing = false` sustituyendo al cálculo en línea de la Task 6.

`SettingsViewModel` no toca el sonido: quien guarda la configuración es `Wiring.applyConfig`, y es allí donde suena el `SUCCESS`, tras `configStore.save(candidate)`.

En `Main.kt`, `Wiring` construye el subsistema y lo precarga:

```kotlin
    /** Cierto mientras la ventana principal tiene el foco. Lo actualiza la propia ventana. */
    val windowFocused = MutableStateFlow(false)

    val sounds = Sounds(
        enabled = { config.value.soundsEnabled() },
        volume = { config.value.soundVolume() },
        windowFocused = { windowFocused.value },
    )
```

Ese `sounds` hay que pasárselo al `ImagesViewModel` que `application { }` construye —`ImagesViewModel(wiring.service, wiring.trackedImages, wiring.controller, wiring.sounds)`— y al `ToastNotificationPort` del `Wiring`. En `ImagesViewModelTest`, el `fixture` construye el view model: dale un subsistema mudo, que además documenta que los tests no dependen de que haya audio:

```kotlin
    private val silencioso = Sounds(enabled = { false }, volume = { 0.0 }, windowFocused = { true })
```

Con `sounds.preload()` al final de `init`, `sounds.close()` junto a `controller.close()` en el «Salir», y dentro de la `Window` principal:

```kotlin
                val focused = LocalWindowInfo.current.isWindowFocused
                LaunchedEffect(focused) { wiring.windowFocused.value = focused }
```

Import nuevo: `androidx.compose.ui.platform.LocalWindowInfo`.

- [ ] **Step 6: Escucharlo**

```bash
JAVA_HOME=~/.jdks/ms-25.0.4.1 IMAGE_NAMES=alpha,beta-fail POLL_INTERVAL_SECONDS=10 ./gradlew run
```

Expected, **con la ventana cerrada o en segundo plano**: suena `update` con el toast y `error` la primera vez que todo falla, no en cada ciclo. Con la ventana enfocada no suena nada. Guardar ajustes y parar el sondeo suenan siempre, porque son respuesta a una acción del usuario.

- [ ] **Step 7: Commit**

```bash
JAVA_HOME=~/.jdks/ms-25.0.4.1 ./gradlew spotlessApply && JAVA_HOME=~/.jdks/ms-25.0.4.1 ./gradlew check
git add -A
git commit -m "feat: anadir los cuatro avisos sonoros

javax.sound.sampled viene en el JDK: no se anade ninguna dependencia. El sonido
vive en ui/ y no como puerto de aplicacion, porque es presentacion.

Tres reglas de comportamiento: no suena con la ventana enfocada -si el usuario
mira, el toast basta-, nunca se solapan dos reproducciones, y si la maquina no
tiene linea de audio el subsistema queda mudo sin caerse.

Los clips se precargan en un hilo de fondo: open() bloquea unos 100 ms y sin
precarga el primer aviso llega tarde y con tiron."
```

---

### Task 9: Instalador de Windows

El plugin de Compose ya envuelve `jpackage`; no hay dependencia nueva que añadir. La versión del paquete se declara aparte de la del proyecto: MSI exige `MAYOR.MENOR.PARCHE` con mayor mayor que cero, y `1.0-SNAPSHOT` no lo cumple.

**Files:**
- Modify: `build.gradle.kts`

**Interfaces:**
- Consumes: `application.mainClass` ya configurado.
- Produces: la tarea `packageMsi`.

- [ ] **Step 1: Generar el identificador de actualización**

```bash
powershell -Command "[guid]::NewGuid().ToString()"
```

Anótalo: tiene que ser **fijo para siempre**. Si cambia entre versiones, Windows trata la nueva como un producto distinto y el usuario acaba con dos ImageWatch instalados.

- [ ] **Step 2: Declarar la distribución**

En `build.gradle.kts`, tras el bloque `application { }`:

```kotlin
compose.desktop {
    application {
        mainClass = "io.github.shizukajiku.imagewatch.MainKt"
        // Skiko carga su libreria nativa con System.load, que JDK 25 considera restringido. El
        // instalador necesita el mismo flag que `./gradlew run`: si no, la aplicacion instalada
        // avisa por consola en cada arranque.
        jvmArgs += "--enable-native-access=ALL-UNNAMED"

        nativeDistributions {
            targetFormats(TargetFormat.Msi)
            packageName = "ImageWatch"
            // Aparte de la version del proyecto a proposito: MSI exige MAYOR.MENOR.PARCHE con
            // mayor > 0, y "1.0-SNAPSHOT" no lo es.
            packageVersion = "1.0.0"
            description = "Vigila las versiones de imágenes de contenedor publicadas en un registry"
            vendor = "ShizukaJiku"

            windows {
                menu = true
                shortcut = true
                dirChooser = true
                // Fijo para siempre: si cambia, Windows instala un segundo ImageWatch en lugar
                // de actualizar el que ya hay.
                upgradeUuid = "<el GUID del paso 1>"
            }
        }
    }
}
```

Import al principio del fichero: `import org.jetbrains.compose.desktop.application.dsl.TargetFormat`.

- [ ] **Step 3: Construir el instalador**

```bash
JAVA_HOME=~/.jdks/ms-25.0.4.1 ./gradlew packageMsi
```

Expected: un `.msi` en `build/compose/binaries/main/msi/`. Si `jpackage` se queja de WiX, instálalo (`winget install WiXToolset.WiXToolset`) y repite: el plugin lo necesita para MSI, aunque no sea una dependencia del proyecto.

- [ ] **Step 4: Probar la instalación**

Instala el MSI, arranca desde el menú de inicio y comprueba que la bandeja aparece, que el estado sigue en `~/.notifier/` y que no hay avisos de acceso nativo en el registro. Desinstálalo después.

- [ ] **Step 5: Commit**

```bash
JAVA_HOME=~/.jdks/ms-25.0.4.1 ./gradlew spotlessApply && JAVA_HOME=~/.jdks/ms-25.0.4.1 ./gradlew check
git add -A
git commit -m "build: generar el instalador de Windows

El plugin de Compose ya envuelve jpackage: no hay dependencia nueva.

La version del paquete se declara aparte de la del proyecto porque MSI exige
MAYOR.MENOR.PARCHE con mayor > 0, y 1.0-SNAPSHOT no lo cumple. El upgradeUuid
queda fijo: si cambiara, Windows instalaria un segundo ImageWatch en lugar de
actualizar el que ya hay."
```

---

### Task 10: Cierre de la fase

- [ ] **Step 1: Batería completa**

```bash
JAVA_HOME=~/.jdks/ms-25.0.4.1 ./gradlew clean check koverVerify
```

Expected: BUILD SUCCESSFUL. Si `koverVerify` falla, informa del porcentaje real antes de tocar el umbral: bajarlo para que pase es falsear la medida.

- [ ] **Step 2: Comprobar que no queda deuda declarada**

```bash
grep -rn "javax.swing\|java.awt.SystemTray\|flatlaf" src/main/ && echo "QUEDAN RESTOS" || echo "sin restos"
grep -rn "println(" src/main/ && echo "QUEDAN PRINTLN" || echo "sin println"
```

Expected: `sin restos` y `sin println`. `java.awt.GraphicsEnvironment` sí aparece —lo usa la capa de toasts para descontar la barra de tareas— y es correcto: no es interfaz Swing, es geometría de pantalla.

- [ ] **Step 3: Actualizar el README**

Cambios concretos:

- **Retira la sección «Regresión temporal: sin notificaciones»** entera: esta fase la cierra.
- En la tabla de órdenes, añade `| `./gradlew packageMsi` | Genera el instalador de Windows en `build/compose/binaries/` |`.
- En la tabla de configuración, sustituye la fila de `TEAMS_ENABLED` por las cinco variables nuevas —`THEME`, `TOASTS_ENABLED`, `TOAST_SECONDS`, `SOUNDS_ENABLED`, `SOUND_VOLUME`— y **explica que ahora solo siembran**: la fuente de verdad pasa a ser `~/.notifier/config.json`, editable desde la pantalla de ajustes.
- En «Dónde vive el estado», añade la fila `| `config.json` | Configuración editable desde la pantalla de ajustes |`.
- Añade una sección corta sobre el sonido: los cuatro avisos, que no suenan con la ventana enfocada y que se desactivan desde ajustes.

- [ ] **Step 4: Barrido de denylist**

```bash
while IFS= read -r p; do
  [ -z "$p" ] && continue
  case "$p" in \#*) continue ;; esac
  if git grep -qiE -- "$p" HEAD 2>/dev/null; then echo "RESIDUO: $p"; fi
done < .denylist.local
echo "barrido completo"
```

Expected: ningún `RESIDUO`. Presta atención especial al `NOTICE` de los sonidos: es texto traído de fuera y es lo último que entró al árbol.

- [ ] **Step 5: Commit e integración**

```bash
git add -A
git commit -m "docs: actualizar el README para la fase 5"
git push -u origin feature/fase-5-ajustes
```

Usa la skill `superpowers:finishing-a-development-branch`.

---

## Criterio de finalización

- [ ] `./gradlew clean check koverVerify` en verde.
- [ ] Los tests del núcleo pasan; los únicos cambios en `application/` son `ConfigStore` y `onPollStarted`.
- [ ] La configuración sobrevive a reiniciar y se edita desde la pantalla de ajustes.
- [ ] Cambiar URL o modo simulación surte efecto **sin reiniciar**.
- [ ] Los tres niveles de error se ven: fila, aviso global y bandeja.
- [ ] Las siete animaciones son visibles, incluidas la 4 y la 7 que la fase 4 dejó fuera.
- [ ] Los cuatro sonidos suenan según sus tres reglas, y la aplicación arranca igual sin dispositivo de audio.
- [ ] `packageMsi` produce un instalador que arranca.
- [ ] Barrido de denylist limpio.

## Fuera de esta fase

- **Integración real con Teams.** El vestigio se elimina en la Task 1 y no vuelve.
- **Endurecimiento de TLS.** Se mantiene el comportamiento actual por decisión explícita del diseño.
- **Notificaciones nativas del sistema.** Los toasts son propios, y es lo que el diseño pide.
- **Integración continua.** Se valorará una vez estabilizado el build de Gradle.
- **detekt.** Descartado en la fase 4: la última versión estable no parsea la versión del JDK del proyecto. Se reconsiderará con detekt 2.x.
