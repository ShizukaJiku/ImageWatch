# ImageWatch

Vigila las versiones de imágenes de contenedor publicadas en un registry remoto y avisa
cuando aparece una más reciente que la que ya diste por vista. Vive en la bandeja del
sistema.

## Requisitos

- **JDK 21.** Es lo único que hay que instalar: Gradle viene con el wrapper.

## Primeros pasos tras clonar

```bash
# 1. Crear la lista de patrones prohibidos (NO se versiona; ver "Secretos")
#    Un patrón de regex extendida por línea.
printf 'un-patron\notro-patron\n' > .denylist.local

# 2. Instalar el hook de pre-commit
./scripts/install-hooks.sh

# 3. Arrancar
./gradlew :desktopApp:run
```

**Sin el paso 1 y 2, todos los commits fallan.** El hook aborta si no encuentra
`.denylist.local`, a propósito: es preferible un commit bloqueado a uno que filtre algo.

Un clon limpio arranca en **modo simulación**, con un origen de prueba cuyas versiones
avanzan con el tiempo. No hace falta configurar nada para verlo funcionar.

## Órdenes

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

## Configuración

La fuente de verdad es `~/.notifier/config.json`. Las variables de entorno solo
**siembran ese fichero en el primer arranque** —cuando todavía no existe—; a partir de ahí
mandan el fichero y la pantalla de ajustes (el engranaje de la cabecera), que lo edita en
caliente: guardar surte efecto sin reiniciar la aplicación.

| Variable | Por defecto | Para qué |
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

El registro solo anota lo que **cambia** —versiones nuevas, imágenes que empiezan a
fallar, reconocimientos—, no un renglón por ciclo.

## Secretos

`.denylist.local` contiene los identificadores que nunca deben entrar en el repositorio y
**no se versiona**: si estuviera dentro, filtraría justo lo que pretende bloquear. El hook
de pre-commit lo lee en cada commit y aborta si encuentra alguna coincidencia, tanto en el
contenido como en los nombres de fichero.

Consecuencia práctica: guarda una copia de ese fichero fuera del repositorio, o tendrás
que reconstruirlo al clonar en otra máquina.

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
