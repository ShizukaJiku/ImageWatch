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

/**
 * Duración fija y muy holgada para las pruebas que no ejercitan el reloj: da igual el número
 * mientras sea mayor que lo que tarda la prueba en correr, porque un `CoroutineScope` real —no de
 * tiempo virtual— no la va a agotar en ese rato.
 */
private fun estadoSinReloj() = ToastState(CoroutineScope(Job()), { 60.seconds })

@OptIn(ExperimentalCoroutinesApi::class)
class ToastStateTest {

    @Test
    fun `una imagen produce un toast con su transicion`() {
        val state = estadoSinReloj()

        state.show(listOf(pending("alpha", "1.0.0", "1.1.0")))

        val toast = state.toasts.value.single()
        assertEquals("alpha", toast.imageName)
        assertTrue(toast.body.contains("1.0.0"))
        assertTrue(toast.body.contains("1.1.0"))
    }

    @Test
    fun `dos imagenes producen dos toasts`() {
        val state = estadoSinReloj()

        state.show(listOf(pending("alpha", "1.0.0", "1.1.0"), pending("beta", "2.0.0", "2.1.0")))

        assertEquals(2, state.toasts.value.size)
    }

    @Test
    fun `tres o mas siguen siendo un toast por imagen`() {
        // Se decidio no agrupar: cada imagen su aviso, y ninguno se pierde.
        val state = estadoSinReloj()

        state.show(
            listOf(
                pending("alpha", "1.0.0", "1.1.0"),
                pending("beta", "2.0.0", "2.1.0"),
                pending("gamma", "3.0.0", "3.1.0"),
            ),
        )

        assertEquals(
            listOf("alpha", "beta", "gamma"),
            state.toasts.value.map { it.imageName },
        )
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
        // Sin tope: con el tope real recortando a los ultimos, perder una escritura y recortarla
        // dan el mismo tamano final y el test dejaria de distinguir la carrera.
        val state = estadoSinReloj()
        val total = 50

        val threads = (1..total).map { i ->
            Thread { state.show(listOf(pending("img$i", "1.0.0", "1.1.0"))) }
        }
        threads.forEach(Thread::start)
        threads.forEach(Thread::join)

        assertEquals(total, state.toasts.value.size)
    }

    @Test
    fun `descartar en paralelo no pierde ningun descarte`() {
        // Sin tope: con el tope real recortando a los ultimos, perder una escritura y recortarla
        // dan el mismo tamano final y el test dejaria de distinguir la carrera.
        val state = estadoSinReloj()
        val total = 50
        (1..total).forEach { i -> state.show(listOf(pending("img$i", "1.0.0", "1.1.0"))) }
        val ids = state.toasts.value.map { it.id }

        val threads = ids.map { id -> Thread { state.dismiss(id) } }
        threads.forEach(Thread::start)
        threads.forEach(Thread::join)

        assertEquals(0, state.toasts.value.size)
    }

    @Test
    fun `los que no caben en la ventana esperan turno en vez de perderse`() {
        // Se muestran los primeros TOASTS_VISIBLES; el resto sigue en la cola y entra en cuanto
        // se libera hueco. Ningun aviso se pierde por llegar en mal momento.
        val state = estadoSinReloj()

        repeat(10) { i -> state.show(listOf(pending("img$i", "1.0.0", "1.1.0"))) }

        assertEquals(10, state.toasts.value.size, "La cola los conserva todos")
        assertEquals(
            listOf("img0", "img1", "img2", "img3"),
            state.toasts.value.take(TOASTS_VISIBLES).map { it.imageName },
            "Se muestran los mas antiguos: son los que llevan mas tiempo esperando",
        )
    }

    @Test
    fun `al descartar uno entra el siguiente de la cola`() {
        val state = estadoSinReloj()
        repeat(6) { i -> state.show(listOf(pending("img$i", "1.0.0", "1.1.0"))) }
        val visiblesAntes = state.toasts.value.take(TOASTS_VISIBLES).map { it.imageName }

        state.dismiss(state.toasts.value.first().id)

        assertEquals(listOf("img0", "img1", "img2", "img3"), visiblesAntes)
        assertEquals(
            listOf("img1", "img2", "img3", "img4"),
            state.toasts.value.take(TOASTS_VISIBLES).map { it.imageName },
        )
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

    @Test
    fun `el que espera turno no gasta su tiempo hasta que se pinta`() = runTest {
        val state = ToastState(backgroundScope, { 5.seconds }, { currentTime })
        repeat(TOASTS_VISIBLES + 1) { i -> state.show(listOf(pending("img$i", "1.0.0", "1.1.0"))) }
        val ultimo = state.toasts.value.last().id

        advanceTimeBy(5_001)

        assertTrue(state.toasts.value.any { it.id == ultimo && !it.leaving }, "Entra ahora, no se pierde")
    }
}
