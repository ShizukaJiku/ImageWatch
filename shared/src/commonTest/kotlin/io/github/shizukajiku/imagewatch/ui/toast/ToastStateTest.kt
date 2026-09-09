package io.github.shizukajiku.imagewatch.ui.toast

import io.github.shizukajiku.imagewatch.domain.ImageState
import io.github.shizukajiku.imagewatch.domain.ImageStatus
import io.github.shizukajiku.imagewatch.domain.Version
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private fun pending(name: String, from: String, to: String) = ImageState(
    name,
    Version(from),
    Version(to),
    "registry.local/$name",
    ImageStatus.PENDING,
    null,
    Instant.fromEpochSeconds(0),
)

private fun failed(name: String, message: String?) = ImageState(
    name,
    null,
    null,
    "registry.local/$name",
    ImageStatus.ERROR,
    message,
    Instant.fromEpochSeconds(0),
)

/**
 * Duración fija y muy holgada para las pruebas que no ejercitan el reloj: da igual el número
 * mientras sea mayor que lo que tarda la prueba en correr, porque un `CoroutineScope` real —no de
 * tiempo virtual— no la va a agotar en ese rato.
 */
private fun estadoSinReloj() = ToastState(CoroutineScope(Job()), { 60.seconds })

@OptIn(ExperimentalCoroutinesApi::class)
class ToastStateTest {

    @Test
    fun `una imagen produce un toast NUEVA sin nombrar la version anterior`() {
        val state = estadoSinReloj()

        state.show(listOf(pending("alpha", "1.0.0", "1.1.0")))

        val toast = state.toasts.value.single()
        assertEquals("alpha", toast.imageName)
        assertEquals(ToastKind.NUEVA, toast.kind)
        assertEquals("versión 1.1.0", toast.sub)
        assertFalse(toast.sub.contains("1.0.0"), "No nombra la version anterior (V-3)")
        assertEquals("registry.local/alpha", toast.meta)
        assertEquals("Visto", toast.action)
    }

    @Test
    fun `dos imagenes producen dos toasts`() {
        val state = estadoSinReloj()

        state.show(listOf(pending("alpha", "1.0.0", "1.1.0"), pending("beta", "2.0.0", "2.1.0")))

        assertEquals(2, state.toasts.value.size)
    }

    @Test
    fun `descartar quita solo el toast pedido`() {
        val state = estadoSinReloj()
        state.show(listOf(pending("alpha", "1.0.0", "1.1.0"), pending("beta", "2.0.0", "2.1.0")))
        val first = state.toasts.value.first()

        state.dismiss(first.id)

        assertEquals(1, state.toasts.value.size)
        assertEquals("beta", state.toasts.value.single().imageName)
    }

    @Test
    fun `mostrar en paralelo no pierde ningun toast`() {
        // show() corre en el hilo del planificador y dismiss() en el de Compose: un
        // leer-modificar-escribir no atomico sobre mutableToasts.value perderia escrituras aqui.
        val state = estadoSinReloj()
        val total = 50

        val threads = (1..total).map { i ->
            Thread { state.show(listOf(pending("img$i", "1.0.0", "1.1.0"))) }
        }
        threads.forEach(Thread::start)
        threads.forEach(Thread::join)

        // Con mas de 3 en cola, la lista publica recorta a 2 reales + RESUMEN.
        assertEquals(3, state.toasts.value.size)
        assertEquals(ToastKind.RESUMEN, state.toasts.value.last().kind)
    }

    @Test
    fun `descartar en paralelo no pierde ningun descarte`() {
        // Se queda en TOASTS_VISIBLES elementos -no en los 50 de antes de esta fase- porque a
        // partir de ahi la cola publica sintetiza un RESUMEN y sus ids reales dejan de ser
        // visibles desde aqui. Con exactamente TOASTS_VISIBLES no hay RESUMEN y los tres ids son
        // los reales: la carrera que este test vigila -leer-modificar-escribir no atomico sobre
        // mutableToasts.value- se ejercita igual con tres hilos que con cincuenta.
        val state = estadoSinReloj()
        val total = TOASTS_VISIBLES
        (1..total).forEach { i -> state.show(listOf(pending("img$i", "1.0.0", "1.1.0"))) }
        val ids = state.toasts.value.map { it.id }

        val threads = ids.map { id -> Thread { state.dismiss(id) } }
        threads.forEach(Thread::start)
        threads.forEach(Thread::join)

        assertEquals(0, state.toasts.value.size)
    }

    @Test
    fun `los que no caben en la ventana esperan turno en vez de perderse`() {
        // Se muestran los primeros TOASTS_VISIBLES reales; el resto sigue en la cola y entra en
        // cuanto se libera hueco. Ningun aviso se pierde por llegar en mal momento.
        val state = estadoSinReloj()

        repeat(10) { i -> state.show(listOf(pending("img$i", "1.0.0", "1.1.0"))) }

        assertEquals(
            listOf("img0", "img1"),
            state.toasts.value.take(2).map { it.imageName },
            "Se muestran los mas antiguos: son los que llevan mas tiempo esperando",
        )
        assertEquals(ToastKind.RESUMEN, state.toasts.value[2].kind)
    }

    @Test
    fun `descartar por imagen retira sus avisos y deja los demas`() {
        val state = estadoSinReloj()
        state.show(listOf(pending("alpha", "1.0.0", "1.1.0")))
        state.show(listOf(pending("beta", "1.0.0", "1.1.0")))
        // Dos de la misma imagen: una segunda version sobre una pendiente sin reconocer.
        state.show(listOf(pending("alpha", "1.1.0", "1.2.0")))

        state.dismissFor("alpha")

        assertEquals(listOf("beta"), state.toasts.value.map { it.imageName })
    }

    @Test
    fun `descartar por una imagen sin avisos no cambia nada`() {
        val state = estadoSinReloj()
        state.show(listOf(pending("alpha", "1.0.0", "1.1.0")))

        state.dismissFor("gamma")

        assertEquals(1, state.toasts.value.size)
    }

    @Test
    fun `showFailures produce un toast ERROR con Reintentar como accion`() {
        val state = estadoSinReloj()

        state.showFailures(listOf(failed("alpha", "HTTP 503")))

        val toast = state.toasts.value.single()
        assertEquals(ToastKind.ERROR, toast.kind)
        assertEquals("HTTP 503", toast.sub)
        assertEquals("Reintentar", toast.action)
        assertEquals("registry.local/alpha", toast.meta)
    }

    @Test
    fun `showFailures sin detalle usa un motivo por defecto`() {
        val state = estadoSinReloj()

        state.showFailures(listOf(failed("alpha", null)))

        assertEquals("sin detalle", state.toasts.value.single().sub)
    }

    @Test
    fun `con mas de tres en cola se sintetiza un RESUMEN con las dos primeras reales`() {
        val state = estadoSinReloj()

        repeat(5) { i -> state.show(listOf(pending("img$i", "1.0.0", "1.1.0"))) }

        val toasts = state.toasts.value
        assertEquals(3, toasts.size)
        assertEquals(listOf("img0", "img1"), toasts.take(2).map { it.imageName })
        val resumen = toasts[2]
        assertEquals(ToastKind.RESUMEN, resumen.kind)
        assertEquals("y 3 novedades más", resumen.title)
        assertEquals("Ver todas", resumen.action)
        assertNull(resumen.imageName)
        assertEquals(listOf("img2", "img3", "img4").joinToString(", "), resumen.meta)
    }

    @Test
    fun `al descartar uno de los reales el RESUMEN baja su cuenta y entra el siguiente`() {
        val state = estadoSinReloj()
        repeat(5) { i -> state.show(listOf(pending("img$i", "1.0.0", "1.1.0"))) }
        val primero = state.toasts.value.first().id

        state.dismiss(primero)

        val toasts = state.toasts.value
        assertEquals(listOf("img1", "img2"), toasts.take(2).map { it.imageName })
        assertEquals("y 2 novedades más", toasts[2].title)
    }

    @Test
    fun `con tres o menos en cola no hay RESUMEN`() {
        val state = estadoSinReloj()
        repeat(3) { i -> state.show(listOf(pending("img$i", "1.0.0", "1.1.0"))) }

        assertEquals(3, state.toasts.value.size)
        assertTrue(state.toasts.value.none { it.kind == ToastKind.RESUMEN })
    }

    @Test
    fun `el reloj de descarte solo corre para los dos reales visibles, no para el RESUMEN`() = runTest {
        val state = ToastState(backgroundScope, { 5.seconds }, { currentTime })
        repeat(5) { i -> state.show(listOf(pending("img$i", "1.0.0", "1.1.0"))) }

        advanceTimeBy(5_001)

        val toasts = state.toasts.value
        // Los dos reales visibles (img0, img1) expiraron y se marcaron salientes.
        assertTrue(toasts[0].leaving && toasts[1].leaving)
        // El RESUMEN no tiene reloj propio: nunca se marca saliente por si mismo.
        assertFalse(toasts[2].leaving)
    }

    @Test
    fun `un aviso se retira solo al agotarse su tiempo`() = runTest {
        val state = ToastState(backgroundScope, { 5.seconds }, { currentTime })
        state.show(listOf(pending("alpha", "1.0.0", "1.1.0")))

        advanceTimeBy(5_001)

        assertTrue(state.toasts.value.single().leaving, "Se marca saliente, no se borra de golpe")
        state.exitFinished(state.toasts.value.single().id)
        assertTrue(state.toasts.value.isEmpty())
    }

    @Test
    fun `el puntero encima pausa el descarte y al salir sigue con lo que quedaba`() = runTest {
        val state = ToastState(backgroundScope, { 5.seconds }, { currentTime })
        state.show(listOf(pending("alpha", "1.0.0", "1.1.0")))
        val id = state.toasts.value.single().id

        advanceTimeBy(3_000)
        state.pause(id)
        advanceTimeBy(60_000)
        assertFalse(state.toasts.value.single().leaving, "Con el puntero encima no se va")

        state.resume(id)
        advanceTimeBy(1_500)
        assertFalse(state.toasts.value.single().leaving, "Quedaban 2 s, no 0")
        advanceTimeBy(600)
        assertTrue(state.toasts.value.single().leaving)
    }
}
