# Migración de ImageWatch a la plantilla KMP de IntelliJ IDEA

- **Fecha:** 2026-09-07
- **Origen:** `%ORIGEN%` (Compose Desktop mono-módulo, Java 25 + Kotlin 2.3.0)
- **Destino:** `C:\Users\shizu\IdeaProjects\ImageWatch` (plantilla Compose Multiplatform: `shared` + `desktopApp`)
- **Principio rector:** nos adaptamos a la plantilla, no la plantilla a nosotros.

## 1. Objetivo

Reemplazar el proyecto actual por uno construido sobre la plantilla KMP, con toda la
lógica portada de Java a Kotlin, sin perder funcionalidad, cobertura ni el
empaquetado MSI. Al terminar y validar, el repositorio privado actual se borra.

Restricciones del encargo:

- Todo en local. Sin commits ni pull requests en el proyecto nuevo.
- El proyecto viejo permanece intacto hasta que el nuevo esté validado.
- El borrado del repositorio privado y de la carpeta local es el último paso y
  requiere confirmación explícita en ese momento.
- **El historial de git no se conserva ni se archiva.** Decisión tomada y
  asumida como irreversible.

## 2. Estado de partida

### Origen

Mono-módulo Gradle. Lógica de negocio en Java (1378 líneas de producción, 1487
de test), interfaz en Kotlin/Compose (3942 líneas de producción, 1529 de test).
Arquitectura hexagonal: `domain`, `application` (puertos y servicios),
`infrastructure` (adaptadores Jackson, `java.net.http`, `java.nio`), `ui`.
Herramienta de calidad: spotless (ktlint y google-java-format), checkstyle,
kover con umbral 80% sobre `domain.*` y `application.*`.

### Destino

Plantilla generada por IntelliJ IDEA: `shared` (Kotlin Multiplatform, único
target `jvm()`) y `desktopApp` (Kotlin JVM). Kotlin 2.4.10, Compose
Multiplatform 1.11.1, material3 1.11.0-alpha07, Gradle 9.1.0, toolchain JDK 21,
`org.gradle.configuration-cache=true`. Git inicializado sin commits. El paquete
ya fue renombrado a `io.github.shizukajiku.imagewatch`; queda pendiente el mismo
cambio en `desktopApp/build.gradle.kts`, que aún dice `io.github.shizuka`.

## 3. Verificaciones realizadas

Se hicieron cuatro comprobaciones antes de escribir este documento. Las cuatro
salieron favorables, y tres de ellas eliminan riesgos que este plan daba por
supuestos.

**La plantilla compila en esta máquina.** `:desktopApp:compileKotlin` y
`:shared:compileKotlinJvm` en 27 s con `JAVA_HOME` apuntando a
`C:\Users\shizu\.jdks\ms-21.0.12.1`, caché de configuración incluida.

**El descenso de Compose 1.12.0 a 1.11.1 no afecta a este código.** Se compiló
en `commonMain` el conjunto completo de APIs de Material3 que usa la interfaz
—`AlertDialog`, `Button`, `DropdownMenu`, `DropdownMenuItem`, `FilterChip`,
`HorizontalDivider`, `IconButton`, `LinearProgressIndicator(progress = {})`,
`MaterialTheme`, `OutlinedTextField`, `Slider`, `Surface`, `Switch`, `Text`,
`TextButton`, `darkColorScheme`, `lightColorScheme`— más `LocalWindowInfo`.
Todo resuelve. El material3 de la plantilla (1.11.0-alpha07) es de hecho más
reciente que el que arrastra Compose 1.12 (1.9.0): el descenso afecta solo al
núcleo. Además `WindowDraggableArea` y `loadSvgPainter` resuelven desde
`shared/jvmMain` sin declarar `compose.desktop.currentOs` en ese módulo.

**El paso de JDK 25 a 21 no rompe nada.** El código Java no usa ninguna API
posterior a 21; `getFirst()` y `getLast()` son de `SequencedCollection`, Java
21. El JDK 21 ya está instalado.

**`kotlin.time` cubre las necesidades de `commonMain`.** `Instant`,
`Clock.System.now()`, `Instant.parse` y `Duration` compilan sin opt-in en
Kotlin 2.4.10. La fase A no necesita kotlinx-datetime.

**Kover 0.9.1 no sirve; kover 0.9.9 sí.** La versión que usa el proyecto actual
falla al configurar el módulo multiplataforma con Kotlin 2.4.10:

```
Could not determine the dependencies of task ':shared:koverGenerateArtifactJvm'.
> Could not get unknown property 'compileKotlinTask' for compilation 'dev'
  (target jvm (jvm)) of type KotlinJvmCompilation_Decorated
```

Con 0.9.9, `:shared:koverVerify` ejecuta `jvmTest`, genera el artefacto y pasa,
con la caché de configuración activa. Además el plugin debe aplicarse **dentro
de `shared`**: declararlo solo en la raíz y agregar con `kover(project(":shared"))`
falla por variantes incompatibles (`org.gradle.usage` `java-api` frente a
`kover`).

**Spotless 8.10.1 convive con la caché de configuración.** `spotlessCheck`
ejecuta y almacena la entrada de caché sin incidencias. Señala infracciones en
los propios ficheros de la plantilla —`settings.gradle.kts` no termina en salto
de línea—, que se corrigen con `spotlessApply` en el primer paso de la
migración.

## 4. Arquitectura destino

```
ImageWatch/
  settings.gradle.kts            plantilla, rootProject.name = "ImageWatch"
  build.gradle.kts               plantilla mas spotless y kover sobre subprojects
  gradle/libs.versions.toml      plantilla mas semver4j, jackson, slf4j, logback,
                                 junit5, assertj, coroutines-test, kover, spotless
  shared/
    src/commonMain/kotlin/       domain, application, config, ui portable
    src/jvmMain/kotlin/          infrastructure, actuals, ui dependiente de AWT
    src/jvmMain/resources/       icons/*.svg, sounds/*.wav, sounds/NOTICE
    src/commonTest/kotlin/       dominio y aplicacion, con kotlin.test
    src/jvmTest/kotlin/          infraestructura, con JUnit 5 y AssertJ
  desktopApp/
    src/main/kotlin/Main.kt      bandeja, cableado, configuracion del MSI
    src/main/resources/          logback.xml
  .github/  scripts/  docs/  design/  .superpowers/  README.md
```

`config/checkstyle` no se migra: checkstyle solo analiza Java, y tras el port no
queda ningún `.java`.

### Reparto de `shared`

`commonMain` recibe:

- `domain`: `ImageRelease`, `ImageState`, `ImageStatus`, `PollSnapshot` y la
  declaración `expect` de `Version`.
- `application`: los puertos `ConfigStore`, `ImageSource`, `ImageStateStore`,
  `NotificationPort`, `PollListener`, `SilencedImageStore`,
  `TrackedImageStore`; el tipo `ImageResult`; y los servicios
  `VersionPollingService` y `PollingController`.
- `config`: `AppConfig`, `ThemePreference`.
- `ui`: `theme` completo, `components/StatusBadge`, `components/VersionPill`,
  `dialogs/ImageDialogs`, `images/ImageRow`, `images/ImagesScreen`,
  `images/ImagesViewModel`, `settings/SettingsScreen`,
  `settings/SettingsViewModel`, `toast/ToastState`,
  `toast/ToastNotificationPort`, los enumerados `AppSvg` y `Sound`, el
  composable `SvgIcon`, y las declaraciones `expect` de `loadAppSvg`, `Sounds`
  y `Log`.

`jvmMain` recibe:

- `infrastructure/persistence` completo (Jackson y `java.nio.file`).
- `infrastructure/remote` completo (`java.net.http`).
- Los `actual` de `Version` (semver4j), `Sounds` (`javax.sound.sampled`),
  `loadAppSvg` (`loadSvgPainter` sobre el classpath) y `Log` (slf4j).
- `ui/components/TitleBar`, `ui/toast/ToastWindow`, `ui/AppIconPainter`.
- Los recursos: iconos SVG y sonidos.

`desktopApp` recibe solo `Main.kt` y `logback.xml`.

### Las cuatro costuras `expect`/`actual`

`loadAppSvg` y `Sounds` los consume código que vive en `commonMain`
—`ImageRow`, `ImagesScreen`, `SettingsScreen`, `ImagesViewModel`,
`SettingsViewModel`, `ToastNotificationPort`—, así que no pueden limitarse a
`jvmMain`. `Version` arrastra semver4j, que tampoco es multiplataforma. Y
`VersionPollingService`, que es núcleo puro, registra con slf4j.

Los enumerados `AppSvg` y `Sound` sí son Kotlin puro y viven en `commonMain`;
solo cruzan la costura la carga del recurso y la reproducción del audio.

La cuarta costura es una fachada de registro mínima, `Log`, con un único
consumidor: `VersionPollingService`. `Main.kt`, `HttpClientFactory` y `Sounds`
quedan en el lado JVM y siguen usando slf4j directamente. La fachada pierde la
evaluación diferida de los `{}` de slf4j a cambio de plantillas de Kotlin; con
un puñado de líneas por ciclo de sondeo, el coste es irrelevante.

Se resuelven con `expect`/`actual` y no convirtiéndolos en puertos inyectados:
es el mecanismo idiomático de KMP para exactamente este caso, y mantiene
intactas las llamadas existentes. Si más adelante hiciera falta sustituirlos en
pruebas, la conversión a puerto sigue disponible.

## 5. Fase A: reestructura y port a Kotlin

### Método

El conversor Java a Kotlin de IntelliJ no es accesible desde el MCP: el servidor
expone 74 herramientas y ninguna realiza la conversión. El port se hace en tres
tiempos:

1. Los `.java` se copian a `shared/src/jvmMain/java`, donde el módulo ya tiene
   Kotlin y la tarea `compileJvmMainJava` está cableada.
2. El usuario abre ImageWatch en IntelliJ y lanza `Ctrl+Alt+Shift+K` sobre ese
   directorio: una sola acción convierte todo el árbol.
3. Los `.kt` resultantes se reparten a `commonMain` y `jvmMain` según la sección
   4, y se someten a una pasada de idioma.

### Traducciones obligadas

El conversor produce Kotlin que compila, no Kotlin idiomático. La pasada
posterior aplica:

| Java | Kotlin |
|---|---|
| `record` | `data class` |
| `Optional<T>` | `T?` |
| `java.time.Instant`, `Clock`, `Duration` | `kotlin.time.Instant`, `Clock`, `Duration` |
| `ConcurrentHashMap`, `CopyOnWriteArrayList`, `AtomicLong` | estado inmutable o `Mutex` |
| `Stream` | `Sequence` o funciones de colección |
| getters `value()`, `name()` | propiedades |
| `!!` residual del conversor | tipos no nulos o `?.` |

Los cuatro ficheros Kotlin de interfaz que hoy importan `java.time`
—`Main.kt`, `ImagesViewModel.kt`, `SettingsViewModel.kt`, `ToastState.kt`—
migran a `kotlin.time` en la misma pasada.

La dependencia jspecify desaparece: la usa un solo fichero y sus anotaciones de
nulabilidad no tienen sentido en Kotlin, donde el sistema de tipos ya las
expresa.

### Tests

AssertJ y JUnit 5 son solo JVM, así que los tests se parten en dos:

- Dominio y aplicación —`VersionTest`, `PollSnapshotTest`,
  `VersionPollingServiceTest`, `PollingControllerTest`— se reescriben a
  `kotlin.test` en `commonTest`. Las aserciones quedan más verbosas; es el
  precio de que el núcleo sea multiplataforma.
- Infraestructura —los cinco tests de `persistence` y los tres de `remote`— se
  quedan en `jvmTest` con JUnit 5 y AssertJ sin tocar la lógica de prueba.
- Los tests de interfaz que ya son Kotlin pasan a `commonTest` o `jvmTest`
  según dónde acabe el sujeto.

`jvmTest` necesita `useJUnitPlatform()` en su tarea de test.

Reescribir aserciones a mano puede debilitarlas sin que nada avise: un
`assertThat(x).containsExactly(a, b)` degradado a una comprobación de tamaño
sigue pasando en verde y ya no prueba nada. Por eso cada test portado a
`commonTest` se valida rompiendo a propósito la línea de producción que cubre y
comprobando que falla. Si no falla, la aserción se perdió en la traducción.

### Empaquetado

El bloque `nativeDistributions` se copia literal del proyecto actual:
`packageName = "ImageWatch"`, `packageVersion = "1.0.1"`,
`upgradeUuid = "fd333cd8-6981-4e85-8e89-db2f23608c85"`, `perUserInstall = true`,
`menu`, `shortcut`, `dirChooser`. La plantilla trae
`packageName = "io.github.shizuka.imagewatch"` y ningún `upgradeUuid`: dejarlo
así haría que Windows instalase un segundo ImageWatch en lugar de actualizar el
existente.

Dos hacks del proyecto actual desaparecen al bajar a JDK 21: el
`jvmArgs += "--enable-native-access=ALL-UNNAMED"` y el
`System.getenv("JAVA_HOME")?.let { javaHome = it }`. El segundo, además, es
incompatible con la caché de configuración que la plantilla activa.

### Calidad

Spotless entra primero, seguido de `spotlessApply` para dejar limpios los
ficheros que trae la plantilla. Kover entra después, **en la versión 0.9.9 y
aplicado dentro de `shared`**, conservando los filtros `domain.*` y
`application.*` y el umbral del 80%. Checkstyle no se reintroduce: queda sin
objetivo en cuanto desaparece el último `.java`, y todo el código acaba en
Kotlin.

### Integración continua

`tests.yml` y `release.yml` se traen y se ajustan: JDK 21 en lugar de 25, y
rutas de tarea con prefijo de módulo (`:desktopApp:packageMsi`,
`:shared:koverVerify`).

### Puerta de salida de la fase A

No se empieza la fase B hasta que: `:desktopApp:run` arranca la aplicación,
todos los tests pasan, `koverVerify` cumple el 80%, `packageMsi` genera un
instalador que actualiza la instalación 1.0.1 existente en lugar de duplicarla,
y el usuario valida la interfaz a ojo.

## 6. Fase B: infraestructura multiplataforma

La fase B reescribe únicamente los `actual` y los adaptadores de `jvmMain` y
los sube a `commonMain`. Los puertos no se mueven, de modo que los tests de
`commonTest` escritos en la fase A sirven de red de seguridad sin modificarse.

| Se sustituye | Por |
|---|---|
| Jackson | kotlinx-serialization |
| `java.net.http.HttpClient` | Ktor client |
| `java.nio.file` | okio |
| `DateTimeFormatter` | kotlinx-datetime |
| semver4j | parseo y comparación propios en Kotlin |

Permanecen en `jvmMain` de forma definitiva, porque no tienen equivalente
multiplataforma y no lo necesitan: `Sounds`, `ToastWindow`, `TitleBar`,
`AppIconPainter` y la bandeja del sistema.

El reemplazo de semver4j es el punto más delicado. El `VersionTest` actual
tiene cinco casos: rechazo de cadena no reconocible, rechazo de nulo,
normalización de `.RELEASE`, orden semántico frente a orden textual, e igualdad
por texto original. Eso no basta para sustituir una librería de semver: no
cubre orden de pre-releases, metadatos de build, prefijo `v`, versiones
parciales ni las etiquetas que devuelven los registries reales.

La mitigación tiene dos partes. Primero, en la fase A se amplía `VersionTest`
con esos casos y se ejecutan **contra semver4j**, de modo que el contrato
registre el comportamiento que hoy existe y no el que suponemos. Ese conjunto
ampliado es lo que la implementación en Kotlin tiene que satisfacer.

Segundo, hay salida de emergencia: si el port de semver resulta más caro de lo
que vale, `Version` puede quedarse indefinidamente como `expect`/`actual` con
semver4j en el lado JVM. No bloquea nada del resto de la fase B, porque el
único coste es que un futuro target no JVM necesitaría su propio `actual`.

Los tests de `jvmTest` de la fase A se conservan hasta que cada adaptador nuevo
pase los mismos casos; entonces se promueven a `commonTest` y los antiguos se
retiran.

## 7. Riesgos vivos

| Riesgo | Estado | Mitigación |
|---|---|---|
| El conversor J2K requiere una acción manual del usuario | Resuelto | Una sola pulsación sobre un directorio; el reparto y la pasada de idioma son automáticos. Si el usuario prefiere no intervenir, el port se hace a mano |
| Identidad del MSI: `packageName` y `upgradeUuid` distintos duplicarían la instalación | Resuelto | Copiar el bloque literal, y probar instalando sobre la 1.0.1 ya presente para ver que actualiza |
| Kover 0.9.1 no configura el módulo KMP con Kotlin 2.4.10 | Resuelto y verificado | Kover 0.9.9, aplicado dentro de `shared` |
| Spotless frente a la caché de configuración | Descartado por prueba | `spotlessCheck` funciona con la caché activa |
| Aserciones debilitadas al reescribir tests a `kotlin.test` | Mitigado | Cada test portado se valida rompiendo la producción que cubre y comprobando que falla |
| Compose Resources no carga `.svg` como drawable | No aplica | Los iconos siguen en el classpath de `jvmMain`, exactamente como hoy |
| Fase B: el contrato de `Version` es demasiado pobre para sustituir semver4j | Mitigado | Ampliar `VersionTest` en la fase A contra semver4j, y dejar como salida que `Version` siga siendo `expect`/`actual` con semver4j |
| Fase B: tres dependencias nuevas reescriben toda la infraestructura | Mitigado | Los puertos no se tocan; los `jvmTest` de la fase A actúan como regresión |
| El historial de git se pierde al borrar el repositorio privado | **Sin solución** | Ninguna. Es una pérdida aceptada por decisión explícita del usuario, no un riesgo gestionado |

## 8. Fuera de alcance

- Cualquier plataforma que no sea escritorio. `shared` mantiene un único target
  `jvm()`; la fase B prepara el terreno pero no añade targets.
- Cambios funcionales. La migración no altera comportamiento observable.
- Rediseño de la interfaz. Se migra la de la fase 6 tal cual.
- Commits, ramas y pull requests en el proyecto nuevo.
