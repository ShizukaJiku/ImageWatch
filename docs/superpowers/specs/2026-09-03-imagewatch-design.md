# ImageWatch — Diseño de migración y reestructuración

**Fecha:** 2026-09-03
**Estado:** aprobado para planificación
**Alcance:** migración a Java 25, UI en Kotlin/Compose Desktop, actualización en
tiempo real, notificaciones, animaciones y sonido nuevos, reestructuración e
independización del origen de datos.

---

## 1. Objetivo

La aplicación vigila versiones de imágenes de contenedor en un registry remoto y
avisa cuando aparece una versión más nueva que la conocida localmente. Reside en
la bandeja del sistema.

Este diseño cubre seis objetivos, en orden de valor:

1. **Tiempo real.** La ventana abierta se actualiza sola cuando cambia el estado.
2. **Notificaciones propias.** Toasts diseñados y animados, no el globo del sistema.
3. **Animaciones.** El cambio de estado se ve ocurrir, no aparece de golpe.
4. **Sonido.** Aviso audible cuando el usuario no está mirando la pantalla, y
   refuerzo sonoro en las interacciones importantes.
5. **Reestructuración.** Romper la clase monolítica de UI; core en Java 25, UI en Kotlin.
6. **Independencia del origen.** Ningún dato de un proveedor concreto en el repositorio.

---

## 2. AS-IS

22 archivos Java, ~1.500 LOC, Maven, sin repositorio git, sin README, sin CI.

### Lo que se conserva

La arquitectura hexagonal está bien planteada y **no cambia de forma**:

- `domain/` — tipos sin dependencias externas.
- `application/` — puertos (interfaces) y servicios.
- `infrastructure/` — adaptadores (HTTP, persistencia JSON, notificaciones).
- `ui/` — presentación.
- `Main` — composition root; el cableado vive fuera del dominio y la aplicación.

El problema no es la arquitectura. Es la capa de UI, el modelo de actualización y
el acoplamiento a un proveedor concreto.

### Deficiencias identificadas

**Tiempo real — no existe.**
`PollingController` usa `scheduleWithFixedDelay` con 300 s por defecto.
`VersionPollingService.lastUpdates()` es un campo `volatile` que la UI lee
**solo al abrir la ventana**. No hay listener, evento ni observable. Con la
ventana abierta los datos quedan congelados hasta reabrirla.

**Notificaciones — obsoletas.**
`TrayIcon.displayMessage` produce el globo heredado de Windows: sin acciones,
sin persistencia en el Centro de actividades, sin agrupación. El campo
`lastFocusName`, que permite enfocar la fila al hacer clic, solo se rellena
cuando hay exactamente una actualización; con dos o más queda nulo.

**Un fallo tumba el ciclo completo.**
`findByNames` lanza excepción en la primera imagen que falla. `poll()` la atrapa
y escribe en `stderr`. Resultado: **ninguna** imagen se actualiza y el usuario no
se entera.

**Notificación repetida indefinidamente.**
`poll()` notifica en cada ciclo por cada imagen pendiente. Con intervalo de 300 s
eso es un aviso cada cinco minutos, para siempre, por la misma versión.

**N+1 secuencial.**
`findByNames` hace una petición HTTP por nombre, en serie, sin concurrencia,
sin reintento y sin caché condicional.

**UI monolítica.**
`ImageManagerFrame` tiene 793 líneas: tema, tabla, cuatro renderers, un editor de
celda, dos diálogos, barra de polling y filtro de búsqueda. Usa un campo
`private static` mutable como singleton. La paleta está definida dos veces
(constantes `Color` y `installTheme()`), solo en oscuro. Cero animaciones.

**Etiqueta incorrecta.**
La columna de estado muestra `"Detectado " + updateTime`, pero `updateTime` es la
marca de tiempo **del remoto**, no del momento de la verificación local.

**Fallo de parseo no controlado.**
`Version` llama a `Semver.parse()`, que devuelve `null` ante una cadena no válida.
El `null` se guarda y provoca `NullPointerException` en `compareTo`.

**Diagnóstico invisible.**
Cinco `System.out/err.println` repartidos por el código. En una aplicación de
escritorio que se abre con doble clic, nadie ve esa salida.

**Build con residuos.**
`source`/`target` en 21 sin `--release`, lo que emite un aviso de módulos de
sistema. Lombok declarado como dependencia y como annotation processor con
**cero usos**. Una property declarada para una librería que ni siquiera figura
como dependencia.

**Cobertura.**
Cuatro tests en dos archivos, sin medición. `PollingController`, el adaptador
HTTP, `Version` y toda la UI no tienen cobertura alguna.

### Verificado y correcto (no tocar)

`tools.jackson.core:jackson-databind` 3.x declara dependencia sobre
`com.fasterxml.jackson.core:jackson-annotations` 2.x. Jackson 3 mantiene las
anotaciones en su paquete original de forma deliberada; no existe un paquete de
anotaciones bajo `tools.jackson`. El uso actual es correcto.

---

## 3. Decisiones tomadas

| Decisión | Resolución |
|---|---|
| Origen de datos | Solo polling. Sin push del servidor. |
| Toolkit de UI | Compose Desktop. |
| Build | Gradle con Kotlin DSL, módulo único. |
| Notificaciones | Toast propio en Compose. Sin toast nativo del sistema. |
| Sonido | `javax.sound.sampled` con clips WAV precargados. Cuatro sonidos. Sin dependencias. |
| Assets de audio | Pack de dominio público (CC0), aprobados antes de incorporarlos. |
| Validación TLS | Se mantiene el comportamiento actual (endpoint accesible solo por VPN). |
| Parseo de versión | Se corrige el fallo de `null`. |
| Integración con Teams | Fuera de alcance. |
| Estrategia de migración | Estrangulamiento por fases; cada fase compila y ejecuta. |
| Nombre | **ImageWatch** |
| Coordenadas | `io.github.shizukajiku.imagewatch` |
| Repositorio | GitHub **privado** |

---

## 4. TO-BE — Estructura

Módulo Gradle único con dos source sets. El plugin de Kotlin compila Kotlin antes
que Java, de modo que el código Java puede referenciar clases Kotlin y viceversa.

```
settings.gradle.kts
build.gradle.kts
gradle/libs.versions.toml
scripts/install-hooks.sh

src/main/java/io/github/shizukajiku/imagewatch/     <- Java 25
  domain/         ImageRelease, Version, ImageState, ImageStatus, PollSnapshot
  application/    ImageSource, ImageStateStore, TrackedImageStore, ConfigStore,
                  NotificationPort, PollListener, AppConfig,
                  VersionPollingService, PollingController
  infrastructure/ remote/      HttpImageSource, ReleaseDto, HttpClientFactory,
                               SimulatedImageSource, ReloadableImageSource
                  persistence/ JsonImageStateStore, JsonTrackedImageStore,
                               JsonConfigStore

src/main/kotlin/io/github/shizukajiku/imagewatch/
  Main.kt                    <- composition root + application {}
  ui/theme/       Colors.kt, Theme.kt, Type.kt
  ui/components/  Pill.kt, StatusBadge.kt, SearchField.kt, IconButton.kt, AppIcon.kt
  ui/images/      ImagesScreen.kt, ImageRow.kt, ImagesViewModel.kt
  ui/settings/    SettingsScreen.kt
  ui/toast/       ToastHost.kt, ComposeToastAdapter.kt
  ui/sound/       Sounds.kt, Sound.kt

src/main/resources/
  sounds/         update.wav, error.wav, success.wav, toggle.wav
  NOTICE          <- procedencia y licencia de los assets de audio
```

Las 793 líneas de la clase de UI monolítica se reparten en unos ocho archivos de
60 a 150 líneas.

Cuatro notas sobre el árbol, porque lo que **no** aparece es tan relevante como
lo que sí:

- `AppConfig` deja el paquete `config/` propio y pasa a `application/`: es el
  contrato del puerto `ConfigStore`, no una capa aparte para un único record.
- **El paquete `infrastructure/notification/` desaparece por completo.** Sus dos
  adaptadores se eliminan (el del sistema operativo queda sustituido por los
  toasts propios; el de Teams está fuera de alcance). El único implementador de
  `NotificationPort` pasa a ser `ComposeToastAdapter`, que vive en la capa de UI
  por ser quien la conduce.
- La clase de demostración manual de notificaciones se elimina en la fase 4:
  existía para probar el globo del sistema, que ya no se usa. Su función la
  cubre el origen simulado descrito en el apartado 7.
- El dibujado del icono de la aplicación pasa de `BufferedImage` con
  `Graphics2D` a un `ImageVector` de Compose, compartido por la bandeja y la
  ventana igual que hoy.

### Cambios de herramienta

| Actual | TO-BE | Motivo |
|---|---|---|
| Maven | Gradle KTS + version catalog | Plugin oficial de Compose; empaquetado nativo |
| `source`/`target` 21 | `toolchain(25)` + `--release 25` | Elimina el aviso de módulos de sistema |
| shade-plugin | `compose.desktop.application` | Genera instalador, no solo un JAR |
| checkstyle (Maven) | checkstyle (plugin nativo de Gradle) | La configuración XML existente se reutiliza sin cambios |
| — | detekt | Análisis estático de Kotlin (detekt **no** analiza Java) |
| spotless-maven | spotless-gradle | google-java-format para Java, ktlint para Kotlin |
| — | Kover | Cobertura de ambos lenguajes en un informe |
| Lombok | eliminado | Cero usos |
| Property huérfana | eliminada | No corresponde a ninguna dependencia |

### Versiones fijadas

- **JDK 25** — disponible localmente.
- **Kotlin 2.3.0** — primera versión con soporte de Java 25.
- **Compose Multiplatform 1.12.0** — estable; incorpora ProGuard 7.8.0, mínimo
  exigido por JDK 25 para el empaquetado nativo.
- `jvmToolchain` y `jvmTarget` deben coincidir ambos en 25, o Gradle falla con
  `Inconsistent JVM Target Compatibility`.

### Nota de arranque

No hay Gradle instalado ni wrapper en el repositorio (el wrapper existente es de
Maven). La primera fase instala Gradle, genera el wrapper y lo versiona; a partir
de ahí la instalación global deja de importar.

---

## 5. Modelo de datos y flujo de eventos

El núcleo permanece en Java puro, **sin coroutines**. Expone un listener; Kotlin
lo envuelve.

### Tipos de dominio

```java
public enum ImageStatus { OK, PENDING, UNKNOWN, ERROR }

public record ImageState(
    String name,
    Optional<Version> local,
    Optional<Version> remote,
    String registry,
    ImageStatus status,
    Optional<String> error,
    Instant lastCheckedAt) {}

public record PollSnapshot(List<ImageState> images, Instant at) {}
```

`ImageState` sustituye al record `Row` privado que hoy vive dentro de la clase de
UI. Con ello la presentación deja de contener lógica de dominio, y
`lastCheckedAt` corrige la etiqueta incorrecta descrita en el AS-IS.

### Aislamiento de fallos

El puerto pasa a devolver resultado por imagen:

```java
public record ImageResult(String name, Optional<ImageRelease> release, Optional<String> error) {}

public interface ImageSource {
  List<ImageResult> findByNames(List<String> names);
}
```

Una imagen que falla ya no impide que las demás se actualicen: se muestra con
estado `ERROR` y su mensaje, y el resto del ciclo continúa.

### Concurrencia

`HttpImageSource` consulta las N imágenes en paralelo con
`Executors.newVirtualThreadPerTaskExecutor()`. Es el cambio mínimo sobre el
código actual y elimina el N+1 secuencial.

### Notificación por transición

`VersionPollingService` compara el snapshot nuevo contra el anterior y notifica
únicamente las transiciones `OK|UNKNOWN -> PENDING`. Se acaba el aviso repetido
cada ciclo por la misma versión.

### Puente Java a Kotlin

```java
public interface PollListener { void onSnapshot(PollSnapshot snapshot); }
```

`VersionPollingService` gana `addListener` / `removeListener` y publica al
terminar cada `poll()`. Del lado Kotlin:

```kotlin
val snapshots: StateFlow<PollSnapshot> = callbackFlow {
    val listener = PollListener { trySend(it) }
    service.addListener(listener)
    awaitClose { service.removeListener(listener) }
}.stateIn(scope, SharingStarted.Eagerly, service.lastSnapshot())
```

Compose recolecta ese `StateFlow` y recompone. Eso es todo el tiempo real: sin
sondeo desde la UI y sin refresco manual. El núcleo sigue siendo testeable sin
Compose ni coroutines, mediante un `PollListener` de prueba.

### Separación del formato de cable

Hoy el record de dominio lleva anotaciones de Jackson: el formato del proveedor
contamina el dominio. Se separa en dos tipos:

- `domain/ImageRelease` — sin anotaciones.
- `infrastructure/remote/ReleaseDto` — con las anotaciones de mapeo.

Vocabulario: `Product` pasa a `ImageRelease`; el campo de referencia
`registry/nombre:tag` pasa a llamarse `reference`, término OCI correcto;
`ProductSource` pasa a `ImageSource`; `ProductStateStore` pasa a `ImageStateStore`.

---

## 6. UI y animaciones

### Forma de la aplicación

Residente en bandeja. `application { }` monta el `Tray` de forma permanente; la
`Window` existe solo mientras el estado `visible` sea verdadero. Cerrar la
ventana la oculta. Desaparecen el `System.exit(0)` forzado y el `CountDownLatch`.

El composable `Tray` de Compose Desktop funciona sin `Window`, que es
exactamente lo que necesita una aplicación residente. Su `sendNotification`
utiliza el globo heredado, por lo que **no se usa**: las notificaciones son
propias.

### Navegación y estado

Dos pantallas (`IMAGES`, `SETTINGS`) con `Crossfade`. Sin librería de
navegación. El estado vive en una clase `ImagesViewModel` plana —sin framework de
inyección de dependencias— que recibe los puertos por constructor desde el
composition root.

### Sustituciones

| Actual | TO-BE |
|---|---|
| `JTable` + 4 renderers + editor de celda | `LazyColumn` + composable `ImageRow` |
| Componente pintado a mano con `Graphics2D` | `Surface(shape = CircleShape)` |
| Librería externa de diálogos modales | `AlertDialog` de Material 3 |
| Look-and-feel externo + paleta duplicada | `Theme.kt` — un `ColorScheme`, claro **y** oscuro |
| Singleton `private static` mutable | Estado en el scope de `application {}` |
| Diálogo invisible para anclar el menú de bandeja | `Tray { Item(...) }` |

### Animaciones

Todas se alimentan del mismo `StateFlow`:

1. **Insignia de estado** — `animateColorAsState`. La transición `OK -> PENDING`
   funde el color en ~400 ms en lugar de saltar.
2. **Número de versión** — `AnimatedContent` con `slideInVertically`. La versión
   anterior sale hacia arriba y la nueva entra desde abajo.
3. **Filas** — `Modifier.animateItem()`. Alta, baja y reordenación se animan.
4. **Primera verificación** — efecto de pulso en las filas `UNKNOWN` mientras la
   consulta está en vuelo.
5. **Aviso de error** — `AnimatedVisibility` con `expandVertically`. Desciende
   una barra cuando el origen no responde y se retrae al recuperarse.
6. **Control de sondeo** — `Crossfade` entre reproducir y pausar; el indicador de
   actividad pulsa mientras el sondeo corre.
7. **Toasts** — descritos abajo.

### Toast

Ventana propia: `undecorated = true`, `transparent = true`, `alwaysOnTop = true`,
`focusable = false`, `resizable = false`.

Dos restricciones que hay que respetar:

- `transparent` **exige** `undecorated`, o Compose lanza excepción.
- Sin `focusable = false` la ventana roba el foco mientras el usuario escribe.

Posición: esquina inferior derecha calculada con
`GraphicsEnvironment.getMaximumWindowBounds()`. No sirve el tamaño de pantalla
crudo, que ignora la barra de tareas y coloca el toast por debajo de ella.

Comportamiento:

- Entrada con `slideInHorizontally` desde la derecha más `fadeIn`; salida inversa.
- Varios toasts se apilan en columna; cada uno anima el hueco al desaparecer.
- Descarte automático a los ~8 s, con barra de progreso descendente visible.
- El puntero encima pausa el descarte.
- Acción «Ver» abre la ventana con esa fila seleccionada y resaltada. Esto
  sustituye al mecanismo actual, que solo funciona con una única actualización.
- Agrupación: una imagen muestra nombre y transición de versión; tres o más
  muestran un resumen expandible.

### Tema

Material 3 con `lightColorScheme()` y `darkColorScheme()` explícitos, definidos
**una sola vez** en `Colors.kt`. Se añade tema claro, inexistente hoy. El color
de acento actual se sustituye por uno neutro sin vínculo con ninguna marca.

---

## 7. Configuración, errores y simulación

### Configuración persistida

```java
public interface ConfigStore {
  AppConfig load();
  void save(AppConfig config);
}
```

Archivo en `~/.notifier/config.json`, junto a los ficheros de estado que ya
residen ahí.

**Precedencia:** las variables de entorno **siembran el fichero en el primer
arranque** y después dejan de tener efecto; el fichero es la fuente de verdad. Es
el mismo patrón que ya emplea el almacén de imágenes vigiladas con su lista de
valores por defecto. Si el entorno prevaleciera siempre, la pantalla de ajustes
no podría modificar nada.

**Campos editables:** URL del origen · intervalo de sondeo · validación TLS ·
modo simulación · tema (sistema/claro/oscuro) · toasts activados y su duración ·
sonidos activados y volumen.

**Validación:** se reutilizan las reglas existentes —el adaptador HTTP ya exige
HTTPS y el controlador ya exige un intervalo de al menos un segundo—. La pantalla
las invoca y muestra el mensaje devuelto; no las duplica.

**Aplicación en caliente:** guardar surte efecto sin reiniciar. El intervalo se
propaga al controlador y **ahora persiste** (hoy se pierde al cerrar). Cambiar la
URL o el modo simulación obliga a reconstruir el origen, que hoy es un campo
final capturado en el arranque; se resuelve con `ReloadableImageSource`, que
delega en un campo `volatile` intercambiable (~20 líneas).

### Errores visibles

SLF4J con logback: consola y fichero rotativo en `~/.notifier/imagewatch.log`.
Se eliminan los cinco `println` de diagnóstico repartidos por el código.

Tres niveles en pantalla:

- **Por fila** — estado `ERROR` con el mensaje en el tooltip y la última versión
  conocida atenuada. La fila no desaparece ni afirma falsamente estar al día.
- **Aviso global** — si fallan todas, desciende una barra indicando la ausencia
  de conexión y cuánto hace de la última verificación correcta. Distingue «el
  origen no responde» de «esta imagen concreta no existe».
- **Bandeja** — el icono cambia de forma discreta ante un error persistente.

### Origen simulado con versiones cambiantes

Imprescindible: sin él no se puede observar el tiempo real ni las animaciones,
porque el origen real solo es accesible desde la red corporativa.

```java
public final class SimulatedImageSource implements ImageSource {
  private final Clock clock;          // inyectable -> tests deterministas
  private final Instant startedAt;
  private final Duration bumpEvery;   // p. ej. 20 s
```

La versión se **deriva** del tiempo transcurrido en lugar de mantener un contador
con estado: `patch = elapsed / bumpEvery`. Cada imagen arranca con un desfase
distinto según el hash de su nombre, de modo que los incrementos quedan
escalonados y las filas cambian de una en una.

Además, cualquier nombre que contenga `fail` devuelve error siempre, lo que
permite observar el estado `ERROR`, el aviso global y el aislamiento por imagen.

Con incremento cada 20 s e intervalo de sondeo de 10 s, en un minuto se observan
las siete animaciones y los tres niveles de error, sin acceso a la red
corporativa.

---

## 8. Sonido

`javax.sound.sampled` forma parte del JDK y reproduce WAV/PCM sin nada más.
**No se añade ninguna dependencia.**

### Paleta: cuatro sonidos

El sonido es el canal más intrusivo de una aplicación. Si son muchos, el usuario
los desactiva todos y se pierde también el que importaba. De ahí la disciplina:

| Sonido | Cuándo suena | Carácter |
|---|---|---|
| `update` | Nueva versión detectada, junto al toast | El relevante. Claro, ascendente. |
| `error` | El origen deja de responder | Grave y distinto: debe reconocerse sin mirar la pantalla. |
| `success` | Acción importante confirmada: guardar ajustes, eliminar imagen | Breve y suave. |
| `toggle` | Arranque y parada del sondeo | Clic muy sutil. |

**No suenan, deliberadamente:** el paso del puntero, la escritura, el filtrado de
búsqueda, el desplazamiento, la selección de fila ni la apertura de la ventana.
Sonorizar eso es lo que convierte una aplicación con sonido en una aplicación que
se desinstala.

### Reglas de reproducción

Las tres son de comportamiento, no de estética:

1. **No suena si la ventana principal tiene el foco.** Si el usuario ya está
   mirando la tabla, el toast basta. El sonido existe para cuando no mira.
2. **Nunca dos reproducciones solapadas.** Un `Clip` no puede sonar dos veces a
   la vez; ante dos avisos simultáneos se reinicia (`framePosition = 0`) en lugar
   de encolar.
3. **Degradación silenciosa, nunca fallo.** Por escritorio remoto, en máquinas
   sin dispositivo de audio y en integración continua, `AudioSystem.getClip()`
   lanza `LineUnavailableException`. Se captura una sola vez en el arranque y el
   subsistema de sonido queda desactivado sin ruido. Una aplicación de escritorio
   no puede caerse porque la máquina no tenga altavoces.

### Implementación

**No se define un puerto de sonido en `application/`.** El sonido es
presentación; un `NotificationPort` que además reprodujera audio mezclaría capas.
Vive en `ui/sound/` como un objeto invocado tanto por el adaptador de toasts como
por los controles de la interfaz.

- Los cuatro clips se **precargan una sola vez al arrancar**, en un hilo de
  fondo. `getClip()` y `open()` bloquean (~100 ms la primera vez); `start()` no.
  Sin precarga, el primer sonido llega tarde y con tirón visible en la interfaz.
- Volumen mediante `FloatControl.Type.MASTER_GAIN`, atenuado por defecto
  (~−12 dB). Un aviso a volumen completo sobresalta.
- Ajustes gana dos campos: sonidos activados y volumen.

Formato: WAV PCM 16 bits 44,1 kHz. `javax.sound.sampled` **no** reproduce MP3 ni
OGG sin un SPI adicional; con WAV no hace falta añadir nada.

### Origen de los archivos

Cuatro WAV de un pack de dominio público (CC0, sin atribución obligatoria),
en `src/main/resources/sounds/`. Se acompaña un fichero `NOTICE` con la
procedencia y la licencia, aunque la CC0 no lo exija.

Los candidatos concretos se presentan para aprobación **antes** de incorporar
ningún binario al repositorio.

---

## 9. Identidad, secretos y desacople

### Fuga en la configuración del IDE

El `.gitignore` actual excluye cuatro ficheros sueltos del directorio del IDE,
pero **no el fichero de espacio de trabajo**, que sí se commitearía. Ese fichero
contiene el nombre de usuario corporativo **de otra persona** en rutas absolutas,
nombres de clases ya inexistentes y configuraciones de ejecución. Otro fichero
del mismo directorio apunta a un repositorio de artefactos interno.

**La fase 0 añade el directorio del IDE completo al `.gitignore` antes del primer
commit.** Una vez commiteado, permanece en el historial de forma permanente.

### Categorías a depurar

Sin enumerar valores concretos —este documento se versiona—, hay que eliminar del
árbol de código:

- El paquete Java actual (presente en los 22 ficheros) hacia `io.github.shizukajiku.imagewatch`.
- El identificador de artefacto y el nombre visible de la aplicación hacia ImageWatch.
- La URL del origen remoto por defecto: **secreto**, fuera del código.
- La lista de nombres de imagen por defecto: **secreto**; es una convención de
  nomenclatura interna.
- Referencias a repositorios de artefactos internos: salen con el directorio del IDE.
- Nombres de usuario corporativos: salen con el directorio del IDE.
- **El color de acento actual**, que es un color de marca registrada y por tanto
  identifica de forma tan directa como un dominio: paleta neutra nueva.

### Manejo de secretos

**Sin valores por defecto en el código.**

- **El modo simulación pasa a estar activo por defecto.** Un clon limpio arranca
  con el origen simulado y funciona completo. El repositorio no contiene indicio
  de que exista un origen corporativo. Es la medida más efectiva del conjunto.
- La URL no tiene valor de reserva. Si no está configurada y el modo simulación
  está desactivado, la aplicación abre Ajustes y la solicita.
- Los valores reales viven en `~/.notifier/config.json`, fuera del repositorio,
  donde ya residen hoy los ficheros de estado.
- Los tests usan hosts bajo `.test` y nombres genéricos. Ningún dato real en
  fixtures.

**Guarda:** un hook `pre-commit` que compara contra una lista de patrones
prohibidos y aborta el commit. La lista **no se versiona**: vive en
`.git/hooks/`, fuera del árbol de trabajo, generada por `scripts/install-hooks.sh`
a partir de un fichero local ignorado. De otro modo la propia lista filtraría lo
que pretende bloquear. Son unas 15 líneas sin dependencias; si algún día se queda
corta, se sustituye por una herramienta dedicada.

**Repositorio:** privado.

### Desacople del proveedor

El adaptador HTTP **ya es genérico**: recibe una URL base y hace `GET /{nombre}`.
No contiene ninguna línea específica de un proveedor. Lo único acoplado son los
valores por defecto de la configuración, que desaparecen.

**No se construye un mecanismo de plugins ni un SPI.** La «implementación aparte»
es, hoy, un fichero de configuración: adaptador HTTP genérico más una URL. El día
que el origen real requiera cabeceras de autenticación o un formato distinto,
entonces un repositorio privado separado tendrá justificación. Hoy sería
andamiaje para un futuro que no existe.

---

## 10. Testing

**Herramientas:** JUnit 5 y AssertJ se conservan. Se añaden
`kotlinx-coroutines-test` para el `StateFlow`, `compose.uiTest` para composables,
y **Kover** para cobertura —mide Java y Kotlin en un solo informe; JaCoCo tiene
problemas con el bytecode que genera el compilador de Compose—.

**Umbral: 80 % sobre `domain/` y `application/`. Sin umbral sobre `ui/`.** La
cobertura de UI en Compose mide recomposiciones, no comportamiento; fijar un
número ahí no aporta información.

Cobertura a añadir, por orden de valor:

| Qué | Por qué |
|---|---|
| Notificación solo en transición | Es el aviso repetido. Sin test, reaparece. |
| Una imagen falla, las demás sobreviven | Es la causa de que los errores sean invisibles. |
| `Version` ante cadena no válida | Es el `NullPointerException`. |
| `PollingController`: arranque, parada, cambio de intervalo, cierre | Sin cobertura hoy, y mantiene estado sincronizado. |
| Adaptador HTTP | Con `com.sun.net.httpserver.HttpServer`, incluido en el JDK. |
| Origen simulado con `Clock` fijo | Determinista, sin esperas reales. |
| `JsonConfigStore`: ida y vuelta, y siembra inicial | Mismo patrón que el test ya existente del almacén de imágenes. |
| `ImageRow` por cada estado; `ToastHost` apila y descarta | Con `mainClock.autoAdvance = false` para avanzar fotogramas. |
| Sonido sin dispositivo de audio disponible | La aplicación debe seguir funcionando en silencio. Es el modo en que corre la integración continua, y también el escritorio remoto. |
| Sonido silenciado cuando la ventana tiene el foco | Es una regla de comportamiento, no un detalle visual; sin test se pierde en el primer refactor. |

---

## 11. Fases

Cada fase compila, se ejecuta y es un commit revisable.

### Fase 0 — Higiene previa al primer commit

No existe control de versiones. Sin él, el enfoque por fases pierde su sentido:
no hay puntos de reversión.

**El orden de esta fase es su única parte delicada.** Todo lo enumerado en el
apartado 9 —nombre de paquete, valores por defecto de configuración, color de
marca, nombre visible— sigue en el árbol de trabajo. Si se inicializa el
repositorio y se commitea antes de purgarlo, todos esos valores quedan en el
historial de forma permanente, y la única salida es reescribir el historial o
recrear el repositorio. La purga **precede** al primer commit; no lo sigue.

1. `.gitignore` con el directorio del IDE completo, y `.denylist.local` con los
   patrones prohibidos (fichero no versionado).
2. **Purga del árbol**, verificada con el build actual: renombrado de paquete y
   coordenadas, eliminación de los valores por defecto de configuración
   (el modo simulación pasa a activo), sustitución del color de acento y del
   nombre visible de la aplicación.
3. Barrido de la denylist sobre el árbol. Cero coincidencias es requisito para
   continuar.
4. `git init`, hook `pre-commit` instalado **antes** de preparar nada, y prueba
   deliberada de que el hook rechaza lo que debe rechazar. Un hook que nunca ha
   rechazado nada no está probado.
5. Commit inicial, y barrido de la denylist sobre el historial resultante.
6. Repositorio remoto **privado**, con la visibilidad verificada antes del envío.

### Fase 1 — Maven a Gradle

Instalar Gradle, generar y versionar el wrapper. `settings.gradle.kts`,
`build.gradle.kts`, `gradle/libs.versions.toml`. **Permanece en Java 21** de
forma deliberada, para aislar la variable. Eliminar `pom.xml` y el wrapper de
Maven.

Verde: los cuatro tests pasan; la aplicación arranca idéntica a hoy.

### Fase 2 — Java 25 y limpieza

`toolchain(25)`, `--release 25`. Eliminar Lombok y la property huérfana. Corregir
el fallo de parseo de `Version`. Jackson no se toca.

Verde: mismos tests sobre 25, sin el aviso de módulos de sistema.

### Fase 3 — Eventos y núcleo

Aquí entra la mayor parte del valor y de la cobertura nueva: tipos nuevos,
`ImageResult`, hilos virtuales, notificación por transición, aislamiento de
fallos, origen simulado con `Clock`, SLF4J y logback.

Y el punto que justifica la estrategia elegida: **la UI actual se adapta a
consumir `PollListener`**, de modo que la ventana empieza a actualizarse sola
usando código de presentación ya probado.

Verde: aplicación con tiempo real real, antes de tocar Compose. Si la fase
siguiente se complica, esta ya es una mejora entregable.

### Fase 4 — Compose

Kotlin 2.3.0 y Compose 1.12.0. Tema, componentes, pantalla de imágenes, view
model, bandeja, composition root.

Tres requisitos de compilación confirmados por la prueba de concepto: hace falta
`google()` en los repositorios, Material 3 se declara aparte de
`compose.desktop.currentOs`, y `--enable-native-access=ALL-UNNAMED` sigue siendo
necesario porque Skiko carga su motor igual que lo hacía FlatLaf.

En el mismo commit se elimina la capa de UI anterior completa (sus tres archivos
más las tres dependencias que solo ella usaba: el look-and-feel, sus extras y la
librería de diálogos modales) y la clase de demostración manual de
notificaciones.

Verde: paridad funcional más las siete animaciones.

### Fase 5 — Ajustes, errores y toasts

`ConfigStore`, `JsonConfigStore`, `ReloadableImageSource`, pantalla de ajustes,
`ToastHost`, aviso global, estado por fila. Se elimina el adaptador de
notificación del sistema. Empaquetado nativo.

El sonido entra aquí y no en una fase propia: comparte el evento que dispara el
toast y los campos de configuración con el resto de esta fase. Los assets de
audio se aprueban antes de incorporarlos.

Verde: TO-BE completo.

---

## 12. Riesgos — resueltos por la prueba de concepto

Los tres riesgos técnicos que condicionaban la Fase 4 se verificaron con una
aplicación desechable antes de escribir su plan. **Los tres quedan descartados.**

| Riesgo | Veredicto | Cómo se comprobó |
|---|---|---|
| Compose Desktop sobre JDK 25 | ✅ viable | Kotlin 2.3.0 y Compose 1.12.0 compilan; Skiko carga su motor y pinta |
| Carga de los iconos SVG | ✅ viable | `loadSvgPainter` devuelve un `Painter` con tamaño correcto para los cinco |
| El composable `Tray` en Windows 11 | ✅ viable | 22 minutos en ejecución, sin excepciones, cierre limpio por `exitApplication` |

No hace falta redibujar iconos como `ImageVector` ni buscar alternativa a la
bandeja del sistema.

### Tres detalles que la prueba de concepto destapó

Los dos primeros rompen la compilación si faltan, y no eran evidentes:

1. **Hace falta `google()` en los repositorios.** Compose 1.12 arrastra
   artefactos `androidx` que no están publicados en Maven Central. Sin ese
   repositorio, once dependencias quedan sin resolver.
2. **`compose.desktop.currentOs` no incluye Material 3.** Hay que declararlo
   por separado, y con la coordenada directa: el alias `compose.material3` sale
   marcado como obsoleto.
3. **El aviso de método restringido no lo causa FlatLaf, sino cualquier
   interfaz con librería nativa.** Estaba anotado como algo que se resolvería al
   retirar FlatLaf; Skiko emite exactamente el mismo. La solución es
   `--enable-native-access=ALL-UNNAMED`, ya aplicada, y sigue siendo necesaria
   después de la migración.

### Riesgo que permanece

| Riesgo | Mitigación |
|---|---|
| La reescritura de la interfaz rompe comportamiento que hoy funciona | La Fase 3 dejó el núcleo cubierto por 59 tests y con su contrato fijado. La Fase 4 sustituye solo presentación: si un test de núcleo se rompe, es que la migración se salió de su capa. |

## 13. Fuera de alcance

- Integración real con Teams. El adaptador actual es un vestigio y se elimina.
- Endurecimiento de la validación TLS. Se mantiene el comportamiento actual por
  decisión explícita: el origen solo es accesible desde la red corporativa.
- Notificaciones nativas del sistema operativo. Los toasts son propios.
- Mecanismo de plugins o SPI para orígenes alternativos.
- Publicación en un repositorio de artefactos público.
- Integración continua. Se valorará una vez estabilizado el build de Gradle.
