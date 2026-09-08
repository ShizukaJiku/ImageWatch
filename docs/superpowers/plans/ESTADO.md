# Estado de la migración — al cierre del 2026-09-08 (segunda sesión)

Documento de continuidad. Se lee **antes** que el plan.

- **Plan:** `docs/superpowers/plans/2026-09-07-migracion-plantilla-kmp.md`
  (79 casillas hechas, 5 pendientes: las cuatro de la retirada del proyecto viejo y una salida
  de emergencia que no se usó)
- **Spec:** `docs/superpowers/specs/2026-09-07-migracion-plantilla-kmp-design.md`
- **Proyecto nuevo:** `C:\Users\shizu\IdeaProjects\ImageWatch`
- **Proyecto de origen:** la carpeta desde la que se migró. Se la llama `%ORIGEN%` aquí y en el
  plan a propósito: este repositorio no guarda el nombre ni la ruta del proyecto anterior.

## Cómo se compila

Gradle solo funciona desde PowerShell con `JAVA_HOME` fijado. En Bash, `./gradlew` sale con
código 127 sin decir nada.

```powershell
$env:JAVA_HOME='C:\Users\shizu\.jdks\ms-21.0.12.1'
Set-Location C:\Users\shizu\IdeaProjects\ImageWatch
.\gradlew.bat <tarea>
```

## Verde al cerrar

```
:shared:jvmTest      167 tests en 19 ficheros, todos pasan
:shared:koverVerify  cumple el umbral del 80%
spotlessCheck        limpio
:desktopApp:run      arranca, sondea y escribe en ~/.notifier/imagewatch.log
MSI instalado        arranca, sondea y escribe en el mismo log
```

**La aplicación corre, tanto desde Gradle como instalada desde el MSI.** Vive en la bandeja: la
ventana arranca oculta y se abre desde el icono. Falta que el usuario la abra desde el menú de
inicio y confirme que carga —paso 3 de T12, sin marcar— y la validación visual completa, que es
la puerta T14.

## Qué está hecho

| Tarea | Estado |
|---|---|
| T1 configuración de compilación | Hecha |
| T2 recursos (19 iconos, 4 sonidos, NOTICE, logback) | Hecha |
| T3 port Java→Kotlin, 40 ficheros | Hecha |
| T4 costura `Log` | Hecha |
| T5 dominio → `commonMain` | Hecha |
| T6 aplicación y configuración → `commonMain` | Hecha |
| T7 infraestructura en `jvmMain` | Hecha |
| T8 interfaz → `shared` | Hecha con desvíos, ver abajo |
| T9 arranque en `desktopApp` | Hecha |
| T11 tests de interfaz | Hecha |
| T12 instalador MSI | Hecha; el usuario confirmó que la instalada arranca |
| T13 CI y documentación | Hecha |
| T14 puerta de la fase A | Hecha; el usuario dio por buena la validación visual el 2026-09-08 |
| T16 ficheros con okio | Hecha con desvíos, ver abajo |
| T17 cliente HTTP con Ktor | Hecha con desvíos, ver abajo |
| T19 `Version` sin semver4j | Hecha; no hizo falta la salida de emergencia |
| T20 limpieza y verificación final | Hecha |
| T10 tests de dominio y aplicación → `commonTest` | Hecha |
| T15 kotlinx-serialization | Hecha con desvío, ver abajo. Casillas tildadas por fin en T20 |
| T18 kotlinx-datetime | Hecha de facto: dependencia y formateo de fechas de `ImagesViewModel`. Queda confirmarla al llegar a fase B |

## Qué falta, en orden

**Solo queda T21, la retirada del proyecto viejo.** El contenido ya está todo aquí —incluido lo
que vivía únicamente en la rama local `feature/fase-7-refinamiento`, portado a mano el 2026-09-08—,
así que lo que falta no es migrar nada sino decidir:

1. **`.denylist.local` solo existe en la carpeta condenada.** El hook de pre-commit de ImageWatch lo
   necesita y no se versiona a propósito. Hay que copiarlo fuera antes de borrar nada.
2. **ImageWatch no tiene ni un commit ni remoto.** Toda la migración vive como ficheros sin
   versionar en disco. Borrar el repo viejo antes de darle historia al nuevo deja el trabajo sin
   ninguna red. Choca con la regla de «sin commits en ImageWatch», así que es decisión explícita
   del usuario.
3. **Borrar `github.com/ShizukaJiku/imagewatch` es una acción distinta de borrar la carpeta**, y
   las dos son irreversibles y sin conservar historial.
3. **T14 — el repaso visual, aplazado.** El usuario pidió priorizar la migración sobre el refactor
   visual, así que la puerta de la fase A queda abierta a propósito y la fase B siguió sin ella. Su
   paso 1, la comprobación automática, sí pasa. **Queda pendiente y hay que volver:** nadie ha
   mirado todavía la aplicación función por función después de portar la interfaz entera.

   La lista de `nativeDistributions.modules` se revisó en T17 —salió `java.net.http`, entró
   `java.management`—, en T19 y en T20, sin más cambios. Queda como
   `java.instrument`, `java.management`, `jdk.unsupported` y `java.naming`, este último el que
   jdeps nunca sugiere.

## Cuatro bugs de compatibilidad silenciosa, ya corregidos

Merecen quedar escritos porque los cuatro habrían llegado a producción sin avisar. Los dos primeros
los introdujo el conversor J2K; el tercero, el cambio de Jackson a kotlinx-serialization; el cuarto
no lo introdujo la migración, pero solo se descubrió al instalar de verdad.

**`ReleaseDto` perdió sus `@JsonAlias`.** El original mapeaba `productName` y `update_time`,
que es lo que publica el origen remoto. Sin las anotaciones, **ninguna imagen mapea y todas
acaban en estado de error**. Restaurado con `@JsonNames` de kotlinx-serialization.

**`Version` cambió de excepción.** J2K convirtió `Semver.parse(...)` en `Semver.parse(...)!!`,
que lanza `NullPointerException` donde el original lanzaba `IllegalArgumentException`, y dejó
muerto el `requireNotNull` que venía detrás. `VersionPollingService.parse` captura
`IllegalArgumentException` para degradar a estado de error: con el `!!` la excepción se habría
escapado y tumbado el ciclo entero.

**`ConfigDto` exigía todos los campos.** Lo destapó el primer arranque real en T9: la aplicación
murió con `MissingFieldException: Field 'mutedAll' is required`. El `config.json` que hay en esta
máquina lo escribió una versión anterior a que ese campo existiera. Jackson rellenaba los campos
ausentes con el valor cero del tipo; kotlinx-serialization exige todos los que no declaren un valor
por defecto. Con eso, **cualquiera que ya tuviera la aplicación instalada se quedaba sin arrancar**.
Corregido dándole `= false` a `mutedAll`, con un comentario que deja escrita la regla: todo campo
que se añada a ese DTO en adelante necesita un valor por defecto.

**El runtime de jlink no llevaba `java.naming` y la aplicación instalada no arrancaba.** El
diálogo decía «Failed to launch JVM» y nada más; el error real solo salió con
`./gradlew :desktopApp:runDistributable`:

```
Caused by: java.lang.ClassNotFoundException: javax.naming.NamingException
	at org.slf4j.LoggerFactory.bind(LoggerFactory.java:201)
	at io.github.shizukajiku.imagewatch.MainKt.<clinit>(Main.kt:66)
```

Logback toca JNDI al inicializarse, así que moría en el inicializador estático, antes de la primera
línea de `main()`. `./gradlew run` no lo veía nunca: ahí está el JDK completo.

Lo peligroso es cómo se encuentra el resto de la lista. `suggestRuntimeModules` sugiere
`java.instrument`, `java.net.http` y `jdk.unsupported`, y **no sugiere `java.naming`**, porque jdeps
no ve una llamada reflexiva. Pero mira `java.net.http`: es el cliente de `HttpImageSource`. Sin él,
la aplicación instalada funciona en modo simulación y revienta el día que se apunte a un registry
real. Los cuatro están ahora declarados en `nativeDistributions`, con el porqué al lado.

**El proyecto viejo tampoco declaraba módulos**, así que es probable que su MSI arrastrara el mismo
fallo. No está comprobado —no había ninguna instalación previa en esta máquina— y no cambia nada de
lo que queda por hacer, pero conviene no atribuirle a la migración un fallo que ya venía.

La lección para lo que queda: la salida de J2K compila, pero no conserva ni anotaciones ni
semántica de excepciones, y el cambio de biblioteca de serialización tampoco conserva la tolerancia
del formato. Ninguno de los cuatro fallos lo habría detectado un test: los tres primeros viven en el
borde entre el código y datos que ya estaban en disco, y el cuarto solo existe fuera de Gradle. **Que
`packageMsi` termine en verde no dice nada sobre si el binario arranca.**

## Desvíos del plan ya aplicados

**kotlinx-serialization adelantada de fase B (T15).** Jackson no construye data classes de
Kotlin sin `jackson-module-kotlin`; añadir una dependencia para apuntalar otra que fase B iba a
borrar era trabajo tirado. Jackson ya no está en el proyecto. Desvío dentro del desvío:
`ConfigDto` y `ReleaseDto` se quedaron en `jvmMain`, no subieron a `commonMain` como decía el
plan. Son formato de cable de adaptadores que siguen siendo JVM; subirán con T16 y T17.

**kotlinx-datetime adelantada (T18).** `ImageRelease.publishedAt` es un `LocalDateTime` y
`commonMain` no tiene JDK. Sin esta dependencia el dominio entero se quedaba en `jvmMain` y la
migración perdía su sentido.

**`AppConfig.stateFile` pasa de `Path` a `String`.** El dominio no debe saber cómo se llama un
fichero en cada sistema. La conversión a `Path` ocurre en el adaptador de persistencia.

**`PollingController` reescrito, no portado.** `@Synchronized` y `ScheduledExecutorService` no
existen en `commonMain`. Ahora usa un canal de un solo consumidor, que da la misma garantía que
el executor de un hilo del original: un ciclo programado y un refresco manual nunca corren a la
vez. Contrato público idéntico y sus 9 tests pasan sin cambios de intención.

**Ficheros de ejemplo de la plantilla borrados en T1, no en T9.** Sus lints hacían fallar
`spotlessApply` en cada tarea intermedia.

**Checkstyle no se migra.** Solo analiza Java y ya no queda ningún `.java`.

**El `.editorconfig` del proyecto viejo NO se migra.** Se probó traerlo en T8 y se descartó por
decisión del usuario: la plantilla no trae ninguno —ni `.editorconfig`, ni ktlint, ni detekt; el
spotless lo añadimos nosotros en T1— y el estilo del repo nuevo es el que ktlint trae de serie
(`ktlint_official`, línea a 140). Lo único que se conserva del viejo es la excepción de nombrado de
los composables, y vive en `build.gradle.kts` con el resto de la configuración de compilación, no en
un fichero suelto:

```kotlin
ktlint().editorConfigOverride(
    mapOf("ktlint_function_naming_ignore_when_annotated_with" to "Composable"),
)
```

Sin esa línea ktlint marca los once composables por empezar en mayúscula, y la alternativa sería
renombrarlos a camelCase, que va contra la convención de Compose. Adoptar el estilo de serie costó
28 líneas en 4 ficheros, todas des-envolviendo firmas que estaban partidas a 120 columnas.

**La interfaz no solo cambió de tipos del JDK: cambió de API del dominio.** El paso 4 de T8 hablaba
de `java.time` y `java.util.concurrent`, pero los ficheros copiados venían llamando `image.name()`,
`image.status()` y `image.local().map { ... }.orElse(x)`, que es la forma que tenía el dominio en
Java. Ahora son propiedades y nulables. Es la misma lección de los dos bugs del J2K: el fichero
copiado compila en su sitio de origen y no dice nada al llegar.

**El reloj de `ToastState` se reescribió, no se portó.** Sus cuatro `ConcurrentHashMap` y su
`AtomicLong` no existen en `commonMain`, y el contrato público no puede volverse `suspend` —lo
llaman el hilo del planificador y el de Compose, ninguno de los dos desde una corrutina—, así que un
`Mutex` quedaba descartado. Ahora hay un solo `MutableStateFlow<Relojes>` con los cuatro mapas
juntos y un ayudante `mutar {}` que es el bucle de `compareAndSet` con el que `update` está escrito
por dentro. Lo delicado era `computeIfAbsent`, que creaba y publicaba el job en un solo paso: se
replica creando el job `CoroutineStart.LAZY`, instalándolo por CAS y arrancándolo solo si este hilo
ganó —el perdedor cancela un job que nunca corrió—. Sin eso quedaría un reloj huérfano que nadie
puede cancelar. `System.nanoTime()` pasa a `TimeSource.Monotonic`.

**`ImagesViewModel` pierde `CopyOnWriteArrayList` y `ConcurrentHashMap` por el mismo camino**, aquí
sin sutilezas: listas y mapas inmutables dentro de un `MutableStateFlow`, editados con `update {}`.
El único sitio que necesitaba leer-y-quitar en un solo paso —`undoJobs.remove(name)?.cancel()`— usa
`getAndUpdate`.

**El refactor que vivía solo en la rama local se portó, no se descartó.** `feature/fase-7-refinamiento`
tenía dos commits que no estaban ni en `main` ni en `origin`: uno consolidaba la regla de «pendiente
con novedad» y el otro añadía una nota de verificación a la documentación. Se comprobó primero que
**el refactor no cambia el comportamiento** —la regla del núcleo filtrada por «ya estaba pendiente»
da exactamente el mismo conjunto que el `remoteBumps` del view model, término por término— y luego
se portó por lo que sí aporta: la comparación de versiones estaba escrita dos veces, y sus dos
copias gobiernan cosas distintas —una qué genera aviso y qué se registra, otra qué fila late—.
Afinar una y olvidar la otra dejaba un aviso sin su resaltado sin que ningún test dijera nada.

En Kotlin la regla quedó como función de nivel de paquete en `application`, no como método estático
de la clase: es una regla pura con dos consumidores, y no tenía por qué ensanchar la API de
`VersionPollingService`. El view model conserva solo su filtro de presentación —«solo laten las que
ya estaban pendientes»—, que sí es decisión suya. La nota de documentación se aplicó con `git apply`
del commit original.

**T20 subió siete tests, no cinco.** El plan solo listaba los cinco de persistencia, pero
`ReloadableImageSourceTest` y `SimulatedImageSourceTest` también prueban clases que T17 movió a
`commonMain`, y seguían usando AssertJ y JUnit. Sin ellos, el paso 3 —retirar dependencias
muertas— no habría podido retirar nada. Ahora **17 de los 19 ficheros de test viven en
`commonTest`**; los dos que quedan en `jvmTest` son los de `Sounds` y `SvgIcon`, que prueban
precisamente el lado JVM de sus costuras y ya usaban `kotlin.test`.

**El `@TempDir` de JUnit se sustituyó por un ayudante propio, no por `FakeFileSystem`.** El plan
proponía `FileSystem.SYSTEM_TEMPORARY_DIRECTORY`, y eso es lo que hace `withTempDir`: crea un
directorio real bajo el temporal del sistema y lo borra al terminar, pase lo que pase. Ficheros
reales y no un sistema de ficheros simulado, a propósito: lo que estas pruebas comprueban es la
escritura atómica, y contra un `FakeFileSystem` esa garantía dejaría de probarse. El nombre lleva un
sufijo aleatorio para que dos pruebas concurrentes no compartan carpeta.

**Fuera AssertJ y JUnit Jupiter del catálogo.** Se queda `junit-platform-launcher` como
`runtimeOnly`: `useJUnitPlatform()` lo necesita para ejecutar lo que `kotlin.test` resuelve a JUnit 5
por variantes. Del mundo Java solo quedan slf4j y logback, que son el `actual` de `Log`.

**T19 se hizo interrogando a semver4j, no leyendo su documentación.** El paso 1 del plan pedía
anotar qué hace cada caso; se hizo con un test desechable que imprimía qué acepta y cómo ordena.
Salió un contrato exacto, y sin él la implementación propia habría divergido en silencio:

- Acepta `MAYOR.MENOR.PARCHE` estricto, y tolera espacios alrededor y una `v` inicial.
- **Rechaza** `1.2`, `1`, `1.2.3.4`, `01.2.3`, `1.02.3`, `latest`, `1.2.3-` y `1.2.3+`. Ese rechazo
  no es un detalle: `VersionPollingService.parse` lo captura para degradar esa imagen a estado de
  error en vez de tumbar el ciclo entero.
- Ordena por SemVer 2.0.0 §11: pre-release por debajo de su final, identificadores numéricos por
  valor y por delante de los alfanuméricos, ASCII entre alfanuméricos, y a igualdad de prefijo gana
  el que tiene más identificadores. Los metadatos de build no cuentan en el orden.

La implementación es la expresión regular oficial de semver.org más esas reglas de comparación.
**Los 9 casos originales de `VersionTest` pasan sin tocarlos**, así que la salida de emergencia del
paso 4 no hizo falta. Se añadieron 5 casos que fijan lo que antes venía de la librería y nadie
comprobaba aquí: qué se rechaza, la `v` inicial, y las tres reglas de orden entre identificadores.

Con esto `domain` queda entero en `commonMain`: ya no hay `Version.jvm.kt`.

**T17 dejó `HttpClientFactory` en `jvmMain`, contra lo que decía el plan.** El plan lo mandaba a
`commonMain` junto con `HttpImageSource`, pero el motor de Ktor y la configuración TLS son de la
plataforma por definición: no hay forma de escribir `sslSocketFactory` ni `hostnameVerifier` en
código común. El adaptador sí subió, y recibe el `HttpClient` ya construido por constructor, que es
como estaba diseñado desde el principio. Cada objetivo que se añada traerá su propio motor sin que
`HttpImageSource` se entere. Ahí es donde se queda `javax.net.ssl`, como ya estaba previsto.

**Sin ContentNegotiation.** El plan pedía instalar el plugin y `ktor-serialization-kotlinx-json`.
Se usa `bodyAsText()` y el `Json` que el adaptador ya tenía: mismo comportamiento, mismos mensajes
de error, dos dependencias menos y el decodificado sigue a la vista en el código.

**`findByNames` pasa a `suspend`, y eso se propaga.** El cliente de Ktor solo ofrece API
suspendida: `client.get()` suspende y no hay variante bloqueante.

Envolverlo en `runBlocking` **sí compila hoy** —se comprobó— porque el proyecto tiene un solo
objetivo, `jvm()`. Se descartó igualmente, por dos razones que no dependen de si compila:

1. `PollingController` ya ejecuta el ciclo dentro de una corrutina. Meter un `runBlocking` ahí
   bloquea un hilo del pool para esperar a otra corrutina que ya podía esperarse sin bloquear
   nada. Y el paralelismo por imagen —seis consultas a la vez— pasaría de seis corrutinas a seis
   hilos bloqueados.
2. Deja de compilar en cuanto se añada un objetivo que no sea JVM ni Native, que es exactamente
   a lo que apunta esta migración. Sería deuda con fecha de caducidad conocida.

Con `findByNames` suspendida suspenden también `VersionPollingService.poll()` y `pollOne()`, y la
cola de `PollingController` pasa a transportar `suspend () -> Unit`. `refreshNow` sigue sin
suspender —solo encola—, así que la interfaz no cambió ni una línea. En los tests, 53 funciones
pasaron a `= runTest {`; ninguna aserción se tocó.

**`findSafely` captura `Exception`, no `RuntimeException`.** Los fallos de red de Ktor no comparten
una raíz común que se pueda nombrar en código multiplataforma. A cambio, **`CancellationException`
se relanza explícitamente**: tragársela dejaría corrutinas zombis y haría que cerrar la aplicación
pareciera un error de red.

**El test de concurrencia cambió de método, no de intención.** Medía tiempo de pared —«tres
consultas de 400 ms deben tardar menos de 1000»—, que depende de la carga de la máquina. El reloj
virtual de `runTest` tampoco sirve: Ktor ejecuta el motor en su propio dispatcher, así que sus
`delay` son de tiempo real y el reloj virtual ni los ve; el primer intento falló con
`expected: <400> but was: <0>`. Ahora se cuentan las consultas en vuelo y se exige un máximo
solapado de 3. En serie sería 1, y no depende de ningún reloj.

**`HttpImageSourceTest` sube a `commonTest` con `MockEngine`** y gana un caso que antes no existía:
que un endpoint remoto sin HTTPS se rechaza. Con él, `com.sun.net.httpserver` desaparece del
proyecto.

**T16 necesitó una quinta costura: `Lock`.** Los cinco ficheros de persistencia usaban
`@Synchronized`, que no existe en `commonMain`, y el plan no lo mencionaba. Un
`kotlinx.coroutines.sync.Mutex` está descartado por lo mismo que en `ToastState`: obligaría a que
`load`, `save` y `findAll` fueran `suspend`, y los llaman el hilo del planificador y el de Compose
sin corrutina de por medio. Cambiar la firma de los puertos por un detalle de la implementación es
justo lo que la arquitectura evita. `kotlin.concurrent.atomics` existe en el stdlib común, pero no
trae cerrojo. Así que `internal expect class Lock` en `commonMain` y un `ReentrantLock` en
`jvmMain`. **Reentrante a propósito**: `JsonTrackedImageStore.findAll()` llama a `save()` y
`JsonImageStateStore.rename()` llama a `find()`; con un cerrojo no reentrante ambas se bloquearían
contra sí mismas, y `@Synchronized` también era reentrante.

**okio pasa a `api`, no `implementation`.** Los constructores de los cuatro almacenes exponen
`okio.Path`, así que es parte de la API pública de `shared` y `desktopApp` no compilaba sin verla.
Es el caso contrario al de material3: allí el tipo lo usaba `desktopApp` por su cuenta, aquí lo
publica `shared`.

**El movimiento atómico pierde una distinción, no la garantía.** `java.nio` lanzaba
`AtomicMoveNotSupportedException` cuando el sistema de ficheros no soportaba el movimiento atómico,
y solo entonces se degradaba a un movimiento normal. okio lanza `IOException` a secas y no permite
distinguirlo de un fallo real, así que ahora se degrada a copiar y borrar ante cualquier `IOException`.
Si el fallo era real, la copia vuelve a fallar y el error sube igual. Está escrito en el comentario
de `JsonFiles.replace`.

**`ConfigDto` sube a `commonMain` con esta tarea**, como estaba previsto. `ReleaseDto` sigue en
`jvmMain` hasta T17.

**Los tests de persistencia se quedan en `jvmTest` con un puente.** JUnit inyecta un
`java.nio.file.Path` en `@TempDir` y los almacenes hablan okio: la conversión con `toOkioPath()` va
en un solo sitio por fichero, así que ninguna prueba cambió. Subirlos a `commonTest` con el
`FakeFileSystem` de okio es trabajo de T20.

**Comprobado contra los datos reales del usuario, no solo con tests.** Tras la migración, la
aplicación arranca y **no reescribe `config.json` ni `tracked-images.json`**: si las rutas de okio
no apuntaran a los mismos ficheros, `JsonConfigStore.load()` habría sembrado una configuración nueva
y `JsonTrackedImageStore.findAll()` habría escrito los valores por defecto encima. Es la misma
comprobación que faltó cuando `ConfigDto` rompió la compatibilidad.

**El flujo de release comprueba los módulos del runtime, y bloquea.** El paso equivalente del
proyecto viejo solo dejaba constancia en el log de la versión del JDK empaquetado. Ahora falla el
release si al runtime le falta cualquiera de los cuatro módulos declarados, que es la regresión
concreta que tumbó la aplicación instalada. Comprueba la declaración, no el arranque: un smoke test
de verdad exigiría ejecutar una aplicación de bandeja que no termina sola. Cubre lo que nos mordió
y no más, y así está escrito en el propio flujo.

**De T13 no se copiaron dos cosas.** El `.editorconfig`, por la decisión de arriba. Y `design/`,
que estaba vacío en el proyecto viejo. Sí se copió `.superpowers/sdd/`, con los diffs de revisión
de las fases 3 a 7: **referencian commits que dejarán de existir con T21**. Si no se quieren como
documento suelto, es un borrado y ya.

**Los flujos de CI perdieron checkstyle** y todas las tareas llevan prefijo de módulo: `test` pasa a
`:shared:jvmTest`, `koverVerify` a `:shared:koverVerify`, `packageMsi` a `:desktopApp:packageMsi`.
La versión de Java baja de 25 a 21. Las rutas de artefactos se mueven a `shared/build/...` y
`desktopApp/build/...`.

**`jvmTest` de `shared` necesita `compose.desktop.currentOs` en `runtimeOnly`.** `SvgIconTest`
llama a `loadSvgPainter`, que carga los binarios nativos de Skia; `shared` compila solo contra la
API de Compose y no los arrastra. Sin eso el test muere con
`LibraryLoadException: Cannot find skiko-windows-x64.dll`. Va como `runtimeOnly` porque no hay nada
contra lo que compilar.

**El paso 2 de T11 no hizo falta.** El plan daba por hecho que los tests de interfaz usaban JUnit 5
y AssertJ, como los del núcleo. No: ya estaban escritos con `kotlin.test`. Lo que sí hubo que hacer
fue la misma adaptación de API del dominio que en T8.

**`desktopApp` declara `compose.material3` por su cuenta.** `Main.kt` usa `Surface`, y `shared`
declara material3 como `implementation`, que no se hereda. Es la plantilla funcionando como debe:
cada módulo declara aquello contra lo que compila.

**`AppConfig.fromEnvironment()` no se copió: se reescribió, y vive dentro de `Main.kt`.** Es
cableado —lee `System.getenv`— y `commonMain` no tiene variables de entorno. Va en `Main.kt` y no en
un fichero aparte porque tiene un solo consumidor, el composition root, que está ahí mismo.

**`withMutedAll` desapareció.** Existía porque `AppConfig` era un `record` de Java y no tenía
`copy()`. Ahora es una data class de Kotlin y la llamada es `config.copy(mutedAll = ...)`.

**El formateo de fechas usa kotlinx-datetime, no aritmética a mano.** El plan pedía escribirlo sobre
los campos de `kotlin.time.Instant` y dejarlo para la fase B, pero `Instant` no sabe de husos: sacar
la hora local a mano desde los segundos de época es imposible de hacer bien sin base de datos de
zonas. La dependencia ya estaba en `commonMain` desde T18, así que la conversión la hace
`toLocalDateTime(TimeZone.currentSystemDefault())` y solo el patrón `dd/MM/yyyy HH:mm` se arma a
mano. Con eso T18 queda hecho de facto.

**jspecify eliminado antes de convertir.** Se pagó un precio que conviene recordar: sin sus
anotaciones, J2K infirió nulabilidad por todas partes y las firmas de los puertos salieron como
`MutableList<String?>?`. Hubo que apretarlas a mano. No fue gratis.

## Qué queda de Java, a día de hoy

Cero ficheros `.java`. `commonMain` y `commonTest` no importan una sola clase del JDK.

- **Librerías Java:** semver4j (se va en T19, con salida de emergencia), slf4j-api y
  logback-classic (**se quedan**, son el `actual` de `Log`), JUnit 5 y AssertJ (se van cuando
  el último test suba a `commonTest`).
- **APIs del JDK en `jvmMain`:** `javax.sound.sampled` y `java.awt` (**se quedan**: son el
  `actual` de `Sounds` y la ventana de avisos), `java.nio.file` (se va en T16), `java.net.http` y
  `java.util.concurrent` (se van en T17), `java.time.Duration` en `HttpClientFactory` (residuo
  suelto, es solo el timeout de conexión), `javax.net.ssl` (**se queda**).
- **En `jvmTest`:** `com.sun.net.httpserver`, que muere cuando `HttpImageSourceTest` pase al
  `MockEngine` de Ktor.

## Reglas que siguen vigentes

- **Sin commits, sin ramas, sin pull requests** en ImageWatch.
- **Nunca `git checkout` ni `git restore`** aquí: el índice contiene la plantilla original y el
  árbol de trabajo lleva encima el renombrado de paquete que hizo el usuario. Un checkout lo
  destruiría.
- El borrado del repositorio privado y de la carpeta vieja (T21) es irreversible, **no
  conserva historial por decisión explícita del usuario**, y exige confirmación en el momento.
