# ImageWatch

Vigila las versiones de imágenes de contenedor publicadas en un registry remoto y avisa
cuando aparece una más reciente que la que ya diste por vista. Vive en la bandeja del
sistema.

## Instalación

Descarga desde la [última Release](https://github.com/ShizukaJiku/ImageWatch/releases):

- **`ImageWatch-X.Y.Z.msi`** — instalador de Windows. Sin permisos de administrador:
  instala en el perfil del usuario, con acceso directo y entrada en el menú Inicio.
  Actualizar conserva tu configuración y tu lista de imágenes. **Se actualiza solo**: la
  app avisa cuando hay una versión más nueva y la instala desde Ajustes, sin descargar
  nada a mano (ver [Actualizaciones automáticas](#actualizaciones-automáticas)).
- **`ImageWatch-X.Y.Z-portable.zip`** — versión sin instalar. Descomprime y ejecuta
  `ImageWatch.exe`. No se actualiza sola: para pasar a una versión nueva, descarga el zip
  y reemplaza la carpeta.

Ninguno necesita tener Java instalado: llevan su propio runtime.

Al primer arranque la lista está vacía. Abre Ajustes (el engranaje de la cabecera), pon la
URL del registry y añade las imágenes que quieras vigilar.

## Actualizaciones automáticas

Solo para la instalación **MSI**. La portable no se actualiza sola.

La app consulta la [API de Releases de GitHub](https://api.github.com/repos/ShizukaJiku/ImageWatch/releases/latest)
al arrancar, una vez al día mientras corre, y cuando pulsas **«Buscar actualizaciones»**
en Ajustes → sección **Actualizaciones**. Sin token; el límite anónimo de GitHub sobra
para esa frecuencia.

Cuando hay una versión más nueva, la sección muestra el número y las notas del Release.
Al pulsar **«Actualizar ahora»**:

1. Descarga `ImageWatch-X.Y.Z.msi` y su `ImageWatch-X.Y.Z.msi.sha256` a
   `~/.notifier/updates/`.
2. Verifica el SHA-256 **al vuelo**. Si no coincide, borra la descarga y no sigue.
3. Con **«Instalar y reiniciar»** (tras confirmar), la app se cierra, `msiexec` aplica el
   MSI en sitio —sin pedir permisos de administrador— y la app vuelve a abrirse en la
   versión nueva. Tu configuración y tu lista de imágenes (`~/.notifier/`) no se tocan.

Si algo falla —sin red, GitHub caído, checksum que no cuadra, `msiexec` con error— la
instalación anterior queda intacta y el motivo aparece en la sección de Ajustes; el botón
permite reintentar.

**Seguridad.** El cliente HTTP del actualizador **siempre valida el certificado TLS de
GitHub**, aunque tengas `IGNORE_SSL_ERRORS=true` (ese interruptor es solo para tu registry
interno). El MSI se comprueba contra el checksum publicado en el mismo Release.

## Compilar desde el código

Requiere **JDK 21**; Gradle viene con el wrapper.

```bash
./gradlew :desktopApp:run
```

Arranca en **modo simulación**, con un origen de prueba cuyas versiones avanzan con el
tiempo. No hace falta configurar nada para verlo funcionar.

| Orden | Qué hace |
|---|---|
| `./gradlew :desktopApp:run` | Arranca la aplicación |
| `./gradlew :shared:jvmTest` | Ejecuta los tests |
| `./gradlew spotlessCheck` | Verifica el formato |
| `./gradlew spotlessApply` | Aplica el formato (no se aplica solo al compilar) |
| `./gradlew :shared:koverVerify` | Comprueba el umbral de cobertura del núcleo (80 %) |
| `./gradlew :desktopApp:createDistributable` | Genera una distribución ejecutable con su propio runtime en `desktopApp/build/compose/binaries/main/app/` |
| `./gradlew :desktopApp:runDistributable` | Arranca esa distribución. **Es la única orden que prueba el binario empaquetado**: `packageMsi` puede terminar en verde y aun así producir un ejecutable que no arranca |
| `./gradlew :desktopApp:packageMsi` | Genera el instalador de Windows en `desktopApp/build/compose/binaries/main/msi/` |
| `./gradlew :desktopApp:packageAppImage` | Genera la versión portable (carpeta con runtime propio) en `desktopApp/build/compose/binaries/main/app/ImageWatch/` |
| `./gradlew :desktopApp:generateIcon` | Regenera `desktopApp/icons/ImageWatch.ico` desde `AppIconPainter`. Solo tras cambiar la marca; el `.ico` va commiteado |

## Empaquetado

Cómo se producen los artefactos de la Release. Dos formatos, ambos con su propio runtime
(JDK 21 recortado con `jlink`): quien instale **no necesita Java**.

### Instalador MSI

`./gradlew :desktopApp:packageMsi` → `desktopApp/build/compose/binaries/main/msi/ImageWatch-1.0.0.msi` (~66 MB).

- **Sin permisos de administrador.** Instala en `%LOCALAPPDATA%\ImageWatch` (perfil del
  usuario), no en `Program Files`.
- Crea entrada en el **menú Inicio** y **acceso directo** en el escritorio.
- Deja elegir carpeta durante la instalación (`dirChooser`).
- `upgradeUuid` fijo: instalar un MSI con versión mayor **actualiza en sitio**, no pone un
  segundo ImageWatch. La versión sale de `imagewatch.version` en `gradle.properties`
  (`MAYOR.MENOR.PARCHE`, mayor > 0).
- **Instalación limpia**: sin datos de prueba. La app empaquetada arranca sin simulación y
  sin imágenes sembradas (lo detecta por `jpackage.app-path`); la lista está vacía hasta
  que pones la URL del registry en Ajustes. En desarrollo (`gradlew run`) sí arranca en
  simulación con las cuatro imágenes de ejemplo.
- **Actualizar conserva los datos**: estado, configuración y lista de imágenes viven en
  `~/.notifier/` (perfil del usuario), que el instalador nunca toca. Desinstalar tampoco lo
  borra.

### Portable (AppImage)

`./gradlew :desktopApp:packageAppImage` → carpeta `desktopApp/build/compose/binaries/main/app/ImageWatch/`
con `ImageWatch.exe` + `runtime/` + `app/` (~120 MB).

- No se instala: se comprime en zip, el usuario descomprime y ejecuta `ImageWatch.exe`.
- Sin escritura en el registro, sin menú Inicio, sin permisos.
- No hay actualización en sitio: se reemplaza la carpeta.

jpackage no produce un `.exe` autoextraíble de un solo fichero; «portable» aquí es esta
carpeta.

### Icono

La marca (`AppIconPainter`: cuadrícula 2×2) es la misma en ventana, bandeja e instalador.
El `.ico` multi-resolución (16–256 px, PNG embebido) se genera desde ese mismo dibujo con
`./gradlew :desktopApp:generateIcon` y se commitea en `desktopApp/icons/`. `build.gradle.kts`
lo referencia en `windows { iconFile }`.

### Firma de código

Ni el MSI ni el `.exe` van firmados. Al instalar, Windows SmartScreen muestra «editor
desconocido». Para quitarlo hace falta un certificado *Authenticode* y firmar **después**
de empaquetar, con `signtool.exe` (Windows SDK):

```
signtool sign /fd SHA256 /td SHA256 /tr http://timestamp.sectigo.com ^
  /f cert.pfx /p <clave> ImageWatch-1.0.0.msi
```

(o `/csp`/`/kc` con token hardware, o el CLI de firma en nube de la CA). El `/tr`
—sello de tiempo— mantiene la firma válida tras caducar el certificado.

Opciones de certificado: **SignPath Foundation** (gratis para proyectos de código
abierto, firma en la nube desde CI, requiere aprobación), **Azure Trusted Signing**
(~10 $/mes, HSM en nube, sin token), **Certum/SSL.com Open Source** (barato, con token).
`sigstore`/`cosign` **no** sirve: no es firma Authenticode y SmartScreen no la reconoce.

Cuando exista un certificado: subir el `.pfx` en base64 como secreto `SIGN_PFX_BASE64` y
su contraseña como `SIGN_PFX_PASSWORD`. El workflow de release firma el MSI solo si están
presentes; sin ellos publica sin firmar.

## Integración continua

Dos workflows en `.github/workflows/`:

- **`tests.yml`** — **solo en pull request**. Formato (`spotlessCheck`), `:shared:jvmTest`,
  cobertura (`:shared:koverVerify`) y compilación de `:desktopApp`. No corre en push a
  `main` para no repetir el trabajo: allí lo cubre `release.yml`.
- **`release.yml`** — al fusionar a `main`. Su job `test` repite la misma comprobación
  completa sobre el commit fusionado exacto; luego lee `imagewatch.version` de
  `gradle.properties` y, si el Release `vX.Y.Z` **no existe todavía**, empaqueta, verifica
  el runtime, firma el MSI (si hay certificado) y publica un Release de GitHub creando el
  tag en el mismo paso. Assets: el MSI, su `ImageWatch-X.Y.Z.msi.sha256` (lo usa la
  autoactualización) y el zip portable, con notas autogeneradas. Fusionar sin subir la
  versión no hace nada. **Publicar una versión = subir `imagewatch.version` en el PR que
  la cierra.**

`main` está protegida por un *ruleset*: todo entra por PR, con una aprobación del
propietario (`CODEOWNERS`) y el check `Formato y tests` en verde; sin push directo ni
force-push. El propietario fusiona sus propios PR por la excepción de administrador
(GitHub no deja aprobar el PR propio).

## Configuración

La fuente de verdad es `~/.notifier/config.json`. Las variables de entorno solo
**siembran ese fichero en el primer arranque** —cuando todavía no existe—; a partir de ahí
mandan el fichero y la pantalla de ajustes (el engranaje de la cabecera), que lo edita en
caliente: guardar surte efecto sin reiniciar la aplicación.

Los valores por defecto de abajo son los de **desarrollo** (`gradlew run`). La **app
empaquetada** arranca sin datos de prueba: `SIMULATION_MODE=false` e `IMAGE_NAMES` vacío.
Las variables de entorno, si están puestas, mandan en ambos casos.

| Variable | Por defecto (dev) | Para qué |
|---|---|---|
| `SIMULATION_MODE` | `true` | Usa el origen de prueba en lugar del remoto |
| `IMAGE_VERSION_URL` | *(vacío)* | URL base del origen. Obligatoria si desactivas la simulación |
| `IMAGE_NAMES` | `alpha,beta,gamma,delta` | Imágenes a vigilar la primera vez |
| `POLL_INTERVAL_SECONDS` | `300` | Cada cuánto se consulta el origen |
| `NOTIFIER_STATE_FILE` | `~/.notifier/images.json` | Dónde se guardan las versiones reconocidas |
| `IGNORE_SSL_ERRORS` | `true` | Desactiva la validación TLS |
| `THEME` | `SYSTEM` | Apariencia: `SYSTEM`, `LIGHT` o `DARK` |
| `TOASTS_ENABLED` | `true` | Muestra avisos emergentes al detectar una versión nueva |
| `TOAST_SECONDS` | `8` | Cuánto tarda un aviso emergente en desaparecer por sí solo |
| `SOUNDS_ENABLED` | `true` | Reproduce un sonido junto con los avisos |
| `SOUND_VOLUME` | `0.5` | Volumen de esos sonidos, de `0.0` a `1.0` |

Para verlo cambiar rápido:

```bash
IMAGE_NAMES=alpha,beta,gamma,delta-fail POLL_INTERVAL_SECONDS=10 ./gradlew run
```

Cualquier nombre que contenga `fail` provoca un error en el origen simulado, lo que
permite ver el estado de error y comprobar que no afecta a las demás imágenes.

## Cómo interpreta los estados

| Estado | Significa |
|---|---|
| **Al día** | La versión que diste por vista es la que publica el origen, o más reciente |
| **Nueva versión** | El origen publica algo más nuevo que lo que diste por visto |
| **Error** | La última verificación de *esa* imagen falló. Las demás siguen actualizándose |
| **Sin verificar** | Todavía no hay una versión reconocida con la que comparar |

La aplicación **no sabe qué versión tienes desplegada**: guarda la que tú marcas como
vista. «Nueva versión» persiste hasta que pulsas *marcar como vista* en la fila, y
sobrevive a reiniciar la aplicación. Marcar como vista funciona además como «esta no la
actualizo ahora»: vuelve a avisar solo cuando salga una versión más nueva todavía.

El primer arranque establece la línea base en silencio, sin avisar de cada imagen.

Cuando una imagen pasa a **Nueva versión**, además de la fila aparece un aviso emergente
(«toast») en la esquina inferior derecha, con una acción **Ver** que abre la ventana
justo en esa fila. Si todas las imágenes están en error, un aviso global lo señala en la
ventana y el icono de la bandeja del sistema cambia para reflejar que el origen no
responde.

## Sonido

Cuatro avisos, y solo cuatro: una versión nueva, el origen que deja de responder, una
acción confirmada —guardar ajustes, dar de alta o eliminar una imagen— y el arranque o
la parada del sondeo. No suena el paso del puntero, ni la escritura, ni el filtrado, ni
el desplazamiento: sonorizar eso es lo que convierte una aplicación con sonido en una
aplicación que se desinstala.

Tres reglas gobiernan cuándo suenan:

- **No suenan si la ventana tiene el foco.** Si ya estás mirando la tabla, el aviso en
  pantalla basta; el sonido existe para cuando no miras.
- **Nunca se solapan dos reproducciones del mismo aviso**: se reinicia en lugar de
  encolarse.
- **Si la máquina no tiene salida de audio, el subsistema queda mudo y no falla.** Por
  escritorio remoto o en una máquina sin altavoces, la aplicación arranca igual.

Se desactivan desde la pantalla de ajustes, junto al volumen, que viene atenuado de
fábrica. Los cuatro ficheros están en `src/main/resources/sounds/`, son de dominio
público, y su procedencia y licencia constan en el `NOTICE` de ese mismo directorio.

## Dónde vive el estado

Todo en `~/.notifier/`:

| Fichero | Contenido |
|---|---|
| `images.json` | Versión reconocida de cada imagen |
| `tracked-images.json` | Lista de imágenes vigiladas |
| `config.json` | Configuración editable desde la pantalla de ajustes |
| `imagewatch.log` | Registro, con rotación diaria y tope de 20 MB |
| `updates/` | Carpeta temporal de la autoactualización (MSI descargado + checksum). Se vacía al empezar cada descarga |

El registro solo anota lo que **cambia** —versiones nuevas, imágenes que empiezan a
fallar, reconocimientos—, no un renglón por ciclo.

## Estructura

Arquitectura hexagonal sobre dos módulos de Kotlin Multiplatform. Todo el código es Kotlin.

```
shared/
  commonMain/    domain/         Tipos sin dependencias externas
                 application/    Puertos (interfaces) y servicios
                 config/         Configuración efectiva
                 ui/             Presentación con Compose Multiplatform
  jvmMain/       infrastructure/ Adaptadores: HTTP, persistencia JSON
                 ui/             Lo que depende de AWT: bandeja, ventana de avisos, iconos
desktopApp/      Main.kt         Composition root: cableado, ventana y bandeja
```

El reparto entre `commonMain` y `jvmMain` no es organizativo, es una restricción real:
`commonMain` no ve el JDK. Lo que necesita ficheros, red o AWT vive en `jvmMain` detrás de
un puerto o de una costura `expect`/`actual`. Hoy hay cuatro costuras: `Log`, `Version`,
`loadAppSvg` y `Sounds`.

`desktopApp` solo contiene el cableado. Es lo que justifica que `AppConfig.fromEnvironment()`
viva ahí y no junto a `AppConfig`: leer `System.getenv` es cableado, y `commonMain` no tiene
variables de entorno.
