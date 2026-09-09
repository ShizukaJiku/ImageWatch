# ImageWatch — Historias de usuario del comportamiento actual

> Escrito el **2026-09-05**, como paso 1 de la fase 7. Este documento describe lo que la
> aplicación **hace hoy**, no lo que debería hacer: es el contrato contra el que se comprobará que
> sacar los temporizadores y las reglas de los composables (paso 2 de la fase 7) no cambia ningún
> comportamiento observable. Cada historia sale del código y de los tests que ya existen, no de la
> guía de estilo ni de la spec de diseño.
>
> Los identificadores `H-01`…`H-88` son correlativos a lo largo de todo el documento y no se
> reordenan: si una historia se parte más adelante, la nueva va al final. Cuando `Prueba:` no es
> `—`, la cita es literal: se abrió el test y se confirmó que comprueba exactamente eso. Un test
> Kotlin se cita entre «comillas» porque su nombre ya es la frase en español; un test Java se cita
> en `código` porque su nombre es un identificador.
>
> Dos ficheros de test no estaban en la lista de lectura de la tarea pero existen en el repositorio
> y cubren comportamiento de las áreas «Sondeo» y «Configuración»
> (`PollingControllerTest.java`, `JsonConfigStoreTest.java`); se leyeron también, para no marcar
> como `Prueba: —` algo que sí tiene prueba.

## Vigilar imágenes

### H-01 · Un nombre inválido se rechaza al agregar

Como usuario que está dando de alta una imagen
quiero que un nombre con caracteres no permitidos se rechace con un mensaje
para que no acabe vigilando algo que no podré consultar nunca.

**Dado** el formulario de agregar imagen
**Cuando** escribo un nombre con un espacio o una barra, como `no vale/esto`
**Entonces** la imagen no se añade a la lista
**Y** recibo un mensaje explicando qué caracteres están permitidos.

Código: `ImagesViewModel.addImage`, `ImagesViewModel.saveName`
Prueba: `ImagesViewModelTest` («un nombre invalido se rechaza con mensaje»)

### H-02 · Un nombre duplicado se rechaza al agregar

Como usuario que está dando de alta una imagen
quiero que un nombre ya vigilado se rechace con un mensaje
para no acabar con dos filas vigilando lo mismo.

**Dado** que ya vigilo `alpha`
**Cuando** intento agregar otra imagen llamada `alpha`
**Entonces** no se añade una segunda entrada
**Y** recibo un mensaje diciendo que ya existe.

Código: `ImagesViewModel.addImage`, `ImagesViewModel.saveName`
Prueba: `ImagesViewModelTest` («un nombre duplicado se rechaza con mensaje»)

### H-03 · Un nombre válido se agrega a la lista

Como usuario
quiero que un nombre correcto y no repetido se registre
para empezar a vigilarlo.

**Dado** que vigilo `alpha`
**Cuando** agrego `beta`
**Entonces** `beta` aparece en la lista de imágenes vigiladas.

Código: `ImagesViewModel.addImage`
Prueba: `ImagesViewModelTest` («un nombre valido se agrega»)

### H-04 · ~~Renombrar una imagen a su propio nombre no es un duplicado~~ (retirada en el rediseño, 2026-09-08)

El rediseño retira el renombrado: el origen de una imagen es su identidad, y para cambiarla se
quita y se vuelve a agregar. `ImagesViewModel.renameImage` y `ImagesViewModel.saveName` ya no
existen; `VersionPollingService.renameImage` y `ImageStateStore.rename` quedan en el núcleo sin
llamador desde la interfaz.

### H-05 · ~~Renombrar una imagen conserva su versión reconocida~~ (retirada en el rediseño, 2026-09-08)

Ver H-04.

### H-06 · Eliminar una imagen la retira de la lista

Como usuario
quiero eliminar una imagen que ya no me interesa vigilar
para que deje de ocupar espacio en la tabla.

**Dado** que vigilo `alpha` y `beta`
**Cuando** elimino `alpha`
**Entonces** la tabla solo muestra `beta`.

Código: `ImagesViewModel.removeImage`
Prueba: `ImagesViewModelTest` («eliminar una imagen la saca de la lista»)

### H-07 · Buscar filtra la tabla sin cambiar el total de imágenes vigiladas

Como usuario con muchas imágenes vigiladas
quiero poder filtrar la tabla por nombre
para encontrar una fila concreta sin perder de vista cuántas vigilo en total.

**Dado** que vigilo `alpha` y `beta`
**Cuando** escribo `alph` en el buscador
**Entonces** la tabla solo muestra `alpha`
**Y** el contador de total sigue diciendo 2, no 1.

Código: `ImagesViewModel.onSearchChange`, `ImagesViewModel.derive`
Prueba: `ImagesViewModelTest` («el filtro deja solo las coincidencias sin perder el total»)

### H-08 · Tras un ciclo, la tabla muestra una fila por cada imagen vigilada

Como usuario
quiero que un ciclo de sondeo actualice todas mis imágenes vigiladas a la vez
para ver el estado conjunto de un vistazo.

**Dado** que vigilo `alpha` y `beta`
**Cuando** ocurre un ciclo de sondeo
**Entonces** la tabla muestra dos filas, y el total dice 2.

Código: `ImagesViewModel.onSnapshot`, `ImagesViewModel.derive`
Prueba: `ImagesViewModelTest` («el estado refleja el snapshot tras un ciclo»)

### H-90 · Abrir una fila muestra su detalle en su sitio (rediseño, 2026-09-08)

Como operador que quiere saber más de una imagen
quiero pulsar la fila y ver su detalle desplegarse debajo, sin partir la ventana
para consultar origen, última versión y cuándo se detectó, y tener a mano sus acciones.

**Dado** una fila cualquiera de la bandeja
**Cuando** hago clic en ella, en un punto que no sea un control
**Entonces** se despliega bajo la fila una banda con Nombre, Origen, Última versión y Detectada,
más las acciones Comprobar ahora, Copiar referencia, Silenciar avisos y Quitar de la lista
**Y** volver a pulsarla la pliega
**Y** abrir otra fila cierra la anterior: solo hay una desplegada a la vez.

Código: `ImagesViewModel.toggleExpand`, `ImageRow.RowCard`/`RowDetail`
Prueba: `ImagesViewModelTest` («abrir una fila fija expandedRow y volver a pulsarla lo limpia», «abrir otra fila cierra la anterior»)

### H-91 · Copiar referencia copia solo el origen y avisa con un cambio de icono (rediseño, 2026-09-08)

Como operador que necesita la referencia de una imagen
quiero copiarla al portapapeles sin que salte un aviso
para no interrumpir lo que estoy haciendo.

**Dado** una fila abierta, o su menú de más acciones
**Cuando** pulso «Copiar referencia»
**Entonces** el portapapeles recibe el origen de la imagen (`registry.local/alpha`), sin `:versión`
**Y** la única señal es la etiqueta de la píldora, que pasa a «Copiado» durante dos segundos.

Código: `ImageRow.RowDetail`/`RowMenu`
Prueba: sin prueba unitaria (portapapeles); repaso visual.

### H-92 · Marcar «Visto» reconoce al momento y deja un rastro (rediseño, 2026-09-08)

Como operador que ya vio la versión nueva
quiero que al marcarla como vista la fila salga de «Versión nueva» sin más
para no tener que confirmar ni esperar una ventana de deshacer.

**Dado** una imagen en «Versión nueva»
**Cuando** pulso «Visto», en su fila o en su menú
**Entonces** la imagen pasa a «Al día» al momento, animando su alto
**Y** la cabecera plegada de «Al día» muestra «<nombre> se ha movido aquí» durante 4 s
**Y** no aparece ninguna línea de «Deshacer».

Código: `ImagesViewModel.acknowledge`
Prueba: `ImagesViewModelTest` («reconocer una imagen la mueve a «Al dia» y deja un rastro que se apaga solo»)

## Sondeo

### H-09 · El sondeo arranca detenido

Como usuario que acaba de crear el controlador de sondeo
quiero que empiece parado
para decidir yo cuándo se dispara la primera consulta.

**Dado** un controlador recién creado con un intervalo de 30 s
**Entonces** su estado es `STOPPED`
**Y** su intervalo es el que se le pasó al construirlo.

Código: `PollingController` (constructor), `PollingController.status`
Prueba: `PollingControllerTest` (`startsStopped`)

### H-10 · El intervalo de sondeo debe ser de al menos un segundo

Como responsable de no saturar el origen remoto
quiero que un intervalo menor de un segundo se rechace
para que un valor mal escrito no dispare una consulta continua.

**Dado** cualquier intento de crear o cambiar el intervalo
**Cuando** el valor es 500 ms, cero o negativo
**Entonces** se lanza una excepción y el intervalo no se acepta.

Código: `PollingController.validateInterval`
Prueba: `PollingControllerTest` (`rejectsIntervalsBelowOneSecond`)

### H-11 · Cambiar el intervalo con el sondeo detenido no lo arranca

Como usuario que ajusta el intervalo antes de empezar a vigilar
quiero que cambiarlo no dispare el sondeo por sí solo
para decidir yo por separado cuándo arranca.

**Dado** un controlador detenido con intervalo de 30 s
**Cuando** cambio el intervalo a 5 s
**Entonces** el intervalo pasa a ser 5 s
**Y** el controlador sigue `STOPPED`.

Código: `PollingController.updateInterval`
Prueba: `PollingControllerTest` (`updatingTheIntervalWhileStoppedKeepsItStopped`)

### H-12 · Un intervalo inválido no reemplaza al vigente

Como usuario que guarda los ajustes con un intervalo mal escrito
quiero que el intervalo anterior siga activo
para que un error de tecleo no deje el sondeo en un estado indefinido.

**Dado** un controlador con intervalo de 30 s
**Cuando** intento cambiarlo a cero
**Entonces** la operación lanza una excepción
**Y** el intervalo sigue siendo 30 s.

Código: `PollingController.updateInterval`, `PollingController.validateInterval`
Prueba: `PollingControllerTest` (`rejectsAnInvalidIntervalUpdateAndKeepsThePreviousOne`)

### H-13 · Detener el sondeo ya detenido no falla

Como quien controla el sondeo
quiero poder pedir «para» aunque ya esté parado
para no tener que comprobar el estado antes de cada llamada.

**Dado** un controlador detenido
**Cuando** pido detenerlo dos veces seguidas
**Entonces** sigue `STOPPED`, sin error.

Código: `PollingController.stop`
Prueba: `PollingControllerTest` (`stopIsIdempotent`)

### H-14 · Arrancar tras cerrar el controlador no hace nada

Como aplicación que se está cerrando
quiero que un intento de arrancar el sondeo después de cerrarlo no reviva nada
para que un cierre a medias no deje un hilo de sondeo huérfano.

**Dado** un controlador ya cerrado con `close()`
**Cuando** pido `start()`
**Entonces** el estado sigue `STOPPED`.

Código: `PollingController.start`, `PollingController.close`
Prueba: `PollingControllerTest` (`startAfterCloseIsIgnored`)

### H-15 · Cerrar el controlador dos veces no falla

Como aplicación que se está cerrando
quiero poder llamar a `close()` más de una vez sin que reviente
para no tener que llevar la cuenta de si ya se cerró.

**Dado** un controlador
**Cuando** lo cierro dos veces seguidas
**Entonces** no se lanza ningún error y el estado sigue `STOPPED`.

Código: `PollingController.close`
Prueba: `PollingControllerTest` (`closeIsIdempotent`)

### H-16 · Un ciclo conserva en la foto a todas las imágenes vigiladas, al día o no

Como usuario
quiero que un ciclo de sondeo incluya en su resultado a todas las imágenes vigiladas
para que ninguna desaparezca de la tabla por estar ya al día.

**Dado** que vigilo `alpha` y `beta`, ambas ya conocidas
**Cuando** ocurre un ciclo
**Entonces** el resultado incluye a las dos, en cualquier orden.

Código: `VersionPollingService.poll`, `VersionPollingService.commit`
Prueba: `VersionPollingServiceTest` (`theSnapshotKeepsEveryTrackedImageEvenWhenUpToDate`)

### H-17 · Cada ciclo publica su resultado a los oyentes registrados

Como interfaz que muestra la tabla
quiero enterarme de cada ciclo que termina
para refrescar lo que se ve sin tener que consultarlo yo misma.

**Dado** un oyente registrado
**Cuando** ocurre un ciclo
**Entonces** el oyente recibe exactamente un resultado con las imágenes vigiladas.

Código: `VersionPollingService.addListener`, `VersionPollingService.publish`
Prueba: `VersionPollingServiceTest` (`publishesTheSnapshotToRegisteredListeners`)

### H-18 · Un oyente retirado deja de recibir resultados

Como interfaz que se cierra
quiero poder darme de baja de los ciclos de sondeo
para no seguir recibiendo actualizaciones de una pantalla que ya no existe.

**Dado** un oyente registrado que ya recibió un ciclo
**Cuando** lo retiro y ocurre otro ciclo
**Entonces** el oyente sigue teniendo solo el resultado anterior.

Código: `VersionPollingService.removeListener`
Prueba: `VersionPollingServiceTest` (`aRemovedListenerStopsReceivingSnapshots`)

### H-19 · Un oyente que falla no impide que los demás reciban el resultado

Como aplicación con varias pantallas suscritas al mismo sondeo
quiero que un oyente roto no bloquee a los demás
para que un fallo aislado no deje a toda la interfaz sin actualizar.

**Dado** dos oyentes, uno de los cuales lanza una excepción al recibir
**Cuando** ocurre un ciclo
**Entonces** el oyente sano recibe igualmente su resultado.

Código: `VersionPollingService.publish`
Prueba: `VersionPollingServiceTest` (`aFailingListenerDoesNotPreventTheOthersFromReceiving`)

### H-20 · Un notificador que falla no impide que el ciclo publique su resultado

Como aplicación que envía avisos por varios canales
quiero que un canal roto no interrumpa el ciclo de sondeo
para que un aviso que no pudo enviarse no deje a la tabla sin actualizar.

**Dado** un notificador que lanza una excepción al recibir transiciones
**Cuando** una imagen pasa a pendiente en un ciclo
**Entonces** el ciclo sigue y publica el resultado igualmente, con esa imagen en `PENDING`.

Código: `VersionPollingService.notifyTransitions`
Prueba: `VersionPollingServiceTest` (`unNotificadorQueLanzaNoImpideQueElCicloPubliqueElSnapshot`)

### H-21 · El aviso de comienzo de ciclo llega antes que el resultado

Como pantalla que enciende un indicador de «verificando»
quiero enterarme de que el ciclo empezó antes de recibir su resultado
para poder mostrar el indicador durante todo el tiempo que dura la consulta.

**Dado** un oyente registrado
**Cuando** ocurre un ciclo
**Entonces** el oyente recibe primero el aviso de inicio y después el resultado, en ese orden.

Código: `VersionPollingService.publishStart`, `VersionPollingService.poll`
Prueba: `VersionPollingServiceTest` (`elOyenteSeEnteraDeQueElCicloEmpiezaAntesDeRecibirElResultado`)

### H-22 · Mientras un ciclo está en curso, la interfaz sabe que se está verificando

Como usuario mirando la tabla
quiero ver que algo se está comprobando
para distinguir «todavía no se sabe» de «esto lleva mucho sin comprobarse».

**Dado** la pantalla en reposo
**Cuando** empieza un ciclo de sondeo
**Entonces** `verifying` pasa a verdadero
**Y** al terminar el ciclo vuelve a falso.

Código: `ImagesViewModel.onPollStarted`, `ImagesViewModel.onSnapshot`
Prueba: `ImagesViewModelTest` («verificando se enciende al empezar el ciclo y se apaga al terminar», «el aviso de comienzo enciende la verificacion»)

### H-23 · Refrescar una imagen concreta la consulta sin esperar el ciclo, sin mover las demás

Como usuario que acaba de cambiar algo y quiere comprobarlo ya
quiero pulsar el botón de refrescar de una fila
para verla actualizada sin esperar al siguiente ciclo programado, y sin que salte al final de la tabla.

**Dado** el sondeo detenido o en marcha
**Cuando** pulso «refrescar» en la fila de `alpha`
**Entonces** solo se consulta `alpha`
**Y** su fila conserva su posición en la tabla, según el orden de las imágenes vigiladas.

Código: `PollingController.refreshNow`, `VersionPollingService.pollOne`
Prueba: `PollingControllerTest` («refreshNowWithANameQueriesOnlyThatImageAndKeepsTheOthersInPlace»)

### H-24 · Comprobar todas ya, sin esperar el intervalo

Como usuario impaciente
quiero un botón que consulte todas las imágenes ahora mismo
para no tener que esperar al próximo ciclo programado.

**Dado** el sondeo detenido o en marcha
**Cuando** pulso el botón de refrescar de la cabecera
**Entonces** se lanza un ciclo completo inmediatamente, sin alterar el calendario del sondeo.

Código: `PollingController.refreshNow` (con `name = null`), `VersionPollingService.poll`
Prueba: `PollingControllerTest` («refreshNowWithoutANameQueriesEverythingImmediatelyWithoutTouchingTheSchedule»)

## Estados de una imagen

### H-25 · Una imagen sin versión reconocida se marca como desconocida, no como pendiente

Como usuario que acaba de agregar una imagen
quiero que su primera consulta no la marque como «pendiente»
para no confundir «nunca se ha comparado» con «hay una versión nueva».

**Dado** `alpha` recién agregada, sin ninguna versión reconocida todavía
**Cuando** el origen responde con una versión
**Entonces** el estado de `alpha` es `UNKNOWN`, no `PENDING`.

Código: `VersionPollingService.toState`, `VersionPollingService.statusOf`
Prueba: `VersionPollingServiceTest` (`anImageWithoutAKnownVersionIsUnknownRatherThanPending`)

### H-26 · Una imagen nunca verificada se muestra sin versiones

Como usuario que acaba de agregar una imagen
quiero ver claramente que todavía no se ha comprobado
para no confundirla con una que ya se consultó y no tiene datos.

**Dado** `alpha` recién agregada y ningún ciclo ejecutado todavía
**Entonces** su fila muestra «—» en local y remoto, estado `UNKNOWN` y el detalle «Sin verificar todavía».

Código: `ImagesViewModel.toRow`
Prueba: `ImagesViewModelTest` («una imagen sin verificar se muestra sin versiones»)

### H-27 · Una imagen pendiente lo sigue estando mientras no se reconozca

Como usuario
quiero que una versión nueva detectada no desaparezca sola
para no perderla de vista si tardo en atenderla.

**Dado** `alpha` en `PENDING`
**Cuando** pasan varios ciclos sin que yo la reconozca
**Entonces** sigue en `PENDING`.

Código: `VersionPollingService.toState`, `VersionPollingService.statusOf`
Prueba: `VersionPollingServiceTest` (`aPendingImageStaysPendingAcrossPolls`)

### H-28 · Un fallo de consulta la deja en error, conservando lo último que se sabía

Como usuario
quiero que un fallo de red no borre lo que ya sabía de una imagen
para seguir viendo su última versión conocida, aunque atenuada, en vez de una fila en blanco.

**Dado** `alpha` con versión local conocida
**Cuando** el origen falla al consultarla (por ejemplo, HTTP 503)
**Entonces** `alpha` pasa a `ERROR`, con el mensaje del fallo
**Y** conserva su versión local y su registro tal como se conocían
**Y** las demás imágenes se actualizan con normalidad.

Código: `VersionPollingService.toState`, `VersionPollingService.failedKeepingKnownLocal`
Prueba: `VersionPollingServiceTest` (`oneFailingImageDoesNotPreventTheOthersFromUpdating`), `ImagesViewModelTest` («una fila con error conserva la ultima version conocida»)

### H-29 · Una referencia mal formada produce error solo para esa imagen

Como usuario
quiero que una referencia rota del origen no tumbe todo el ciclo
para que las demás imágenes se sigan comprobando con normalidad.

**Dado** que el origen devuelve una referencia irreconocible para `alpha` (por ejemplo, sin `registro/nombre:tag`) y una válida para `beta`
**Cuando** ocurre un ciclo
**Entonces** `alpha` queda en `ERROR`, conservando su versión y registro previos
**Y** `beta` se actualiza con normalidad.

Código: `VersionPollingService.toState`, `VersionPollingService.parse`
Prueba: `VersionPollingServiceTest` (`anUnparseableReferenceBecomesAnErrorForThatImageOnly`)

### H-30 · Si todas las imágenes vigiladas fallan a la vez, aparece el aviso de sin conexión

Como usuario
quiero distinguir «el origen no responde» de «esta imagen concreta tiene un problema»
para saber si el problema es mío, de la imagen, o de la red entera.

**Dado** que vigilo `alpha` y `beta`
**Cuando** las dos fallan en el mismo ciclo
**Entonces** aparece el aviso de «sin conexión con el origen».

Código: `ImagesViewModel.derive` (`allFailing`), `ImagesScreen.ConnectionBanner`
Prueba: `ImagesViewModelTest` («si fallan todas se marca el aviso global»)

### H-31 · Una sola imagen caída no dispara el aviso de sin conexión

Como usuario
quiero que el fallo de una sola imagen no se confunda con una caída general
para no ir a revisar mi conexión cuando en realidad una imagen dejó de existir.

**Dado** que vigilo `alpha` y `beta`
**Cuando** solo `alpha` falla (por ejemplo, HTTP 404)
**Entonces** el aviso de sin conexión no aparece.

Código: `ImagesViewModel.derive` (`allFailing`)
Prueba: `ImagesViewModelTest` («una sola imagen caida no dispara el aviso global»)

### H-32 · Dar de alta una imagen durante una caída general no retrae el aviso de sin conexión

Como usuario
quiero que agregar una imagen mientras el origen está caído no oculte el aviso
para no pensar erróneamente que la conexión se recuperó.

**Dado** que `alpha` y `beta` fallan en el ciclo actual y el aviso de sin conexión está visible
**Cuando** agrego `gamma`, todavía sin verificar
**Entonces** el aviso de sin conexión sigue visible: `gamma` no cuenta para el cálculo hasta que se consulte por primera vez.

Código: `ImagesViewModel.derive` (`consultadas`, `allFailing`)
Prueba: `ImagesViewModelTest` («dar de alta una imagen durante una caida no retrae el aviso ni repite el sonido», cubre el aviso; el sonido de esa misma prueba se cita en H-71)

## Reconocer

### H-33 · Reconocer una imagen la deja al día sin esperar el siguiente ciclo

Como usuario que acaba de ver la versión nueva
quiero que marcarla como vista actualice su fila ya mismo
para no seguir viéndola como pendiente hasta el próximo ciclo, que puede tardar minutos.

**Dado** `alpha` en `PENDING`
**Cuando** la reconozco
**Entonces** su fila pasa a `OK` de inmediato, sin esperar a un nuevo ciclo.

Código: `ImagesViewModel.acknowledge`, `VersionPollingService.acknowledge`
Prueba: `ImagesViewModelTest` («reconocer una imagen la deja al dia sin esperar al siguiente ciclo»)

### H-34 · Reconocer evita que el siguiente ciclo la vuelva a marcar pendiente

Como usuario
quiero que reconocer una versión la dé por vista de verdad
para que el próximo ciclo no me la vuelva a presentar como novedad.

**Dado** `alpha` en `PENDING`
**Cuando** la reconozco y después ocurre un nuevo ciclo (el origen sigue publicando la misma versión)
**Entonces** `alpha` aparece en `OK`.

Código: `VersionPollingService.acknowledge`, `VersionPollingService.statusOf`
Prueba: `VersionPollingServiceTest` (`acknowledgingAnImageClearsItsPendingStatus`)

### H-35 · Reconocer publica el nuevo estado sin esperar el siguiente ciclo

Como interfaz suscrita al sondeo
quiero enterarme del cambio en cuanto el usuario reconoce
para repintar la fila sin esperar a que otro ciclo la traiga.

**Dado** `alpha` en `PENDING` y un oyente registrado
**Cuando** reconozco `alpha`
**Entonces** el oyente recibe un nuevo resultado, con `alpha` ya en `OK`, sin que haya corrido otro ciclo.

Código: `VersionPollingService.acknowledge`, `VersionPollingService.publish`
Prueba: `VersionPollingServiceTest` (`acknowledgingPublishesTheUpdatedStateWithoutWaitingForTheNextPoll`)

### H-36 · Reconocer una imagen retira su aviso en pantalla

Como usuario que ya vio la versión nueva
quiero que al marcarla como vista desaparezca su aviso
para que la pantalla no siga anunciando algo que ya atendí.

**Dado** un aviso en pantalla para `alpha`
**Cuando** marco `alpha` como vista
**Entonces** el aviso de `alpha` desaparece, incluso si aún esperaba turno en la cola
**Y** los avisos de las demás imágenes siguen donde estaban.

Código: `ToastState.dismissFor`, `ImagesViewModel.acknowledge`
Prueba: `ToastStateTest` («descartar por imagen retira sus avisos y deja los demas»), `ImagesViewModelTest` («reconocer una imagen retira su aviso en pantalla»)

### H-37 · Reconocer todas retira los avisos de todas las pendientes, de una vez

Como usuario con varias versiones nuevas a la vez
quiero un botón que las dé todas por vistas
para no tener que reconocerlas una a una, ni escuchar un sonido por cada una.

**Dado** `alpha` y `beta` en `PENDING`, con sus avisos en pantalla
**Cuando** pulso «reconocer todas»
**Entonces** las dos pasan a `OK`
**Y** los avisos de ambas desaparecen.

Código: `ImagesViewModel.acknowledgeAll`, `VersionPollingService.acknowledgeAll`
Prueba: `ImagesViewModelTest` («reconocer todas retira los avisos de todas las pendientes»)

### H-38 · El check de reconocer solo se ofrece cuando la imagen está pendiente

Como usuario
quiero no ver un botón de reconocer en una fila que no tiene nada que reconocer
para no pulsar algo que no haría nada.

**Dado** una fila en `OK`, `ERROR` o `UNKNOWN`
**Entonces** no se muestra el icono de reconocer, aunque se reserva su hueco para que los demás botones no se muevan.

Código: `ImageRowState.canAcknowledge` (`ImageRow` solo lee el booleano)
Prueba: `ImagesViewModelTest` («solo se puede reconocer lo que esta pendiente»)

### H-39 · Reconocer una imagen nunca vista no hace nada

Como aplicación
quiero que reconocer un nombre que no apareció en el último ciclo no rompa nada
para no dejar datos inventados en el almacén de estado.

**Dado** una imagen `ausente` que nunca se ha consultado
**Cuando** intento reconocerla
**Entonces** no se guarda ninguna versión para ella.

Código: `VersionPollingService.acknowledge`
Prueba: `VersionPollingServiceTest` (`acknowledgingAnImageThatWasNeverSeenIsANoOp`)

### H-40 · Reconocer una imagen no afecta a las demás pendientes

Como usuario con varias imágenes pendientes
quiero reconocer solo la que ya revisé
para que las demás sigan avisando hasta que yo decida.

**Dado** `alpha` y `beta`, las dos en `PENDING`
**Cuando** reconozco solo `alpha`
**Entonces** `alpha` pasa a `OK`
**Y** `beta` sigue en `PENDING`.

Código: `VersionPollingService.acknowledge`
Prueba: `VersionPollingServiceTest` (`acknowledgingOneImageLeavesTheOthersUntouched`)

## Avisos

### H-41 · Una imagen que pasa a pendiente por primera vez genera un aviso

Como usuario
quiero enterarme en cuanto una imagen tiene una versión nueva
para no tener que revisar la tabla entera cada vez.

**Dado** `alpha` y `beta` al día
**Cuando** un ciclo detecta que `alpha` tiene una versión más nueva
**Entonces** se genera un aviso solo para `alpha`.

Código: `VersionPollingService.notifyTransitions`, `VersionPollingService.pendingNews`
Prueba: `VersionPollingServiceTest` (`notifiesOnlyImagesThatBecamePending`)

### H-42 · El primer ciclo de la sesión no avisa de nada

Como usuario que acaba de arrancar la aplicación
quiero que el primer ciclo no me avise de versiones que ya decidí posponer en una sesión anterior
para no repetir cada día lo que ya había visto.

**Dado** `alpha` ya con una versión pendiente registrada en el almacén de estado
**Cuando** ocurre el primer ciclo de una sesión nueva
**Entonces** no se genera ningún aviso
**Y** el estado de `alpha` sigue siendo `PENDING`: la ventana ya lo muestra al abrirse.

Código: `VersionPollingService.notifyTransitions` (`previous == PollSnapshot.EMPTY`)
Prueba: `VersionPollingServiceTest` (`theFirstPollOfASessionDoesNotNotify`)

### H-43 · La primera vez que se ve una imagen, su versión queda registrada sin avisar

Como usuario que acaba de agregar una imagen
quiero que su primera versión detectada se tome como línea base
para que el arranque no me avise de todas las imágenes vigiladas.

**Dado** `alpha` recién agregada, sin ninguna versión reconocida
**Cuando** ocurre el primer ciclo
**Entonces** su versión queda registrada como ya vista
**Y** no se genera ningún aviso.

Código: `VersionPollingService.persist`
Prueba: `VersionPollingServiceTest` (`theFirstSightingOfAnImageIsRecordedAsAcknowledged`)

### H-44 · Una pendiente sin cambios no repite su aviso en ciclos sucesivos

Como usuario
quiero que una versión pendiente que ya conozco no me avise otra vez en cada ciclo
para no acabar ignorando los avisos por repetición.

**Dado** `alpha` en `PENDING` con una versión remota concreta
**Cuando** pasan más ciclos y el origen sigue publicando la misma versión
**Entonces** no se genera ningún aviso nuevo.

Código: `VersionPollingService.pendingNews`
Prueba: `VersionPollingServiceTest` (`doesNotNotifyTheSamePendingImageTwice`, `unaPendienteConLaMismaVersionRemotaNoVuelveAAvisar`)

### H-45 · Si una pendiente se reconoce y vuelve a quedar pendiente, avisa de nuevo

Como usuario
quiero enterarme de una versión nueva aunque ya hubiera reconocido una anterior
para no perderme actualizaciones posteriores a la que ya vi.

**Dado** `alpha`, reconocida y al día
**Cuando** el origen publica una versión más nueva
**Entonces** se genera un nuevo aviso para `alpha`.

Código: `VersionPollingService.pendingNews`
Prueba: `VersionPollingServiceTest` (`notifiesAgainWhenAnImageBecomesPendingOnceMore`)

### H-46 · Una versión remota más nueva sobre una pendiente sin reconocer vuelve a avisar

Como usuario que todavía no atendió una versión pendiente
quiero enterarme si aparece una versión más nueva todavía
para no quedarme con una versión desactualizada en la mano cuando por fin la reconozca.

**Dado** `alpha` en `PENDING` sobre la versión `2.0.0`, sin reconocer
**Cuando** el origen publica `3.0.0`
**Entonces** se genera un nuevo aviso para `alpha`, con la versión remota `3.0.0`
**Y** su fila late brevemente en la tabla para señalar que llegó algo nuevo
**Y**, si la ventana tiene el foco, no suena nada —el latido basta—; si no lo tiene, sí suena.

Código: `VersionPollingService.pendingNews`, `ImagesViewModel.remoteBumps`
Prueba: `VersionPollingServiceTest` (`avisaOtraVezCuandoLlegaUnaVersionMasNuevaSobreUnaPendienteSinReconocer`), `ImagesViewModelTest` («una version mas nueva sobre una pendiente sin reconocer marca la fila»); la regla de sonido según el foco se cita en H-60/H-61

### H-47 · Una pendiente con la misma versión remota no vuelve a marcar la fila

Como usuario
quiero que el latido de «llegó algo nuevo» no se repita si no ha llegado nada nuevo
para que la animación siga significando lo que dice.

**Dado** `alpha` en `PENDING`
**Cuando** ocurre otro ciclo con la misma versión remota
**Entonces** la fila no vuelve a latir.

Código: `ImagesViewModel.remoteBumps`
Prueba: `ImagesViewModelTest` («una pendiente con la misma version no vuelve a marcar la fila»)

### H-48 · Una imagen que falla no se notifica como actualización

Como usuario
quiero que un fallo de consulta no se confunda con una versión nueva
para no ir a mirar una fila que en realidad solo tiene un error de red.

**Dado** `alpha` que falla al consultarse
**Cuando** ocurre el ciclo
**Entonces** no se genera ningún aviso de actualización para `alpha`.

Código: `VersionPollingService.pendingNews` (filtra por `ImageStatus.PENDING`)
Prueba: `VersionPollingServiceTest` (`anImageThatFailsIsNotNotifiedAsAnUpdate`)

### H-49 · Un aviso muestra la transición de versión de la imagen

Como usuario
quiero que el aviso me diga de qué versión a qué versión se pasó
para saber si el salto es grande sin tener que abrir la ventana.

**Dado** una transición de `alpha` de `1.0.0` a `1.1.0`
**Entonces** el aviso muestra ambas versiones en su cuerpo.

Código: `ToastState.toastOf`
Prueba: `ToastStateTest` («una imagen produce un toast con su transicion»)

### H-50 · Cada imagen tiene su propio aviso; nunca se agrupan

Como usuario con varias versiones nuevas a la vez
quiero un aviso por imagen
para no perder el detalle de ninguna en un resumen genérico.

**Dado** tres imágenes que pasan a pendientes en el mismo ciclo
**Entonces** aparecen tres avisos, uno por imagen, y no un aviso agrupado.

Código: `ToastState.show`
Prueba: `ToastStateTest` («dos imagenes producen dos toasts», «tres o mas siguen siendo un toast por imagen»)

### H-51 · Descartar un aviso concreto retira solo ese, dejando los demás

Como usuario
quiero cerrar un aviso sin que se lleve a los demás por delante
para seguir viendo lo que todavía no atendí.

**Dado** dos avisos en pantalla
**Cuando** cierro uno de ellos
**Entonces** solo desaparece ese; el otro sigue en pantalla.

Código: `ToastState.dismiss`
Prueba: `ToastStateTest` («descartar quita solo el toast pedido»)

### H-52 · Los avisos que no caben esperan turno en la cola en vez de perderse

Como usuario que recibe muchos avisos a la vez
quiero que los que no caben en la ventana no se pierdan
para no dejar de enterarme de una versión nueva solo por mal momento.

**Dado** diez imágenes que pasan a pendientes en el mismo ciclo
**Entonces** las diez quedan en la cola de avisos
**Y** solo se muestran las cuatro más antiguas a la vez
**Y**, al cerrarse una de las visibles, entra la siguiente de la cola, respetando el orden de llegada.

Código: `ToastState.show`, `TOASTS_VISIBLES`, `ToastWindow.ToastLayer`
Prueba: `ToastStateTest` («los que no caben en la ventana esperan turno en vez de perderse», «al descartar uno entra el siguiente de la cola»)

### H-53 · La cola de avisos es segura entre hilos

Como aplicación en la que el sondeo y la interfaz corren en hilos distintos
quiero que mostrar y descartar avisos en paralelo no pierda ninguno
para que un aviso no desaparezca —o dos no se fundan en uno— por una coincidencia de tiempo.

**Dado** cincuenta avisos generándose y descartándose desde hilos distintos a la vez
**Entonces** ni se pierde ningún aviso al mostrarlos, ni queda ninguno sin descartar al descartarlos todos.

Código: `ToastState.show`, `ToastState.dismiss` (ambos con `MutableStateFlow.update`)
Prueba: `ToastStateTest` («mostrar en paralelo no pierde ningun toast», «descartar en paralelo no pierde ningun descarte»)

### H-54 · Descartar por una imagen sin avisos no cambia nada

Como aplicación
quiero que pedir el descarte de una imagen sin avisos pendientes no falle
para no tener que comprobar antes si existe algo que descartar.

**Dado** un aviso en pantalla para `alpha`, ninguno para `gamma`
**Cuando** pido descartar los avisos de `gamma`
**Entonces** el aviso de `alpha` sigue intacto.

Código: `ToastState.dismissFor`
Prueba: `ToastStateTest` («descartar por una imagen sin avisos no cambia nada»)

### H-55 · Pulsar «Ver» en un aviso hace visible la ventana y resalta la fila

Como usuario que ve un aviso con la ventana principal cerrada
quiero que pulsar «Ver» la abra ya señalando la imagen
para no tener que buscarla yo mismo en la tabla.

**Dado** un aviso para `alpha`, con la ventana principal cerrada
**Cuando** pulso «Ver»
**Entonces** la ventana se hace visible, el filtro de búsqueda se limpia y la fila de `alpha` queda resaltada
**Y**, si la ventana ya estaba visible pero solo detrás de otra aplicación, «Ver» no la trae al frente: únicamente resalta la fila (el caso queda en «Casos de uso sin cubrir»).

Código: `ImagesViewModel.highlight`, `Main.kt` (`onView`), `ImagesScreen` (scroll a la fila resaltada)
Prueba: `ImagesViewModelTest` («resaltar una fila la marca y limpia el filtro de busqueda»; cubre solo `ImagesViewModel.highlight` — hacer visible la ventana (`Main.kt`) y el scroll a la fila (`ImagesScreen`) son cableado de composable sin infraestructura de test todavía)

### H-56 · El resaltado de una fila señalada desde un aviso se apaga solo

Como usuario
quiero que el resaltado desaparezca solo al cabo de un rato
para que no se acumule un resaltado permanente en toda fila que alguna vez señalé.

**Dado** una fila recién resaltada por «Ver»
**Cuando** pasan 4 segundos sin otra interacción
**Entonces** el resaltado se apaga.

Código: `ImagesViewModel.highlight` (temporizador propio con `Dwell.HIGHLIGHT_MILLIS`; `ImageRow` solo pinta `RowEmphasis.SENALADA`)
Prueba: `ImagesViewModelTest` («el resaltado se apaga solo al cumplirse su tiempo»)

### H-57 · El latido de «otra versión más» se apaga solo

Como usuario
quiero que el latido de una fila con versión nueva se apague por sí mismo
para que deje de señalar algo en cuanto ya lo he visto.

**Dado** una fila que acaba de latir por una versión más nueva sobre otra pendiente
**Cuando** pasan 3 segundos
**Entonces** el latido se apaga.

Código: `ImagesViewModel.onSnapshot`/`scheduleBumpClear` (temporizador propio con `Dwell.BUMP_MILLIS`; `ImageRow` solo pinta `RowEmphasis.NOVEDAD`)
Prueba: `ImagesViewModelTest` («una novedad late su tiempo aunque entre otro ciclo»)

### H-58 · El aviso en pantalla se descarta solo tras unos segundos

Como usuario que no interactúa con un aviso
quiero que desaparezca por sí solo
para no tener que cerrar manualmente cada uno.

**Dado** un aviso recién mostrado, con una duración configurada
**Cuando** transcurre ese tiempo sin que el usuario interactúe con él
**Entonces** el aviso se desliza hacia fuera y se retira de la cola.

Código: `ToastState` (`crearJob`, `remaining`, `marcarSaliente`), `ToastWindow.ToastCard` (`LaunchedEffect(saliendo)`)
Prueba: `ToastStateTest` (`un aviso se retira solo al agotarse su tiempo`)

### H-59 · Pasar el puntero sobre un aviso pausa su descarte automático

Como usuario que está leyendo un aviso
quiero que quedarme con el puntero encima detenga la cuenta atrás
para tener tiempo de leerlo sin que se cierre solo a mitad de lectura.

**Dado** un aviso con su cuenta atrás en marcha
**Cuando** paso el puntero por encima
**Entonces** la cuenta atrás se detiene donde estaba
**Y**, al retirar el puntero, se reanuda con el tiempo que le quedaba, no con el total.

Código: `ToastState` (`pause`, `resume`, `remaining`), `ToastWindow.ToastCard` (`hovered`, `onPointerEvent`)
Prueba: `ToastStateTest` (`el puntero encima pausa el descarte y al salir sigue con lo que quedaba`)

## Sonidos

### H-60 · Los avisos que llegan solos callan si la ventana ya tiene el foco

Como usuario que está mirando la tabla
quiero que un aviso que llega solo no suene si ya lo veo en pantalla
para no sobresaltarme con un sonido que no aporta nada nuevo.

**Dado** la ventana enfocada
**Cuando** llega un aviso de actualización
**Entonces** no suena nada.

Código: `Sounds.play` (`Sound.silenciadoPorFoco`)
Prueba: `SoundsTest` («no reproduce si la ventana tiene el foco»)

### H-61 · Los avisos suenan si la ventana no tiene el foco

Como usuario que no está mirando la ventana
quiero enterarme por sonido de que algo pasó
para no depender de tener la tabla siempre a la vista.

**Dado** la ventana sin el foco y los sonidos activados
**Cuando** llega un aviso de actualización
**Entonces** suena.

Código: `Sounds.play`
Prueba: `SoundsTest` («reproduce si no hay foco y esta activado»)

### H-62 · Las confirmaciones suenan aunque la ventana tenga el foco

Como usuario que acaba de pulsar un botón
quiero oír la confirmación de que mi acción surtió efecto
para saber que se aplicó, sin depender de mirar la pantalla.

**Dado** la ventana enfocada
**Cuando** hago algo que se confirma con sonido (guardar, activar el sondeo)
**Entonces** el sonido suena igualmente: la regla del foco no aplica a las confirmaciones.

Código: `Sounds.play` (`Sound.SUCCESS`, `Sound.TOGGLE`, ambos con `silenciadoPorFoco = false`)
Prueba: `SoundsTest` («las confirmaciones suenan aunque la ventana tenga el foco»)

### H-63 · Si los sonidos están desactivados, nada suena

Como usuario en un entorno silencioso
quiero poder apagar todos los sonidos
para trabajar sin avisos audibles.

**Dado** los sonidos desactivados en ajustes
**Cuando** ocurre cualquier evento que normalmente sonaría
**Entonces** no suena nada.

Código: `Sounds.play` (`enabled`)
Prueba: `SoundsTest` («no reproduce si el sonido esta desactivado»)

### H-64 · Un aviso reinicia el sonido en curso en vez de solaparse

Como usuario que recibe dos avisos casi seguidos
quiero que el segundo sonido no se mezcle con el primero
para oír cada aviso con claridad, uno detrás de otro y no encima.

**Dado** un sonido reproduciéndose
**Cuando** llega otro aviso que debe sonar
**Entonces** el clip se detiene, vuelve al principio y arranca de nuevo, en vez de encolarse o solaparse.

Código: `Sounds.play` (`clip.stop()`, `clip.framePosition = 0`)
Prueba: `SoundsTest` («un segundo aviso reinicia el clip en vez de continuar donde iba»)

### H-65 · Si falla la carga o no hay dispositivo de audio, la aplicación sigue muda pero funcionando

Como usuario en una máquina sin altavoces, por escritorio remoto o en integración continua
quiero que la falta de audio no tumbe la aplicación
para poder seguir usándola aunque no pueda oír los avisos.

**Dado** un entorno sin línea de audio disponible
**Cuando** se intenta precargar y reproducir los cuatro sonidos
**Entonces** ninguna llamada lanza una excepción; el subsistema de sonido queda simplemente mudo.

Código: `Sounds.load`, `Sounds.play`
Prueba: `SoundsTest` («reproducir sin dispositivo de audio no lanza»)

### H-66 · Activar o detener el sondeo confirma con un sonido

Como usuario
quiero una confirmación audible al cambiar el interruptor de sondeo
para saber que el cambio se aplicó, sin tener que mirar el indicador.

**Dado** cualquier estado del sondeo
**Cuando** lo activo o lo detengo
**Entonces** suena el sonido de conmutación.

Código: `ImagesViewModel.togglePolling`
Prueba: `ImagesViewModelTest` («activar o parar el sondeo reproduce el sonido de conmutacion»)

### H-67 · Eliminar una imagen confirma con un sonido de éxito

Como usuario
quiero una confirmación audible al eliminar una imagen
para saber que la acción se realizó.

**Dado** una imagen vigilada
**Cuando** la elimino
**Entonces** suena el sonido de éxito.

Código: `ImagesViewModel.removeImage`
Prueba: `ImagesViewModelTest` («eliminar una imagen reproduce el sonido de exito»)

### H-68 · Agregar una imagen válida confirma con un sonido de éxito

Como usuario
quiero una confirmación audible al agregar una imagen correctamente
para saber que se registró.

**Dado** un nombre válido y no repetido
**Cuando** lo agrego
**Entonces** suena el sonido de éxito.

Código: `ImagesViewModel.addImage`
Prueba: `ImagesViewModelTest` («agregar una imagen valida reproduce el sonido de exito»)

### H-69 · Reconocer y guardar los ajustes también confirman con un sonido de éxito

Como usuario
quiero la misma confirmación audible al reconocer una imagen o guardar mis ajustes
para que toda acción que persiste una decisión mía se confirme igual.

**Dado** cualquiera de estas acciones
**Cuando** la completo con éxito (reconocer una imagen, reconocer todas, guardar ajustes)
**Entonces** suena el sonido de éxito.

Código: `ImagesViewModel.acknowledge`, `ImagesViewModel.acknowledgeAll`, `Wiring.applyConfig`
Prueba: `ImagesViewModelTest` («reconocer una imagen reproduce el sonido de exito», «reconocer todas reproduce el sonido de exito»); la rama de `Wiring.applyConfig` (guardar ajustes) queda fuera — depende de la familia `SettingsViewModel.save`/botón «Guardar» que reescribe la Tarea 10 (fase 4 del rediseño la retira)

### H-70 · Una caída general no repite el sonido de error en ciclos sucesivos

Como usuario
quiero que el aviso sonoro de «el origen no responde» suene solo al principio de la caída
para no acabar apagando todos los sonidos por la repetición.

**Dado** que todas las imágenes vigiladas fallan en un ciclo, y el sonido de error ya sonó
**Cuando** ocurre otro ciclo y siguen fallando las mismas
**Entonces** el sonido de error no vuelve a sonar.

Código: `ImagesViewModel.onSnapshot` (`antesFallabaTodo`)
Prueba: `ImagesViewModelTest` («si fallan todas y siguen siendo las mismas imagenes, el error no repite en el segundo ciclo»)

### H-71 · El sonido de error no repite aunque cambien las imágenes vigiladas durante la caída

Como usuario
quiero que agregar o quitar una imagen mientras el origen está caído no reactive el sonido de error
para que la misma caída, nunca interrumpida, no suene dos veces.

**Dado** una caída general ya avisada por sonido
**Cuando**, sin que la caída se interrumpa, elimino una imagen y agrego otra que también falla
**Entonces** el sonido de error no vuelve a sonar; sigue habiendo sonado una sola vez.

Código: `ImagesViewModel.derive` (`consultadas`), `ImagesViewModel.onSnapshot`
Prueba: `ImagesViewModelTest` («una caida continua suena una sola vez aunque cambien las imagenes vigiladas», «dar de alta una imagen durante una caida no retrae el aviso ni repite el sonido», cubre el sonido; el aviso visual de esa misma prueba se cita en H-32)

### H-89 · Desactivar los avisos en pantalla no silencia su sonido

Como usuario que apaga los toasts pero no los sonidos
quiero seguir oyendo el aviso de una versión nueva aunque no se pinte en pantalla
para enterarme igual, con la ventana cerrada, sin depender de una ventana de avisos que decidí no ver.

**Dado** los toasts desactivados en ajustes y los sonidos activados
**Cuando** una imagen pasa a pendiente
**Entonces** el sonido de actualización suena igualmente; solo el aviso en pantalla se omite.

Código: `ToastNotificationPort.notifyUpdates` (`sounds.play(Sound.UPDATE)` corre antes de comprobar `toastsEnabled`)
Prueba: `ToastNotificationPortTest` («con los toasts desactivados el sonido de actualizacion suena igual»)

## Ventana y bandeja

### H-72 · Cerrar la ventana no cierra la aplicación

Como usuario que quiere quitar la ventana de en medio sin dejar de vigilar
quiero que cerrarla la oculte, no que termine el proceso
para que el sondeo siga corriendo en segundo plano.

**Dado** la ventana principal abierta
**Cuando** la cierro
**Entonces** la ventana se oculta, pero la aplicación sigue viva en la bandeja, vigilando.

Código: `Main.kt` (`onCloseRequest` de la `Window`, sin `exitApplication()`)
Prueba: verificación manual — ver «Historias de verificación manual»

### H-73 · Salir desde la bandeja desengancha el view model del sondeo

Como aplicación que se está cerrando de verdad
quiero desuscribir la interfaz del sondeo antes de terminar
para no dejar un oyente registrado sobre un servicio que va a desaparecer.

**Dado** la aplicación en marcha
**Cuando** elijo «Salir» en el menú de la bandeja
**Entonces** el view model se desengancha del servicio de sondeo antes de cerrar.

Código: `ImagesViewModel.close`, `Main.kt` (ítem «Salir» de la bandeja)
Prueba: `ImagesViewModelTest` («cerrar el view model lo desengancha del servicio»)

### H-74 · El icono y el texto de la bandeja avisan cuando el origen no responde

Como usuario con la ventana cerrada
quiero notar la caída del origen sin tener que abrir la ventana
para enterarme aunque esté trabajando en otra cosa.

**Dado** todas las imágenes vigiladas fallando
**Entonces** el icono de la bandeja cambia al de alerta y su texto dice «ImageWatch — sin conexión».

Código: `Main.kt` (`Tray`, `icon`/`tooltip` según `state.allFailing`)
Prueba: verificación manual — ver «Historias de verificación manual» (la decisión `state.allFailing` sí está probada por `ImagesViewModelTest`; lo que falta es que el `Tray` la pinte)

### H-75 · «Abrir» en la bandeja, o su icono, traen la ventana al frente

Como usuario con la ventana minimizada o detrás de otra aplicación
quiero que el icono de la bandeja o su «Abrir» la traigan al frente
para no tener que buscarla yo mismo en la barra de tareas.

**Dado** la ventana oculta, minimizada o detrás de otra aplicación
**Cuando** pulso el icono de la bandeja, o elijo «Abrir» en su menú
**Entonces** la ventana se muestra, pide el foco y queda por delante.
**Y** «Ver» en un aviso no dispara este mecanismo: solo hace visible la ventana y resalta la fila (H-55), sin traerla al frente si ya estaba detrás de otra aplicación.

Código: `Main.kt` (`traerAlFrente`, `Tray.onAction`, ítem «Abrir», `window.toFront()`, `window.requestFocus()`)
Prueba: verificación manual — ver «Historias de verificación manual» (comportamiento pendiente de cambio en la Tarea 13)

### H-76 · Cerrar la ventana limpia el resaltado pendiente

Como usuario que cierra la ventana después de mirar una fila resaltada
quiero que ese resaltado no reaparezca la próxima vez que abra la ventana
para que lo que veo al reabrir no dependa de lo que pasó la última vez que la cerré.

**Dado** una fila resaltada por un aviso
**Cuando** cierro la ventana
**Entonces** el resaltado se limpia, sin relación con lo que la vuelva a abrir después.

Código: `Main.kt` (`onCloseRequest` → `viewModel.clearHighlight()`)
Prueba: verificación manual — ver «Historias de verificación manual»

### H-77 · Cerrar la ventana marca que perdió el foco

Como aplicación que decide si un aviso debe sonar según el foco de la ventana
quiero que cerrarla se registre como «sin foco»
para que los sonidos no se queden mudos para siempre solo porque la ventana ya no existe para actualizar esa marca.

**Dado** la ventana enfocada
**Cuando** la cierro
**Entonces** el estado de foco pasa a falso.

Código: `Main.kt` (`onCloseRequest` → `wiring.windowFocused.value = false`)
Prueba: verificación manual — ver «Historias de verificación manual»

### H-78 · La ventana no tiene decoración del sistema; una barra propia permite cerrarla

Como usuario
quiero una ventana con estilo propio de la aplicación
para tener una experiencia consistente, incluida la posibilidad de arrastrarla y cerrarla desde su propia barra.

**Dado** la ventana principal
**Entonces** aparece sin la decoración del sistema operativo, con su propia barra de título, desde la que también se puede cerrar.

Código: `Main.kt` (`Window(undecorated = true, ...)`, `TitleBar`)
Prueba: verificación manual — ver «Historias de verificación manual»

## Configuración

### H-79 · Los ajustes arrancan con los valores vigentes

Como usuario que abre la pantalla de ajustes
quiero ver los valores que ya están aplicados
para saber qué voy a cambiar antes de tocar nada.

**Dado** una configuración vigente concreta
**Cuando** abro el formulario de ajustes
**Entonces** cada campo muestra el valor que ya estaba aplicado (URL, intervalo, tema, etc.).

Código: `SettingsViewModel` (constructor)
Prueba: `SettingsViewModelTest` («arranca con los valores vigentes»)

### H-80 · Guardar los ajustes entrega la configuración editada a quien la aplica

Como usuario que edita varios campos
quiero que guardar aplique todos los cambios de una vez
para no tener que guardar campo a campo.

**Dado** varios campos editados en el formulario
**Cuando** pulso guardar
**Entonces** se entrega la configuración completa con todos los cambios
**Y**, si no hay error, queda registrada la marca de guardado.

Código: `SettingsViewModel.save`
Prueba: `SettingsViewModelTest` («entrega al aplicador la configuracion editada»)

### H-81 · Un intervalo no numérico se rechaza sin llegar a aplicarse

Como usuario que teclea algo que no es un número en el intervalo
quiero que se rechace antes de intentar aplicarlo
para no dejar a la aplicación en un estado a medio configurar.

**Dado** el campo de intervalo con un valor no numérico, como «cada rato»
**Cuando** guardo
**Entonces** aparece un mensaje de error
**Y** quien aplica la configuración nunca llega a ser invocado.

Código: `SettingsViewModel.save`
Prueba: `SettingsViewModelTest` («un intervalo no numerico se rechaza sin llegar al aplicador»)

### H-82 · Una duración de aviso no numérica o no positiva se rechaza

Como usuario que teclea un valor inválido para cuánto dura un aviso en pantalla
quiero que también se rechace, igual que el intervalo
para no acabar con avisos que duran cero segundos o que ni siquiera son un número.

**Dado** el campo de duración del aviso con un valor no numérico, cero o negativo
**Cuando** guardo
**Entonces** aparece un mensaje de error y no se aplica la configuración.

Código: `SettingsViewModel.save`
Prueba: pendiente: Tarea 10 (reescribe `SettingsViewModel` para que la configuración se aplique sin botón «Guardar»; una prueba de `save()` hoy nacería muerta)

### H-83 · El mensaje de quien aplica los ajustes se muestra tal cual

Como usuario
quiero ver el motivo exacto por el que un ajuste no se pudo guardar
para saber qué corregir, aunque la regla viva en el núcleo y no en el formulario.

**Dado** un ajuste que el núcleo rechaza (por ejemplo, una URL sin HTTPS)
**Cuando** guardo
**Entonces** el formulario muestra el mismo mensaje que devolvió quien lo aplica, sin reescribirlo
**Y** la marca de guardado no se actualiza.

Código: `SettingsViewModel.save`
Prueba: `SettingsViewModelTest` («el mensaje del aplicador se muestra tal cual»)

### H-84 · Editar un campo limpia el error y la marca de guardado anteriores

Como usuario que corrige un valor tras un error
quiero que el mensaje de error desaparezca en cuanto empiezo a corregir
para no seguir viendo un aviso sobre un valor que ya cambié.

**Dado** un error visible tras un guardado fallido, o la marca de «Guardado» de uno anterior
**Cuando** edito cualquier campo del formulario
**Entonces** tanto el error como la marca de guardado desaparecen.

Código: `SettingsViewModel.edit`
Prueba: pendiente: Tarea 10 (misma familia que H-82: depende de `SettingsViewModel.save`/botón «Guardar», que esa tarea reescribe)

### H-85 · El intervalo y el interruptor de sondeo viven en Ajustes, no en la lista de imágenes

Como usuario
quiero configurar cada cuánto se comprueba y si el sondeo corre desde la pantalla de ajustes
para que la cabecera de la lista solo tenga acciones sobre las imágenes, no sobre el calendario.

**Dado** la pantalla de ajustes
**Entonces** ahí están el campo de intervalo y el interruptor de sondeo; en la cabecera de la lista solo queda el indicador de si está activo o detenido.

Código: `SettingsViewModel.onIntervalChange`, `SettingsScreen` (interruptor), `ImagesScreen.Header` (solo el indicador)
Prueba: `SettingsViewModelTest` («entrega al aplicador la configuracion editada», cubre el intervalo; la ubicación del interruptor en `SettingsScreen` no tiene prueba propia)

### H-86 · Guardar los ajustes persiste la configuración para el próximo arranque

Como usuario
quiero que mis ajustes sobrevivan a cerrar y volver a abrir la aplicación
para no tener que reconfigurarla cada vez.

**Dado** una configuración guardada
**Cuando** se vuelve a leer desde el fichero, incluso desde una instancia nueva
**Entonces** los valores leídos son los que se guardaron, no los de la semilla original.

Código: `SettingsViewModel.save`, `Wiring.applyConfig`, `JsonConfigStore.save`
Prueba: `JsonConfigStoreTest` (`el_fichero_manda_sobre_la_semilla`, cubre la capa de persistencia; el tramo `SettingsViewModel` → `Wiring.applyConfig` no tiene prueba propia)

### H-87 · La ruta del fichero de estado no se persiste; llega siempre de la semilla de arranque

Como aplicación
quiero que la ubicación de `images.json` no dependa de lo último que se guardó en `config.json`
para poder recolocar el fichero de estado sin que la configuración persistida lo contradiga.

**Dado** un `config.json` ya existente
**Cuando** se arranca con una semilla que apunta a otra ruta de estado
**Entonces** la ruta de estado leída es la de la semilla nueva, no la que se guardó antes.

Código: `JsonConfigStore.load`
Prueba: `JsonConfigStoreTest` (`la_ruta_del_estado_no_se_persiste_y_llega_desde_la_semilla`)

### H-88 · El primer arranque siembra `config.json` con los valores por defecto

Como usuario que ejecuta la aplicación por primera vez
quiero que se cree un fichero de configuración con los valores iniciales
para tener algo editable desde el primer momento, sin tener que crearlo a mano.

**Dado** que `config.json` no existe todavía
**Cuando** arranca la aplicación
**Entonces** el fichero se crea con los valores de la semilla (intervalo, imágenes, etc.).

Código: `JsonConfigStore.load`
Prueba: `JsonConfigStoreTest` (`siembra_el_fichero_en_el_primer_arranque`)

## Historias sin prueba

Tras la Tarea 3 no queda ninguna con `Prueba: —` sin más: cada una de las 18 que llegaron así a
esta tarea terminó en una prueba, en la lista de verificación manual de abajo, o pendiente de una
tarea concreta de esta misma fase.

- **H-23**, **H-24** — cubiertas ahora, en `PollingControllerTest`.
- **H-55** — cubierta ahora en su mitad de `ImagesViewModel`; la mitad de `Main.kt`/`ImagesScreen` es verificación manual (ver abajo).
- **H-64** — cubierta ahora, en `SoundsTest`.
- **H-69** — cubierta ahora en su mitad de `ImagesViewModel`; la rama de `Wiring.applyConfig` queda para la Tarea 10 (misma familia que H-82/H-84).
- **H-89** — cubierta ahora, en `ToastNotificationPortTest`.
- **H-58, H-59** — cubiertas ahora, en `ToastStateTest`: la Tarea 4 subió el reloj de descarte y pausa de `ToastWindow.ToastCard` a `ToastState`, que es donde se prueban sin depender de Compose.
- **H-38, H-56, H-57** — cubiertas ahora, en `ImagesViewModelTest`: la Tarea 5 subió a `ImagesViewModel` lo que antes vivía en `ImageRow`/`ImagesScreen` -si se puede reconocer, y cuándo se apagan el resaltado y el latido-, así que se prueban junto al resto del estado de intención, sin Compose.
- **H-82, H-84** — pendientes: Tarea 10. Dependen de `SettingsViewModel.save()` y del botón «Guardar», que esa tarea reescribe para que la configuración se aplique sin botón; una prueba de `save()` hoy nacería muerta.
- **H-72, H-74, H-75, H-76, H-77, H-78** — pasan a «Historias de verificación manual» (ver abajo): viven enteras dentro de `application { }` en `Main.kt`, y el proyecto no tiene infraestructura de test de interfaz de Compose.

## Historias de verificación manual

Comportamiento de cableado de ventana y bandeja que vive dentro de `application { }` en
`Main.kt`. Probarlo exigiría infraestructura de test de interfaz de Compose que el proyecto no
tiene, y montarla para seis historias de cableado costaría más de lo que protege. Se verifican a
mano con la aplicación levantada, y así se hará en cada fase que las toque.

- **H-72** — Cerrar la ventana con la X de la barra propia; el proceso sigue vivo y el icono permanece en la bandeja.
- **H-74** — Con todas las imágenes vigiladas fallando, el icono de la bandeja cambia al de alerta y su tooltip dice «ImageWatch — sin conexión». (La decisión `state.allFailing` sí está probada por `ImagesViewModelTest`; lo que no se prueba es que el `Tray` la pinte.)
- **H-75** — Con la ventana minimizada o detrás de otra aplicación, pulsar el icono de la bandeja o «Abrir» en su menú; la ventana se muestra, pide el foco y queda por delante. Verificación manual; comportamiento pendiente de cambio en la Tarea 13 (que arregla que «Ver» tampoco trae la ventana al frente).
- **H-76** — Resaltar una fila desde un aviso y cerrar la ventana; al reabrirla, ninguna fila queda resaltada.
- **H-77** — Con la ventana enfocada, cerrarla; el siguiente aviso que llegue sin la ventana abierta debe sonar (si no sonara, `windowFocused` se quedó en `true`).
- **H-78** — Abrir la ventana principal; no debe tener barra de título ni bordes del sistema operativo, solo la barra propia, y arrastrarla y cerrarla desde ahí debe funcionar.

## Casos de uso sin cubrir

Comportamiento que la aplicación no define hoy y que habría que decidir. Cada uno con lo que
ocurre ahora y por qué es dudoso. No se implementa nada aquí.

### ¿Qué ve el usuario si borra todas las imágenes vigiladas?

Hoy la tabla queda simplemente vacía: `ImagesViewModel.derive` produce `rows = emptyList()` y
`total = 0`, y `ImagesScreen` no tiene ningún estado especial para «cero imágenes». Es dudoso
porque la guía de estilo (`2026-09-05-imagewatch-guia-de-estilo.md`, §5.3) ya señala esto como algo
que falta: un usuario nuevo que abre la aplicación por primera vez —o alguien que borró todo por
error— no tiene ninguna pista de qué hacer a continuación.

### ¿Qué ocurre si `images.json`, `tracked-images.json` o `config.json` están corruptos o vacíos?

No hay ningún test que lo cubra, y el código de lectura de esos tres ficheros no estaba en la
lista de la tarea 1, así que no se puede afirmar con certeza qué excepción concreta se propaga.
Lo que sí se sabe por `Wiring` (`Main.kt`) es que no hay una pantalla de error dedicada: un fallo
de carga en el arranque no tiene un camino de recuperación visible más allá de lo que el `check {}`
de `Wiring.init` deje pasar como excepción no capturada, que terminaría el proceso sin explicación
para quien no mira el registro. Es dudoso porque tres ficheros JSON editables a mano son tres
puntos de fallo con el mismo problema y ninguno tiene un mensaje pensado para el usuario.

### ¿Qué pasa si el usuario da de alta dos veces el mismo nombre con distinta caja (`Alpha` y `alpha`)?

`ImagesViewModel.saveName` compara con `current.any { it == value && it != editing }`, una
igualdad de `String` sensible a mayúsculas: hoy `Alpha` y `alpha` se aceptan como dos imágenes
distintas. Es dudoso porque muchos registros de contenedores tratan los nombres como
insensibles a mayúsculas, así que dos entradas así probablemente apunten a la misma imagen real,
duplicando sondeos, avisos y sonidos por algo que el usuario no ve como dos cosas distintas.

### ¿Qué ocurre con los avisos en cola si se borra la imagen que los produjo?

`ImagesViewModel.removeImage` retira el nombre de `TrackedImageStore` y recalcula la tabla, pero
nunca llama a `dismissToastsFor`: los avisos ya generados para esa imagen —incluidos los que
todavía esperaban turno en la cola— se quedan en `ToastState` y acabarán mostrándose o
descartándose por su propio temporizador. Es dudoso porque el aviso invita a «Ver» una fila que
al pulsarlo ya no existe: `ImagesScreen` resalta por nombre y, si no lo encuentra en `rows`, no
falla pero tampoco desplaza a ningún sitio (el propio código lo señala: el `LaunchedEffect` que
hace scroll comenta que si la fila «se borró, por ejemplo», no hay adónde desplazarse), así que el
usuario pulsa «Ver» y no pasa nada visible.

### ¿Qué debería pasar al cambiar el origen mientras hay imágenes pendientes de reconocer?

`Wiring.applyConfig` reemplaza el `ImageSource` (`source.swap(it)`) sin tocar ni el estado
reconocido (`ImageStateStore`) ni el snapshot vigente: las imágenes que estaban `PENDING` contra el
origen antiguo lo siguen estando contra el nuevo hasta el próximo ciclo, comparando una versión
remota que ahora viene de un sitio distinto. Es dudoso porque un cambio de origen —de simulación a
real, o de una URL a otra— puede significar que se está empezando a vigilar un registro con un
esquema de versiones completamente distinto, y hoy no hay ningún aviso de que las pendientes
«heredadas» puedan no significar lo mismo que antes.

### ¿Puede un usuario perder un aviso sin haberlo descartado ni reconocido?

Surgió al escribir H-52 y H-58: la cola de `ToastState` no tiene límite (`TOASTS_VISIBLES` solo
limita cuántos se **pintan**, no cuántos se guardan), así que en teoría ningún aviso se pierde por
acumulación. Pero el temporizador de descarte automático de `ToastWindow.ToastCard` corre por
tarjeta pintada, no por aviso en cola: si la ventana de toasts nunca llega a mostrar una tarjeta
concreta —por ejemplo, si el proceso termina antes de que le toque turno—, ese aviso desaparece con
el proceso sin haber llegado a descartarse explícitamente ni a persistir en ningún sitio. Es dudoso
porque hoy no hay ninguna imagen pendiente sin aviso mostrado que sobreviva a un reinicio salvo por
su insignia en la tabla, que es donde el usuario tendría que fijarse en su lugar.

### ¿«Ver» en un aviso debería traer la ventana al frente, como ya hacen «Abrir» y el icono de la bandeja?

Surgió al escribir H-55 y H-75, y lo confirmó la revisión de este documento: `Main.kt` resuelve
`onView` (el callback que recibe `ToastLayer` cuando se pulsa «Ver») así:

```kotlin
onView = { name ->
    windowVisible = true
    name?.let(viewModel::highlight)
}
```

Nunca incrementa `traerAlFrente`, que es lo único que dispara `window.toFront()` y
`window.requestFocus()` — eso solo lo hacen el `onAction` del icono de la bandeja y el ítem
«Abrir» de su menú. Si la ventana ya está visible pero simplemente detrás de otra aplicación,
pulsar «Ver» no hace nada para traerla al frente: solo resalta la fila. El propio código apunta a
que esto no fue deliberado: el comentario junto a la declaración de `traerAlFrente` dice
literalmente que existe porque, sin él, «"Ver" no traia nada» — nombrando «Ver» como uno de los
casos que se propuso arreglar—, pero `onView` hoy no lo incrementa. Mientras el código sea el que
es, un usuario con la ventana detrás de otra aplicación puede pulsar «Ver» y no notar que ha
pasado nada. Es dudoso porque no está claro si esto es un cabo suelto del cableado —y «Ver»
debería incrementar `traerAlFrente` igual que «Abrir»— o si se dejó así a propósito para no robar
el foco de golpe mientras el usuario trabaja en otra ventana.
