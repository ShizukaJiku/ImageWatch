# ImageWatch — Guía de estilo de la interfaz

> Escrita el **2026-09-05**, después de la verificación visual de la fase 5. Es una **propuesta de
> a dónde ir**, no un inventario de lo que ya hay: recoge lo que el usuario resumió como «ahora
> está bien, pero siento que falta bastante a mejorar a nivel de UI/UX». Sobre ella se apoyará
> después la herramienta de diseño; lo que aquí se fija es qué decisiones son de diseño y por
> dónde entran en el código sin tocar el comportamiento.

## 0. La regla que gobierna todo lo demás

En palabras del usuario:

> «La arquitectura debe ser de forma que la lógica de UX esté desacoplada de cómo se ven los
> componentes, de forma que podamos modificar temas de animaciones, formas, etc. sin modificar el
> comportamiento de la app.»

Traducido a una regla operativa: **cambiar la apariencia no debe abrir un fichero de lógica, y
cambiar la lógica no debe obligar a redecidir la apariencia.** Se comprueba con dos preguntas:

1. Para bajar todas las animaciones un 20 %, ¿cuántos ficheros hay que tocar? Hoy: cinco.
   Objetivo: **uno**.
2. Para cambiar qué hace un botón, ¿hay que mirar algún valor visual? Hoy: no. Eso ya está bien y
   se conserva.

Lo que ya se cumple y no se toca: ningún composable recibe un view model. Todos reciben estado ya
cocido (`ImagesUiState`, `SettingsUiState`) y lambdas. La frontera existe; lo que falta es que los
**valores** de apariencia vivan de un solo lado de ella.

## 1. Dónde no se cumple hoy

| Síntoma | Dónde | Qué obliga a hacer |
|---|---|---|
| `dp` y `sp` literales por toda la pantalla | `ImagesScreen`, `ImageRow`, `SettingsScreen`, `TitleBar` | Cambiar la densidad = repasar cinco ficheros a ojo |
| Duraciones como constante privada de cada fichero | `SLIDE_MILLIS` 320 (`VersionPill`), `PULSE_MILLIS` 700 y `BUMP_MILLIS` 600 (`ImageRow`), `TRANSITION_MILLIS` 400 (`StatusBadge`), `EXIT_MILLIS` 220 (`ToastWindow`) | No hay un sitio donde leer «el ritmo de la aplicación» |
| Anchos fijos que compensan el layout | `STATUS_WIDTH`, `TOAST_WIDTH` | Números elegidos para que no baile la fila, sin nombre que lo diga |
| Radios repetidos a mano | `RoundedCornerShape(999.dp)` en cuatro sitios, `6.dp` en el tooltip | La forma de «píldora» es una decisión de marca escrita cinco veces |
| Tiempos de UX mezclados con tiempos de animación | `HIGHLIGHT_DURATION_MILLIS` 4000, `BUMP_DURATION_MILLIS` 3000 | Son otra cosa —cuánto dura un aviso visual— y merecen su propio grupo |

El color **sí** está bien: vive entero en `Colors.kt` y se consume por `MaterialTheme.colorScheme`
y `statusColors(status, dark)`. **Ese es el patrón a extender**; no hay que inventar nada nuevo.

## 2. La propuesta: `ui/theme/Tokens.kt`

Un solo fichero hermano de `Colors.kt`, con cuatro objetos y ningún composable dentro. Sin
abstracciones de más: son constantes con nombre, no un sistema de temas paralelo al de Material.

```kotlin
/** Espaciado. La escala es de 4: todo lo que no esté en ella es un caso a justificar. */
object Space {
    val xs = 4.dp    // separación entre iconos de una misma fila
    val sm = 8.dp    // separación entre controles hermanos
    val md = 12.dp   // padding vertical de una fila de la tabla
    val lg = 16.dp   // separación entre bloques
    val xl = 20.dp   // margen lateral de las pantallas
}

/** Formas. La píldora es la forma de marca: versiones, insignias, botón de agregar, buscador. */
object Radius {
    val sm = 6.dp      // tooltips y superficies pequeñas
    val md = 10.dp     // tarjetas y toasts
    val pill = 999.dp  // píldoras
}

/** Tipografía. Cuatro tamaños, cada uno con un oficio. Un quinto tamaño es una decisión nueva. */
object TypeScale {
    val title = 16.sp    // título de pantalla
    val body = 13.sp     // nombre de imagen, etiquetas de formulario
    val meta = 12.sp     // subtítulos y contadores
    val caption = 11.sp  // detalle, registro, indicadores
}

/** Ritmo. Tres velocidades de animación y dos duraciones de aviso visual. */
object Motion {
    const val QUICK = 220     // entradas y salidas de toasts
    const val NORMAL = 320    // transiciones de contenido (píldora de versión)
    const val EMPHASIS = 400  // cambios de estado (insignia)
    const val PULSE = 700     // latido de "esto está en vuelo"
    const val BUMP = 600      // latido de "esto es nuevo"
}

/** Cuánto vive un aviso visual antes de apagarse solo. Es UX, no animación. */
object Dwell {
    const val HIGHLIGHT_MILLIS = 4000L
    const val BUMP_MILLIS = 3000L
}
```

Tres reglas de uso, y ninguna más:

- **Ningún `dp`, `sp` ni duración literal nuevo fuera de `Tokens.kt` y `Colors.kt`.** Tamaños de
  icono incluidos.
- Un valor que solo existe para que el layout no baile (`STATUS_WIDTH`, `TOAST_WIDTH`) se queda
  donde está, con su comentario: no es estilo, es una compensación de composición.
- Si un valor nuevo no encaja en la escala, la escala gana. Si de verdad no encaja, se añade a
  `Tokens.kt` y se justifica ahí, no en la pantalla.

**Por qué `object` y no `CompositionLocal`:** un `LocalSpacing` permitiría temas de densidad
distintos —compacto, cómodo— y hoy no hay ninguno. Añadirlo ahora es un `CompositionLocalProvider`
en cada raíz y una indirección en cada lectura, a cambio de nada. Cuando exista un segundo tema de
densidad, la migración es mecánica: `Space.md` → `LocalSpace.current.md`. No antes.

## 3. Reglas de movimiento

La animación de esta aplicación **dice algo**; no decora. Cada una responde a una pregunta del
usuario, y esa es la prueba para aceptar una nueva:

| Animación | Qué contesta |
|---|---|
| Latido de fila sin verificar | «¿Se está consultando ahora?» |
| Latido de fila con versión encima de otra pendiente | «¿Ha cambiado algo que ya estaba pendiente?» |
| Deslizamiento de la píldora de versión | «¿Qué número acaba de cambiar?» |
| Transición de la insignia | «¿A qué estado pasó?» |
| Entrada y salida del toast | «¿Esto acaba de llegar o se está yendo?» |
| Bajada de la barra de sin conexión | «¿Esto afecta a todo o a una imagen?» |

De ahí salen tres reglas:

1. **Una animación sin pregunta detrás no entra.** Un movimiento decorativo en una ventana que
   vive en la bandeja se convierte en ruido de fondo.
2. **Nada late para siempre.** El latido termina cuando termina lo que anunciaba
   (`Dwell.*_MILLIS` lo apaga), porque una tabla entera resaltada no señala nada.
3. **El movimiento nunca es el único canal.** El estado se lee también en la insignia y en el
   texto; la animación solo lo acelera. Esto es además lo que hace la interfaz utilizable con
   movimiento reducido el día que se respete esa preferencia del sistema.

## 4. Jerarquía de pantallas

- **La lista es para las imágenes.** Un control que no actúa sobre la lista no vive en su
  cabecera. La fase 6 ya movió el intervalo y el interruptor de sondeo a Ajustes; en la cabecera
  queda el indicador, que informa y no manda.
- **La cabecera admite acciones sobre el conjunto**: comprobar todas, reconocer todas, agregar,
  buscar, abrir ajustes. Nada más.
- **La fila admite acciones sobre esa imagen**: reconocer, comprobar, editar, borrar. Un botón que
  no haría nada no se dibuja: se reserva su hueco, para que los demás no se muevan.
- **Ajustes es un formulario y se guarda con su botón.** La única excepción es el interruptor de
  sondeo, que actúa al pulsarlo; por eso es un botón y no un `Switch`, para no prometer las mismas
  reglas que sus vecinos.

## 5. Lo que falta y no es cuestión de tokens

Ordenado por lo que más se nota:

1. **Teclado.** Hoy no hay recorrido de foco pensado ni atajos. Mínimos: `Esc` cierra diálogos y
   vuelve de Ajustes, `Enter` confirma el diálogo de nombre, `/` enfoca el buscador.
2. **Accesibilidad de los iconos.** `SvgIcon` pone `contentDescription = svg.name`, que anuncia
   «CHECK» en vez de «marcar como vista». El texto alternativo es del sitio donde se usa el icono,
   no del icono.
3. **Estado vacío.** Sin imágenes vigiladas la tabla queda en blanco. Un estado vacío que explique
   qué hacer es lo primero que ve un usuario nuevo.
4. **Densidad de la fila.** Cuatro botones, dos píldoras, insignia y detalle en 190 dp fijos. Con
   veinte imágenes la tabla se lee peor de lo que se leería con las acciones secundarias
   agrupadas.
5. **Contraste de las píldoras de estado en tema claro.** Las parejas de `statusColors` se
   eligieron a ojo; falta medirlas contra 4.5:1.
6. **Ventana sin decoración.** `TitleBar` es propia: arrastrar, maximizar y el doble clic en la
   barra tienen que comportarse como los del sistema, o la ventana se siente rara antes de que el
   usuario sepa decir por qué.

## 6. Migración, sin big bang

1. Crear `Tokens.kt` con los valores **actuales**, tal cual. Ningún cambio visible.
2. Reemplazar literales fichero a fichero, empezando por `ImageRow` y `ImagesScreen`, que son los
   que más tienen.
3. Recién entonces afinar la escala: ya se puede ver toda junta y decidir de una vez.
4. Con la escala fija, entrar en la lista del punto 5 por orden.

La comprobación de que funcionó es la pregunta del principio: bajar todas las animaciones un 20 %
debe ser un único fichero tocado.

## 7. Lo que queda para la herramienta de diseño

Esta guía fija **la estructura** —qué es token, qué es composición y qué es comportamiento— y deja
abierto **el gusto**: la paleta definitiva, la personalidad tipográfica, el peso de las sombras, el
tratamiento de la ventana sin decoración. Cuando eso se decida en la herramienta de diseño, aterriza
en `Colors.kt` y `Tokens.kt`, y en ningún otro sitio. Si aterrizara en otro sitio, es que esta guía
falló.
