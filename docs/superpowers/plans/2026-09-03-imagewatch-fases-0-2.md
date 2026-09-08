# ImageWatch — Plan de implementación, Fases 0 a 2

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Dejar el proyecto en un repositorio privado limpio de identificadores corporativos, construido con Gradle sobre JDK 25, con el mismo comportamiento observable que hoy y sin el fallo de parseo de versiones.

**Architecture:** Migración por estrangulamiento. Ninguna fase cambia la arquitectura hexagonal existente (`domain` / `application` / `infrastructure` / `ui`, con el cableado en el composition root). La Fase 0 purga el árbol **antes** de que exista historial de git; la Fase 1 sustituye Maven por Gradle sin tocar el código; la Fase 2 eleva la plataforma a Java 25 y corrige un defecto concreto. Cada fase termina con la aplicación arrancando igual que antes.

**Tech Stack:** Java 21 → 25 · Gradle 9.7.1 (Kotlin DSL) · JUnit 5 · AssertJ · checkstyle · spotless + google-java-format · Swing/FlatLaf (se sustituye en la Fase 4, fuera de este plan)

**Spec:** `docs/superpowers/specs/2026-09-03-imagewatch-design.md`

## Global Constraints

Estas restricciones aplican a **todas** las tareas de este plan.

- **JDK 25** en `~/.jdks/ms-25.0.4.1` (`openjdk 25.0.4.1 LTS`). Es el único JDK 25 de la máquina.
- **Gradle 9.7.1.** Java 25 requiere Gradle ≥ 9.1.0; 9.7.1 es la versión actual.
- **Paquete raíz:** `io.github.shizukajiku.imagewatch`. La convención de GitHub es `io.github.<usuario>`, no `org.github.<usuario>`.
- **Coordenadas:** `group = "io.github.shizukajiku"`, `rootProject.name = "imagewatch"`.
- **Nombre visible de la aplicación:** `ImageWatch`.
- **El repositorio es privado.**
- **Ningún identificador corporativo puede entrar en el historial de git.** Esto incluye: nombre de paquete anterior, URL del origen remoto, lista de nombres de imagen por defecto, nombres de usuario corporativos, repositorios de artefactos internos y el color de marca. La purga precede al primer commit; una vez commiteado, permanece.
- **`SIMULATION_MODE` por defecto `true`.** Un clon limpio arranca sin datos corporativos.
- **Jackson no se toca.** `tools.jackson.core:jackson-databind` 3.x depende de `com.fasterxml.jackson.core:jackson-annotations` 2.x por diseño. El uso actual es correcto.
- **La validación TLS mantiene su comportamiento actual.** Fuera de alcance por decisión explícita.
- Mensajes de commit en formato convencional (`feat:`, `fix:`, `chore:`, `build:`, `refactor:`, `test:`).

---

## Estructura de ficheros

### Se crean

| Fichero | Responsabilidad |
|---|---|
| `.denylist.local` | Patrones prohibidos, uno por línea. **No versionado.** Única sede de los valores literales. |
| `scripts/pre-commit` | Hook genérico. Lee `.denylist.local` en tiempo de commit; no contiene ningún patrón. Versionable sin filtrar nada. |
| `scripts/install-hooks.sh` | Copia `scripts/pre-commit` a `.git/hooks/` y le da permiso de ejecución. |
| `settings.gradle.kts` | Nombre del proyecto raíz. |
| `gradle/libs.versions.toml` | Version catalog: una sola sede para versiones de dependencias y plugins. |
| `build.gradle.kts` | Configuración de compilación, dependencias, checkstyle, spotless y arranque. |
| `gradle/wrapper/*`, `gradlew`, `gradlew.bat` | Wrapper. Fija la versión de Gradle para cualquiera que clone. |

### Se modifican

| Fichero | Cambio |
|---|---|
| `.gitignore` | `.idea/` completo (hoy solo cuatro ficheros sueltos), entradas de Gradle, `.denylist.local`. |
| Los 22 ficheros `.java` | Renombrado de paquete e imports. |
| `AppConfig.java` | Se eliminan la URL y los nombres de imagen por defecto; `SIMULATION_MODE` pasa a `true`. |
| `ImageManagerFrame.java` | Color de acento y sus derivados. |
| `TrayUi.java`, `NotificationDemoMain.java` | Nombre visible de la aplicación. |
| `Version.java` | Corrección del fallo de parseo. |
| Los 2 ficheros de test | Nombres de imagen neutros. |

### Se eliminan

| Fichero | Motivo |
|---|---|
| `pom.xml`, `.mvn/` | Sustituidos por Gradle. |
| `.idea/` (del árbol de trabajo no; solo se ignora) | Contiene datos que no deben versionarse. |

---

## FASE 0 — Higiene previa al primer commit

> **Lectura obligatoria antes de empezar.** No existe repositorio git. Las tareas 1 y 2 **no terminan en commit** porque todavía no hay dónde commitear: el primer commit es la tarea 3, y llega solo cuando el árbol ya está limpio. Es deliberado. Si se inicializa el repositorio antes de purgar, los identificadores corporativos quedan en el historial de forma permanente y la única salida es reescribir el historial o recrear el repositorio.
>
> Verificación en cada tarea de esta fase: `mvn -Pquick test` (Maven sigue siendo el build hasta la Fase 1).

---

### Task 1: Blindar `.gitignore` y crear la denylist

**Files:**
- Modify: `.gitignore`
- Create: `.denylist.local`

**Interfaces:**
- Produces: `.denylist.local` en la raíz del repositorio, un patrón de regex extendida por línea, líneas vacías y las que empiezan por `#` ignoradas. Lo consumen `scripts/pre-commit` (Task 3) y las verificaciones manuales de las tareas 2 y 4.

- [ ] **Step 1: Sustituir el bloque de IntelliJ en `.gitignore`**

El `.gitignore` actual excluye cuatro ficheros sueltos del directorio del IDE. El fichero de espacio de trabajo **no** está en esa lista y sí se commitearía; contiene el nombre de usuario corporativo de otra persona, rutas absolutas y referencias a un repositorio de artefactos interno.

Reemplaza este bloque:

```
### IntelliJ IDEA ###
.idea/modules.xml
.idea/jarRepositories.xml
.idea/compiler.xml
.idea/libraries/
*.iws
*.iml
*.ipr
```

por este:

```
### IntelliJ IDEA ###
.idea/
*.iws
*.iml
*.ipr
```

- [ ] **Step 2: Añadir las entradas de Gradle y de secretos**

Añade al final de `.gitignore`:

```
### Gradle ###
.gradle/
build/
!gradle/wrapper/gradle-wrapper.jar

### Secretos locales ###
.denylist.local
*.local
```

- [ ] **Step 3: Crear `.denylist.local`**

Este fichero contiene los valores literales que nunca deben entrar en el repositorio. **Nunca se versiona** — de lo contrario filtraría exactamente lo que pretende bloquear.

Crea `.denylist.local` con un patrón de regex extendida por línea. Debe cubrir, como mínimo, una entrada por cada categoría del apartado 9 de la spec:

- el nombre del paquete Java anterior,
- el nombre comercial que aparecía en el artifactId y en el título de la ventana,
- el dominio del origen remoto y su sufijo interno,
- el host del repositorio de artefactos interno,
- el patrón de los nombres de imagen por defecto,
- el color de marca en hexadecimal,
- los nombres de usuario corporativos que aparecen en el directorio del IDE.

Formato:

```
# Un patrón de regex extendida por línea. Las líneas vacías y las que
# empiezan por '#' se ignoran. El hook las aplica sin distinguir mayúsculas.
patron-uno
patron-dos
```

- [ ] **Step 4: Verificar que la denylist está ignorada**

Todavía no hay repositorio, así que la comprobación es textual:

```bash
grep -n "^\.denylist\.local$" .gitignore
grep -n "^\.idea/$" .gitignore
```

Expected: ambas líneas aparecen. Si `.idea/` no aparece exactamente así (con la barra final y sin sufijos), la exclusión no es recursiva y el fichero de espacio de trabajo se colaría.

- [ ] **Step 5: Confirmar que el build sigue verde**

```bash
mvn -Pquick test
```

Expected: `BUILD SUCCESS`, 5 tests ejecutados. Esta tarea no toca código; si falla, algo más está roto y hay que resolverlo antes de seguir.

Sin commit: no existe repositorio todavía.

---

### Task 2: Purgar los identificadores del árbol

**Files:**
- Modify: los 22 ficheros bajo `src/main/java/` y `src/test/java/` (renombrado de paquete)
- Modify: `pom.xml`
- Modify: `src/main/java/**/config/AppConfig.java`
- Modify: `src/main/java/**/ui/ImageManagerFrame.java`
- Modify: `src/main/java/**/ui/AppIcon.java`
- Modify: `src/main/java/**/ui/TrayUi.java`
- Modify: `src/main/java/**/demo/NotificationDemoMain.java`
- Modify: `src/test/java/**/application/VersionPollingServiceTest.java`
- Modify: `src/test/java/**/infrastructure/persistence/JsonTrackedImageStoreTest.java`

**Interfaces:**
- Produces: paquete raíz `io.github.shizukajiku.imagewatch` para todo el código; `AppConfig.fromEnvironment()` conserva su firma pero devuelve `simulationMode() == true`, `remoteUrl() == ""` y `imageNames()` con cuatro nombres neutros cuando no hay variables de entorno definidas.

> **Nota sobre los dos primeros pasos.** Derivan el paquete antiguo del propio
> árbol en lugar de escribirlo literalmente. Es deliberado: este plan se versiona,
> y escribir aquí el identificador que pretendemos erradicar lo metería en el
> historial —y el hook de la Task 3 rechazaría este mismo documento—.

- [ ] **Step 1: Mover los directorios de paquete**

```bash
old_pkg="$(sed -n 's/^package \(.*\);/\1/p' "$(find src/main/java -name Main.java)")"
old_path="${old_pkg//.//}"
old_root="${old_pkg%%.*}"
new_path="io/github/shizukajiku/imagewatch"

echo "paquete actual: $old_pkg"

for tree in src/main/java src/test/java; do
  [ -d "$tree/$old_path" ] || continue
  mkdir -p "$tree/$(dirname "$new_path")"
  mv "$tree/$old_path" "$tree/$new_path"
  rm -rf "${tree:?}/${old_root:?}"
done
```

Expected: `echo` muestra el paquete actual y los ficheros quedan bajo
`src/main/java/io/github/shizukajiku/imagewatch/` y su equivalente en `src/test/`.

- [ ] **Step 2: Reescribir las declaraciones de paquete e imports**

Reutiliza `$old_pkg` de la misma sesión de terminal. Si abriste una nueva,
vuelve a calcularlo con la primera línea del paso anterior antes de continuar.

```bash
new_pkg="io.github.shizukajiku.imagewatch"
esc="${old_pkg//./\\.}"

grep -rl "$esc" src | xargs sed -i "s/$esc/$new_pkg/g"
grep -rn "$esc" src && echo "QUEDAN RESIDUOS" || echo "sin residuos"
```

Expected: `sin residuos`.

- [ ] **Step 3: Actualizar las coordenadas en `pom.xml`**

Cambia tres valores:

```xml
<groupId>io.github.shizukajiku</groupId>
<artifactId>imagewatch</artifactId>
```

y en `<properties>`:

```xml
<main.class>io.github.shizukajiku.imagewatch.Main</main.class>
```

- [ ] **Step 4: Vaciar los valores por defecto de `AppConfig`**

Sustituye el cuerpo de `fromEnvironment()` por esta versión. Los tres cambios son: nombres de imagen neutros, URL sin valor de reserva y modo simulación activo por defecto.

```java
public static AppConfig fromEnvironment() {
    var home = Path.of(System.getenv().getOrDefault("USERPROFILE", "."));
    var names = System.getenv().getOrDefault("IMAGE_NAMES", "alpha,beta,gamma,delta");
    return new AppConfig(
        Path.of(
            System.getenv()
                .getOrDefault(
                    "NOTIFIER_STATE_FILE",
                    home.resolve(".notifier/local-products.json").toString())),
        System.getenv().getOrDefault("IMAGE_VERSION_URL", ""),
        Duration.ofSeconds(
            Long.parseLong(System.getenv().getOrDefault("POLL_INTERVAL_SECONDS", "300"))),
        Arrays.stream(names.split(",")).map(String::trim).filter(s -> !s.isBlank()).toList(),
        Boolean.parseBoolean(System.getenv().getOrDefault("SIMULATION_MODE", "true")),
        Boolean.parseBoolean(System.getenv().getOrDefault("TEAMS_ENABLED", "false")),
        Boolean.parseBoolean(System.getenv().getOrDefault("IGNORE_SSL_ERRORS", "true")));
}
```

`IGNORE_SSL_ERRORS` conserva su valor actual a propósito: está fuera de alcance por decisión explícita (spec, apartado 13).

- [ ] **Step 5: Dar un mensaje claro cuando falte la URL**

Con la URL vacía, `HttpProductSource` fallaría con un mensaje sobre el esquema HTTPS, que no explica el problema real. En `Main.java`, antes de construir el origen HTTP:

```java
if (!config.simulationMode() && config.remoteUrl().isBlank()) {
  throw new IllegalStateException(
      "Falta la URL del origen remoto. Define IMAGE_VERSION_URL "
          + "o deja SIMULATION_MODE=true para usar el origen simulado.");
}
```

- [ ] **Step 6: Sustituir el color de acento**

En `ImageManagerFrame.java`, el color de acento actual es un color de marca registrada. Cambia la constante:

```java
private static final Color ACCENT = new Color(0x4c6ef5);
```

y los tres derivados que aparecen en `installTheme()`:

```java
UIManager.put("Button.default.focusedBackground", new Color(0x6b87f7));
UIManager.put("Button.default.hoverBackground", new Color(0x6b87f7));
UIManager.put("Button.default.pressedBackground", new Color(0x3b5bdb));
```

y la cadena de estilo del botón de añadir, en `buildHeader()`:

```java
addButton.putClientProperty(
    FlatClientProperties.STYLE,
    "arc:999;"
        + "background:#4c6ef5;"
        + "hoverBackground:#6b87f7;"
        + "pressedBackground:#3b5bdb;"
        + "foreground:#ffffff;"
        + "borderWidth:0;"
        + "focusWidth:0");
```

En `AppIcon.java`:

```java
private static final Color ACCENT = new Color(0x4c6ef5);
```

- [ ] **Step 7: Cambiar el nombre visible de la aplicación**

En `TrayUi.java`:

```java
var icon = new TrayIcon(AppIcon.draw(16), "ImageWatch");
```

En `NotificationDemoMain.java`:

```java
var icon = new TrayIcon(createIcon(), "ImageWatch - Demo");
```

- [ ] **Step 8: Neutralizar los nombres de imagen en los tests**

En `VersionPollingServiceTest.java` y `JsonTrackedImageStoreTest.java`, sustituye cada nombre de imagen por uno neutro, manteniendo la correspondencia (el mismo nombre real siempre pasa al mismo nombre neutro):

- el primer nombre de imagen → `alpha`
- el segundo → `beta`
- el tercero → `gamma`

Las cadenas de release del tipo `registry.local/<nombre>:<version>` conservan la estructura; solo cambia el `<nombre>`. `registry.local` es un host de ejemplo y puede quedarse.

- [ ] **Step 9: Verificar que no queda ningún identificador**

```bash
while IFS= read -r p; do
  [ -z "$p" ] && continue
  case "$p" in \#*) continue;; esac
  if grep -rniE -- "$p" src pom.xml config 2>/dev/null; then
    echo "RESIDUO: $p"
  fi
done < .denylist.local
echo "barrido completo"
```

Expected: ninguna línea `RESIDUO:`. Si aparece alguna, corrígela antes de continuar. Este barrido es la única defensa antes de que exista historial.

- [ ] **Step 10: Confirmar que el build sigue verde**

```bash
mvn -Pquick test
```

Expected: `BUILD SUCCESS`, 5 tests. El renombrado de paquete es masivo pero mecánico; si compila y los tests pasan, está bien hecho.

- [ ] **Step 11: Comprobar que la aplicación arranca**

```bash
mvn -Pquick -DskipTests package
java -jar target/imagewatch-1.0-SNAPSHOT.jar
```

Expected: aparece el icono en la bandeja. Al hacer doble clic se abre la ventana con cuatro filas (`alpha`, `beta`, `gamma`, `delta`) y el botón de añadir en el nuevo color. Cierra la aplicación desde el menú contextual del icono.

Sin commit: sigue sin existir repositorio. El siguiente paso lo crea.

---

### Task 3: Inicializar el repositorio con el hook ya instalado

**Files:**
- Create: `scripts/pre-commit`
- Create: `scripts/install-hooks.sh`
- Create: `.git/` (vía `git init`)

**Interfaces:**
- Consumes: `.denylist.local` de la Task 1.
- Produces: repositorio git local con un único commit que contiene el árbol ya purgado, y un hook `pre-commit` activo que rechaza cualquier commit posterior cuyo contenido o nombres de fichero coincidan con la denylist.

- [ ] **Step 1: Escribir el hook genérico**

Crea `scripts/pre-commit`. **No contiene ningún patrón**: los lee en tiempo de commit. Por eso puede versionarse sin filtrar nada.

```bash
#!/usr/bin/env bash
# Rechaza el commit si el contenido o los nombres de fichero en stage
# coinciden con algún patrón de .denylist.local (no versionado).
set -euo pipefail

root="$(git rev-parse --show-toplevel)"
list="$root/.denylist.local"

if [ ! -f "$list" ]; then
  echo "pre-commit: falta .denylist.local; ejecuta scripts/install-hooks.sh" >&2
  exit 1
fi

staged_content="$(git diff --cached -U0 --diff-filter=ACMR)"
staged_names="$(git diff --cached --name-only --diff-filter=ACMR)"
failed=0

while IFS= read -r pattern || [ -n "$pattern" ]; do
  [ -z "$pattern" ] && continue
  case "$pattern" in \#*) continue ;; esac
  # Here-string en lugar de tubería, deliberadamente: con 'set -o pipefail',
  # 'grep -q' cierra la tubería al primer acierto, el productor recibe SIGPIPE
  # y sale con 141, y pipefail devuelve ese 141 en lugar del 0 de grep. Un
  # acierto se leería como fallo y el hook quedaría mudo.
  if grep -qiE -- "$pattern" <<< "$staged_content"; then
    echo "pre-commit: patrón prohibido en el contenido: $pattern" >&2
    failed=1
  fi
  if grep -qiE -- "$pattern" <<< "$staged_names"; then
    echo "pre-commit: patrón prohibido en un nombre de fichero: $pattern" >&2
    failed=1
  fi
done < "$list"

if [ "$failed" -ne 0 ]; then
  echo "pre-commit: commit abortado." >&2
  exit 1
fi
```

- [ ] **Step 2: Escribir el instalador**

Crea `scripts/install-hooks.sh`:

```bash
#!/usr/bin/env bash
# Instala los hooks de git de este repositorio.
set -euo pipefail

root="$(git rev-parse --show-toplevel)"

if [ ! -f "$root/.denylist.local" ]; then
  echo "Falta .denylist.local en la raíz del repositorio." >&2
  echo "Crea el fichero con un patrón de regex extendida por línea." >&2
  exit 1
fi

install -m 755 "$root/scripts/pre-commit" "$root/.git/hooks/pre-commit"
echo "Hook pre-commit instalado."
```

- [ ] **Step 3: Inicializar el repositorio**

```bash
git init -b main
git config user.name "Shizuka"
git config user.email "shizuka.jiku@gmail.com"
```

- [ ] **Step 4: Instalar el hook antes de preparar nada**

```bash
chmod +x scripts/install-hooks.sh scripts/pre-commit
./scripts/install-hooks.sh
```

Expected: `Hook pre-commit instalado.`

- [ ] **Step 5: Preparar el árbol y comprobar qué entra**

```bash
git add -A
git status --short
```

Expected: **no aparece nada bajo `.idea/`, ni `.denylist.local`, ni `target/`.** Si aparece alguno, el `.gitignore` de la Task 1 no está bien y hay que corregirlo antes de commitear.

- [ ] **Step 6: Verificar que el hook funciona antes de confiar en él**

Un hook que nunca ha rechazado nada no está probado. Provoca un fallo deliberado:

```bash
printf 'patron-de-prueba-que-no-existe-en-el-arbol\n' >> .denylist.local
echo "esto contiene patron-de-prueba-que-no-existe-en-el-arbol" > prueba-hook.txt
git add prueba-hook.txt
git commit -m "test: no debe llegar a crearse" || echo "HOOK OK: rechazó el commit"
```

Expected: `HOOK OK: rechazó el commit`, precedido del mensaje del hook indicando el patrón. Si el commit se crea, el hook no está activo.

Limpia:

```bash
git reset prueba-hook.txt
rm prueba-hook.txt
sed -i '/patron-de-prueba-que-no-existe-en-el-arbol/d' .denylist.local
```

- [ ] **Step 7: Commit inicial**

```bash
git add -A
git commit -m "chore: commit inicial del proyecto ImageWatch

Árbol purgado de identificadores corporativos antes de crear historial:
paquete, coordenadas, valores por defecto de configuración, color de
acento y nombre visible de la aplicación.

Incluye el hook pre-commit que valida los commits posteriores contra una
denylist local no versionada."
```

Expected: el commit se crea. El hook se ejecuta sobre él y no encuentra nada.

- [ ] **Step 8: Confirmar que el historial está limpio**

```bash
while IFS= read -r p; do
  [ -z "$p" ] && continue
  case "$p" in \#*) continue ;; esac
  if git grep -qiE -- "$p" HEAD 2>/dev/null; then echo "RESIDUO EN HISTORIAL: $p"; fi
done < .denylist.local
echo "historial verificado"
```

Expected: ninguna línea `RESIDUO EN HISTORIAL:`. Si aparece alguna, la salida correcta es borrar `.git/` y repetir desde la Task 2 — no un commit de corrección, que dejaría el valor en el historial igualmente.

---

### Task 4: Publicar el repositorio privado

**Files:** ninguno (operación remota)

- [ ] **Step 1: Crear el repositorio remoto como privado**

```bash
gh repo create imagewatch --private --source=. --remote=origin --description "Vigila versiones de imágenes de contenedor y avisa cuando hay una nueva."
```

Expected: se crea el repositorio y se añade el remoto `origin`.

- [ ] **Step 2: Confirmar que es privado antes de enviar nada**

```bash
gh repo view --json name,visibility,url
```

Expected: `"visibility": "PRIVATE"`. **Si dice `PUBLIC`, detente**: corrígelo con `gh repo edit --visibility private --accept-visibility-change-consequences` y vuelve a comprobarlo antes del push.

- [ ] **Step 3: Enviar**

```bash
git push -u origin main
```

- [ ] **Step 4: Añadir la spec y este plan al repositorio**

```bash
git add docs/
git commit -m "docs: añadir spec de diseño y plan de fases 0-2"
git push
```

Expected: el hook valida ambos documentos y los acepta — ya se verificó que están libres de identificadores.

---

## FASE 1 — De Maven a Gradle

> Esta fase **no cambia una sola línea de código**. Solo el sistema de construcción. Se mantiene deliberadamente en **Java 21** para aislar la variable: si algo se rompe, es Gradle, no la versión de la plataforma. Java 25 llega en la Fase 2.

---

### Task 5: Instalar Gradle y generar el wrapper

**Files:**
- Create: `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties`

**Interfaces:**
- Produces: `./gradlew` operativo y fijado a Gradle 9.7.1, de modo que ninguna tarea posterior dependa de la instalación global.

- [ ] **Step 1: Instalar Gradle**

No hay Gradle en la máquina (`gradle` no está en PATH y no existe `~/.gradle`).

```bash
scoop install gradle
gradle --version
```

Expected: `Gradle 9.x`. Necesitamos ≥ 9.1.0 para Java 25; si scoop instala una anterior, actualiza antes de continuar.

- [ ] **Step 2: Generar el wrapper fijado**

```bash
gradle wrapper --gradle-version 9.7.1 --distribution-type bin
```

- [ ] **Step 3: Verificar que el wrapper funciona por sí solo**

```bash
./gradlew --version
```

Expected: `Gradle 9.7.1`. A partir de aquí todas las órdenes usan `./gradlew`, nunca `gradle`.

- [ ] **Step 4: Commit**

```bash
git add gradlew gradlew.bat gradle/wrapper
git commit -m "build: añadir wrapper de Gradle 9.7.1"
```

---

### Task 6: Version catalog y configuración del proyecto

**Files:**
- Create: `settings.gradle.kts`
- Create: `gradle/libs.versions.toml`

**Interfaces:**
- Produces: los alias `libs.semver4j`, `libs.jspecify`, `libs.jackson.databind`, `libs.flatlaf`, `libs.flatlaf.extras`, `libs.modal.dialog`, `libs.junit.jupiter`, `libs.assertj`, `libs.plugins.spotless` y `libs.versions.googleJavaFormat`, consumidos por `build.gradle.kts` en la Task 7.

- [ ] **Step 1: Crear `settings.gradle.kts`**

```kotlin
rootProject.name = "imagewatch"
```

Gradle detecta `gradle/libs.versions.toml` automáticamente; no hay que declararlo.

- [ ] **Step 2: Crear `gradle/libs.versions.toml`**

Las versiones son las que declara el `pom.xml` actual, con dos excepciones:

- **spotless**, que en Gradle tiene su propio esquema de versiones.
- **google-java-format `1.36.1`**, en lugar del `1.25.2` original. Verificado
  empíricamente durante la Task 2: sobre JDK 25 la versión 1.25.2 aborta con
  `NoSuchMethodError: Log$DeferredDiagnosticHandler.getDiagnostics()`, porque
  JDK 25 cambió el tipo de retorno de ese método interno de javac de `Queue` a
  `List`. La 1.36.1 funciona sobre 21 y sobre 25. Sin este cambio, la Fase 2
  rompe el build en cuanto se eleva el toolchain.

```toml
[versions]
java = "21"
semver4j = "6.0.0"
jspecify = "1.0.0"
jackson = "3.2.1"
flatlaf = "3.7.1"
modalDialog = "2.6.1"
junit = "5.12.2"
junitPlatform = "1.12.2"
assertj = "3.27.3"
googleJavaFormat = "1.36.1"
spotless = "8.10.1"

[libraries]
semver4j = { module = "org.semver4j:semver4j", version.ref = "semver4j" }
jspecify = { module = "org.jspecify:jspecify", version.ref = "jspecify" }
jackson-databind = { module = "tools.jackson.core:jackson-databind", version.ref = "jackson" }
flatlaf = { module = "com.formdev:flatlaf", version.ref = "flatlaf" }
flatlaf-extras = { module = "com.formdev:flatlaf-extras", version.ref = "flatlaf" }
modal-dialog = { module = "io.github.dj-raven:modal-dialog", version.ref = "modalDialog" }
junit-jupiter = { module = "org.junit.jupiter:junit-jupiter", version.ref = "junit" }
junit-platform-launcher = { module = "org.junit.platform:junit-platform-launcher", version.ref = "junitPlatform" }
assertj = { module = "org.assertj:assertj-core", version.ref = "assertj" }

[plugins]
spotless = { id = "com.diffplug.spotless", version.ref = "spotless" }
```

Lombok no aparece: tiene cero usos en el código y se retira en esta migración.

- [ ] **Step 3: Verificar que Gradle lee la configuración**

```bash
./gradlew projects
```

Expected: `Root project 'imagewatch'`. Todavía no hay tareas de compilación; solo se comprueba que `settings.gradle.kts` y el catálogo se parsean.

- [ ] **Step 4: Commit**

```bash
git add settings.gradle.kts gradle/libs.versions.toml
git commit -m "build: añadir settings y version catalog de Gradle"
```

---

### Task 7: Script de compilación con paridad de comportamiento

**Files:**
- Create: `build.gradle.kts`

**Interfaces:**
- Consumes: los alias del catálogo de la Task 6.
- Produces: las tareas `./gradlew test`, `./gradlew run`, `./gradlew check` y `./gradlew installDist`.

- [ ] **Step 1: Crear `build.gradle.kts`**

```kotlin
plugins {
    application
    checkstyle
    alias(libs.plugins.spotless)
}

group = "io.github.shizukajiku"
version = "1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(libs.versions.java.get().toInt())
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.semver4j)
    implementation(libs.jspecify)
    implementation(libs.jackson.databind)
    implementation(libs.flatlaf)
    implementation(libs.flatlaf.extras)
    implementation(libs.modal.dialog)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj)
    testRuntimeOnly(libs.junit.platform.launcher)
}

application {
    mainClass = "io.github.shizukajiku.imagewatch.Main"
}

checkstyle {
    configFile = file("config/checkstyle/checkstyle.xml")
    isIgnoreFailures = false
}

spotless {
    java {
        googleJavaFormat(libs.versions.googleJavaFormat.get())
        removeUnusedImports()
    }
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}
```

`testRuntimeOnly(libs.junit.platform.launcher)` es obligatorio: Gradle 9 ya no lo añade de forma implícita y sin él los tests fallan al arrancar con `Failed to load JUnit Platform`.

- [ ] **Step 2: Ejecutar los tests**

```bash
./gradlew test
```

Expected: `BUILD SUCCESSFUL`, 5 tests ejecutados, los mismos que pasaban con Maven:
`lastUpdatesKeepsEveryTrackedImageEvenWhenUpToDate`, `onlyPendingUpdatesAreNotified`,
`seedsDefaultsOnFirstRead`, `savedNamesSurviveARoundTrip`.

- [ ] **Step 3: Ejecutar checkstyle y spotless**

```bash
./gradlew check
```

Expected: `BUILD SUCCESSFUL`.

Si `spotlessCheck` falla, ejecuta `./gradlew spotlessApply` y vuelve a lanzarlo.

**Diferencia deliberada respecto a Maven:** el `pom.xml` ejecutaba `spotless:apply` en la fase `validate`, es decir, reformateaba el código fuente en cada compilación. Aquí spotless solo **verifica** durante `check`; reformatear es una orden explícita (`./gradlew spotlessApply`). Un build que modifica los ficheros fuente por su cuenta sorprende y ensucia diffs.

- [ ] **Step 4: Comprobar que la aplicación arranca**

```bash
./gradlew run
```

Expected: aparece el icono en la bandeja; al hacer doble clic se abre la ventana con las cuatro filas neutras. Cierra desde el menú contextual del icono.

- [ ] **Step 5: Commit**

```bash
git add build.gradle.kts
git commit -m "build: añadir script de compilación de Gradle con paridad de comportamiento"
```

---

### Task 8: Retirar Maven

**Files:**
- Delete: `pom.xml`
- Delete: `.mvn/`
- Modify: `.gitignore`

- [ ] **Step 1: Comprobar que nada más referencia a Maven**

```bash
grep -rn "pom.xml\|maven" --include="*.kts" --include="*.md" --include="*.sh" . 2>/dev/null | grep -v "^./docs/" || echo "sin referencias"
```

Expected: `sin referencias`, o únicamente coincidencias dentro de `docs/`, que describen la migración y son correctas.

- [ ] **Step 2: Eliminar los ficheros de Maven**

```bash
rm -rf .mvn
git rm pom.xml
```

`.mvn/` **no** lleva `git rm`: se comprobó en la Task 3 que el directorio está
vacío, y git no trackea directorios vacíos, así que nunca entró en el índice.
`git rm -r --cached .mvn` fallaría con `did not match any files`.

- [ ] **Step 3: Limpiar `.gitignore`**

Elimina las líneas que ya no aplican:

```
target/
!.mvn/wrapper/maven-wrapper.jar
!**/src/main/**/target/
!**/src/test/**/target/
```

Deja las de Gradle añadidas en la Task 1.

- [ ] **Step 4: Verificar en un árbol limpio**

```bash
rm -rf build target
./gradlew clean test run
```

Expected: los 5 tests pasan y la aplicación arranca, sin ningún resto de Maven.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "build: retirar Maven en favor de Gradle"
git push
```

---

## FASE 2 — Java 25 y corrección del parseo

---

### Task 9: Elevar la plataforma a Java 25

**Files:**
- Modify: `gradle/libs.versions.toml`
- Create: `gradle.properties`

**Interfaces:**
- Produces: compilación con `--release 25`; el bytecode resultante ya no es ejecutable en JVM anteriores, lo cual es intencionado.

- [ ] **Step 1: Elevar la versión en el catálogo**

En `gradle/libs.versions.toml`:

```toml
java = "25"
```

Es el único cambio necesario: `build.gradle.kts` ya lee `libs.versions.java`. Al declarar un *toolchain*, Gradle usa `--release` automáticamente, lo que elimina el aviso `location of system modules is not set` que emitía Maven.

- [ ] **Step 2: Asegurar que Gradle encuentra el JDK 25**

Gradle detecta los JDK instalados en `~/.jdks`, que es donde está. Si la detección automática falla, crea `gradle.properties`:

```properties
org.gradle.java.installations.paths=C:\\Users\\shizu\\.jdks\\ms-25.0.4.1
```

- [ ] **Step 3: Confirmar que compila con 25**

```bash
./gradlew clean compileJava --info 2>&1 | grep -i "release\|toolchain\|system modules"
```

Expected: aparece una referencia al toolchain 25 y **no** aparece `location of system modules is not set`, que era el aviso de la configuración de Maven.

- [ ] **Step 4: Ejecutar los tests y la aplicación**

```bash
./gradlew test run
```

Expected: los 5 tests pasan sobre JDK 25 y la aplicación arranca. Ya no aparece el aviso de `sun.misc.Unsafe` que emitía Lombok, porque Lombok ya no está en el build.

- [ ] **Step 5: Commit**

```bash
git add gradle/libs.versions.toml gradle.properties
git commit -m "build: elevar la plataforma a Java 25"
```

---

### Task 10: Corregir el fallo de parseo de versiones

**Files:**
- Create: `src/test/java/io/github/shizukajiku/imagewatch/domain/VersionTest.java`
- Modify: `src/main/java/io/github/shizukajiku/imagewatch/domain/Version.java`

**Interfaces:**
- Produces: `new Version(String)` lanza `IllegalArgumentException` con el valor ofensivo en el mensaje cuando la cadena no es una versión reconocible, y `NullPointerException` cuando es `null`. Antes almacenaba `null` en silencio y fallaba después, en `compareTo`, lejos del origen.

**Contexto verificado empíricamente sobre semver4j 6.0.0:**

```
Semver.parse("no-es-semver")  -> null
Semver.parse("1.28.3")        -> 1.28.3
new Semver("no-es-semver")    -> lanza SemverException
```

`parse` devuelve `null`, y ese `null` es el origen exacto del `NullPointerException`.

- [ ] **Step 1: Escribir los tests que fallan**

Crea `src/test/java/io/github/shizukajiku/imagewatch/domain/VersionTest.java`:

```java
package io.github.shizukajiku.imagewatch.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class VersionTest {

  @Test
  void unparseableValueIsRejectedAtConstruction() {
    assertThatThrownBy(() -> new Version("no-es-una-version"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("no-es-una-version");
  }

  @Test
  void nullValueIsRejectedAtConstruction() {
    assertThatThrownBy(() -> new Version(null)).isInstanceOf(NullPointerException.class);
  }

  @Test
  void releaseSuffixIsNormalisedBeforeParsing() {
    assertThat(new Version("1.2.3.RELEASE").value()).isEqualTo("1.2.3.RELEASE");
  }

  @Test
  void versionsCompareBySemanticOrderNotByText() {
    assertThat(new Version("1.10.0")).isGreaterThan(new Version("1.9.0"));
  }

  @Test
  void equalityIsBasedOnTheOriginalText() {
    assertThat(new Version("1.2.3")).isEqualTo(new Version("1.2.3"));
  }
}
```

`versionsCompareBySemanticOrderNotByText` no cubre el defecto, pero fija el contrato que hace útil a esta clase: sin él, un refactor podría sustituir la comparación semántica por una textual y `1.10.0 < 1.9.0` pasaría inadvertido.

- [ ] **Step 2: Ejecutar los tests para verificar que fallan**

```bash
./gradlew test --tests "*VersionTest*"
```

Expected: FALLAN `unparseableValueIsRejectedAtConstruction` y `nullValueIsRejectedAtConstruction`.

El primero falla con `NullPointerException` en lugar de `IllegalArgumentException` — **ese `NullPointerException` es exactamente el defecto**, reproducido en un test.

Los otros tres deberían pasar ya; confirman que la corrección no rompe el comportamiento existente.

- [ ] **Step 3: Corregir el constructor**

En `Version.java`, sustituye el constructor:

```java
public Version(@NonNull String value) {
    this.value = Objects.requireNonNull(value, "La versión no puede ser nula");
    var parsed = Semver.parse(value.replace(".RELEASE", "-RELEASE"));
    if (parsed == null) {
      throw new IllegalArgumentException("Versión no reconocida: " + value);
    }
    this.semver = parsed;
}
```

`Objects` ya está importado en el fichero, para `hashCode`.

- [ ] **Step 4: Ejecutar los tests para verificar que pasan**

```bash
./gradlew test --tests "*VersionTest*"
```

Expected: los 5 pasan.

- [ ] **Step 5: Ejecutar la batería completa**

```bash
./gradlew check
```

Expected: 10 tests en total (5 previos + 5 nuevos), checkstyle y spotless en verde.

- [ ] **Step 6: Comprobar que la aplicación sigue arrancando**

```bash
./gradlew run
```

Expected: la aplicación arranca con el origen simulado, que produce versiones válidas, así que el camino de error no se dispara.

- [ ] **Step 7: Commit**

```bash
git add src/test/java/io/github/shizukajiku/imagewatch/domain/VersionTest.java \
        src/main/java/io/github/shizukajiku/imagewatch/domain/Version.java
git commit -m "fix: rechazar versiones no parseables en la construcción

Semver.parse devuelve null ante una cadena no válida. Ese null se
almacenaba y provocaba un NullPointerException después, en compareTo,
lejos del origen del problema. Ahora se rechaza en el constructor con un
mensaje que incluye el valor ofensivo."
git push
```

---

## Criterio de finalización

Al terminar la Task 10:

- [ ] `./gradlew check` en verde, 10 tests.
- [ ] `./gradlew run` arranca la aplicación con el origen simulado.
- [ ] No existen `pom.xml` ni `.mvn/`.
- [ ] El repositorio remoto es privado (`gh repo view --json visibility`).
- [ ] El barrido de la denylist sobre **todo el historial** no encuentra nada:

```bash
while IFS= read -r p; do
  [ -z "$p" ] && continue
  case "$p" in \#*) continue ;; esac
  if git grep -qiE -- "$p" $(git rev-list --all) 2>/dev/null; then echo "RESIDUO: $p"; fi
done < .denylist.local
echo "historial completo verificado"
```

Este último es el que importa. Los otros se arreglan con un commit; este no.

---

## Fuera de este plan

Las fases 3, 4 y 5 de la spec tienen plan propio:

- **Fase 3** (eventos y núcleo) — se planifica al cerrar la Task 10. Es determinable ya, pero sus tareas se apoyan en el build de Gradle existiendo.
- **Fase 4** (Compose) — se planifica **después de la prueba de concepto** descrita en el apartado 12 de la spec. Escribir ahora el código exacto de los composables sería especular sobre tres incógnitas sin verificar: Compose Desktop sobre JDK 25, la carga de los iconos SVG y el composable de bandeja en Windows 11.
- **Fase 5** (ajustes, errores, toasts, sonido) — depende de la forma que tome la Fase 4.
