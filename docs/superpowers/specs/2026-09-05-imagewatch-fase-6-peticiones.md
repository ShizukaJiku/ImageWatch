# ImageWatch — Peticiones para la fase 6

> Recogidas el **2026-09-05** durante la verificación visual de la fase 5, con la aplicación
> delante. Están en las palabras del usuario y con lo que hoy hace el código, para que quien las
> implemente no tenga que reconstruir el contexto.

## Estado del que se parte

La fase 5 está terminada en `feature/fase-5-ajustes`, con el **PR #2** abierto y sin fusionar.
La revisión visual con el usuario ya ocurrió: de ella salieron correcciones que **ya están hechas**
y las peticiones de este documento, que **no**.

Lo verificado y correcto: los toasts y su animación de entrada, la pausa al pasar el puntero, el
resaltado desde «Ver», el pulso de las filas sin verificar, la insignia de estado, la píldora de
versión, los tres niveles de error, el tema claro y oscuro, la persistencia de la configuración y
el instalador.

## 1. Reconocer una imagen debe retirar su aviso

**Hoy:** los toasts viven en `ToastState`, que no sabe nada de lo que el usuario hace en la
ventana. Si un aviso está en pantalla y el usuario marca esa imagen como vista —en la fila o con
el botón de todas—, el aviso se queda hasta que se le acabe el tiempo.

**Se pide:** que reconocer una imagen retire su aviso, tanto en el reconocimiento individual como
en el de todas. El aviso ya no describe nada cierto.

**Por dónde:** `ImagesViewModel.acknowledge` y `acknowledgeAll` no conocen `ToastState`; hoy solo
`Wiring` y `ToastNotificationPort` lo tocan. Hace falta una vía de vuelta —descartar por nombre de
imagen— sin que el view model de la lista acabe dependiendo de la capa de avisos: lo natural es que
`ToastState` gane un `dismissFor(name: String)` y que el cableado se lo pase al view model como
una lambda, igual que ya se le pasa el aplicador del intervalo.

## 2. La configuración del sondeo se va a Ajustes

**Hoy:** la cabecera de la pantalla de imágenes lleva el campo «Intervalo (s)», el botón
«Aplicar» y el botón «Detener/Iniciar», además del indicador «● Activo».

**Se pide:** que esos controles vivan en la pantalla de ajustes, no en la principal. La pantalla
principal es para las imágenes.

**A decidir al implementarlo:** si el indicador de si el sondeo corre se queda en la cabecera
—como información, no como control— o desaparece con lo demás. El diseño pide que «el indicador de
actividad pulse mientras el sondeo corre», así que algo debería quedar.

**Ojo:** el intervalo ya pasa por `Wiring.applyPollInterval`, que persiste. Al mover el control, ese
camino queda con un solo llamante y puede simplificarse; el mensaje de validación tiene que seguir
llegando a la pantalla.

## 3. El icono de «marcar como vista» debe ser un check

**Hoy:** la fila usa `AppSvg.PLUS` para reconocer, que no dice nada de lo que hace.

**Se pide:** un check en la fila, y el doble check para «todas» —que ya está puesto así en la
cabecera—. Los iconos `check-all.svg` y `minus.svg` ya existen en `resources/icons/`; falta el
check simple.

## 4. Una versión nueva sobre otra pendiente

**Hoy:** `VersionPollingService.notifyTransitions` avisa solo de las imágenes que **acaban de**
pasar a pendientes. Una imagen que ya estaba pendiente y recibe otra versión más nueva no vuelve a
avisar. Se hizo así para no repetir el mismo aviso en cada ciclo, y eso sigue siendo correcto.

**Se pide distinguir dos casos:**

- **Con la ventana delante:** que la fila lo señale con una animación, **sin sonido**. El usuario
  lo está viendo; el sonido sobra.
- **Con la aplicación en la bandeja:** que sí llegue el aviso, como cualquier otra versión nueva.

**Lo que esto implica:** la condición de aviso deja de ser «pasó a pendiente» y pasa a ser «la
versión remota cambió respecto a la del ciclo anterior», que es un dato que hoy no se compara.
Cuidado con no reintroducir el aviso por ciclo que el diseño evita: la clave es comparar la versión
remota anterior con la nueva, no el estado.

## 5. Guía de estilo de la interfaz — pendiente de escribir

El usuario la quiere **como propuesta de a dónde ir**, no como inventario de lo que ya hay. Luego
se apoyará en la herramienta de diseño. Sus palabras: «ahora está bien, pero siento que falta
bastante a mejorar a nivel de UI/UX».

Con una condición de arquitectura que atraviesa todo lo demás:

> «La arquitectura debe ser de forma que la lógica de UX esté desacoplada de cómo se ven los
> componentes, de forma que podamos modificar temas de animaciones, formas, etc. sin modificar el
> comportamiento de la app.»

**Dónde no se cumple hoy**, para que la guía tenga de dónde tirar:

- Los composables ya reciben estado y lambdas, nunca el view model. Eso sí está bien y se conserva.
- Pero los valores de apariencia están repartidos: `dp` y `sp` en línea por toda la pantalla,
  duraciones de animación como constantes privadas de cada fichero (`SLIDE_MILLIS` en `VersionPill`,
  `PULSE_MILLIS` en `ImageRow`, `EXIT_MILLIS` en `ToastWindow`), anchos fijos en `ImagesScreen`.
  Cambiar el ritmo de las animaciones exige tocar cinco ficheros.
- El color sí vive en un solo sitio, `Colors.kt`, y ese es el patrón a extender a espaciado, radios,
  tipografía y tiempos.

## Lo que queda del trabajo anterior

- **PR #2 sin fusionar.** Todo esto se construye encima.
- Deuda ya anotada en la revisión final: el estado de `ImagesViewModel` debería vivir en
  `mutableState.update { }` en lugar de en campos `@Volatile` paralelos; `imageNames` está duplicado
  entre `config.json` y `tracked-images.json`; `Wiring` es privado y `applyConfig` no tiene
  cobertura.
- **detekt** sigue descartado: la última versión estable no parsea la versión del JDK del proyecto.
