# Plan de migración de ImageWatch a la plantilla KMP

> **Para agentes ejecutores:** SUB-SKILL OBLIGATORIA: usar
> `superpowers:subagent-driven-development` (recomendado) o
> `superpowers:executing-plans` para ejecutar tarea a tarea. Los pasos usan
> casillas (`- [ ]`) para seguimiento.

**`$ORIGEN` y `%ORIGEN%`** son la carpeta del proyecto de origen, en forma POSIX y de Windows.
No se escriben aquí su nombre ni su ruta: este repositorio no guarda referencias al anterior.

**Objetivo:** reemplazar el proyecto de origen por uno
construido sobre la plantilla KMP de IntelliJ IDEA, con la lógica portada de
Java a Kotlin, sin perder funcionalidad, cobertura ni el empaquetado MSI.

**Arquitectura:** dos módulos Gradle. `shared` es un módulo Kotlin
Multiplatform con un único target `jvm()`: su `commonMain` aloja dominio,
aplicación y la interfaz portable, y su `jvmMain` los adaptadores y los
`actual` de las cuatro costuras. `desktopApp` es un módulo Kotlin JVM que
contiene solo el arranque, la bandeja del sistema y la configuración del
instalador.

**Stack:** Kotlin 2.4.10, Compose Multiplatform 1.11.1, material3
1.11.0-alpha07, Gradle 9.1.0, JDK 21, kover 0.9.9, spotless 8.10.1.

**Spec:** `docs/superpowers/specs/2026-09-07-migracion-plantilla-kmp-design.md`

**Estado:** `docs/superpowers/plans/ESTADO.md` — leer primero. Registra qué
tareas están hechas, los desvíos ya aplicados y los dos bugs que introdujo el
conversor J2K.

---

## Restricciones globales

Se aplican a todas las tareas.

- **Directorio de trabajo:** `C:\Users\shizu\IdeaProjects\ImageWatch`. El
  proyecto de origen, `%ORIGEN%`,
  es **solo de lectura** hasta el final del plan.
- **Sin commits, sin ramas, sin pull requests** en el proyecto nuevo. Cada
  tarea termina en una verificación ejecutable, no en un commit. No ejecutar
  `git add`, `git commit`, `git checkout` ni `git restore` en ImageWatch: el
  índice contiene la plantilla original y el árbol de trabajo tiene encima el
  renombrado de paquete que hizo el usuario; un `checkout` lo destruiría.
- **Cómo se compila.** Gradle solo funciona desde PowerShell con `JAVA_HOME`
  fijado. En Bash, `./gradlew` sale con código 127 sin decir nada. Toda orden
  de este plan usa esta forma:

  ```powershell
  $env:JAVA_HOME='C:\Users\shizu\.jdks\ms-21.0.12.1'
  Set-Location C:\Users\shizu\IdeaProjects\ImageWatch
  .\gradlew.bat <tarea>
  ```

- **Paquete raíz:** `io.github.shizukajiku.imagewatch` en todo el proyecto.
- **Identidad del instalador,** literal y sin variación: `packageName =
  "ImageWatch"`, `packageVersion = "1.0.1"`, `upgradeUuid =
  "fd333cd8-6981-4e85-8e89-db2f23608c85"`, `perUserInstall = true`.
- **Kover 0.9.9**, aplicado dentro de `shared`. La 0.9.1 no configura el módulo
  KMP con Kotlin 2.4.10.
- **Umbral de cobertura:** 80% sobre `io.github.shizukajiku.imagewatch.domain.*`
  y `io.github.shizukajiku.imagewatch.application.*`.
- **Checkstyle y jspecify no se migran.** Ambos son específicos de Java y tras
  el port no queda ningún `.java`.
- Al terminar cada tarea, ejecutar `.\gradlew.bat spotlessApply` antes de la
  verificación final de esa tarea.

---

# FASE A — Reestructura y port a Kotlin

## Tarea 1: Configuración de compilación del proyecto destino

Deja el proyecto con las dependencias, el catálogo de versiones, spotless,
kover y el bloque del instalador correctos, todavía con el código de ejemplo de
la plantilla.

**Ficheros:**
- Modificar: `gradle/libs.versions.toml`
- Modificar: `build.gradle.kts`
- Modificar: `shared/build.gradle.kts`
- Modificar: `desktopApp/build.gradle.kts`

**Interfaces:**
- Produce: los alias del catálogo que consumen todas las tareas siguientes
  (`libs.semver4j`, `libs.jackson.databind`, `libs.slf4j.api`,
  `libs.logback.classic`, `libs.junit.jupiter`, `libs.assertj`,
  `libs.junit.platform.launcher`, `libs.kotlinx.coroutinesTest`,
  `libs.plugins.kover`, `libs.plugins.spotless`).

- [x] **Paso 1: reescribir el catálogo de versiones**

`gradle/libs.versions.toml` queda así. Se eliminan `junit` (JUnit 4) y
`kotlin-testJunit`, que la plantilla trae y este proyecto no usa.

```toml
[versions]
androidx-lifecycle = "2.11.0-beta01"
assertj = "3.27.3"
composeMultiplatform = "1.11.1"
jackson = "3.2.1"
junitJupiter = "5.12.2"
junitPlatform = "1.12.2"
kotlin = "2.4.10"
kotlinx-coroutines = "1.11.0"
kover = "0.9.9"
logback = "1.5.18"
material3 = "1.11.0-alpha07"
semver4j = "6.0.0"
slf4j = "2.0.17"
spotless = "8.10.1"

[libraries]
kotlin-test = { module = "org.jetbrains.kotlin:kotlin-test", version.ref = "kotlin" }
androidx-lifecycle-viewmodelCompose = { module = "org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "androidx-lifecycle" }
androidx-lifecycle-runtimeCompose = { module = "org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose", version.ref = "androidx-lifecycle" }
compose-runtime = { module = "org.jetbrains.compose.runtime:runtime", version.ref = "composeMultiplatform" }
compose-foundation = { module = "org.jetbrains.compose.foundation:foundation", version.ref = "composeMultiplatform" }
compose-material3 = { module = "org.jetbrains.compose.material3:material3", version.ref = "material3" }
compose-ui = { module = "org.jetbrains.compose.ui:ui", version.ref = "composeMultiplatform" }
compose-components-resources = { module = "org.jetbrains.compose.components:components-resources", version.ref = "composeMultiplatform" }
compose-uiToolingPreview = { module = "org.jetbrains.compose.ui:ui-tooling-preview", version.ref = "composeMultiplatform" }
kotlinx-coroutinesSwing = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-swing", version.ref = "kotlinx-coroutines" }
kotlinx-coroutinesTest = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "kotlinx-coroutines" }
assertj = { module = "org.assertj:assertj-core", version.ref = "assertj" }
jackson-databind = { module = "tools.jackson.core:jackson-databind", version.ref = "jackson" }
junit-jupiter = { module = "org.junit.jupiter:junit-jupiter", version.ref = "junitJupiter" }
junit-platform-launcher = { module = "org.junit.platform:junit-platform-launcher", version.ref = "junitPlatform" }
logback-classic = { module = "ch.qos.logback:logback-classic", version.ref = "logback" }
semver4j = { module = "org.semver4j:semver4j", version.ref = "semver4j" }
slf4j-api = { module = "org.slf4j:slf4j-api", version.ref = "slf4j" }

[plugins]
composeMultiplatform = { id = "org.jetbrains.compose", version.ref = "composeMultiplatform" }
composeCompiler = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlinJvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
kotlinMultiplatform = { id = "org.jetbrains.kotlin.multiplatform", version.ref = "kotlin" }
kover = { id = "org.jetbrains.kotlinx.kover", version.ref = "kover" }
spotless = { id = "com.diffplug.spotless", version.ref = "spotless" }
```

- [x] **Paso 2: configurar spotless en la raíz**

`build.gradle.kts`:

```kotlin
plugins {
    // Se cargan aquí sin aplicar para que cada subproyecto los resuelva desde
    // el mismo cargador de clases.
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kover) apply false
    alias(libs.plugins.spotless)
}

spotless {
    kotlin {
        ktlint()
        target("**/src/**/*.kt")
    }
    kotlinGradle {
        ktlint()
    }
}
```

- [x] **Paso 3: configurar `shared`**

`shared/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kover)
}

kotlin {
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutinesTest)
        }
        jvmMain.dependencies {
            implementation(libs.semver4j)
            implementation(libs.jackson.databind)
            implementation(libs.slf4j.api)
            implementation(libs.kotlinx.coroutinesSwing)
        }
        jvmTest.dependencies {
            implementation(libs.junit.jupiter)
            implementation(libs.assertj)
            runtimeOnly(libs.junit.platform.launcher)
        }
    }
}

// Con la plataforma JUnit configurada, `kotlin-test` resuelve por variantes a
// su versión de JUnit 5. Sin esta línea caería a JUnit 4 y los tests de
// `jvmTest`, escritos con Jupiter, no se ejecutarían.
tasks.named<Test>("jvmTest") {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

kover {
    reports {
        // El filtro va aquí y no dentro de `rule`: el marcador de DSL de kover
        // impide llamar a `filters` desde una regla.
        filters {
            includes {
                // El umbral mide el núcleo, no la interfaz: la cobertura de
                // composables cuenta recomposiciones, no comportamiento.
                classes("io.github.shizukajiku.imagewatch.domain.*")
                classes("io.github.shizukajiku.imagewatch.application.*")
            }
        }
        verify {
            rule {
                minBound(80)
            }
        }
    }
}
```

- [x] **Paso 4: configurar `desktopApp`**

`desktopApp/build.gradle.kts`. Corrige de paso el `io.github.shizuka` que la
plantilla dejó sin renombrar.

```kotlin
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(project(":shared"))

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)
    implementation(libs.compose.uiToolingPreview)
    implementation(libs.slf4j.api)
    // runtimeOnly a propósito: el código compila solo contra la fachada, así no
    // puede acoplarse a la implementación por accidente.
    runtimeOnly(libs.logback.classic)
}

compose.desktop {
    application {
        mainClass = "io.github.shizukajiku.imagewatch.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi)
            packageName = "ImageWatch"
            // Aparte de la versión del proyecto a propósito: MSI exige
            // MAYOR.MENOR.PARCHE con mayor > 0.
            packageVersion = "1.0.1"
            description = "Vigila las versiones de imágenes de contenedor publicadas en un registry"
            vendor = "ShizukaJiku"

            windows {
                menu = true
                shortcut = true
                dirChooser = true
                // Instala bajo el perfil del usuario en vez de Program Files:
                // sin esto jpackage pide elevación de administrador.
                perUserInstall = true
                // Fijo para siempre: si cambia, Windows instala un segundo
                // ImageWatch en lugar de actualizar el que ya hay.
                upgradeUuid = "fd333cd8-6981-4e85-8e89-db2f23608c85"
            }
        }
    }
}
```

- [x] **Paso 5: formatear y verificar**

```powershell
$env:JAVA_HOME='C:\Users\shizu\.jdks\ms-21.0.12.1'
Set-Location C:\Users\shizu\IdeaProjects\ImageWatch
.\gradlew.bat spotlessApply
.\gradlew.bat --configuration-cache :shared:koverVerify :desktopApp:compileKotlin
```

Esperado: `BUILD SUCCESSFUL`. `spotlessApply` corrige el salto de línea final
que le falta a `settings.gradle.kts` en la plantilla.

---

## Tarea 2: Recursos y configuración de registro

**Ficheros:**
- Crear: `shared/src/jvmMain/resources/icons/` (19 ficheros `.svg`)
- Crear: `shared/src/jvmMain/resources/sounds/` (4 `.wav` y el fichero `NOTICE`)
- Crear: `desktopApp/src/main/resources/logback.xml`
- Borrar: `shared/src/commonMain/composeResources/drawable/compose-multiplatform.xml`

**Interfaces:**
- Produce: rutas de classpath `/icons/<nombre>.svg` y `/sounds/<nombre>.wav`,
  que consumen `loadAppSvg` (tarea 8) y `Sounds` (tarea 8).

- [x] **Paso 1: copiar los recursos**

```bash
SRC=$ORIGEN/src/main/resources
DST=/c/Users/shizu/IdeaProjects/ImageWatch/shared/src/jvmMain/resources
mkdir -p "$DST"
cp -r "$SRC/icons" "$DST/"
cp -r "$SRC/sounds" "$DST/"
mkdir -p /c/Users/shizu/IdeaProjects/ImageWatch/desktopApp/src/main/resources
cp "$SRC/logback.xml" /c/Users/shizu/IdeaProjects/ImageWatch/desktopApp/src/main/resources/
rm -f /c/Users/shizu/IdeaProjects/ImageWatch/shared/src/commonMain/composeResources/drawable/compose-multiplatform.xml
```

- [x] **Paso 2: verificar el inventario**

```bash
ls /c/Users/shizu/IdeaProjects/ImageWatch/shared/src/jvmMain/resources/icons | wc -l
ls /c/Users/shizu/IdeaProjects/ImageWatch/shared/src/jvmMain/resources/sounds
```

Esperado: 19 iconos; `error.wav`, `NOTICE`, `success.wav`, `toggle.wav`,
`update.wav`. El fichero `NOTICE` documenta la licencia de los sonidos y su
ausencia sería un problema legal, no una omisión menor.

---

## Tarea 3: Conversión Java a Kotlin

Único punto del plan que requiere una acción manual del usuario. El conversor de
IntelliJ no es accesible desde el MCP.

**Ficheros:**
- Crear: `shared/src/jvmMain/java/io/github/shizukajiku/imagewatch/**` (27 `.java`)
- Crear: `shared/src/jvmTest/java/io/github/shizukajiku/imagewatch/**` (12 `.java`)
- Resultado: los mismos ficheros como `.kt`, ya sin `.java`

- [x] **Paso 1: copiar las fuentes Java al proyecto nuevo**

```bash
SRC=$ORIGEN/src
DST=/c/Users/shizu/IdeaProjects/ImageWatch/shared/src
mkdir -p "$DST/jvmMain/java" "$DST/jvmTest/java"
cp -r "$SRC/main/java/io" "$DST/jvmMain/java/"
cp -r "$SRC/test/java/io" "$DST/jvmTest/java/"
find "$DST/jvmMain/java" "$DST/jvmTest/java" -name '*.java' | wc -l
```

Esperado: 39 ficheros.

- [x] **Paso 2: quitar jspecify antes de convertir**

Un solo fichero lo usa. Las anotaciones de nulabilidad no significan nada en
Kotlin y el conversor las arrastraría como dependencia muerta.

```bash
cd /c/Users/shizu/IdeaProjects/ImageWatch/shared/src
grep -rl "org.jspecify" jvmMain/java | while read f; do
  sed -i '/import org\.jspecify/d; s/@NonNull //g; s/@Nullable //g' "$f"
done
grep -rn "jspecify\|@NonNull\|@Nullable" jvmMain/java || echo "jspecify fuera"
```

- [x] **Paso 3: verificar que el Java copiado compila donde está**

```powershell
$env:JAVA_HOME='C:\Users\shizu\.jdks\ms-21.0.12.1'
Set-Location C:\Users\shizu\IdeaProjects\ImageWatch
.\gradlew.bat :shared:compileJvmMainJava
```

Esperado: `BUILD SUCCESSFUL`. Este paso confirma que el problema, si algo falla
después, está en la conversión y no en la copia.

- [x] **Paso 4: pedir al usuario la conversión**

Mensaje literal para el usuario:

> Abre `C:\Users\shizu\IdeaProjects\ImageWatch` en IntelliJ IDEA. En el árbol
> del proyecto selecciona la carpeta `shared/src/jvmMain/java` y pulsa
> `Ctrl+Alt+Shift+K` (Code → Convert Java File to Kotlin File). Acepta cuando
> pregunte si corregir las llamadas desde el resto del código. Repite lo mismo
> con `shared/src/jvmTest/java`. Avísame cuando termine.

**Detener la ejecución hasta que el usuario confirme.**

- [x] **Paso 5: comprobar el resultado de la conversión**

```bash
cd /c/Users/shizu/IdeaProjects/ImageWatch/shared/src
find jvmMain/java jvmTest/java -name '*.java' | wc -l
find jvmMain/java jvmTest/java -name '*.kt' | wc -l
```

Esperado: 0 ficheros `.java` y 39 `.kt`. Si quedan `.java`, el conversor no
recorrió todo el árbol: repetir el paso 4 sobre los directorios que falten.

- [x] **Paso 6: mover los `.kt` fuera del directorio `java`**

El conversor deja los `.kt` donde estaban los `.java`. Gradle los compilaría
igual, pero un `.kt` bajo `src/jvmMain/java` confunde a cualquiera que lo lea
después.

```bash
cd /c/Users/shizu/IdeaProjects/ImageWatch/shared/src
cp -r jvmMain/java/io jvmMain/kotlin/
cp -r jvmTest/java/io jvmTest/kotlin/
rm -rf jvmMain/java jvmTest/java
find jvmMain/kotlin jvmTest/kotlin -name '*.kt' | wc -l
```

- [x] **Paso 7: verificar que todo compila y los tests pasan**

```powershell
$env:JAVA_HOME='C:\Users\shizu\.jdks\ms-21.0.12.1'
Set-Location C:\Users\shizu\IdeaProjects\ImageWatch
.\gradlew.bat :shared:jvmTest
```

Esperado: `BUILD SUCCESSFUL` con los 12 ficheros de test ejecutados. Este es el
punto de control más importante de la fase A: el comportamiento portado sigue
siendo el mismo, verificado por los tests originales.

---

## Tarea 4: Costura `Log`

`VersionPollingService` es núcleo puro pero registra con slf4j, que es solo JVM.
Se resuelve antes de mover nada a `commonMain`, porque bloquea la tarea 6.

**Ficheros:**
- Crear: `shared/src/commonMain/kotlin/io/github/shizukajiku/imagewatch/Log.kt`
- Crear: `shared/src/jvmMain/kotlin/io/github/shizukajiku/imagewatch/Log.jvm.kt`
- Modificar: las 8 llamadas de registro de `VersionPollingService.kt`

**Interfaces:**
- Produce: `class Log(name: String)` con `debug(String)`, `info(String)`,
  `warn(String, Throwable?)` y `error(String, Throwable?)`. La consume la tarea 6.

- [x] **Paso 1: declarar la fachada en `commonMain`**

```kotlin
package io.github.shizukajiku.imagewatch

/**
 * Registro mínimo para el código que vive en `commonMain`. Un solo consumidor:
 * `VersionPollingService`. El resto del proyecto está en el lado JVM y usa
 * slf4j directamente.
 *
 * Pierde la evaluación diferida de los `{}` de slf4j a cambio de plantillas de
 * Kotlin. Con un puñado de líneas por ciclo de sondeo, el coste es irrelevante.
 */
expect class Log(name: String) {
    fun debug(message: String)

    fun info(message: String)

    fun warn(message: String, error: Throwable? = null)

    fun error(message: String, error: Throwable? = null)
}
```

- [x] **Paso 2: implementarla en `jvmMain`**

```kotlin
package io.github.shizukajiku.imagewatch

import org.slf4j.LoggerFactory

actual class Log actual constructor(name: String) {
    private val delegate = LoggerFactory.getLogger(name)

    actual fun debug(message: String) = delegate.debug(message)

    actual fun info(message: String) = delegate.info(message)

    actual fun warn(message: String, error: Throwable?) {
        if (error == null) delegate.warn(message) else delegate.warn(message, error)
    }

    actual fun error(message: String, error: Throwable?) {
        if (error == null) delegate.error(message) else delegate.error(message, error)
    }
}
```

- [x] **Paso 3: reescribir las llamadas de `VersionPollingService`**

Sustituir la obtención del logger por `private val log = Log("io.github.shizukajiku.imagewatch.application.VersionPollingService")`
y convertir los `{}` de slf4j en plantillas de Kotlin. Las ocho llamadas, con su
forma final:

```kotlin
log.info("${release.name} marcada como vista en ${release.reference}")
log.warn("Un notificador falló al recibir las transiciones", e)
log.warn("No se pudo verificar ${image.name}: ${image.error ?: "sin detalle"}")
log.warn("Un oyente falló al recibir el comienzo del ciclo", e)
log.warn("Un oyente falló al recibir el snapshot", e)
log.info("$name marcada como vista en ${release.reference}")
```

Las dos restantes —el `LOG.info` de la línea 214 y el `LOG.debug` de la 227 del
fichero Java original— son llamadas multilínea: traducir cada `{}` a su
interpolación en el mismo orden en que aparecen los argumentos.

- [x] **Paso 4: verificar**

```powershell
$env:JAVA_HOME='C:\Users\shizu\.jdks\ms-21.0.12.1'
Set-Location C:\Users\shizu\IdeaProjects\ImageWatch
.\gradlew.bat spotlessApply :shared:jvmTest
```

Esperado: `BUILD SUCCESSFUL`, mismos tests en verde que en la tarea 3.

---

## Tarea 5: Dominio a `commonMain`

**Ficheros:**
- Mover a `shared/src/commonMain/kotlin/io/github/shizukajiku/imagewatch/domain/`:
  `ImageRelease.kt`, `ImageState.kt`, `ImageStatus.kt`, `PollSnapshot.kt`
- Crear: `shared/src/commonMain/kotlin/io/github/shizukajiku/imagewatch/domain/Version.kt`
- Crear: `shared/src/jvmMain/kotlin/io/github/shizukajiku/imagewatch/domain/Version.jvm.kt`
- Borrar: `shared/src/jvmMain/kotlin/io/github/shizukajiku/imagewatch/domain/Version.kt`

**Interfaces:**
- Consume: nada.
- Produce: `Version(value: String)` con `val value: String`, `Comparable<Version>`;
  `ImageState`, `ImageStatus`, `PollSnapshot`, `ImageRelease` como tipos de
  `commonMain`. Los consumen las tareas 6, 7, 8 y 10.

- [x] **Paso 1: declarar `Version` en `commonMain`**

```kotlin
package io.github.shizukajiku.imagewatch.domain

/**
 * Una versión publicada. El orden es semántico, no textual: `1.10.0` va después
 * de `1.9.0`. La igualdad, en cambio, es por el texto original, porque dos
 * cadenas distintas que ordenan igual siguen siendo dos etiquetas distintas del
 * registry.
 *
 * Vive tras una costura `expect`/`actual` porque el análisis semántico lo hace
 * semver4j, que es solo JVM.
 */
expect class Version(value: String) : Comparable<Version> {
    val value: String
}
```

- [x] **Paso 2: implementar `Version` en `jvmMain`**

```kotlin
package io.github.shizukajiku.imagewatch.domain

import org.semver4j.Semver

actual class Version actual constructor(
    actual val value: String,
) : Comparable<Version> {
    // Semver.parse devuelve null ante una cadena no reconocible. Guardarlo
    // diferiría el fallo hasta compareTo, lejos del origen; se rechaza aquí.
    private val semver: Semver =
        Semver.parse(value.replace(".RELEASE", "-RELEASE"))
            ?: throw IllegalArgumentException("Versión no reconocida: $value")

    override fun compareTo(other: Version): Int = semver.compareTo(other.semver)

    override fun equals(other: Any?): Boolean = other is Version && value == other.value

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = value
}
```

- [x] **Paso 3: mover el resto del dominio**

```bash
cd /c/Users/shizu/IdeaProjects/ImageWatch/shared/src
mkdir -p commonMain/kotlin/io/github/shizukajiku/imagewatch/domain
cd jvmMain/kotlin/io/github/shizukajiku/imagewatch/domain
mv ImageRelease.kt ImageState.kt ImageStatus.kt PollSnapshot.kt \
   ../../../../../../../commonMain/kotlin/io/github/shizukajiku/imagewatch/domain/
```

- [x] **Paso 4: adaptar los tipos JDK del dominio**

En los cuatro ficheros movidos, sustituir `java.time.Instant` por
`kotlin.time.Instant` y `java.util.Optional<T>` por `T?`. `Optional.empty()`
pasa a `null`, `Optional.of(x)` a `x`, `.orElse(y)` a `?: y`, `.isPresent` a
`!= null`, `.orElseThrow()` a `!!` solo donde el original ya garantizaba
presencia. `List.copyOf(images)` pasa a `images.toList()`, que hace la misma
copia defensiva. `Instant.EPOCH` pasa a `Instant.fromEpochSeconds(0)`.

- [x] **Paso 5: verificar**

```powershell
$env:JAVA_HOME='C:\Users\shizu\.jdks\ms-21.0.12.1'
Set-Location C:\Users\shizu\IdeaProjects\ImageWatch
.\gradlew.bat spotlessApply :shared:jvmTest
```

Esperado: `BUILD SUCCESSFUL`. Los tests siguen en `jvmTest` y siguen pasando:
mover código a `commonMain` no cambia lo que el target JVM ve.

---

## Tarea 6: Aplicación y configuración a `commonMain`

**Ficheros:**
- Mover a `shared/src/commonMain/kotlin/io/github/shizukajiku/imagewatch/application/`:
  `ConfigStore.kt`, `ImageResult.kt`, `ImageSource.kt`, `ImageStateStore.kt`,
  `NotificationPort.kt`, `PollListener.kt`, `PollingController.kt`,
  `SilencedImageStore.kt`, `TrackedImageStore.kt`, `VersionPollingService.kt`
- Mover a `shared/src/commonMain/kotlin/io/github/shizukajiku/imagewatch/config/`:
  `AppConfig.kt`, `ThemePreference.kt`

**Interfaces:**
- Consume: `Version`, `ImageState`, `ImageStatus`, `PollSnapshot`,
  `ImageRelease` (tarea 5); `Log` (tarea 4).
- Produce: los siete puertos y los dos servicios como tipos de `commonMain`.
  Los consumen las tareas 7, 8, 9 y 10.

- [x] **Paso 1: mover los ficheros**

```bash
cd /c/Users/shizu/IdeaProjects/ImageWatch/shared/src
mkdir -p commonMain/kotlin/io/github/shizukajiku/imagewatch/application \
         commonMain/kotlin/io/github/shizukajiku/imagewatch/config
mv jvmMain/kotlin/io/github/shizukajiku/imagewatch/application/*.kt \
   commonMain/kotlin/io/github/shizukajiku/imagewatch/application/
mv jvmMain/kotlin/io/github/shizukajiku/imagewatch/config/*.kt \
   commonMain/kotlin/io/github/shizukajiku/imagewatch/config/
rmdir jvmMain/kotlin/io/github/shizukajiku/imagewatch/application \
      jvmMain/kotlin/io/github/shizukajiku/imagewatch/config
```

- [x] **Paso 2: sustituir la concurrencia del JDK**

`PollingController` usa `java.util.concurrent`. La traducción, en orden de
preferencia: si el estado puede ser inmutable y reemplazarse entero, un
`MutableStateFlow` con `update {}`; si hace falta exclusión, un
`kotlinx.coroutines.sync.Mutex` con `withLock`. `ConcurrentHashMap` pasa a
`MutableMap` protegido por ese `Mutex`; `CopyOnWriteArrayList` a una `List`
inmutable reemplazada entera; `AtomicLong` a un `Long` bajo el mismo `Mutex`.

Los `ExecutorService` y `ScheduledExecutorService` que aparezcan pasan a
corrutinas: un `CoroutineScope` propio del controlador, con `launch` y `delay`
en lugar de `scheduleAtFixedRate`, y `cancel()` en el cierre.

- [x] **Paso 3: sustituir el resto de tipos JDK**

`java.time.Instant`, `Clock` y `Duration` pasan a sus equivalentes de
`kotlin.time`: `Clock.System.now()` en lugar de `Instant.now(clock)`, y
`n.minutes` en lugar de `Duration.ofMinutes(n)`. Los `Stream` pasan a funciones
de colección (`filter`, `map`, `firstOrNull`, `toList`). Los `Optional` siguen
la misma tabla de la tarea 5.

- [x] **Paso 4: verificar**

```powershell
$env:JAVA_HOME='C:\Users\shizu\.jdks\ms-21.0.12.1'
Set-Location C:\Users\shizu\IdeaProjects\ImageWatch
.\gradlew.bat spotlessApply :shared:jvmTest
```

Esperado: `BUILD SUCCESSFUL`, los 12 ficheros de test en verde.
`PollingControllerTest` y `VersionPollingServiceTest` son los que de verdad
prueban esta tarea: 739 líneas entre los dos.

---

## Tarea 7: Infraestructura, ya en su sitio

Los adaptadores se quedan donde el conversor los dejó. Esta tarea solo verifica
que el reparto es correcto y limpia lo que sobre.

**Ficheros:**
- Verificar en `shared/src/jvmMain/kotlin/io/github/shizukajiku/imagewatch/infrastructure/`:
  `persistence/` (`ConfigDto`, `JsonConfigStore`, `JsonFiles`,
  `JsonImageStateStore`, `JsonSilencedImageStore`, `JsonTrackedImageStore`) y
  `remote/` (`HttpClientFactory`, `HttpImageSource`, `ReleaseDto`,
  `ReloadableImageSource`, `SimulatedImageSource`)

**Interfaces:**
- Consume: los puertos de la tarea 6 y los tipos de dominio de la tarea 5.
- Produce: las implementaciones concretas que cablea `Main.kt` en la tarea 9.

- [x] **Paso 1: comprobar que no queda nada fuera de sitio**

```bash
cd /c/Users/shizu/IdeaProjects/ImageWatch/shared/src
echo "--- jvmMain (debe ser solo infrastructure, ui y los .jvm.kt) ---"
find jvmMain/kotlin -name '*.kt' | sed 's|.*/imagewatch/||' | sort
echo "--- commonMain ---"
find commonMain/kotlin -name '*.kt' | sed 's|.*/imagewatch/||' | sort
```

- [x] **Paso 2: confirmar que ninguna clase de `commonMain` importa el JDK**

```bash
cd /c/Users/shizu/IdeaProjects/ImageWatch/shared/src/commonMain
grep -rn "^import java\.\|^import javax\.\|^import org\.slf4j\|^import org\.semver4j\|^import tools\.jackson" . || echo "commonMain limpio"
```

Esperado: `commonMain limpio`. Cualquier coincidencia es una clase mal repartida
en las tareas 5 o 6, y hay que devolverla a `jvmMain` o darle su costura.

- [x] **Paso 3: verificar**

```powershell
$env:JAVA_HOME='C:\Users\shizu\.jdks\ms-21.0.12.1'
Set-Location C:\Users\shizu\IdeaProjects\ImageWatch
.\gradlew.bat spotlessApply :shared:jvmTest
```

Esperado: `BUILD SUCCESSFUL`.

---

## Tarea 8: Interfaz a `shared`

**Ficheros:**
- Copiar desde el proyecto origen a `shared/src/commonMain/kotlin/.../ui/`:
  `theme/Colors.kt`, `theme/Theme.kt`, `theme/Tokens.kt`,
  `components/StatusBadge.kt`, `components/VersionPill.kt`,
  `dialogs/ImageDialogs.kt`, `images/ImageRow.kt`, `images/ImagesScreen.kt`,
  `images/ImagesViewModel.kt`, `settings/SettingsScreen.kt`,
  `settings/SettingsViewModel.kt`, `toast/ToastState.kt`,
  `toast/ToastNotificationPort.kt`
- Crear: `shared/src/commonMain/kotlin/.../ui/components/SvgIcon.kt` (enum
  `AppSvg`, composable `SvgIcon`, `expect fun loadAppSvg`)
- Crear: `shared/src/jvmMain/kotlin/.../ui/components/SvgIcon.jvm.kt`
- Crear: `shared/src/commonMain/kotlin/.../ui/sound/Sounds.kt` (enum `Sound`,
  `expect class Sounds`)
- Crear: `shared/src/jvmMain/kotlin/.../ui/sound/Sounds.jvm.kt`
- Copiar a `shared/src/jvmMain/kotlin/.../ui/`: `AppIconPainter.kt`,
  `components/TitleBar.kt`, `toast/ToastWindow.kt`

**Interfaces:**
- Consume: dominio (tarea 5), aplicación (tarea 6), recursos de classpath (tarea 2).
- Produce: `loadAppSvg(svg: AppSvg, density: Density): Painter`;
  `Sounds(enabled: () -> Boolean, volume: () -> Double, windowFocused: () -> Boolean, onPlay: (Sound) -> Unit)`
  con `preload()`, `play(sound: Sound)` y `close()`. Los consume la tarea 9.

- [x] **Paso 1: copiar la interfaz**

```bash
SRC=$ORIGEN/src/main/kotlin/io/github/shizukajiku/imagewatch/ui
CM=/c/Users/shizu/IdeaProjects/ImageWatch/shared/src/commonMain/kotlin/io/github/shizukajiku/imagewatch/ui
JM=/c/Users/shizu/IdeaProjects/ImageWatch/shared/src/jvmMain/kotlin/io/github/shizukajiku/imagewatch/ui
mkdir -p "$CM"/{theme,components,dialogs,images,settings,toast,sound} "$JM"/{components,toast}
cp "$SRC"/theme/*.kt "$CM/theme/"
cp "$SRC"/components/StatusBadge.kt "$SRC"/components/VersionPill.kt "$CM/components/"
cp "$SRC"/dialogs/*.kt "$CM/dialogs/"
cp "$SRC"/images/*.kt "$CM/images/"
cp "$SRC"/settings/*.kt "$CM/settings/"
cp "$SRC"/toast/ToastState.kt "$SRC"/toast/ToastNotificationPort.kt "$CM/toast/"
cp "$SRC"/components/TitleBar.kt "$JM/components/"
cp "$SRC"/toast/ToastWindow.kt "$JM/toast/"
cp "$SRC"/AppIconPainter.kt "$JM/"
```

- [x] **Paso 2: partir `SvgIcon` por la costura**

`commonMain/.../ui/components/SvgIcon.kt`: se conserva el enum `AppSvg`
completo, con sus 19 entradas y su `resourcePath`, y el composable `SvgIcon` tal
cual está hoy —`Density`, `Painter` y `ColorFilter` son multiplataforma—. Solo
cambia `loadAppSvg`, que pasa a declaración:

```kotlin
/**
 * Carga un icono desde el classpath. Lanza si el recurso falta: un icono
 * ausente es un error de empaquetado, y fallar pronto avisa mejor que un hueco
 * en la pantalla.
 */
expect fun loadAppSvg(svg: AppSvg, density: Density): Painter
```

`jvmMain/.../ui/components/SvgIcon.jvm.kt`:

```kotlin
package io.github.shizukajiku.imagewatch.ui.components

import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.loadSvgPainter
import androidx.compose.ui.unit.Density

actual fun loadAppSvg(svg: AppSvg, density: Density): Painter {
    val stream = requireNotNull(AppSvg::class.java.getResourceAsStream(svg.resourcePath)) {
        "Falta el icono ${svg.resourcePath} en los recursos"
    }
    return stream.use { loadSvgPainter(it, density) }
}
```

- [x] **Paso 3: partir `Sounds` por la costura**

`commonMain/.../ui/sound/Sounds.kt` conserva el enum `Sound` íntegro —las cuatro
entradas, `silenciadoPorFoco` y `resourcePath`, con sus comentarios— y declara:

```kotlin
/**
 * Los cuatro avisos sonoros y las tres reglas que los gobiernan.
 *
 * @param onPlay gancho de prueba: recibe el sonido que se va a reproducir justo
 *   antes de hacerlo. Existe porque los tests no pueden comprobar que algo sonó,
 *   solo que se decidió reproducirlo.
 */
expect class Sounds(
    enabled: () -> Boolean,
    volume: () -> Double,
    windowFocused: () -> Boolean,
    onPlay: (Sound) -> Unit = {},
) {
    fun preload()

    fun play(sound: Sound)

    fun close()
}
```

`jvmMain/.../ui/sound/Sounds.jvm.kt` es el cuerpo actual de la clase sin cambios
de lógica: se marcan `actual` la clase, el constructor y los tres métodos
públicos, y **el valor por defecto de `onPlay` no se repite** —en KMP los
valores por defecto solo se declaran en el `expect`—. La constante
`BASE_ATTENUATION_DB` y el logger privado se mudan con ella.

- [x] **Paso 4: sustituir los tipos JDK de la interfaz**

`ImagesViewModel.kt`, `SettingsViewModel.kt` y `ToastState.kt` importan
`java.time` y `java.util.concurrent`. Aplicar la misma tabla de las tareas 5 y 6.
Para `DateTimeFormatter` en `ImagesViewModel`, escribir el formateo a mano sobre
los campos de `kotlin.time.Instant` —la fase B lo sustituirá por
kotlinx-datetime— y dejarlo marcado con un comentario que lo diga.

- [x] **Paso 5: verificar**

```powershell
$env:JAVA_HOME='C:\Users\shizu\.jdks\ms-21.0.12.1'
Set-Location C:\Users\shizu\IdeaProjects\ImageWatch
.\gradlew.bat spotlessApply :shared:jvmTest
```

Esperado: `BUILD SUCCESSFUL`.

---

## Tarea 9: Arranque en `desktopApp`

**Ficheros:**
- Crear: `desktopApp/src/main/kotlin/io/github/shizukajiku/imagewatch/Main.kt`
- Borrar: `desktopApp/src/main/kotlin/io/github/shizukajiku/imagewatch/main.kt`
  (el de la plantilla)
- Borrar: `shared/src/commonMain/kotlin/.../App.kt`, `Greeting.kt`,
  `GreetingUtil.kt`, `Platform.kt`; `shared/src/jvmMain/kotlin/.../Platform.jvm.kt`;
  `shared/src/commonTest/kotlin/.../SharedCommonTest.kt`;
  `shared/src/jvmTest/kotlin/.../SharedLogicDesktopTest.kt`

**Interfaces:**
- Consume: todo lo anterior.
- Produce: `main()` en `io.github.shizukajiku.imagewatch.MainKt`, que es lo que
  apunta `mainClass` en la tarea 1.

- [x] **Paso 1: retirar el código de ejemplo de la plantilla**

```bash
cd /c/Users/shizu/IdeaProjects/ImageWatch
rm -f desktopApp/src/main/kotlin/io/github/shizukajiku/imagewatch/main.kt \
      shared/src/commonMain/kotlin/io/github/shizukajiku/imagewatch/{App,Greeting,GreetingUtil,Platform}.kt \
      shared/src/jvmMain/kotlin/io/github/shizukajiku/imagewatch/Platform.jvm.kt \
      shared/src/commonTest/kotlin/io/github/shizukajiku/imagewatch/SharedCommonTest.kt \
      shared/src/jvmTest/kotlin/io/github/shizukajiku/imagewatch/SharedLogicDesktopTest.kt
```

- [x] **Paso 2: copiar `Main.kt`**

```bash
cp $ORIGEN/src/main/kotlin/io/github/shizukajiku/imagewatch/Main.kt \
   /c/Users/shizu/IdeaProjects/ImageWatch/desktopApp/src/main/kotlin/io/github/shizukajiku/imagewatch/Main.kt
```

- [x] **Paso 3: adaptar `Main.kt`**

Sustituir `java.time.Clock` y `java.time.Duration` por `kotlin.time`. El logger
sigue siendo slf4j directo: `Main.kt` está en el lado JVM y no necesita la
fachada `Log`. El resto —bandeja, ventana, cableado de adaptadores— no cambia.

- [x] **Paso 4: arrancar la aplicación**

```powershell
$env:JAVA_HOME='C:\Users\shizu\.jdks\ms-21.0.12.1'
Set-Location C:\Users\shizu\IdeaProjects\ImageWatch
.\gradlew.bat spotlessApply
.\gradlew.bat :desktopApp:run
```

Esperado: la ventana de ImageWatch abre. No hace falta `--enable-native-access`:
esa restricción es de JDK 25 y este proyecto compila con 21.

---

## Tarea 10: Tests de dominio y aplicación a `commonTest`

La parte más delicada del plan: reescribir aserciones a mano puede debilitarlas
sin que nada avise.

**Ficheros:**
- Mover a `shared/src/commonTest/kotlin/io/github/shizukajiku/imagewatch/`:
  `domain/VersionTest.kt`, `domain/PollSnapshotTest.kt`,
  `application/VersionPollingServiceTest.kt`,
  `application/PollingControllerTest.kt`
- Quedan en `shared/src/jvmTest/`: los cinco tests de `persistence` y los tres
  de `remote`

- [x] **Paso 1: traducir las aserciones**

Tabla de equivalencias entre AssertJ y `kotlin.test`:

| AssertJ | `kotlin.test` |
|---|---|
| `assertThat(x).isEqualTo(y)` | `assertEquals(y, x)` |
| `assertThat(x).isNotNull()` | `assertNotNull(x)` |
| `assertThat(x).isPresent()` | `assertNotNull(x)` |
| `assertThat(x).isEmpty()` | `assertNull(x)` o `assertTrue(x.isEmpty())` |
| `assertThat(list).containsExactly(a, b)` | `assertEquals(listOf(a, b), list)` |
| `assertThat(list).hasSize(n)` | `assertEquals(n, list.size)` |
| `assertThat(x).isGreaterThan(y)` | `assertTrue(x > y)` |
| `assertThatThrownBy { … }.isInstanceOf(E::class)` | `assertFailsWith<E> { … }` |
| `.hasMessageContaining(s)` | `assertTrue(s in ex.message!!)` sobre el resultado de `assertFailsWith` |

`assertThat(list).containsExactly(...)` compara orden y contenido: traducirlo a
`hasSize` sería perder la prueba.

- [x] **Paso 2: retirar el test que ya no tiene sentido**

`VersionTest.nullValueIsRejectedAtConstruction` comprobaba que el constructor
rechaza `null`. En Kotlin, `Version(value: String)` no admite nulos y el
compilador lo impide: el test es incomprobable. Se elimina y se anota el porqué
en el fichero.

- [x] **Paso 3: ampliar el contrato de `Version`**

Requisito de la fase B, escrito ahora para que registre lo que **hoy** hace
semver4j y no lo que suponemos. Añadir a `VersionTest`, ejecutándolos contra la
implementación actual:

```kotlin
@Test
fun preReleasesOrderBeforeTheirRelease() {
    assertTrue(Version("1.2.3-alpha") < Version("1.2.3"))
}

@Test
fun preReleaseIdentifiersOrderAmongThemselves() {
    assertTrue(Version("1.2.3-alpha") < Version("1.2.3-beta"))
}

@Test
fun buildMetadataDoesNotAffectOrder() {
    assertEquals(0, Version("1.2.3+build1").compareTo(Version("1.2.3+build2")))
}

@Test
fun buildMetadataStillCountsForEquality() {
    assertTrue(Version("1.2.3+build1") != Version("1.2.3+build2"))
}

@Test
fun tagsWithSuffixAreAccepted() {
    assertEquals("1.2.3-alpine", Version("1.2.3-alpine").value)
}
```

Si alguno falla, **no se corrige la implementación**: se corrige el test para
que refleje el comportamiento real de semver4j. El objetivo es capturar lo que
hay, no imponer lo que nos gustaría.

- [x] **Paso 4: comprobar que cada test portado sigue probando algo**

Para cada uno de los cuatro ficheros movidos: romper a propósito una línea de la
producción que cubre —invertir una comparación, devolver una lista vacía—,
ejecutar `:shared:jvmTest` y confirmar que falla. Deshacer el cambio. Si no
falla, la aserción se perdió en la traducción y hay que reescribirla.

- [x] **Paso 5: verificar cobertura**

```powershell
$env:JAVA_HOME='C:\Users\shizu\.jdks\ms-21.0.12.1'
Set-Location C:\Users\shizu\IdeaProjects\ImageWatch
.\gradlew.bat spotlessApply
.\gradlew.bat --configuration-cache :shared:koverVerify
```

Esperado: `BUILD SUCCESSFUL`. Si `koverVerify` falla por debajo del 80%, el
informe está en `shared/build/reports/kover/`; la causa más probable es un test
que no se movió.

---

## Tarea 11: Tests de interfaz

**Ficheros:**
- Mover a `shared/src/commonTest/kotlin/.../ui/`: `theme/ColorsTest.kt`,
  `toast/ToastStateTest.kt`, `toast/ToastNotificationPortTest.kt`,
  `images/ImagesViewModelTest.kt`, `settings/SettingsViewModelTest.kt`
- Mover a `shared/src/jvmTest/kotlin/.../ui/`: `components/SvgIconTest.kt`,
  `sound/SoundsTest.kt`

- [x] **Paso 1: copiar y repartir**

```bash
SRC=$ORIGEN/src/test/kotlin/io/github/shizukajiku/imagewatch/ui
CT=/c/Users/shizu/IdeaProjects/ImageWatch/shared/src/commonTest/kotlin/io/github/shizukajiku/imagewatch/ui
JT=/c/Users/shizu/IdeaProjects/ImageWatch/shared/src/jvmTest/kotlin/io/github/shizukajiku/imagewatch/ui
mkdir -p "$CT"/{theme,toast,images,settings} "$JT"/{components,sound}
cp "$SRC"/theme/ColorsTest.kt "$CT/theme/"
cp "$SRC"/toast/*.kt "$CT/toast/"
cp "$SRC"/images/ImagesViewModelTest.kt "$CT/images/"
cp "$SRC"/settings/SettingsViewModelTest.kt "$CT/settings/"
cp "$SRC"/components/SvgIconTest.kt "$JT/components/"
cp "$SRC"/sound/SoundsTest.kt "$JT/sound/"
```

`SvgIconTest` y `SoundsTest` van a `jvmTest` porque prueban precisamente el lado
JVM de sus costuras: que el recurso existe en el classpath y que el audio carga.

- [x] **Paso 2: traducir aserciones de los que van a `commonTest`**

Misma tabla de la tarea 10. Los cinco ficheros usan JUnit 5 y AssertJ hoy.

- [x] **Paso 3: verificar**

```powershell
$env:JAVA_HOME='C:\Users\shizu\.jdks\ms-21.0.12.1'
Set-Location C:\Users\shizu\IdeaProjects\ImageWatch
.\gradlew.bat spotlessApply
.\gradlew.bat --configuration-cache :shared:jvmTest :shared:koverVerify
```

Esperado: `BUILD SUCCESSFUL` con los 19 ficheros de test ejecutados.

---

## Tarea 12: Instalador

**Ficheros:**
- Verificar: `desktopApp/build.gradle.kts` (bloque de la tarea 1)

- [x] **Paso 1: generar el instalador**

```powershell
$env:JAVA_HOME='C:\Users\shizu\.jdks\ms-21.0.12.1'
Set-Location C:\Users\shizu\IdeaProjects\ImageWatch
.\gradlew.bat :desktopApp:packageMsi
Get-ChildItem -Recurse desktopApp\build\compose\binaries -Filter *.msi | Select-Object FullName, Length
```

Esperado: un `.msi` llamado `ImageWatch-1.0.1.msi`.

- [x] **Paso 2: probar que actualiza en lugar de duplicar**

Con la versión 1.0.1 ya instalada desde el proyecto viejo, ejecutar el `.msi`
generado y después comprobar el inventario:

```powershell
Get-ItemProperty HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall\* |
  Where-Object { $_.DisplayName -like 'ImageWatch*' } |
  Select-Object DisplayName, DisplayVersion
```

Esperado: **una sola** entrada `ImageWatch`. Dos entradas significan que el
`upgradeUuid` o el `packageName` no coinciden con los del proyecto original: es
el fallo que esta tarea existe para detectar.

- [x] **Paso 3: arrancar la aplicación instalada**

Abrirla desde el menú de inicio y comprobar que la ventana carga sin errores en
consola.

---

## Tarea 13: Integración continua y documentación

**Ficheros:**
- Crear: `.github/workflows/tests.yml`, `.github/workflows/release.yml`
- Crear: `scripts/install-hooks.sh`, `scripts/pre-commit`
- Crear: `README.md` (reemplaza el de la plantilla), `docs/`, `design/`,
  `.superpowers/`
- Modificar: `.gitignore`

- [x] **Paso 1: copiar lo que se conserva**

```bash
SRC=$ORIGEN
DST=/c/Users/shizu/IdeaProjects/ImageWatch
cp -r "$SRC/.github" "$SRC/scripts" "$SRC/design" "$SRC/.superpowers" "$DST/"
cp -r "$SRC/docs/"* "$DST/docs/"
cp "$SRC/README.md" "$SRC/.editorconfig" "$DST/"
```

`docs/` ya existe en el destino y contiene el spec y este plan: la copia añade,
no reemplaza.

- [x] **Paso 2: ajustar los flujos de trabajo**

En `tests.yml` y `release.yml`: la versión de Java pasa de 25 a **21**, y los
nombres de tarea llevan prefijo de módulo. `test` pasa a `:shared:jvmTest`,
`koverVerify` a `:shared:koverVerify`, `packageMsi` a `:desktopApp:packageMsi`,
`build` a `build` (sigue siendo del proyecto entero). Toda referencia a
`checkstyleMain` o `checkstyleTest` se elimina.

- [x] **Paso 3: actualizar el README**

La sección de estructura del proyecto describe un módulo único; reescribirla con
los dos módulos y el reparto `commonMain`/`jvmMain`. Las órdenes de compilación
pasan a llevar prefijo de módulo. La sección de requisitos pasa de JDK 25 a 21.

- [x] **Paso 4: completar `.gitignore`**

El de la plantilla ignora `.idea`, `*.iml`, `.gradle`, `**/build/` y `.kotlin`.
Añadir lo que tenía el proyecto viejo y no esté ya.

- [x] **Paso 5: verificar el proyecto completo**

```powershell
$env:JAVA_HOME='C:\Users\shizu\.jdks\ms-21.0.12.1'
Set-Location C:\Users\shizu\IdeaProjects\ImageWatch
.\gradlew.bat --configuration-cache clean build :shared:koverVerify
```

Esperado: `BUILD SUCCESSFUL`.

---

## Tarea 14: Puerta de salida de la fase A

Sin acciones de código. Es el punto donde el usuario decide si se sigue.

- [x] **Paso 1: comprobación automática**

```powershell
$env:JAVA_HOME='C:\Users\shizu\.jdks\ms-21.0.12.1'
Set-Location C:\Users\shizu\IdeaProjects\ImageWatch
.\gradlew.bat --configuration-cache clean build :shared:koverVerify spotlessCheck
```

Los cuatro deben pasar.

- [x] **Paso 2: validación visual del usuario**

Arrancar con `.\gradlew.bat :desktopApp:run` y pedir al usuario que compruebe,
uno por uno: la tabla de imágenes con sus estados y colores; añadir, editar y
borrar una imagen; el buscador; silenciar y reactivar; la pantalla de ajustes
con tema, intervalo y volumen; los toasts; los sonidos; la bandeja del sistema;
y la barra de título propia con arrastre y botones.

**Detener la ejecución hasta que el usuario apruebe.** La fase B no empieza
antes.

---

# FASE B — Infraestructura multiplataforma

Cada tarea sustituye un adaptador y lo sube a `commonMain`. Los puertos no se
tocan, así que los tests de `commonTest` de la fase A no cambian: son la red de
seguridad.

## Tarea 15: Serialización con kotlinx-serialization

**Ficheros:**
- Modificar: `gradle/libs.versions.toml`, `shared/build.gradle.kts`,
  `build.gradle.kts`
- Mover y reescribir: `ConfigDto.kt`, `ReleaseDto.kt`, `JsonConfigStore.kt`,
  `JsonImageStateStore.kt`, `JsonSilencedImageStore.kt`,
  `JsonTrackedImageStore.kt`
- Modificar: `JsonFiles.kt` (la parte de serialización; la de ficheros es la
  tarea 16)

- [x] **Paso 1: añadir el plugin y la librería**

En `libs.versions.toml`:

```toml
[versions]
kotlinxSerialization = "1.9.0"

[libraries]
kotlinx-serializationJson = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "kotlinxSerialization" }

[plugins]
kotlinSerialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
```

En `build.gradle.kts`, añadir `alias(libs.plugins.kotlinSerialization) apply false`.
En `shared/build.gradle.kts`, aplicar `alias(libs.plugins.kotlinSerialization)` y
añadir `implementation(libs.kotlinx.serializationJson)` a `commonMain`.

- [x] **Paso 2: convertir los DTO**

`ConfigDto` y `ReleaseDto` pasan a `commonMain` como `@Serializable data class`.
Las anotaciones de Jackson se sustituyen por las de kotlinx: `@JsonProperty("x")`
por `@SerialName("x")`, y los campos opcionales por propiedades con valor por
defecto.

- [x] **Paso 3: sustituir el `ObjectMapper`**

Donde había `ObjectMapper`, un `Json` configurado:

```kotlin
private val json = Json {
    // El fichero de configuración lo edita gente a mano: un campo desconocido
    // no debe impedir arrancar.
    ignoreUnknownKeys = true
    prettyPrint = true
}
```

- [x] **Paso 4: verificar**

```powershell
$env:JAVA_HOME='C:\Users\shizu\.jdks\ms-21.0.12.1'
Set-Location C:\Users\shizu\IdeaProjects\ImageWatch
.\gradlew.bat spotlessApply
.\gradlew.bat --configuration-cache :shared:jvmTest
```

Los tests de persistencia siguen en `jvmTest` y siguen pasando sin cambios: son
la prueba de que el reemplazo es equivalente.

---

## Tarea 16: Sistema de ficheros con okio

**Ficheros:**
- Modificar: `gradle/libs.versions.toml`, `shared/build.gradle.kts`
- Mover a `commonMain` y reescribir: `JsonFiles.kt` y los cuatro almacenes JSON

- [x] **Paso 1: añadir okio**

```toml
[versions]
okio = "3.10.2"

[libraries]
okio = { module = "com.squareup.okio:okio", version.ref = "okio" }
```

`implementation(libs.okio)` en `commonMain`.

- [x] **Paso 2: reescribir `JsonFiles`**

`java.nio.file.Path` pasa a `okio.Path`, `Files.readString` a
`FileSystem.SYSTEM.read(path) { readUtf8() }`, `Files.writeString` a
`FileSystem.SYSTEM.write(path) { writeUtf8(texto) }`,
`Files.createDirectories` a `FileSystem.SYSTEM.createDirectories`, y
`Files.exists` a `FileSystem.SYSTEM.exists`.

La escritura atómica —fichero temporal y `ATOMIC_MOVE`— se conserva con
`FileSystem.SYSTEM.atomicMove(temporal, destino)`. Es lo que evita que un corte
a mitad de escritura deje la configuración del usuario truncada, así que no se
simplifica.

- [x] **Paso 3: verificar**

```powershell
$env:JAVA_HOME='C:\Users\shizu\.jdks\ms-21.0.12.1'
Set-Location C:\Users\shizu\IdeaProjects\ImageWatch
.\gradlew.bat spotlessApply
.\gradlew.bat --configuration-cache :shared:jvmTest
```

`JsonFilesTest` cubre precisamente la escritura atómica: si pasa, el reemplazo
mantiene la garantía.

---

## Tarea 17: Cliente HTTP con Ktor

**Ficheros:**
- Modificar: `gradle/libs.versions.toml`, `shared/build.gradle.kts`
- Mover a `commonMain` y reescribir: `HttpImageSource.kt`, `HttpClientFactory.kt`
- Mover a `commonMain`: `ReloadableImageSource.kt`, `SimulatedImageSource.kt`

- [x] **Paso 1: añadir Ktor**

```toml
[versions]
ktor = "3.3.0"

[libraries]
ktor-clientCore = { module = "io.ktor:ktor-client-core", version.ref = "ktor" }
ktor-clientContentNegotiation = { module = "io.ktor:ktor-client-content-negotiation", version.ref = "ktor" }
ktor-serializationJson = { module = "io.ktor:ktor-serialization-kotlinx-json", version.ref = "ktor" }
ktor-clientOkhttp = { module = "io.ktor:ktor-client-okhttp", version.ref = "ktor" }
ktor-clientMock = { module = "io.ktor:ktor-client-mock", version.ref = "ktor" }
```

Los tres primeros en `commonMain`, el motor `okhttp` en `jvmMain`, y
`ktor-clientMock` en `commonTest`.

- [x] **Paso 2: reescribir el cliente**

`HttpClient.newBuilder()` pasa a `HttpClient(engine) { install(ContentNegotiation) { json(json) } }`.
Las llamadas `send(...)` bloqueantes pasan a `suspend fun` con `client.get(url)`.
El tiempo de espera se configura con el plugin `HttpTimeout`, no con
`Duration` en el constructor.

`HttpImageSource.findByNames` pasa a `suspend`, y con ella el puerto
`ImageSource` en `commonMain`. `VersionPollingService`, que ya vive en
corrutinas tras la tarea 6, la llama directamente.

- [x] **Paso 3: portar los tests de `HttpImageSource`**

Las 155 líneas de `HttpImageSourceTest` usan hoy un servidor HTTP local.
Reescribirlas con `MockEngine` de Ktor y moverlas a `commonTest`: cada caso de
respuesta —200 con cuerpo válido, 404, cuerpo ilegible, tiempo agotado— se
declara como respuesta del motor simulado.

- [x] **Paso 4: verificar**

```powershell
$env:JAVA_HOME='C:\Users\shizu\.jdks\ms-21.0.12.1'
Set-Location C:\Users\shizu\IdeaProjects\ImageWatch
.\gradlew.bat spotlessApply
.\gradlew.bat --configuration-cache :shared:jvmTest :shared:koverVerify
```

---

## Tarea 18: Fechas con kotlinx-datetime

**Ficheros:**
- Modificar: `gradle/libs.versions.toml`, `shared/build.gradle.kts`
- Modificar: `ImagesViewModel.kt` (el formateo marcado en la tarea 8)

- [x] **Paso 1: añadir la librería**

```toml
[versions]
kotlinxDatetime = "0.7.1"

[libraries]
kotlinx-datetime = { module = "org.jetbrains.kotlinx:kotlinx-datetime", version.ref = "kotlinxDatetime" }
```

`implementation(libs.kotlinx.datetime)` en `commonMain`.

- [x] **Paso 2: sustituir el formateo manual**

Reemplazar el formateo escrito a mano en la tarea 8 por
`LocalDateTime.Format { … }` de kotlinx-datetime, conservando exactamente el
mismo patrón de salida que producía el `DateTimeFormatter` original. Retirar el
comentario que marcaba la deuda.

- [x] **Paso 3: verificar**

```powershell
$env:JAVA_HOME='C:\Users\shizu\.jdks\ms-21.0.12.1'
Set-Location C:\Users\shizu\IdeaProjects\ImageWatch
.\gradlew.bat spotlessApply
.\gradlew.bat --configuration-cache :shared:jvmTest
.\gradlew.bat :desktopApp:run
```

Comprobar a ojo que la columna de última comprobación muestra el mismo formato
que antes.

---

## Tarea 19: `Version` sin semver4j

Última costura de la fase B, y la única con salida de emergencia.

**Ficheros:**
- Mover a `commonMain` y reescribir: `Version.kt`
- Borrar: `Version.jvm.kt`
- Modificar: `shared/build.gradle.kts` (retirar `libs.semver4j`),
  `gradle/libs.versions.toml`

- [x] **Paso 1: comprobar el contrato**

Ejecutar `VersionTest` —ampliado en la tarea 10— contra la implementación con
semver4j y anotar qué pasa cada caso. Ese es el comportamiento a reproducir.

- [x] **Paso 2: escribir la implementación**

`Version` pasa de `expect class` a clase normal en `commonMain`. Reproduce:
normalización de `.RELEASE` a `-RELEASE` antes de analizar; rechazo con
`IllegalArgumentException` cuyo mensaje contiene el valor original; orden por
mayor, menor y parche numéricos; pre-release antes que su versión final; orden
entre identificadores de pre-release; metadatos de build ignorados en el orden
pero significativos en la igualdad; igualdad por el texto original.

- [x] **Paso 3: verificar**

```powershell
$env:JAVA_HOME='C:\Users\shizu\.jdks\ms-21.0.12.1'
Set-Location C:\Users\shizu\IdeaProjects\ImageWatch
.\gradlew.bat spotlessApply
.\gradlew.bat --configuration-cache :shared:jvmTest :shared:koverVerify
```

Todos los casos de `VersionTest` deben pasar sin haberlos tocado.

- [ ] **Paso 4: salida de emergencia** — no hizo falta: los 9 casos originales pasan sin tocarlos

Si la implementación no reproduce el comportamiento tras un intento razonable,
**revertir esta tarea y dejar `Version` como `expect`/`actual` con semver4j.**
No bloquea nada más: el único coste es que un futuro target no JVM necesitaría
su propio `actual`. Anotarlo en el spec y continuar.

---

## Tarea 20: Limpieza y verificación final

- [x] **Paso 1: comprobar qué queda en `jvmMain`**

```bash
cd /c/Users/shizu/IdeaProjects/ImageWatch/shared/src
find jvmMain/kotlin -name '*.kt' | sed 's|.*/imagewatch/||' | sort
```

Esperado, y nada más: `Log.jvm.kt`, `ui/sound/Sounds.jvm.kt`,
`ui/components/SvgIcon.jvm.kt`, `ui/components/TitleBar.kt`,
`ui/toast/ToastWindow.kt`, `ui/AppIconPainter.kt`, el motor de Ktor si necesita
configuración propia, y `Version.jvm.kt` solo si se activó la salida de la
tarea 19.

- [x] **Paso 2: promover los tests de persistencia**

Tras las tareas 15 y 16, `JsonConfigStoreTest`, `JsonFilesTest`,
`JsonImageStateStoreTest`, `JsonSilencedImageStoreTest` y
`JsonTrackedImageStoreTest` ya no prueban nada específico de la JVM: el sujeto
vive en `commonMain`. Se traducen sus aserciones con la tabla de la tarea 10, se
mueven a `commonTest`, y las versiones de `jvmTest` se retiran. Se hacen **de
uno en uno**, ejecutando `:shared:jvmTest` tras cada uno, para que un fallo
señale a un fichero concreto.

Los ficheros temporales que usan pasan de `@TempDir` de JUnit a
`FileSystem.SYSTEM_TEMPORARY_DIRECTORY` de okio, creando y borrando el
directorio en el propio test.

- [x] **Paso 3: retirar dependencias muertas**

Comprobar que `jackson-databind` y `slf4j-api` ya no están en `commonMain`, y
que `semver4j` desapareció del catálogo si la tarea 19 salió bien.

- [x] **Paso 4: verificación completa**

```powershell
$env:JAVA_HOME='C:\Users\shizu\.jdks\ms-21.0.12.1'
Set-Location C:\Users\shizu\IdeaProjects\ImageWatch
.\gradlew.bat --configuration-cache clean build :shared:koverVerify spotlessCheck
.\gradlew.bat :desktopApp:packageMsi
```

- [x] **Paso 5: validación visual final**

Repetir la lista de la tarea 14 completa.

---

## Tarea 21: Retirada del proyecto antiguo

**Irreversible.** No ejecutar ningún paso sin confirmación explícita del usuario
en ese momento.

- [ ] **Paso 1: pedir confirmación**

Mensaje literal para el usuario:

> La migración está validada. El siguiente paso borra el repositorio privado de
> GitHub y la carpeta local del proyecto de origen. Se pierde todo el
> historial de git: las seis fases, los pull requests y los mensajes de commit.
> No hay copia ni archivo, tal como decidiste. ¿Procedo?

**Detener hasta recibir un sí explícito.**

- [ ] **Paso 2: borrar el repositorio remoto**

```powershell
gh repo delete ShizukaJiku/<repositorio-de-origen> --yes
```

- [ ] **Paso 3: borrar la carpeta local**

```powershell
Remove-Item -Recurse -Force %ORIGEN%
```

- [ ] **Paso 4: comprobar que ImageWatch sigue en pie**

```powershell
$env:JAVA_HOME='C:\Users\shizu\.jdks\ms-21.0.12.1'
Set-Location C:\Users\shizu\IdeaProjects\ImageWatch
.\gradlew.bat --configuration-cache build :shared:koverVerify
```

Esperado: `BUILD SUCCESSFUL`. Ninguna ruta del proyecto nuevo debe apuntar al
viejo.
