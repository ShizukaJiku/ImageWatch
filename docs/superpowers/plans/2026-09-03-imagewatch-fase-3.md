# ImageWatch — Plan de implementación, Fase 3: eventos y núcleo

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Que la ventana abierta se actualice sola cuando cambia el estado, que el fallo de una imagen no impida ver las demás, y que las notificaciones dejen de repetirse en cada ciclo — todo ello con la interfaz Swing actual, antes de tocar Compose.

**Architecture:** El núcleo permanece en Java puro y sin coroutines: `VersionPollingService` publica un `PollSnapshot` inmutable a los `PollListener` registrados al terminar cada ciclo. La interfaz se suscribe y repinta. El puerto de origen pasa a devolver un resultado por imagen, de modo que un fallo aislado deja de tumbar el ciclo entero. El vocabulario del dominio se independiza del formato de cable del proveedor.

**Tech Stack:** Java 25 (hilos virtuales) · Gradle 9.7.1 · JUnit 5 · AssertJ · SLF4J + logback · `com.sun.net.httpserver` (incluido en el JDK) para los tests de HTTP

**Spec:** `docs/superpowers/specs/2026-09-03-imagewatch-design.md`

**Plan anterior:** `docs/superpowers/plans/2026-09-03-imagewatch-fases-0-2.md` (completado)

## Global Constraints

- **Paquete raíz:** `io.github.shizukajiku.imagewatch`.
- **Java 25**, toolchain declarado en `gradle/libs.versions.toml` como `java = "25"`.
- **Ningún identificador corporativo** puede entrar en el repositorio. El hook `pre-commit` lo verifica; no lo desactives.
- **Este plan no toca Compose ni Kotlin.** La interfaz sigue siendo Swing y se elimina entera en la Fase 4. Las adaptaciones a la interfaz son las mínimas para demostrar el tiempo real, no un rediseño.
- **El dominio no lleva anotaciones de serialización.** El formato de cable vive en la capa de infraestructura.
- **Todo cambio de comportamiento va precedido de su test.** Escribe el test, compruébalo en rojo, implementa, compruébalo en verde.
- **`./gradlew check` en verde al cerrar cada tarea.** Si spotless se queja, `./gradlew spotlessApply`.
- Mensajes de commit en formato convencional.

---

## Estructura de ficheros

### Se crean

| Fichero | Responsabilidad |
|---|---|
| `domain/ImageRelease.java` | Publicación de una imagen: nombre, referencia OCI, fecha de publicación en el origen. Sustituye a `Product`. |
| `domain/ImageStatus.java` | `OK`, `PENDING`, `UNKNOWN`, `ERROR`. |
| `domain/ImageState.java` | Estado calculado de una imagen vigilada: versiones, estado, error, momento de la verificación. |
| `domain/PollSnapshot.java` | Fotografía inmutable de todas las imágenes tras un ciclo. |
| `application/ImageResult.java` | Resultado por imagen: la publicación o el error. Permite aislar fallos. |
| `application/PollListener.java` | Puerto de salida por el que el núcleo publica cada snapshot. |
| `infrastructure/remote/ReleaseDto.java` | Formato de cable del origen. Aquí viven las anotaciones de Jackson. |
| `src/main/resources/logback.xml` | Consola y fichero rotativo. |
| Tests para todo lo anterior más `PollingController`. |

### Se renombran

| Antes | Después | Motivo |
|---|---|---|
| `domain/Product` | `domain/ImageRelease` | El dominio es de imágenes, no de productos. |
| `Product.lastRelease` | `ImageRelease.reference` | `registry/nombre:tag` es una *referencia* OCI. |
| `Product.updateTime` | `ImageRelease.publishedAt` | Es la marca del origen, no la de verificación local. |
| `application/ProductSource` | `application/ImageSource` | |
| `application/ProductStateStore` | `application/ImageStateStore` | |
| `infrastructure/persistence/JsonProductStateStore` | `JsonImageStateStore` | |
| `infrastructure/remote/SimulatedProductSource` | `SimulatedImageSource` | |
| `infrastructure/remote/HttpProductSource` | `HttpImageSource` | |

### Se eliminan

| Fichero | Motivo |
|---|---|
| `domain/ImageUpdate.java` | Sustituido por `ImageState`, que además cubre los estados de error y sin verificar. |

---

### Task 1: Rama de trabajo

**Files:** ninguno

- [ ] **Step 1: Comprobar que la fase anterior está cerrada y sincronizada**

```bash
git status -sb | head -1
./gradlew check
```

Expected: `## main...origin/main` sin divergencias, y `BUILD SUCCESSFUL` con 10 tests.

- [ ] **Step 2: Crear la rama**

```bash
git switch -c feature/fase-3-eventos
```

A partir de la Fase 3 hay historial que proteger, así que cada fase va en su rama. La Fase 0 fue directa a `main` porque creaba el repositorio y no había de dónde ramificar.

---

### Task 2: Cobertura de `PollingController`

Se hace primero porque no depende de nada y cierra el mayor hueco de cobertura actual: la clase mantiene estado sincronizado y no tiene un solo test.

**Files:**
- Create: `src/test/java/io/github/shizukajiku/imagewatch/application/PollingControllerTest.java`

**Interfaces:**
- Consumes: `PollingController(VersionPollingService, Duration)` tal como existe hoy.

- [ ] **Step 1: Escribir los tests**

```java
package io.github.shizukajiku.imagewatch.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class PollingControllerTest {

  @Test
  void startsStopped() {
    try (var controller = new PollingController(null, Duration.ofSeconds(30))) {
      assertThat(controller.status()).isEqualTo(PollingController.Status.STOPPED);
      assertThat(controller.interval()).isEqualTo(Duration.ofSeconds(30));
    }
  }

  @Test
  void rejectsIntervalsBelowOneSecond() {
    assertThatThrownBy(() -> new PollingController(null, Duration.ofMillis(500)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new PollingController(null, Duration.ZERO))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new PollingController(null, Duration.ofSeconds(-1)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void updatingTheIntervalWhileStoppedKeepsItStopped() {
    try (var controller = new PollingController(null, Duration.ofSeconds(30))) {
      controller.updateInterval(Duration.ofSeconds(5));

      assertThat(controller.interval()).isEqualTo(Duration.ofSeconds(5));
      assertThat(controller.status()).isEqualTo(PollingController.Status.STOPPED);
    }
  }

  @Test
  void stopIsIdempotent() {
    try (var controller = new PollingController(null, Duration.ofSeconds(30))) {
      controller.stop();
      controller.stop();

      assertThat(controller.status()).isEqualTo(PollingController.Status.STOPPED);
    }
  }

  @Test
  void startAfterCloseIsIgnored() {
    var controller = new PollingController(null, Duration.ofSeconds(30));
    controller.close();

    controller.start();

    assertThat(controller.status()).isEqualTo(PollingController.Status.STOPPED);
  }

  @Test
  void closeIsIdempotent() {
    var controller = new PollingController(null, Duration.ofSeconds(30));

    controller.close();
    controller.close();

    assertThat(controller.status()).isEqualTo(PollingController.Status.STOPPED);
  }
}
```

Se pasa `null` como servicio a propósito: ninguno de estos tests arranca el planificador, así que nunca se desreferencia. Probar el arranque real exigiría esperas y haría los tests lentos y frágiles; ese camino queda cubierto por los tests de `VersionPollingService`, que invocan `poll()` de forma directa.

- [ ] **Step 2: Ejecutar**

```bash
./gradlew test --tests "*PollingControllerTest*"
```

Expected: los 6 pasan. Son tests de caracterización: fijan el comportamiento actual, que hasta ahora no estaba verificado por nada.

- [ ] **Step 3: Commit**

```bash
./gradlew spotlessApply && ./gradlew check
git add src/test/java/io/github/shizukajiku/imagewatch/application/PollingControllerTest.java
git commit -m "test: cubrir el ciclo de vida de PollingController

Mantiene estado sincronizado y no tenía un solo test. Tests de
caracterización antes de tocar el núcleo en esta misma fase."
```

---

### Task 3: Independizar el dominio del formato de cable

Renombrado de vocabulario y extracción del DTO. **Sin cambios de comportamiento.**

**Files:**
- Create: `domain/ImageRelease.java`, `infrastructure/remote/ReleaseDto.java`
- Delete: `domain/Product.java`
- Modify: `application/ProductSource.java` → `ImageSource.java`, `application/ProductStateStore.java` → `ImageStateStore.java`, los tres adaptadores, `VersionPollingService`, `ImageUpdate`, `Main`, `ImageManagerFrame`, `NotificationDemoMain`, y los tests existentes.

**Interfaces:**
- Produces: `ImageRelease(String name, String reference, LocalDateTime publishedAt)`; `ReleaseDto` con las anotaciones de Jackson y los métodos `toDomain()` / `fromDomain(ImageRelease)`.

- [ ] **Step 1: Crear `ImageRelease`**

```java
package io.github.shizukajiku.imagewatch.domain;

import java.time.LocalDateTime;

/**
 * Una publicación concreta de una imagen. {@code reference} es la referencia OCI completa
 * ({@code registry/nombre:tag}) y {@code publishedAt} es la marca de tiempo que declara el
 * origen remoto, no el momento en que se verificó localmente.
 */
public record ImageRelease(String name, String reference, LocalDateTime publishedAt) {}
```

Sin anotaciones de serialización: el dominio no conoce el formato de cable.

- [ ] **Step 2: Crear `ReleaseDto`**

```java
package io.github.shizukajiku.imagewatch.infrastructure.remote;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.github.shizukajiku.imagewatch.domain.ImageRelease;
import java.time.LocalDateTime;

/** Formato de cable del origen remoto. Mantiene sus nombres de campo fuera del dominio. */
public record ReleaseDto(
    @JsonAlias("productName") String name,
    @JsonAlias("lastRelease") String lastRelease,
    @JsonAlias("update_time") LocalDateTime updateTime) {

  public ImageRelease toDomain() {
    return new ImageRelease(name, lastRelease, updateTime);
  }
}
```

Las anotaciones siguen en `com.fasterxml.jackson.annotation`: Jackson 3 las mantiene ahí de forma deliberada, como se verificó en la spec.

- [ ] **Step 3: Renombrar los tipos restantes**

Renombra fichero y contenido en cada caso:

| Fichero | Nuevo nombre | Cambios internos |
|---|---|---|
| `application/ProductSource.java` | `ImageSource.java` | `List<ImageRelease> findByNames(List<String> names)` |
| `application/ProductStateStore.java` | `ImageStateStore.java` | `Optional<ImageRelease> find(String)`, `void save(List<ImageRelease>)` |
| `infrastructure/persistence/JsonProductStateStore.java` | `JsonImageStateStore.java` | Lee y escribe `ImageRelease[]` |
| `infrastructure/remote/HttpProductSource.java` | `HttpImageSource.java` | Deserializa `ReleaseDto` y devuelve `dto.toDomain()` |
| `infrastructure/remote/SimulatedProductSource.java` | `SimulatedImageSource.java` | Construye `ImageRelease` |

En `ImageUpdate`, `VersionPollingService`, `Main`, `ImageManagerFrame` y `NotificationDemoMain`, sustituye `Product` por `ImageRelease`, `product()` por `release()`, `lastRelease()` por `reference()` y `updateTime()` por `publishedAt()`.

Borra `domain/Product.java`.

- [ ] **Step 4: Cambiar el nombre del fichero de estado local**

En `Main.java`, el almacén de estado apuntaba a `local-products.json`. El esquema del fichero cambia con este renombrado (`lastRelease` pasa a `reference`, `updateTime` a `publishedAt`), así que un fichero antiguo se deserializaría con campos nulos y reventaría al parsear la versión.

Cambia la ruta a `images.json`:

```java
var state = new JsonImageStateStore(config.stateFile());
```

y en `AppConfig`, el valor por defecto:

```java
home.resolve(".notifier/images.json").toString())),
```

Un nombre nuevo evita escribir código de migración para un fichero que es solo una caché: si no existe, el primer ciclo lo reconstruye.

- [ ] **Step 5: Actualizar los tests existentes**

En `VersionPollingServiceTest`, sustituye `Product` por `ImageRelease` y los nombres de accesor. La estructura de los tests no cambia.

- [ ] **Step 6: Verificar que no hay cambio de comportamiento**

```bash
./gradlew spotlessApply && ./gradlew check
```

Expected: los 16 tests pasan (10 previos + 6 de `PollingControllerTest`). Esta tarea es un renombrado: si algún test cambia de resultado, es que se coló un cambio de comportamiento.

- [ ] **Step 7: Comprobar que la aplicación sigue arrancando**

```bash
./gradlew run
```

Expected: arranca con el origen simulado y muestra las cuatro filas. Ciérrala desde el menú de la bandeja.

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "refactor: independizar el dominio del formato de cable del origen

El record de dominio llevaba anotaciones de Jackson con los nombres de campo
del proveedor. Se separa en ImageRelease (dominio, sin anotaciones) y
ReleaseDto (infraestructura, con ellas).

Vocabulario alineado con OCI: reference en lugar de lastRelease, y publishedAt
en lugar de updateTime, que es la marca del origen y no la de verificación.

El fichero de estado local pasa a images.json: el esquema cambia y es una
caché, así que un nombre nuevo evita código de migración."
```

---

### Task 4: Tipos de estado del dominio

**Files:**
- Create: `domain/ImageStatus.java`, `domain/ImageState.java`, `domain/PollSnapshot.java`
- Create: `src/test/java/io/github/shizukajiku/imagewatch/domain/PollSnapshotTest.java`

**Interfaces:**
- Produces:
  - `enum ImageStatus { OK, PENDING, UNKNOWN, ERROR }`
  - `record ImageState(String name, Optional<Version> local, Optional<Version> remote, String registry, ImageStatus status, Optional<String> error, Instant lastCheckedAt)` con las factorías estáticas `unknown(String, Instant)` y `failed(String, String, Instant)`.
  - `record PollSnapshot(List<ImageState> images, Instant at)` con `find(String): Optional<ImageState>` y `pending(): List<ImageState>`, y una constante `EMPTY`.

- [ ] **Step 1: Escribir el test de `PollSnapshot`**

```java
package io.github.shizukajiku.imagewatch.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PollSnapshotTest {

  private static final Instant NOW = Instant.parse("2026-09-03T10:00:00Z");

  @Test
  void findsAnImageByName() {
    var snapshot = new PollSnapshot(List.of(ok("alpha"), ok("beta")), NOW);

    assertThat(snapshot.find("beta")).map(ImageState::name).contains("beta");
    assertThat(snapshot.find("ausente")).isEmpty();
  }

  @Test
  void pendingReturnsOnlyImagesWithANewerRemoteVersion() {
    var snapshot = new PollSnapshot(List.of(ok("alpha"), pending("beta"), ok("gamma")), NOW);

    assertThat(snapshot.pending()).extracting(ImageState::name).containsExactly("beta");
  }

  @Test
  void theImageListIsDefensivelyCopied() {
    var mutable = new java.util.ArrayList<ImageState>();
    mutable.add(ok("alpha"));
    var snapshot = new PollSnapshot(mutable, NOW);

    mutable.clear();

    assertThat(snapshot.images()).hasSize(1);
    assertThatThrownBy(() -> snapshot.images().add(ok("beta")))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void anErrorStateCarriesItsMessageAndNoVersions() {
    var state = ImageState.failed("alpha", "HTTP 503", NOW);

    assertThat(state.status()).isEqualTo(ImageStatus.ERROR);
    assertThat(state.error()).contains("HTTP 503");
    assertThat(state.remote()).isEmpty();
    assertThat(state.lastCheckedAt()).isEqualTo(NOW);
  }

  @Test
  void anUnknownStateHasNeitherVersionsNorError() {
    var state = ImageState.unknown("alpha", NOW);

    assertThat(state.status()).isEqualTo(ImageStatus.UNKNOWN);
    assertThat(state.error()).isEmpty();
    assertThat(state.local()).isEmpty();
    assertThat(state.remote()).isEmpty();
  }

  private static ImageState ok(String name) {
    return new ImageState(
        name,
        Optional.of(new Version("1.0.0")),
        Optional.of(new Version("1.0.0")),
        "registry.local/" + name,
        ImageStatus.OK,
        Optional.empty(),
        NOW);
  }

  private static ImageState pending(String name) {
    return new ImageState(
        name,
        Optional.of(new Version("1.0.0")),
        Optional.of(new Version("2.0.0")),
        "registry.local/" + name,
        ImageStatus.PENDING,
        Optional.empty(),
        NOW);
  }
}
```

- [ ] **Step 2: Ejecutar y comprobar que no compila**

```bash
./gradlew test --tests "*PollSnapshotTest*"
```

Expected: FALLA la compilación — `ImageStatus`, `ImageState` y `PollSnapshot` no existen todavía.

- [ ] **Step 3: Crear `ImageStatus`**

```java
package io.github.shizukajiku.imagewatch.domain;

/** Situación de una imagen vigilada tras el último ciclo de verificación. */
public enum ImageStatus {
  /** La versión local coincide con la remota, o es más reciente. */
  OK,
  /** El origen publica una versión más reciente que la conocida localmente. */
  PENDING,
  /** Todavía no se ha verificado, o no hay versión local con la que comparar. */
  UNKNOWN,
  /** La última verificación de esta imagen falló. */
  ERROR
}
```

- [ ] **Step 4: Crear `ImageState`**

```java
package io.github.shizukajiku.imagewatch.domain;

import java.time.Instant;
import java.util.Optional;

/**
 * Estado calculado de una imagen vigilada. Reemplaza a la representación privada que vivía
 * dentro de la capa de presentación, de modo que la interfaz deja de contener lógica de dominio.
 *
 * <p>{@code lastCheckedAt} es el momento de la verificación local, no la marca de tiempo que
 * declara el origen.
 */
public record ImageState(
    String name,
    Optional<Version> local,
    Optional<Version> remote,
    String registry,
    ImageStatus status,
    Optional<String> error,
    Instant lastCheckedAt) {

  public static ImageState unknown(String name, Instant at) {
    return new ImageState(
        name, Optional.empty(), Optional.empty(), "—", ImageStatus.UNKNOWN, Optional.empty(), at);
  }

  public static ImageState failed(String name, String message, Instant at) {
    return new ImageState(
        name,
        Optional.empty(),
        Optional.empty(),
        "—",
        ImageStatus.ERROR,
        Optional.of(message),
        at);
  }
}
```

- [ ] **Step 5: Crear `PollSnapshot`**

```java
package io.github.shizukajiku.imagewatch.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Fotografía inmutable de todas las imágenes vigiladas al terminar un ciclo. */
public record PollSnapshot(List<ImageState> images, Instant at) {

  public static final PollSnapshot EMPTY = new PollSnapshot(List.of(), Instant.EPOCH);

  public PollSnapshot {
    images = List.copyOf(images);
  }

  public Optional<ImageState> find(String name) {
    return images.stream().filter(image -> image.name().equals(name)).findFirst();
  }

  public List<ImageState> pending() {
    return images.stream().filter(image -> image.status() == ImageStatus.PENDING).toList();
  }
}
```

`List.copyOf` en el constructor compacto es lo que hace que el snapshot pueda cruzar hilos sin sincronización: quien lo publica no puede modificarlo después.

- [ ] **Step 6: Ejecutar**

```bash
./gradlew test --tests "*PollSnapshotTest*"
```

Expected: los 5 pasan.

- [ ] **Step 7: Commit**

```bash
./gradlew spotlessApply && ./gradlew check
git add -A
git commit -m "feat: añadir los tipos de estado del dominio

ImageState reemplaza a la representación privada que vivía dentro de la
ventana, con dos añadidos que aquella no tenía: estado de error por imagen y
lastCheckedAt, que es el momento de la verificación local y no la marca de
tiempo del origen.

PollSnapshot copia su lista en el constructor compacto: es lo que le permite
cruzar hilos sin sincronización."
```

---

### Task 5: Aislar los fallos por imagen

Hoy `findByNames` lanza en la primera imagen que falla, `poll()` atrapa y escribe en `stderr`, y **ninguna** imagen se actualiza.

**Files:**
- Create: `application/ImageResult.java`
- Modify: `application/ImageSource.java`, `HttpImageSource.java`, `SimulatedImageSource.java`, `VersionPollingService.java`
- Modify: `src/test/java/.../application/VersionPollingServiceTest.java`

**Interfaces:**
- Produces: `record ImageResult(String name, Optional<ImageRelease> release, Optional<String> error)` con las factorías `found(ImageRelease)` y `failed(String, String)`; `ImageSource.findByNames` pasa a devolver `List<ImageResult>`.

- [ ] **Step 1: Escribir el test del aislamiento**

Añade a `VersionPollingServiceTest`, y actualiza `FakeImageSource` para que pueda fallar en una imagen concreta:

```java
  @Test
  void oneFailingImageDoesNotPreventTheOthersFromUpdating() {
    var names = new FakeTrackedImageStore(List.of("alpha", "beta"));
    var state = new FakeImageStateStore();
    state.seed("beta", "registry.local/beta:1.0.0");
    var source = new FakeImageSource(Map.of("beta", "registry.local/beta:2.0.0"));
    source.failOn("alpha", "HTTP 503");
    var notifications = new RecordingNotificationPort();
    var service = new VersionPollingService(source, state, List.of(notifications), names);

    service.poll();

    var snapshot = service.lastSnapshot();
    assertThat(snapshot.find("alpha"))
        .hasValueSatisfying(
            image -> {
              assertThat(image.status()).isEqualTo(ImageStatus.ERROR);
              assertThat(image.error()).contains("HTTP 503");
            });
    assertThat(snapshot.find("beta"))
        .hasValueSatisfying(image -> assertThat(image.status()).isEqualTo(ImageStatus.PENDING));
  }
```

- [ ] **Step 2: Ejecutar y comprobar que falla**

```bash
./gradlew test --tests "*VersionPollingServiceTest*"
```

Expected: no compila — no existen `ImageResult.failOn`, `lastSnapshot()` ni `ImageStatus` en este contexto.

- [ ] **Step 3: Crear `ImageResult`**

```java
package io.github.shizukajiku.imagewatch.application;

import io.github.shizukajiku.imagewatch.domain.ImageRelease;
import java.util.Optional;

/**
 * Resultado de consultar una imagen concreta. Que el fallo viaje por imagen, y no como excepción
 * del lote, es lo que impide que una imagen caída impida ver el estado de las demás.
 */
public record ImageResult(String name, Optional<ImageRelease> release, Optional<String> error) {

  public static ImageResult found(ImageRelease release) {
    return new ImageResult(release.name(), Optional.of(release), Optional.empty());
  }

  public static ImageResult failed(String name, String message) {
    return new ImageResult(name, Optional.empty(), Optional.of(message));
  }
}
```

- [ ] **Step 4: Cambiar el puerto**

```java
package io.github.shizukajiku.imagewatch.application;

import java.util.List;

public interface ImageSource {
  /**
   * Consulta cada nombre y devuelve un resultado por imagen. Las implementaciones no propagan
   * el fallo de una imagen: lo devuelven dentro de su {@link ImageResult}.
   */
  List<ImageResult> findByNames(List<String> names);
}
```

- [ ] **Step 5: Adaptar `HttpImageSource`**

Envuelve la consulta de cada nombre para que capture su propio fallo:

```java
  @Override
  public List<ImageResult> findByNames(List<String> names) {
    return names.stream().map(this::findSafely).toList();
  }

  private ImageResult findSafely(String name) {
    try {
      return ImageResult.found(find(name));
    } catch (RuntimeException e) {
      return ImageResult.failed(name, e.getMessage());
    }
  }
```

`find(String)` conserva el cuerpo actual, salvo que devuelve `ImageRelease` construido desde `ReleaseDto`. La concurrencia llega en la Task 10.

- [ ] **Step 6: Adaptar `SimulatedImageSource`**

```java
  @Override
  public List<ImageResult> findByNames(List<String> names) {
    return names.stream()
        .map(
            name ->
                ImageResult.found(
                    new ImageRelease(
                        name, "registry.local/" + name + ":2.0.0", LocalDateTime.now())))
        .toList();
  }
```

El origen simulado con versiones cambiantes llega en la Task 10.

- [ ] **Step 7: Adaptar `VersionPollingService`**

Sustituye el cuerpo de la clase por esta versión. Desaparecen `ImageUpdate` y el `System.err`.

```java
package io.github.shizukajiku.imagewatch.application;

import io.github.shizukajiku.imagewatch.domain.ImageRelease;
import io.github.shizukajiku.imagewatch.domain.ImageState;
import io.github.shizukajiku.imagewatch.domain.ImageStatus;
import io.github.shizukajiku.imagewatch.domain.PollSnapshot;
import io.github.shizukajiku.imagewatch.domain.Version;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

public final class VersionPollingService {
  private static final Pattern REFERENCE = Pattern.compile("^([^/]+/[^:]+):(.+)$");

  private final ImageSource source;
  private final ImageStateStore state;
  private final List<NotificationPort> notifiers;
  private final TrackedImageStore trackedImages;
  private final Clock clock;
  private volatile PollSnapshot lastSnapshot = PollSnapshot.EMPTY;

  public VersionPollingService(
      ImageSource source,
      ImageStateStore state,
      List<NotificationPort> notifiers,
      TrackedImageStore trackedImages) {
    this(source, state, notifiers, trackedImages, Clock.systemUTC());
  }

  public VersionPollingService(
      ImageSource source,
      ImageStateStore state,
      List<NotificationPort> notifiers,
      TrackedImageStore trackedImages,
      Clock clock) {
    this.source = source;
    this.state = state;
    this.notifiers = List.copyOf(notifiers);
    this.trackedImages = trackedImages;
    this.clock = clock;
  }

  public void poll() {
    var at = clock.instant();
    var results = source.findByNames(trackedImages.findAll());
    var states = results.stream().map(result -> toState(result, at)).toList();
    lastSnapshot = new PollSnapshot(states, at);
    persist(results);
  }

  public PollSnapshot lastSnapshot() {
    return lastSnapshot;
  }

  private void persist(List<ImageResult> results) {
    var releases = results.stream().flatMap(result -> result.release().stream()).toList();
    if (!releases.isEmpty()) {
      state.save(releases);
    }
  }

  private ImageState toState(ImageResult result, Instant at) {
    if (result.error().isPresent()) {
      return ImageState.failed(result.name(), result.error().get(), at);
    }
    var release = result.release().orElseThrow();
    var remote = parse(release.reference());
    if (remote.isEmpty()) {
      return ImageState.failed(
          result.name(), "Referencia no reconocida: " + release.reference(), at);
    }
    var local = state.find(result.name()).flatMap(known -> parse(known.reference()));
    var status = statusOf(local, remote.get());
    return new ImageState(
        result.name(), local, remote, registryOf(release.reference()), status,
        Optional.empty(), at);
  }

  private ImageStatus statusOf(Optional<Version> local, Version remote) {
    if (local.isEmpty()) {
      return ImageStatus.UNKNOWN;
    }
    return remote.compareTo(local.get()) > 0 ? ImageStatus.PENDING : ImageStatus.OK;
  }

  private Optional<Version> parse(String reference) {
    var matcher = REFERENCE.matcher(reference);
    if (!matcher.matches()) {
      return Optional.empty();
    }
    try {
      return Optional.of(new Version(matcher.group(2)));
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  private String registryOf(String reference) {
    var index = reference.lastIndexOf(':');
    return index > 0 ? reference.substring(0, index) : reference;
  }
}
```

Dos cambios de fondo respecto al código anterior, además del aislamiento:

- Una referencia mal formada ya no lanza `IllegalArgumentException` que tumbe el ciclo: produce un estado `ERROR` para esa imagen. Lo mismo con una versión no parseable, que ahora es posible detectar gracias a la corrección de la Fase 2.
- Se persisten solo las publicaciones obtenidas con éxito. Antes, un fallo impedía persistir nada.

La notificación desaparece temporalmente de `poll()`; vuelve en la Task 6, ya por transición. Los tests de notificación existentes fallarán hasta entonces: es esperado y la Task 6 los restaura.

- [ ] **Step 8: Actualizar los fakes del test**

En `VersionPollingServiceTest`, `FakeImageSource` pasa a devolver `List<ImageResult>` y gana `failOn`:

```java
  private static final class FakeImageSource implements ImageSource {
    private final Map<String, String> referenceByName;
    private final Map<String, String> failures = new HashMap<>();

    FakeImageSource(Map<String, String> referenceByName) {
      this.referenceByName = referenceByName;
    }

    void failOn(String name, String message) {
      failures.put(name, message);
    }

    @Override
    public List<ImageResult> findByNames(List<String> names) {
      var results = new ArrayList<ImageResult>();
      for (var name : names) {
        if (failures.containsKey(name)) {
          results.add(ImageResult.failed(name, failures.get(name)));
        } else {
          results.add(
              ImageResult.found(
                  new ImageRelease(name, referenceByName.get(name), LocalDateTime.now())));
        }
      }
      return results;
    }
  }
```

Adapta también `FakeImageStateStore` a `ImageRelease`, y sustituye las aserciones sobre `lastUpdates()` por `lastSnapshot()`.

Comenta con `@Disabled("se restaura en la Task 6")` el test `onlyPendingUpdatesAreNotified`.

- [ ] **Step 9: Ejecutar**

```bash
./gradlew test --tests "*VersionPollingServiceTest*"
```

Expected: pasa `oneFailingImageDoesNotPreventTheOthersFromUpdating` y el de `lastSnapshot`; `onlyPendingUpdatesAreNotified` aparece como omitido.

- [ ] **Step 10: Commit**

```bash
./gradlew spotlessApply && ./gradlew check
git add -A
git commit -m "feat: aislar los fallos por imagen en el ciclo de verificación

findByNames lanzaba en la primera imagen que fallaba, poll() lo atrapaba y
escribía en stderr, y ninguna imagen se actualizaba. Ahora el fallo viaja
dentro de ImageResult y solo afecta a su propia imagen.

Una referencia mal formada o una versión no parseable tampoco tumban el
ciclo: producen estado ERROR para esa imagen. Solo se persisten las
publicaciones obtenidas con éxito.

La notificación se restaura en el commit siguiente, ya por transición."
```

---

### Task 6: Notificar solo las transiciones

Hoy `poll()` notifica en cada ciclo por cada imagen pendiente: con intervalo de 300 s, un aviso cada cinco minutos, para siempre, por la misma versión.

**Files:**
- Modify: `application/NotificationPort.java`, `VersionPollingService.java`, `WindowsNotificationAdapter.java`, `TeamsNotificationAdapter.java`, `NotificationDemoMain.java`
- Modify: `VersionPollingServiceTest.java`

**Interfaces:**
- Produces: `NotificationPort.notifyUpdates(List<ImageState>)`.

- [ ] **Step 1: Escribir los tests**

Reemplaza `onlyPendingUpdatesAreNotified` y quita su `@Disabled`:

```java
  @Test
  void notifiesOnlyImagesThatBecamePending() {
    var names = new FakeTrackedImageStore(List.of("alpha", "beta"));
    var state = new FakeImageStateStore();
    state.seed("alpha", "registry.local/alpha:1.0.0");
    state.seed("beta", "registry.local/beta:2.0.0");
    var source =
        new FakeImageSource(
            Map.of("alpha", "registry.local/alpha:2.0.0", "beta", "registry.local/beta:2.0.0"));
    var notifications = new RecordingNotificationPort();
    var service = new VersionPollingService(source, state, List.of(notifications), names);

    service.poll();

    assertThat(notifications.received).extracting(ImageState::name).containsExactly("alpha");
  }

  @Test
  void doesNotNotifyTheSamePendingImageTwice() {
    var names = new FakeTrackedImageStore(List.of("alpha"));
    var state = new FakeImageStateStore();
    state.seed("alpha", "registry.local/alpha:1.0.0");
    var source = new FakeImageSource(Map.of("alpha", "registry.local/alpha:2.0.0"));
    var notifications = new RecordingNotificationPort();
    var service = new VersionPollingService(source, state, List.of(notifications), names);

    service.poll();
    notifications.received.clear();
    service.poll();

    assertThat(notifications.received).isEmpty();
  }

  @Test
  void notifiesAgainWhenAnImageBecomesPendingOnceMore() {
    var names = new FakeTrackedImageStore(List.of("alpha"));
    var state = new FakeImageStateStore();
    state.seed("alpha", "registry.local/alpha:1.0.0");
    var source = new FakeImageSource(Map.of("alpha", "registry.local/alpha:2.0.0"));
    var notifications = new RecordingNotificationPort();
    var service = new VersionPollingService(source, state, List.of(notifications), names);

    service.poll();
    notifications.received.clear();

    // La imagen se pone al día y vuelve a quedarse atrás.
    state.seed("alpha", "registry.local/alpha:2.0.0");
    service.poll();
    state.seed("alpha", "registry.local/alpha:2.0.0");
    source.setReference("alpha", "registry.local/alpha:3.0.0");
    service.poll();

    assertThat(notifications.received).extracting(ImageState::name).containsExactly("alpha");
  }

  @Test
  void anImageThatFailsIsNotNotifiedAsAnUpdate() {
    var names = new FakeTrackedImageStore(List.of("alpha"));
    var state = new FakeImageStateStore();
    var source = new FakeImageSource(Map.of());
    source.failOn("alpha", "HTTP 503");
    var notifications = new RecordingNotificationPort();
    var service = new VersionPollingService(source, state, List.of(notifications), names);

    service.poll();

    assertThat(notifications.received).isEmpty();
  }
```

Añade `setReference` a `FakeImageSource`:

```java
    void setReference(String name, String reference) {
      referenceByName.put(name, reference);
    }
```

y cambia su campo a `private final Map<String, String> referenceByName = new HashMap<>();` inicializado desde el constructor con `putAll`.

`RecordingNotificationPort` pasa a acumular `ImageState`.

- [ ] **Step 2: Ejecutar y comprobar que fallan**

```bash
./gradlew test --tests "*VersionPollingServiceTest*"
```

Expected: FALLAN los cuatro. `poll()` no notifica nada ahora mismo.

- [ ] **Step 3: Cambiar el puerto de notificación**

```java
package io.github.shizukajiku.imagewatch.application;

import io.github.shizukajiku.imagewatch.domain.ImageState;
import java.util.List;

public interface NotificationPort {
  /** Recibe únicamente las imágenes que acaban de pasar a tener una versión pendiente. */
  void notifyUpdates(List<ImageState> updates);
}
```

- [ ] **Step 4: Implementar la detección de transiciones**

En `VersionPollingService`, dentro de `poll()`, entre el cálculo del snapshot y la persistencia:

```java
  public void poll() {
    var at = clock.instant();
    var previous = lastSnapshot;
    var results = source.findByNames(trackedImages.findAll());
    var states = results.stream().map(result -> toState(result, at)).toList();
    var snapshot = new PollSnapshot(states, at);
    lastSnapshot = snapshot;
    notifyTransitions(previous, snapshot);
    persist(results);
  }

  /**
   * Notifica solo las imágenes que <em>acaban de</em> pasar a pendientes. Notificar en cada ciclo
   * produciría un aviso por intervalo, indefinidamente, por la misma versión.
   */
  private void notifyTransitions(PollSnapshot previous, PollSnapshot current) {
    var newlyPending =
        current.pending().stream()
            .filter(
                image ->
                    previous
                        .find(image.name())
                        .map(before -> before.status() != ImageStatus.PENDING)
                        .orElse(true))
            .toList();
    if (newlyPending.isEmpty()) {
      return;
    }
    for (var notifier : notifiers) {
      notifier.notifyUpdates(newlyPending);
    }
  }
```

- [ ] **Step 5: Adaptar los adaptadores de notificación**

En `WindowsNotificationAdapter`, cambia el tipo de la lista a `ImageState` y el formateo:

```java
  private String formatUpdate(ImageState update) {
    var previous = update.local().map(Version::value).orElse("sin versión local");
    var next = update.remote().map(Version::value).orElse("?");
    return "• " + safe(update.name()) + ": " + safe(previous) + " → " + safe(next);
  }
```

En `TeamsNotificationAdapter` y `NotificationDemoMain`, ajusta los tipos.

- [ ] **Step 6: Ejecutar**

```bash
./gradlew test --tests "*VersionPollingServiceTest*"
```

Expected: los cuatro pasan, junto con los de las tareas anteriores.

- [ ] **Step 7: Commit**

```bash
./gradlew spotlessApply && ./gradlew check
git add -A
git commit -m "fix: notificar solo cuando una imagen pasa a pendiente

poll() notificaba en cada ciclo por cada imagen pendiente: con el intervalo
por defecto, un aviso cada cinco minutos, indefinidamente, por la misma
versión. Ahora se compara contra el snapshot anterior y solo se notifican las
transiciones hacia PENDING.

NotificationPort recibe ImageState en lugar de ImageUpdate, que desaparece."
```

---

## Deuda detectada durante la ejecución

Registrada aquí para que no quede flotando. Cada punto se salda en la tarea que
ya abre ese fichero, no en una tarea de limpieza al final.

| # | Hallazgo | Dónde se salda |
|---|---|---|
| D1 | El aviso del primer ciclo se vuelve molesto al persistir el estado pendiente | Task 7 |
| D2 | `Optional` como parámetro de `statusOf` — viola el estándar del proyecto | Task 7 |
| D3 | `updates.get(0)` pudiendo ser `getFirst()` | Task 7 |
| D4 | La clase de demostración manual escribe en la salida estándar | Task 11 |
| D5 | `ImageManagerFrame.registryOf` quedó huérfano | Task 12 |
| D6 | Una imagen con error se etiqueta «Sin verificar» | Task 12 |
| D7 | El repositorio no tiene README | Task 13 |

D1, D5 y D6 salieron de revisar el código a mano; D2, D3 y D4 los encontraron las
inspecciones del IDE.

**Deuda aceptada a conciencia**, para que no se vuelva a levantar en cada
revisión: `ImageState` usa componentes `Optional`. El estándar del proyecto
prohíbe `Optional` como tipo de campo, y un componente de record es un campo. Se
mantiene porque es un valor inmutable que cruza hilos y se renderiza: la
alternativa es `null`, que traslada la comprobación a cada consumidor. La
intención de la regla —evitar `Optional` en entidades mutables y en parámetros—
sí se respeta, y D2 corrige la única violación real.

---

### Task 7: Estado pendiente persistente

El defecto de fondo: el almacén guarda **lo último que publicó el origen** y lo guarda **en el mismo ciclo en que lo detecta**. Reconocer es automático e instantáneo, así que nada puede quedarse pendiente.

```
t=0s    remoto 1.0.1, local 1.0.0  →  PENDING, notifica, guarda 1.0.1
t=10s   remoto 1.0.1, local 1.0.1  →  OK
```

`PENDING` dura un ciclo. Con el intervalo por defecto, la columna de estado dice *Al día* prácticamente siempre — aunque no hayas atendido nada. Si te pierdes el aviso, la información desaparece.

La corrección no añade campos: **el almacén pasa a significar «versión reconocida»**, y deja de escribirse en cada ciclo.

**Files:**
- Modify: `application/ImageStateStore.java` (documentación del contrato), `VersionPollingService.java`
- Modify: `VersionPollingServiceTest.java`

**Interfaces:**
- Produces: `VersionPollingService.acknowledge(String name)`. `persist` pasa a escribir únicamente la línea base de imágenes nunca vistas.

- [ ] **Step 1: Escribir los tests**

```java
  @Test
  void aPendingImageStaysPendingAcrossPolls() {
    var names = new FakeTrackedImageStore(List.of("alpha"));
    var state = new FakeImageStateStore();
    state.seed("alpha", "registry.local/alpha:1.0.0");
    var source = new FakeImageSource(Map.of("alpha", "registry.local/alpha:2.0.0"));
    var service = new VersionPollingService(source, state, List.of(), names);

    service.poll();
    service.poll();
    service.poll();

    assertThat(service.lastSnapshot().find("alpha"))
        .hasValueSatisfying(image -> assertThat(image.status()).isEqualTo(ImageStatus.PENDING));
  }

  @Test
  void theFirstSightingOfAnImageIsRecordedAsAcknowledged() {
    var names = new FakeTrackedImageStore(List.of("alpha"));
    var state = new FakeImageStateStore();
    var source = new FakeImageSource(Map.of("alpha", "registry.local/alpha:1.0.0"));
    var notifications = new RecordingNotificationPort();
    var service = new VersionPollingService(source, state, List.of(notifications), names);

    service.poll();

    assertThat(state.find("alpha")).isPresent();
    assertThat(notifications.received).isEmpty();
  }

  @Test
  void acknowledgingAnImageClearsItsPendingStatus() {
    var names = new FakeTrackedImageStore(List.of("alpha"));
    var state = new FakeImageStateStore();
    state.seed("alpha", "registry.local/alpha:1.0.0");
    var source = new FakeImageSource(Map.of("alpha", "registry.local/alpha:2.0.0"));
    var service = new VersionPollingService(source, state, List.of(), names);
    service.poll();

    service.acknowledge("alpha");
    service.poll();

    assertThat(service.lastSnapshot().find("alpha"))
        .hasValueSatisfying(image -> assertThat(image.status()).isEqualTo(ImageStatus.OK));
  }

  @Test
  void acknowledgingAnImageThatWasNeverSeenIsANoOp() {
    var names = new FakeTrackedImageStore(List.of("alpha"));
    var state = new FakeImageStateStore();
    var source = new FakeImageSource(Map.of("alpha", "registry.local/alpha:1.0.0"));
    var service = new VersionPollingService(source, state, List.of(), names);

    service.acknowledge("ausente");

    assertThat(state.find("ausente")).isEmpty();
  }
```

`theFirstSightingOfAnImageIsRecordedAsAcknowledged` es el que evita el efecto secundario molesto: sin línea base, el primer arranque dispararía un aviso por cada imagen vigilada.

`aPendingImageStaysPendingAcrossPolls` es el test que fija la corrección. Con el código actual falla en el segundo `poll()`.

- [ ] **Step 2: Ejecutar y comprobar que fallan**

```bash
./gradlew test --tests "*VersionPollingServiceTest*"
```

Expected: FALLAN `aPendingImageStaysPendingAcrossPolls` (pasa a `OK` en el segundo ciclo) y los dos de `acknowledge`, que no compila.

- [ ] **Step 3: Documentar el contrato del almacén**

```java
package io.github.shizukajiku.imagewatch.application;

import io.github.shizukajiku.imagewatch.domain.ImageRelease;
import java.util.List;
import java.util.Optional;

/**
 * Guarda, por imagen, la publicación que el usuario ya ha dado por vista.
 *
 * <p>No es «lo último que publicó el origen»: si lo fuera, cada ciclo reconocería
 * automáticamente lo que acaba de detectar y ninguna imagen podría quedarse pendiente. Solo se
 * escribe al establecer la línea base de una imagen nueva y al reconocer explícitamente.
 */
public interface ImageStateStore {
  Optional<ImageRelease> find(String name);

  void save(List<ImageRelease> releases);
}
```

- [ ] **Step 4: Implementar**

En `VersionPollingService`, sustituye `persist` y añade `acknowledge`:

```java
  /**
   * Registra la línea base de las imágenes nunca vistas. No toca las ya conocidas: hacerlo
   * reconocería automáticamente la versión recién detectada y el estado pendiente duraría un
   * solo ciclo.
   */
  private void persist(List<ImageResult> results) {
    var unseen =
        results.stream()
            .flatMap(result -> result.release().stream())
            .filter(release -> state.find(release.name()).isEmpty())
            .toList();
    if (!unseen.isEmpty()) {
      state.save(unseen);
    }
  }

  /** Da por vista la versión que el origen publica ahora mismo para {@code name}. */
  public void acknowledge(String name) {
    lastSnapshot.find(name).stream()
        .flatMap(image -> latestRelease(name).stream())
        .forEach(release -> state.save(List.of(release)));
  }
```

`acknowledge` necesita la publicación completa, no solo la versión, porque el almacén guarda `ImageRelease`. Guarda la última publicación conocida por imagen en un mapa `volatile` poblado en `poll()`:

```java
  private volatile Map<String, ImageRelease> latestReleases = Map.of();

  private Optional<ImageRelease> latestRelease(String name) {
    return Optional.ofNullable(latestReleases.get(name));
  }
```

y en `poll()`, junto a la asignación del snapshot:

```java
    latestReleases =
        results.stream()
            .flatMap(result -> result.release().stream())
            .collect(Collectors.toUnmodifiableMap(ImageRelease::name, release -> release));
```

- [ ] **Step 5: Ejecutar**

```bash
./gradlew test --tests "*VersionPollingServiceTest*"
```

Expected: los cuatro nuevos pasan, y siguen pasando los de transición de la Task 6.

Comprueba en particular que `doesNotNotifyTheSamePendingImageTwice` sigue verde: ahora la imagen permanece `PENDING` entre ciclos, y es la comparación de transición —no el auto-reconocimiento— la que impide el aviso repetido. Antes ambos mecanismos tapaban el mismo síntoma; ahora solo queda el correcto.

- [ ] **Step 6: D1 — dejar de avisar en el primer ciclo de cada sesión**

La Task 6 hizo que una imagen ausente del snapshot anterior contara como
transición. Con `PENDING` transitorio era un recordatorio útil. Con el estado
persistente que introduce esta misma tarea, pasa a ser una molestia: si tienes
tres imágenes que decidiste no actualizar, **cada arranque te las repite**, y la
ventana ya te las muestra al abrirla.

Escribe primero el test:

```java
  @Test
  void theFirstPollOfASessionDoesNotNotify() {
    var names = new FakeTrackedImageStore(List.of("alpha"));
    var state = new FakeImageStateStore();
    state.seed("alpha", "registry.local/alpha:1.0.0");
    var source = new FakeImageSource(Map.of("alpha", "registry.local/alpha:2.0.0"));
    var notifications = new RecordingNotificationPort();
    var service = new VersionPollingService(source, state, List.of(notifications), names);

    service.poll();

    assertThat(service.lastSnapshot().find("alpha"))
        .hasValueSatisfying(image -> assertThat(image.status()).isEqualTo(ImageStatus.PENDING));
    assertThat(notifications.received).isEmpty();
  }
```

Fíjate en que comprueba las dos mitades: **no** avisa, pero el estado **sí** queda
pendiente. Sin la segunda aserción, el test pasaría también si se rompiera la
detección entera.

Ese test contradice a `notifiesOnlyImagesThatBecamePending` de la Task 6, que
esperaba aviso en el primer ciclo. Añádele un `poll()` previo para que el aviso
que verifica sea una transición real:

```java
    service.poll(); // línea base de la sesión
    source.setReference("alpha", "registry.local/alpha:3.0.0");
    notifications.received.clear();

    service.poll();

    assertThat(notifications.received).extracting(ImageState::name).containsExactly("alpha");
```

Implementación, en `notifyTransitions`:

```java
    // El primer ciclo de la sesión no avisa: el estado pendiente persiste y la
    // ventana ya lo muestra. Avisar al arrancar repetiría cada día lo que el
    // usuario ya decidió posponer.
    if (previous == PollSnapshot.EMPTY) {
      return;
    }
```

Si más adelante se prefiere el recordatorio al arrancar, es esta línea la que se
quita — pero entonces conviene que sea un resumen agrupado, no un aviso por imagen.

- [ ] **Step 7: D2 — quitar el `Optional` de la firma de `statusOf`**

El estándar del proyecto prohíbe `Optional` como parámetro. Es la única violación
real que quedó al reescribir el servicio:

```java
  private ImageStatus statusOf(Version local, Version remote) {
    return remote.compareTo(local) > 0 ? ImageStatus.PENDING : ImageStatus.OK;
  }
```

y en `toState`, la ausencia se resuelve en el punto de llamada, que es donde
significa algo:

```java
    return new ImageState(
        result.name(),
        local,
        remote,
        registryOf(release.reference()),
        local.map(known -> statusOf(known, remote.get())).orElse(ImageStatus.UNKNOWN),
        Optional.empty(),
        at);
```

- [ ] **Step 8: D3 — usar `getFirst()` en el adaptador de notificación**

En `WindowsNotificationAdapter`, `updates.get(0)` pasa a `updates.getFirst()`.
`List` es `SequencedCollection` desde Java 21 y el proyecto ya está en 25.

- [ ] **Step 9: Ejecutar la batería completa**

```bash
./gradlew spotlessApply && ./gradlew check
```

Expected: verde. Presta atención a `doesNotNotifyTheSamePendingImageTwice`: hasta
ahora pasaba porque el auto-reconocimiento devolvía la imagen a `OK` sola. Con
esta tarea la imagen **sigue pendiente** en el segundo ciclo, así que ese test
depende por fin de `notifyTransitions`. Si se pone rojo aquí, significa que la
lógica de transición nunca funcionó y el otro mecanismo la estaba tapando.

- [ ] **Step 10: Commit**

```bash
./gradlew spotlessApply && ./gradlew check
git add -A
git commit -m "fix: el estado pendiente persiste hasta que el usuario lo reconoce

El almacén guardaba lo último publicado por el origen, y lo guardaba en el
mismo ciclo en que lo detectaba. Reconocer era automático e instantáneo, así
que PENDING duraba un único ciclo y la columna de estado decía 'Al día' casi
siempre, aunque no se hubiera atendido nada. Perderse el aviso significaba
perder la información.

El almacén pasa a significar 'versión reconocida'. Solo se escribe al
establecer la línea base de una imagen nueva -para que el primer arranque no
dispare un aviso por imagen- y al llamar a acknowledge()."
```

---

### Task 8: Publicar los snapshots a los oyentes

Es la pieza que hace posible el tiempo real.

**Files:**
- Create: `application/PollListener.java`
- Modify: `VersionPollingService.java`
- Modify: `VersionPollingServiceTest.java`

**Interfaces:**
- Produces: `interface PollListener { void onSnapshot(PollSnapshot snapshot); }`; `VersionPollingService.addListener(PollListener)` y `removeListener(PollListener)`.

- [ ] **Step 1: Escribir los tests**

```java
  @Test
  void publishesTheSnapshotToRegisteredListeners() {
    var names = new FakeTrackedImageStore(List.of("alpha"));
    var source = new FakeImageSource(Map.of("alpha", "registry.local/alpha:1.0.0"));
    var service =
        new VersionPollingService(source, new FakeImageStateStore(), List.of(), names);
    var received = new ArrayList<PollSnapshot>();
    service.addListener(received::add);

    service.poll();

    assertThat(received).hasSize(1);
    assertThat(received.get(0).find("alpha")).isPresent();
  }

  @Test
  void aRemovedListenerStopsReceivingSnapshots() {
    var names = new FakeTrackedImageStore(List.of("alpha"));
    var source = new FakeImageSource(Map.of("alpha", "registry.local/alpha:1.0.0"));
    var service =
        new VersionPollingService(source, new FakeImageStateStore(), List.of(), names);
    var received = new ArrayList<PollSnapshot>();
    PollListener listener = received::add;
    service.addListener(listener);

    service.poll();
    service.removeListener(listener);
    service.poll();

    assertThat(received).hasSize(1);
  }

  @Test
  void aFailingListenerDoesNotPreventTheOthersFromReceiving() {
    var names = new FakeTrackedImageStore(List.of("alpha"));
    var source = new FakeImageSource(Map.of("alpha", "registry.local/alpha:1.0.0"));
    var service =
        new VersionPollingService(source, new FakeImageStateStore(), List.of(), names);
    var received = new ArrayList<PollSnapshot>();
    service.addListener(
        snapshot -> {
          throw new IllegalStateException("oyente roto");
        });
    service.addListener(received::add);

    service.poll();

    assertThat(received).hasSize(1);
  }
```

El tercero importa: la interfaz gráfica va a ser un oyente, y un fallo suyo no puede dejar el núcleo sin publicar a los demás.

- [ ] **Step 2: Ejecutar y comprobar que no compila**

```bash
./gradlew test --tests "*VersionPollingServiceTest*"
```

Expected: FALLA — `addListener` no existe.

- [ ] **Step 3: Crear el puerto**

```java
package io.github.shizukajiku.imagewatch.application;

import io.github.shizukajiku.imagewatch.domain.PollSnapshot;

/**
 * Recibe el resultado de cada ciclo. Es el puerto que permite a la interfaz actualizarse sola,
 * sin sondear al servicio.
 *
 * <p>Las implementaciones se invocan en el hilo del planificador: si tocan la interfaz, deben
 * saltar ellas mismas al hilo de la interfaz.
 */
@FunctionalInterface
public interface PollListener {
  void onSnapshot(PollSnapshot snapshot);
}
```

- [ ] **Step 4: Implementar la publicación**

En `VersionPollingService`, añade el registro y la publicación al final de `poll()`:

```java
  private final List<PollListener> listeners = new CopyOnWriteArrayList<>();

  public void addListener(PollListener listener) {
    listeners.add(listener);
  }

  public void removeListener(PollListener listener) {
    listeners.remove(listener);
  }

  private void publish(PollSnapshot snapshot) {
    for (var listener : listeners) {
      try {
        listener.onSnapshot(snapshot);
      } catch (RuntimeException e) {
        LOG.warn("Un oyente falló al recibir el snapshot", e);
      }
    }
  }
```

`CopyOnWriteArrayList` porque los oyentes se registran desde el hilo de la interfaz mientras el planificador itera sobre ellos.

Llama a `publish(snapshot)` como última sentencia de `poll()`, después de `persist`.

El `LOG` se introduce en la Task 11; hasta entonces usa `System.err.println` y **añade un comentario `// ponytail: sustituido por SLF4J en la Task 11`** para no perderlo de vista.

- [ ] **Step 5: Ejecutar**

```bash
./gradlew test --tests "*VersionPollingServiceTest*"
```

Expected: los tres pasan.

- [ ] **Step 6: Commit**

```bash
./gradlew spotlessApply && ./gradlew check
git add -A
git commit -m "feat: publicar el snapshot de cada ciclo a los oyentes registrados

Es la pieza que permite a la interfaz actualizarse sola en lugar de leer un
campo volatile solo al abrirse.

Un oyente que lanza no impide que los demás reciban: la interfaz va a ser uno
de ellos y no puede dejar al núcleo sin publicar."
```

---

### Task 9: Consultar las imágenes en paralelo

Hoy son N peticiones en serie.

**Files:**
- Modify: `infrastructure/remote/HttpImageSource.java`
- Create: `src/test/java/io/github/shizukajiku/imagewatch/infrastructure/remote/HttpImageSourceTest.java`

- [ ] **Step 1: Escribir el test con el servidor del JDK**

`com.sun.net.httpserver.HttpServer` viene en el JDK: no hace falta WireMock ni ninguna otra dependencia.

```java
package io.github.shizukajiku.imagewatch.infrastructure.remote;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HttpImageSourceTest {

  private HttpServer server;
  private String baseUrl;

  @BeforeEach
  void startServer() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.start();
    baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
  }

  @AfterEach
  void stopServer() {
    server.stop(0);
  }

  @Test
  void mapsTheWireFormatOntoTheDomain() {
    respond(
        "/alpha",
        200,
        """
        {"productName":"alpha","lastRelease":"registry.local/alpha:1.2.3",
         "update_time":"2026-09-03T10:00:00"}
        """);

    var results = source().findByNames(List.of("alpha"));

    assertThat(results).hasSize(1);
    assertThat(results.get(0).release())
        .hasValueSatisfying(
            release -> {
              assertThat(release.name()).isEqualTo("alpha");
              assertThat(release.reference()).isEqualTo("registry.local/alpha:1.2.3");
            });
  }

  @Test
  void reportsAnErrorPerImageWithoutAffectingTheOthers() {
    respond(
        "/alpha",
        200,
        """
        {"productName":"alpha","lastRelease":"registry.local/alpha:1.2.3",
         "update_time":"2026-09-03T10:00:00"}
        """);
    respond("/beta", 503, "");

    var results = source().findByNames(List.of("alpha", "beta"));

    assertThat(results).hasSize(2);
    assertThat(results.get(0).error()).isEmpty();
    assertThat(results.get(1).error()).isPresent();
  }

  @Test
  void preservesTheRequestedOrder() {
    for (var name : List.of("alpha", "beta", "gamma")) {
      respond(
          "/" + name,
          200,
          "{\"productName\":\"%s\",\"lastRelease\":\"registry.local/%s:1.0.0\",\"update_time\":\"2026-09-03T10:00:00\"}"
              .formatted(name, name));
    }

    var results = source().findByNames(List.of("gamma", "alpha", "beta"));

    assertThat(results).extracting(r -> r.name()).containsExactly("gamma", "alpha", "beta");
  }

  @Test
  void rejectsImageNamesThatCouldEscapeThePath() {
    var results = source().findByNames(List.of("../admin"));

    assertThat(results.get(0).error()).isPresent();
  }

  private HttpImageSource source() {
    return new HttpImageSource(HttpClient.newHttpClient(), baseUrl);
  }

  private void respond(String path, int status, String body) {
    server.createContext(
        path,
        exchange -> {
          var bytes = body.getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
          if (bytes.length > 0) {
            try (var out = exchange.getResponseBody()) {
              out.write(bytes);
            }
          }
        });
  }
}
```

`preservesTheRequestedOrder` es el test que protege de la trampa clásica al paralelizar: recoger los resultados según van llegando y devolverlos desordenados, lo que reordenaría las filas de la tabla en cada ciclo.

`rejectsImageNamesThatCouldEscapeThePath` fija la validación de nombre que ya existe hoy, ahora como resultado de error en lugar de excepción.

- [ ] **Step 2: Levantar la restricción de HTTPS solo para pruebas locales**

`HttpImageSource` exige HTTPS. El servidor de pruebas es HTTP en `127.0.0.1`, así que la restricción pasa a admitir explícitamente el bucle local:

```java
    var endpoint = URI.create(baseUrl.replaceAll("/$", ""));
    var loopback = "127.0.0.1".equals(endpoint.getHost()) || "localhost".equals(endpoint.getHost());
    if (!"https".equalsIgnoreCase(endpoint.getScheme()) && !loopback) {
      throw new IllegalArgumentException("El endpoint remoto debe utilizar HTTPS");
    }
```

Es una relajación real y acotada: solo el bucle local, donde no hay tráfico que interceptar. Cualquier host remoto sigue obligado a HTTPS.

- [ ] **Step 3: Ejecutar y comprobar que falla**

```bash
./gradlew test --tests "*HttpImageSourceTest*"
```

Expected: fallan por el orden y/o por la validación de nombre, según el estado del adaptador.

- [ ] **Step 4: Paralelizar con hilos virtuales**

```java
  @Override
  public List<ImageResult> findByNames(List<String> names) {
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      // Se materializa la lista de futuros antes de recogerlos para que las consultas
      // arranquen todas; recogerlas en el mismo stream las volvería a serializar.
      var futures = names.stream().map(name -> executor.submit(() -> findSafely(name))).toList();
      return futures.stream().map(this::join).toList();
    }
  }

  private ImageResult join(Future<ImageResult> future) {
    try {
      return future.get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return ImageResult.failed("?", "Consulta interrumpida");
    } catch (ExecutionException e) {
      return ImageResult.failed("?", String.valueOf(e.getCause().getMessage()));
    }
  }
```

El orden se conserva porque se recorre la lista de futuros en el orden de envío, no en el de finalización.

`findSafely` ya captura sus propios fallos, así que `ExecutionException` solo puede venir de un error no previsto.

Mueve la validación del nombre dentro de `findSafely` para que produzca un `ImageResult.failed` en lugar de una excepción.

- [ ] **Step 5: Ejecutar**

```bash
./gradlew test --tests "*HttpImageSourceTest*"
```

Expected: los cuatro pasan.

- [ ] **Step 6: Commit**

```bash
./gradlew spotlessApply && ./gradlew check
git add -A
git commit -m "perf: consultar las imágenes en paralelo con hilos virtuales

Eran N peticiones en serie por ciclo. Los futuros se recogen en orden de
envío, no de finalización, para que las filas de la tabla no se reordenen en
cada ciclo.

La exigencia de HTTPS se relaja solo para el bucle local, que es lo que
permite probar el adaptador contra com.sun.net.httpserver sin añadir ninguna
dependencia de test."
```

---

### Task 10: Origen simulado con versiones cambiantes

Sin esto no se puede observar el tiempo real: el origen real solo existe tras la red corporativa.

**Files:**
- Modify: `infrastructure/remote/SimulatedImageSource.java`
- Create: `src/test/java/io/github/shizukajiku/imagewatch/infrastructure/remote/SimulatedImageSourceTest.java`
- Modify: `Main.java`

- [ ] **Step 1: Escribir el test**

```java
package io.github.shizukajiku.imagewatch.infrastructure.remote;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class SimulatedImageSourceTest {

  private static final Instant START = Instant.parse("2026-09-03T10:00:00Z");

  @Test
  void theVersionAdvancesAsTimePasses() {
    var clock = new MutableClock(START);
    var source = new SimulatedImageSource(clock, Duration.ofSeconds(20));

    var first = reference(source, "alpha");
    clock.advance(Duration.ofSeconds(60));
    var later = reference(source, "alpha");

    assertThat(later).isNotEqualTo(first);
  }

  @Test
  void theVersionIsStableWhileTimeDoesNotAdvance() {
    var clock = new MutableClock(START);
    var source = new SimulatedImageSource(clock, Duration.ofSeconds(20));

    assertThat(reference(source, "alpha")).isEqualTo(reference(source, "alpha"));
  }

  @Test
  void differentImagesAdvanceOutOfStep() {
    var clock = new MutableClock(START);
    var source = new SimulatedImageSource(clock, Duration.ofSeconds(20));

    var alpha = reference(source, "alpha");
    var beta = reference(source, "beta");

    assertThat(alpha.replace("alpha", "X")).isNotEqualTo(beta.replace("beta", "X"));
  }

  @Test
  void anyNameContainingFailAlwaysReportsAnError() {
    var source = new SimulatedImageSource(new MutableClock(START), Duration.ofSeconds(20));

    var results = source.findByNames(List.of("delta-fail"));

    assertThat(results.get(0).error()).isPresent();
    assertThat(results.get(0).release()).isEmpty();
  }

  private String reference(SimulatedImageSource source, String name) {
    return source.findByNames(List.of(name)).get(0).release().orElseThrow().reference();
  }

  private static final class MutableClock extends Clock {
    private Instant now;

    MutableClock(Instant now) {
      this.now = now;
    }

    void advance(Duration amount) {
      now = now.plus(amount);
    }

    @Override
    public Instant instant() {
      return now;
    }

    @Override
    public java.time.ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(java.time.ZoneId zone) {
      return this;
    }
  }
}
```

`differentImagesAdvanceOutOfStep` puede fallar de forma legítima si dos nombres caen en el mismo desfase. Si ocurre, cambia los nombres del test: es una propiedad estadística del reparto, no un invariante.

- [ ] **Step 2: Ejecutar y comprobar que falla**

```bash
./gradlew test --tests "*SimulatedImageSourceTest*"
```

Expected: no compila — el constructor con `Clock` no existe.

- [ ] **Step 3: Implementar**

```java
package io.github.shizukajiku.imagewatch.infrastructure.remote;

import io.github.shizukajiku.imagewatch.application.ImageResult;
import io.github.shizukajiku.imagewatch.application.ImageSource;
import io.github.shizukajiku.imagewatch.domain.ImageRelease;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Origen de prueba cuyas versiones avanzan con el tiempo. Es lo que permite observar el
 * comportamiento en tiempo real sin acceso al origen real.
 *
 * <p>La versión se <em>deriva</em> del tiempo transcurrido en lugar de mantener un contador, de
 * modo que el resultado es determinista para un reloj dado y los tests no necesitan esperar.
 *
 * <p>Cualquier nombre que contenga {@code fail} devuelve error siempre, para poder ejercitar el
 * estado de error y el aislamiento por imagen.
 */
public final class SimulatedImageSource implements ImageSource {

  private final Clock clock;
  private final Instant startedAt;
  private final Duration bumpEvery;

  public SimulatedImageSource(Clock clock, Duration bumpEvery) {
    this.clock = clock;
    this.startedAt = clock.instant();
    this.bumpEvery = bumpEvery;
  }

  @Override
  public List<ImageResult> findByNames(List<String> names) {
    return names.stream().map(this::simulate).toList();
  }

  private ImageResult simulate(String name) {
    if (name.contains("fail")) {
      return ImageResult.failed(name, "Origen simulado: fallo forzado para '" + name + "'");
    }
    var seconds = bumpEvery.toSeconds();
    var elapsed = Duration.between(startedAt, clock.instant()).toSeconds();
    var offset = Math.floorMod(name.hashCode(), seconds);
    var patch = (elapsed + offset) / seconds;
    var reference = "registry.local/" + name + ":1.0." + patch;
    return ImageResult.found(new ImageRelease(name, reference, LocalDateTime.now(clock)));
  }
}
```

- [ ] **Step 4: Cablearlo en `Main`**

```java
    var source =
        config.simulationMode()
            ? new SimulatedImageSource(Clock.systemUTC(), Duration.ofSeconds(20))
            : new HttpImageSource(
                HttpClientFactory.create(config.ignoreSslErrors()), config.remoteUrl());
```

- [ ] **Step 5: Ejecutar**

```bash
./gradlew test --tests "*SimulatedImageSourceTest*"
```

Expected: los cuatro pasan.

- [ ] **Step 6: Commit**

```bash
./gradlew spotlessApply && ./gradlew check
git add -A
git commit -m "feat: origen simulado con versiones que avanzan con el tiempo

La versión se deriva del tiempo transcurrido en vez de mantener un contador:
determinista para un reloj dado, así que los tests no esperan. Cada imagen
arranca con un desfase según su nombre para que los incrementos queden
escalonados.

Cualquier nombre que contenga 'fail' devuelve error siempre, lo que permite
ejercitar el estado de error sin tocar la red."
```

---

### Task 11: Diagnóstico visible

**Files:**
- Modify: `gradle/libs.versions.toml`, `build.gradle.kts`
- Create: `src/main/resources/logback.xml`
- Modify: `VersionPollingService.java`, `HttpClientFactory.java`, `TeamsNotificationAdapter.java`, `TrayUi.java`

- [ ] **Step 1: Añadir las dependencias al catálogo**

```toml
slf4j = "2.0.16"
logback = "1.5.18"
```

```toml
slf4j-api = { module = "org.slf4j:slf4j-api", version.ref = "slf4j" }
logback-classic = { module = "ch.qos.logback:logback-classic", version.ref = "logback" }
```

En `build.gradle.kts`:

```kotlin
    implementation(libs.slf4j.api)
    runtimeOnly(libs.logback.classic)
```

`logback` va como `runtimeOnly` a propósito: el código compila solo contra la fachada, así que no puede acoplarse a la implementación por accidente.

- [ ] **Step 2: Crear `logback.xml`**

```xml
<configuration>
  <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
    <encoder>
      <pattern>%d{HH:mm:ss.SSS} %-5level %logger{20} - %msg%n</pattern>
    </encoder>
  </appender>

  <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
    <file>${user.home}/.notifier/imagewatch.log</file>
    <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
      <fileNamePattern>${user.home}/.notifier/imagewatch.%d{yyyy-MM-dd}.log</fileNamePattern>
      <maxHistory>7</maxHistory>
      <totalSizeCap>20MB</totalSizeCap>
    </rollingPolicy>
    <encoder>
      <pattern>%d{ISO8601} %-5level %logger{36} - %msg%n</pattern>
    </encoder>
  </appender>

  <root level="INFO">
    <appender-ref ref="CONSOLE"/>
    <appender-ref ref="FILE"/>
  </root>
</configuration>
```

El fichero importa más que la consola: en una aplicación que se abre con doble clic, nadie ve la salida estándar.

- [ ] **Step 3: Sustituir las escrituras a la salida estándar**

En cada clase afectada:

```java
  private static final Logger LOG = LoggerFactory.getLogger(VersionPollingService.class);
```

| Fichero | Sustitución |
|---|---|
| `VersionPollingService` | El `System.err` del `publish` de la Task 8 pasa a `LOG.warn`. Añade `LOG.debug("Ciclo completado: {} imágenes, {} pendientes", ...)` al final de `poll()`. |
| `HttpClientFactory` | El aviso de validación TLS desactivada pasa a `LOG.warn`. |
| `TeamsNotificationAdapter` | El `System.out` pasa a `LOG.info`. |
| `TrayUi` | El aviso de bandeja no disponible pasa a `LOG.warn`. |

- [ ] **Step 4: D4 — borrar la clase de demostración manual**

`NotificationDemoMain` escribe dos veces en la salida estándar, así que la
comprobación del paso siguiente fallaría. No se convierte a SLF4J: **se borra**.

Existía para probar a mano el globo del sistema, que esta migración sustituye, y
su función —ver el comportamiento sin tocar la red— la cubre mejor el origen
simulado de la Task 10, que además tiene tests.

```bash
git rm src/main/java/io/github/shizukajiku/imagewatch/demo/NotificationDemoMain.java
rmdir src/main/java/io/github/shizukajiku/imagewatch/demo 2>/dev/null || true
```

El plan original la borraba en la Fase 4; adelantarlo elimina una contradicción
con la verificación de este paso y deja código menos, no más.

- [ ] **Step 5: Comprobar que no queda ninguna**

```bash
grep -rn "System\.out\.print\|System\.err\.print" src/main/java && echo "QUEDAN RESTOS" || echo "sin restos"
```

Expected: `sin restos`.

- [ ] **Step 6: Verificar que el fichero se escribe**

```bash
./gradlew run
```

Ciérrala y comprueba:

```bash
ls -la ~/.notifier/imagewatch.log && tail -5 ~/.notifier/imagewatch.log
```

Expected: el fichero existe y contiene al menos una línea del ciclo de verificación.

- [ ] **Step 7: Commit**

```bash
./gradlew spotlessApply && ./gradlew check
git add -A
git commit -m "feat: diagnóstico por SLF4J con fichero rotativo

Los avisos y errores iban a la salida estándar, que en una aplicación de
escritorio no ve nadie. Ahora van también a ~/.notifier/imagewatch.log, con
rotación diaria y tope de 20MB.

logback entra como runtimeOnly: el código compila solo contra la fachada y no
puede acoplarse a la implementación."
```

---

### Task 12: La interfaz se actualiza sola

La tarea que hace visible todo lo anterior.

**Files:**
- Modify: `ui/ImageManagerFrame.java`, `ui/TrayUi.java`, `Main.java`

- [ ] **Step 1: D6 — retirar el `enum Status` propio de la ventana**

La ventana tiene un `enum Status` de tres valores, anterior a `ImageStatus`. Al
mapear sobre él, `ERROR` y `UNKNOWN` colapsan en el mismo:

```java
case ERROR, UNKNOWN -> Status.UNKNOWN;   // la píldora dice "Sin verificar"
```

Una imagen que **falló** se etiqueta igual que una que **nunca se comprobó**. Son
cosas distintas, y distinguirlas es un entregable de esta fase, no de la
siguiente: el mensaje de error aparece en la línea de debajo, pero la píldora
—que es lo que se lee de un vistazo— dice otra cosa.

Borra `private enum Status { OK, PENDING, UNKNOWN }` y usa `ImageStatus` en el
record `Row`. En `StatusRenderer`, los tres `switch` ganan su cuarto caso:

```java
      var bg =
          switch (data.status()) {
            case PENDING -> PENDING_BG;
            case OK -> OK_BG;
            case ERROR -> PENDING_BG;
            case UNKNOWN -> SURFACE_3;
          };
      var fg =
          switch (data.status()) {
            case PENDING -> PENDING_FG;
            case OK -> OK_FG;
            case ERROR -> PENDING_FG;
            case UNKNOWN -> TEXT_2;
          };
      var text =
          switch (data.status()) {
            case PENDING -> "Nueva versión";
            case OK -> "Al día";
            case ERROR -> "Error";
            case UNKNOWN -> "Sin verificar";
          };
```

`ERROR` reutiliza la paleta roja de `PENDING` a propósito: son los dos estados
que piden atención, y añadir un color más para distinguirlos aporta menos que el
texto, que ya los separa.

- [ ] **Step 2: D5 — borrar el método huérfano**

`ImageManagerFrame.registryOf` quedó sin llamantes al pasar el cálculo del
registry a `ImageState`. Lo confirman las inspecciones del IDE: *«Private method
registryOf(String) is never used»*. Bórralo.

- [ ] **Step 3: Sustituir el mapeo de filas**

`ImageManagerFrame` construye hoy sus filas desde `service.lastSnapshot()` y calcula el estado por su cuenta. Ahora consume `ImageState` directamente:

```java
  private Row toRow(String name, ImageState image) {
    if (image == null) {
      return new Row(name, "sin verificar", "—", "—", Status.UNKNOWN, "Sin verificar todavía");
    }
    var local = image.local().map(Version::value).orElse("—");
    var remote = image.remote().map(Version::value).orElse("—");
    var when =
        image.error().isPresent()
            ? image.error().get()
            : "Verificado " + WHEN_FORMAT.format(
                LocalDateTime.ofInstant(image.lastCheckedAt(), ZoneId.systemDefault()));
    var status =
        switch (image.status()) {
          case OK -> Status.OK;
          case PENDING -> Status.PENDING;
          case ERROR, UNKNOWN -> Status.UNKNOWN;
        };
    return new Row(name, image.registry(), local, remote, status, when);
  }
```

La etiqueta pasa de *"Detectado \<marca del origen\>"* a *"Verificado \<marca local\>"*, que es lo que decía querer decir.

- [ ] **Step 4: Suscribir la ventana**

En el constructor de `ImageManagerFrame`:

```java
    this.listener = snapshot -> SwingUtilities.invokeLater(() -> applySnapshot(snapshot));
    service.addListener(listener);
```

`SwingUtilities.invokeLater` es obligatorio: el oyente se invoca en el hilo del planificador, y tocar componentes Swing fuera del hilo de despacho de eventos produce fallos intermitentes e irreproducibles.

Y el método:

```java
  private void applySnapshot(PollSnapshot snapshot) {
    this.snapshot = snapshot;
    refresh();
  }
```

`refresh()` deja de llamar a `service.lastUpdates()` y usa el campo `snapshot`.

- [ ] **Step 5: Darse de baja al cerrar**

La ventana se oculta al cerrarse, pero si alguna vez se descarta hay que quitar el oyente, o el servicio retendría una referencia a una ventana muerta:

```java
    addWindowListener(
        new WindowAdapter() {
          @Override
          public void windowClosed(WindowEvent e) {
            service.removeListener(listener);
          }
        });
```

- [ ] **Step 6: Añadir la acción «visto» a las filas pendientes**

Sin un gesto para reconocer, el estado pendiente introducido en la Task 7 no se
podría limpiar nunca desde la interfaz.

En `ActionsEditor`, antes de los botones de editar y borrar:

```java
      var seen = iconButton("icons/plus.svg");
      seen.setToolTipText("Marcar como vista");
      seen.addActionListener(
          e -> {
            fireEditingStopped();
            service.acknowledge(model.rows.get(editingRow).name());
            refresh();
          });
```

Se reutiliza un icono existente a propósito: los iconos definitivos llegan con
Compose en la Fase 4, y añadir un SVG ahora sería trabajo que se tira.

En `ActionsRenderer`, añade el mismo botón al grupo para que renderizador y editor
coincidan; si no, el botón aparece y desaparece al entrar en edición.

- [ ] **Step 7: Verificación manual — es el criterio de esta fase**

```bash
IMAGE_NAMES=alpha,beta,gamma,delta-fail POLL_INTERVAL_SECONDS=10 ./gradlew run
```

Abre la ventana y **déjala abierta sin tocarla**. Con incrementos cada 20 s y verificación cada 10 s, en un par de minutos debes observar, sin reabrir la ventana:

1. Las versiones de la columna remota **cambian solas**.
2. Alguna fila pasa de *Al día* a *Nueva versión* por sí sola.
3. `delta-fail` muestra su mensaje de error y **las otras tres siguen actualizándose**.
4. Aparece un aviso en la bandeja **la primera vez** que una imagen pasa a pendiente, y **no** se repite en los ciclos siguientes mientras siga pendiente.

Los puntos 1 y 2 no ocurrían antes en absoluto. El 3 y el 4 eran defectos.

- [ ] **Step 8: Commit**

```bash
./gradlew spotlessApply && ./gradlew check
git add -A
git commit -m "feat: la ventana se actualiza sola al recibir cada snapshot

Leía un campo volatile solo al abrirse: con la ventana abierta, los datos
quedaban congelados hasta reabrirla. Ahora se suscribe como PollListener y
repinta en el hilo de despacho de eventos.

La columna de estado pasa de 'Detectado <marca del origen>' a
'Verificado <marca local>', que es lo que pretendía decir."
```

---

### Task 13: Cierre de la fase

- [ ] **Step 1: Batería completa**

```bash
./gradlew clean check
```

Expected: `BUILD SUCCESSFUL`. Recuento esperado, en torno a 40 tests: 5 de
`VersionTest`, 3 de `JsonTrackedImageStoreTest`, 13 de `VersionPollingServiceTest`,
6 de `PollingControllerTest`, 5 de `PollSnapshotTest`, 4 de `HttpImageSourceTest`
y 4 de `SimulatedImageSourceTest`.

- [ ] **Step 2: Comprobar la cobertura del núcleo**

Kover llega en la Fase 4; por ahora la comprobación es de bulto: cada clase de `domain/` y `application/` debe tener un fichero de test, salvo los puertos, que son interfaces.

```bash
ls src/main/java/io/github/shizukajiku/imagewatch/domain/ \
   src/main/java/io/github/shizukajiku/imagewatch/application/
ls src/test/java/io/github/shizukajiku/imagewatch/domain/ \
   src/test/java/io/github/shizukajiku/imagewatch/application/
```

- [ ] **Step 3: Barrido de la denylist**

```bash
while IFS= read -r p; do
  [ -z "$p" ] && continue
  case "$p" in \#*) continue ;; esac
  if git grep -qiE -- "$p" HEAD 2>/dev/null; then echo "RESIDUO: $p"; fi
done < .denylist.local
echo "barrido completo"
```

- [ ] **Step 4: D7 — escribir el README**

El repositorio no tiene ninguno. Quien lo clone —incluido tú dentro de seis
meses— no ve cómo arrancarlo ni qué hace falta para que funcione. Crea
`README.md` cubriendo, como mínimo:

- Qué hace la aplicación, en dos frases.
- Requisitos: JDK 25. El wrapper trae Gradle, no hace falta instalarlo.
- Órdenes: `./gradlew run`, `./gradlew check`, `./gradlew spotlessApply`.
- Variables de entorno reconocidas y su valor por defecto: `IMAGE_NAMES`,
  `IMAGE_VERSION_URL`, `POLL_INTERVAL_SECONDS`, `SIMULATION_MODE`,
  `NOTIFIER_STATE_FILE`, `IGNORE_SSL_ERRORS`, `TEAMS_ENABLED`.
- Que `SIMULATION_MODE` está **activo por defecto**, de modo que un clon limpio
  arranca y funciona sin configurar nada.
- Dónde vive el estado: `~/.notifier/` — imágenes vigiladas, versiones
  reconocidas y el log.
- **Que `.denylist.local` no se versiona y hay que recrearlo tras clonar**, o el
  hook `pre-commit` bloqueará todos los commits. Y que se instala con
  `scripts/install-hooks.sh`.

El último punto es el que más ahorra: sin él, un clon nuevo parece roto.

- [ ] **Step 5: Integrar**

```bash
git push -u origin feature/fase-3-eventos
```

Y usa la skill `superpowers:finishing-a-development-branch` para decidir cómo integrar en `main`.

---

## Criterio de finalización

- [ ] `./gradlew clean check` en verde.
- [ ] Con la ventana abierta y sin tocarla, las versiones cambian y los estados transicionan solos.
- [ ] Una imagen que falla muestra su error y no impide que las demás se actualicen.
- [ ] Una imagen pendiente se notifica **una vez**, no en cada ciclo.
- [ ] Una imagen pendiente **sigue pendiente** en los ciclos siguientes, y solo pasa a `OK` cuando se marca como vista.
- [ ] El primer arranque con imágenes nuevas **no** dispara un aviso por cada una.
- [ ] `~/.notifier/imagewatch.log` se escribe.
- [ ] Cero `System.out.print` / `System.err.print` en `src/main/java`.
- [ ] Barrido de denylist limpio.

## Decisión: qué significa «versión local»

Detectada al revisar el flujo completo de `poll()`, y **resuelta**: se implementa
en la Task 7.

El almacén guardaba lo que dijo el origen en el ciclo anterior, escrito en el
mismo ciclo en que lo detectaba. Reconocer era automático e instantáneo, de modo
que `PENDING` duraba un solo ciclo y la columna de estado decía *Al día* casi
siempre. Perderse el aviso equivalía a perder la información.

**El almacén pasa a significar «versión reconocida».** Se escribe solo en dos
momentos: al establecer la línea base de una imagen nueva, y al reconocer
explícitamente. `PENDING` persiste hasta que el usuario lo atiende, y sobrevive a
reiniciar la aplicación porque está en disco.

Esto importa más allá de esta fase: las animaciones de transición de estado, las
píldoras de color, el filtro y el resumen «N con actualización pendiente» de las
Fases 4 y 5 se construyen todos sobre ese campo. Con la semántica anterior habrían
representado un valor que está en `OK` casi todo el tiempo, y la animación
`OK → PENDING` habría sido un parpadeo en lugar de una transición.

Se descartó una tercera opción, que el usuario declare la versión que tiene
desplegada. Es la única que responde «estás desactualizado» con exactitud, pero
obliga a mantener a mano un dato que ya vive en los ficheros de despliegue.
Fricción diaria a cambio de una precisión que esta herramienta no necesita: su
trabajo es avisar de que salió algo nuevo, no auditar despliegues.

## Fuera de esta fase

- **Fase 4** (Compose): se planifica tras la prueba de concepto de tres incógnitas — Compose Desktop sobre JDK 25, carga de los iconos SVG y el composable de bandeja en Windows 11.
- **Fase 5** (ajustes, errores en pantalla, toasts, sonido): depende de la forma que tome la Fase 4.

Dos apuntes recogidos durante la ejecución de las fases anteriores, para la Fase 4:

- FlatLaf emite `WARNING: java.lang.System::load has been called ... Restricted methods will be blocked in a future release`. Se resuelve solo al retirar FlatLaf.
- Kover, el umbral de cobertura del 80 % sobre `domain/` y `application/`, y detekt entran con el build de Kotlin.
