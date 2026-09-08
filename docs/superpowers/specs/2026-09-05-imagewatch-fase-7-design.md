# ImageWatch — Fase 7: separación de la lógica de UI y refinamiento

> Escrita el **2026-09-05**, después de fusionar la fase 6 (PR #3, `main` en `80b4fc0`). Recoge lo
> que el usuario pidió tras la verificación visual y lo ordena en tres pasos, cada uno con su PR.

## Punto de partida

La fase 6 dejó funcionando lo que se le pidió: los avisos se retiran al reconocer, el sondeo vive
en Ajustes, la fila reconoce con un check y una versión nueva sobre otra pendiente vuelve a avisar.
La revisión de rama aprobó sin bloqueantes y dejó tres apuntes que entran aquí.

La guía de estilo (`2026-09-05-imagewatch-guia-de-estilo.md`) fijó el rumbo: **la lógica de UX tiene
que quedar desacoplada de cómo se ven los componentes**. Esta fase es la que lo hace de verdad, y
por un motivo concreto: después vendrá Claude Design a trabajar sobre la capa de apariencia, y
mientras las decisiones de comportamiento sigan viviendo dentro de los composables, un retoque
visual puede llevarse por delante lógica sin que nadie lo note.

## Objetivo

1. **Escribir lo que la aplicación ya hace**, como historias de usuario formales, y encontrar los
   casos de uso que faltan.
2. **Separar la lógica de la apariencia**, sin cambiar nada visible, verificándolo contra esas
   historias.
3. **Refinar** lo que el usuario pidió, ya sobre la arquitectura separada.

Los tres pasos van en ese orden y en PRs distintos. El paso 2 se puede revertir entero sin perder
funcionalidad, que es justo lo que se quiere de un refactor.

## No objetivos

Quedan para la fase 8, y están en el punto 5 de la guía de estilo: teclado y atajos, texto
alternativo de los iconos, estado vacío de la tabla, densidad de la fila y contraste de las
píldoras. Tampoco entra el rediseño visual en sí: esta fase prepara el terreno, no lo pinta.

---

## Paso 1 — Historias de usuario (PR 1, solo documentación)

### Qué se escribe

Un documento en `docs/superpowers/specs/2026-09-05-imagewatch-historias-de-usuario.md` con el
comportamiento **actual**, transcrito del código y de lo verificado con la aplicación delante. No es
una lista de deseos: es el contrato contra el que se comprueba que el paso 2 no cambió nada.

Formato de cada historia:

```
### H-07 · Reconocer una imagen retira su aviso
Como usuario que ya vio la versión nueva
quiero que al marcarla como vista desaparezca su aviso
para que la pantalla no siga anunciando algo que ya atendí.

Dado un aviso en pantalla para `alpha`
Cuando marco `alpha` como vista, en su fila o con el botón de todas
Entonces el aviso de `alpha` desaparece, incluso si aún esperaba turno en la cola
Y los avisos de las demás imágenes siguen donde estaban.

Código: ToastState.dismissFor, ImagesViewModel.acknowledge/acknowledgeAll
Prueba: ToastStateTest, ImagesViewModelTest («reconocer una imagen retira su aviso»)
```

Cada historia lleva **traza al código y a la prueba que la cubre**. Una historia sin prueba se
marca con `Prueba: —`, y esa lista es una de las salidas del paso.

### Alcance

Las áreas a cubrir, que salen de lo que hoy existe:

- **Vigilar imágenes:** agregar, renombrar, borrar, buscar, validación de nombres.
- **Sondeo:** ciclo automático, comprobar todas ya, comprobar una fila, detener y reanudar,
  intervalo, persistencia del intervalo.
- **Estados de una imagen:** al día, pendiente, error, sin verificar; qué enseña la fila en cada uno
  y qué conserva cuando falla.
- **Reconocer:** individual, en bloque, qué persiste, qué pasa al renombrar.
- **Avisos:** cuándo se notifica y cuándo no —primer ciclo, misma versión, versión nueva sobre
  pendiente—, cola y turno, descarte manual y automático, pausa al pasar el puntero, «Ver».
- **Sonidos:** los cuatro, y las reglas de silencio por foco.
- **Ventana y bandeja:** cerrar sin salir, abrir desde la bandeja, traer al frente, icono de alerta.
- **Configuración:** origen y simulación, TLS, tema, toasts y duración, sonidos y volumen,
  persistencia y validación.

### Y después: los casos de uso que faltan

Con las historias escritas, un repaso buscando huecos, que se anota en el mismo documento bajo
«Casos de uso sin cubrir». Ya se ven algunos candidatos —qué ocurre al quedarse sin imágenes
vigiladas, qué pasa si el fichero de estado está corrupto, qué debería pasar si la misma imagen
aparece dos veces— pero la lista se cierra al escribirla, no ahora. Ninguno se implementa en este
paso: se anotan y se priorizan.

---

## Paso 2 — Separar la lógica de la apariencia (PR 2, sin cambios visibles)

### La regla

Un composable es una función de estado a píxeles. **No decide**: no mide tiempo, no aplica reglas
de negocio, no deduce qué acciones están disponibles. Todo eso vive en el view model, que se prueba
sin abrir una ventana.

Verificable, y así se revisa el PR:

- Ningún `delay` ni temporizador dentro de un `@Composable`.
- Ningún `if` sobre estado del dominio dentro de un composable (`status == PENDING`, `pending > 0`).
- Ningún literal de `dp`, `sp` ni duración fuera de `Tokens.kt` y `Colors.kt`.

### Qué se mueve, en concreto

| Hoy vive en | Qué es | A dónde va |
|---|---|---|
| `ImagesScreen`: `LaunchedEffect { delay(HIGHLIGHT_DURATION_MILLIS) }` | Cuánto dura el resaltado | `ImagesViewModel`, con su propio scope de corrutina |
| `ImagesScreen`: `LaunchedEffect { delay(BUMP_DURATION_MILLIS) }` | Cuánto late una novedad | `ImagesViewModel` |
| `ImageRow`: `if (row.status == PENDING)` decide si hay botón de reconocer | Regla de disponibilidad | `ImageRowState.canAcknowledge` |
| `ImageRow`: `highlighted` y `bumped` como dos booleanos de apariencia | Intención de la fila | `ImageRowState.emphasis: NINGUNO / SEÑALADA / NOVEDAD` |
| `ImagesScreen`: `if (state.pending > 0)` decide si hay botón de todas | Regla de disponibilidad | `ImagesUiState.canAcknowledgeAll` |
| `ToastCard`: temporizador de descarte, pausa al pasar el puntero, tiempo restante | Ciclo de vida del aviso | `ToastState` (o un `ToastPresenter` a su lado) |
| Constantes privadas de cinco ficheros | Ritmo y medidas | `ui/theme/Tokens.kt` |

El estado que reciben los composables pasa a expresar **intención, no apariencia**: `emphasis =
NOVEDAD` dice qué pasó; que eso se pinte como un latido, un borde o un icono es decisión de la capa
visual, y cambiarla no toca lógica. Ese es el punto entero del paso.

### El caso difícil: el temporizador del toast

Hoy el descarte automático, la pausa al pasar el puntero y el tiempo restante viven dentro de
`ToastCard`, y funcionan bien. Moverlos a `ToastState` tiene una ventaja concreta —se pueden probar
sin ventana, y hoy no hay ni una prueba de ese comportamiento— y un riesgo: la animación de salida
está acoplada al descarte, y separarlos mal hace que la tarjeta desaparezca de golpe en vez de
deslizarse.

La frontera propuesta: `ToastState` decide **cuándo** un aviso debe irse (temporizador, pausa,
tiempo restante) y lo marca como saliente; la tarjeta decide **cómo** se va (deslizamiento,
duración de la animación) y avisa al terminar. La animación es apariencia; el reloj no.

### Tokens

`ui/theme/Tokens.kt` con los valores **actuales**, tal como propone la guía de estilo: `Space`,
`Radius`, `TypeScale`, `Motion`, `Dwell`. Se migran los ficheros que esta fase toca igualmente
—`ToastWindow`, `SettingsScreen`, `ImagesScreen`, `ImageRow`— y el resto queda para cuando se
toquen. Ningún cambio visible: los valores son los que ya había.

### Cómo se comprueba que no cambió nada

Las historias del paso 1, sus pruebas —incluidas las que el paso 1 detectó que faltaban y que se
escriben aquí, antes de mover el código— y una verificación visual final con la aplicación
levantada.

---

## Paso 3 — Refinamiento (PR 3)

### 3.1 Configuración en tiempo real

`SettingsViewModel` deja de ser un formulario con `save()`. Cada cambio produce un `AppConfig`
candidato y va por `applyConfig`, que ya valida, persiste y propaga.

- **Interruptores, tema y chips:** se aplican al pulsarlos.
- **Campos de texto** (URL, intervalo, duración del toast): al salir del campo o con Enter. No con
  cada tecla: una URL a medio escribir no es una URL, y aplicar por pulsación reiniciaría el sondeo
  y tiraría un cliente HTTP nuevo en cada letra.
- **Volumen:** se oye al arrastrar, se persiste al soltar.
- **Errores:** el mensaje se ancla al campo que lo causó (`isError` + `supportingText`). Un valor
  inválido no se aplica y la configuración vigente no se toca.

Se van el botón «Guardar», la marca «Guardado» y el sonido de confirmación por cambio —sonaría en
cada interruptor—. El sonido se reserva para las acciones destructivas. La confirmación de que algo
se guardó pasa a ser que el valor se quedó puesto, que es lo que un ajuste en tiempo real promete.

### 3.2 La capa de avisos

Tres síntomas, una raíz: la geometría se estima en vez de calcularse.

`ToastWindow.kt` calcula la posición con `bounds.width - TOAST_WIDTH - MARGIN` en píxeles de AWT y
lo pasa como `dp`. Con el escalado de Windows al 125 % o 150 % eso desplaza la ventana abajo y a la
derecha de donde debería, y la última tarjeta —con su barrita de progreso— queda bajo la barra de
tareas. Y `LAYER_HEIGHT = 420` es fijo: cuatro tarjetas no caben, así que solo se pintan tres aunque
`TOASTS_VISIBLES` sea 4.

Arreglo:

- Una función pura `toastLayerPlacement(bounds, escala, avisosVisibles)` que convierte los píxeles
  de AWT a `dp` con la escala real de la pantalla y devuelve posición y alto. Pura y por tanto
  probable con escalas 100/125/150 % y con la barra de tareas arriba, abajo o a un lado, sin abrir
  ventana.
- El alto de la capa sale de los tokens de la tarjeta por el número de avisos visibles, no de un
  literal.
- La tarjeta fija su alto: título y cuerpo a una línea con puntos suspensivos, que a 340 dp es lo
  que ya ocurre en la práctica.

Se mantiene la ventana única: una ventana por aviso evita el recorte pero trae cuatro ventanas
nativas con su parpadeo al crearse, y una ventana a la altura de la pantalla intercepta los clics
del escritorio.

### 3.3 Ajustes ampliados

Sección «Datos», al final de la pantalla, con dos acciones y un diálogo de confirmación cada una:

- **Restablecer ajustes:** `config.json` vuelve a los valores de fábrica. Las imágenes vigiladas y
  el historial de versiones reconocidas siguen intactos.
- **Borrar datos locales:** además se van `images.json` y `tracked-images.json`. La aplicación queda
  como recién instalada **sin cerrarse**: se detiene el sondeo, se limpia el estado vivo y se
  reanuda.

El diálogo dice qué se pierde, con esas palabras. Es la única parte de Ajustes que suena.

### 3.4 La deuda de la revisión de la fase 6

- **`acknowledgeAll` (MEDIUM):** hoy lee las pendientes antes de reconocer; si el planificador
  publica un ciclo en medio, esa imagen se reconoce pero su aviso no se retira. A quién retirarle el
  aviso se decide con lo que devuelve el reconocimiento, no con lo leído antes.
- **Regla duplicada (MEDIUM):** `VersionPollingService.pendingNews` y `ImagesViewModel.remoteBumps`
  son la misma comparación con distinto filtro. Un único cálculo en el núcleo, del que leen los dos.
- **`bumped` (LOW):** se reemplaza en cada ciclo; con un intervalo corto, un ciclo apaga el latido
  de otra fila antes de tiempo. Se acumula, y solo el temporizador lo retira.

---

## Pruebas

| Qué | Cómo |
|---|---|
| Historias del paso 1 | Cada una traza a una prueba; las que no la tengan se escriben en el paso 2, antes de mover código |
| Separación | Los tres criterios verificables —sin `delay`, sin reglas, sin literales— se revisan en el PR |
| Ciclo de vida del aviso | Pruebas nuevas de `ToastState`: descarte por tiempo, pausa y reanudación con el tiempo restante |
| `toastLayerPlacement` | Función pura: escalas 100/125/150 %, barra de tareas en los cuatro bordes |
| Configuración en tiempo real | Cada cambio llega al aplicador; un valor inválido no llega y marca el campo |
| Restablecer y borrar | Contra un directorio temporal: qué ficheros quedan y qué estado vivo queda |
| Todo junto | Verificación visual con la aplicación levantada en simulación, como en la fase 6 |

## Riesgos

- **El refactor del paso 2 es el riesgo real de la fase.** Mitigación: va en su propio PR, sin
  cambios visibles, y con las historias delante. Si algo se rompe, se revierte entero.
- **El temporizador del toast** es la pieza más delicada de mover, porque su animación de salida
  está acoplada al descarte. La frontera «el reloj arriba, la animación abajo» es la propuesta; si
  al implementarla la salida deja de verse, se reconsidera antes de seguir.
- **`applyConfig` en tiempo real escribe a disco más a menudo.** Con blur y Enter, y el volumen solo
  al soltar, no hay ráfaga. Si aun así molesta, el siguiente paso es agrupar escrituras, no volver
  al botón.
