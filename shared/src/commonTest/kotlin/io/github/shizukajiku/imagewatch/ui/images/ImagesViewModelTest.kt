package io.github.shizukajiku.imagewatch.ui.images

import io.github.shizukajiku.imagewatch.application.ImageResult
import io.github.shizukajiku.imagewatch.application.ImageSource
import io.github.shizukajiku.imagewatch.application.ImageStateStore
import io.github.shizukajiku.imagewatch.application.PollingController
import io.github.shizukajiku.imagewatch.application.SilencedImageStore
import io.github.shizukajiku.imagewatch.application.TrackedImageStore
import io.github.shizukajiku.imagewatch.application.VersionPollingService
import io.github.shizukajiku.imagewatch.domain.ImageRelease
import io.github.shizukajiku.imagewatch.domain.ImageStatus
import io.github.shizukajiku.imagewatch.ui.sound.Sound
import io.github.shizukajiku.imagewatch.ui.sound.Sounds
import io.github.shizukajiku.imagewatch.ui.theme.Dwell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private fun ahoraLocal() = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

private fun release(name: String, version: String) = ImageRelease(name, "registry.local/$name:$version", ahoraLocal())

@OptIn(ExperimentalCoroutinesApi::class)
class ImagesViewModelTest {

    @Test
    fun `el estado refleja el snapshot tras un ciclo`() = runTest {
        val fixture = fixture(listOf("alpha", "beta"))
        fixture.service.poll()

        val state = fixture.viewModel.state.value

        assertEquals(2, state.rows.size)
        assertEquals(2, state.total)
    }

    @Test
    fun `el filtro deja solo las coincidencias sin perder el total`() = runTest {
        val fixture = fixture(listOf("alpha", "beta"))
        fixture.service.poll()

        fixture.viewModel.onSearchChange("alph")

        val state = fixture.viewModel.state.value
        assertEquals(listOf("alpha"), state.rows.map { it.name })
        assertEquals(2, state.total, "El total cuenta imágenes vigiladas, no filtradas")
    }

    @Test
    fun `un nombre invalido se rechaza con mensaje`() {
        val fixture = fixture(listOf("alpha"))

        val error = fixture.viewModel.addImage("no vale/esto")

        assertNotNull(error)
        assertTrue(fixture.names.findAll().none { it == "no vale/esto" })
    }

    @Test
    fun `un nombre duplicado se rechaza con mensaje`() {
        val fixture = fixture(listOf("alpha"))

        val error = fixture.viewModel.addImage("alpha")

        assertNotNull(error)
        assertEquals(1, fixture.names.findAll().size)
    }

    @Test
    fun `un nombre valido se agrega`() {
        val fixture = fixture(listOf("alpha"))

        val error = fixture.viewModel.addImage("beta")

        assertNull(error)
        assertTrue(fixture.names.findAll().contains("beta"))
    }

    @Test
    fun `reconocer una imagen la deja al dia sin esperar al siguiente ciclo`() = runTest {
        val fixture = fixture(listOf("alpha"), localVersion = "1.0.0", remoteVersion = "2.0.0")
        fixture.service.poll()
        assertEquals(ImageStatus.PENDING, fixture.viewModel.state.value.rows.first().status)

        fixture.viewModel.acknowledge("alpha")

        assertEquals(ImageStatus.OK, fixture.viewModel.state.value.rows.first().status)
    }

    @Test
    fun `solo se puede reconocer lo que esta pendiente`() = runTest {
        val fixture = fixture(listOf("alpha"), localVersion = "1.0.0", remoteVersion = "2.0.0")
        fixture.service.poll()

        val fila = fixture.viewModel.state.value.rows.single()

        assertTrue(fila.canAcknowledge)
        assertTrue(fixture.viewModel.state.value.canAcknowledgeAll)
    }

    @Test
    fun `los campos derivados marcan un unico estado a la vez`() = runTest {
        val alDia = fixture(listOf("alpha"), localVersion = "1.0.0", remoteVersion = "1.0.0")
        alDia.service.poll()
        val filaAlDia = alDia.viewModel.state.value.rows.single()
        assertEquals(ImageStatus.OK, filaAlDia.status)
        assertTrue(!filaAlDia.unverified && !filaAlDia.failed && !filaAlDia.remoteHighlighted)

        val pendiente = fixture(listOf("alpha"), localVersion = "1.0.0", remoteVersion = "2.0.0")
        pendiente.service.poll()
        val filaPendiente = pendiente.viewModel.state.value.rows.single()
        assertEquals(ImageStatus.PENDING, filaPendiente.status)
        assertTrue(filaPendiente.remoteHighlighted)
        assertTrue(!filaPendiente.unverified && !filaPendiente.failed)

        val fallida = fixture(listOf("alpha"))
        fallida.state.seed("alpha", "registry.local/alpha:1.0.0")
        fallida.source.failOn("alpha", "HTTP 503")
        fallida.service.poll()
        val filaFallida = fallida.viewModel.state.value.rows.single()
        assertEquals(ImageStatus.ERROR, filaFallida.status)
        assertTrue(filaFallida.failed)
        assertTrue(!filaFallida.unverified && !filaFallida.remoteHighlighted)

        val sinVerificar = fixture(listOf("alpha"))
        val filaSinVerificar = sinVerificar.viewModel.state.value.rows.single()
        assertEquals(ImageStatus.UNKNOWN, filaSinVerificar.status)
        assertTrue(filaSinVerificar.unverified)
        assertTrue(!filaSinVerificar.failed && !filaSinVerificar.remoteHighlighted)
    }

    @Test
    fun `eliminar una imagen la saca de la lista`() = runTest {
        val fixture = fixture(listOf("alpha", "beta"))
        fixture.service.poll()

        fixture.viewModel.removeImage("alpha")

        assertEquals(listOf("beta"), fixture.viewModel.state.value.rows.map { it.name })
    }

    @Test
    fun `una imagen sin verificar se muestra sin versiones`() {
        val fixture = fixture(listOf("alpha"))

        val row = fixture.viewModel.state.value.rows.first()

        assertEquals(ImageStatus.UNKNOWN, row.status)
        assertEquals("—", row.remote)
    }

    @Test
    fun `verificando se enciende al empezar el ciclo y se apaga al terminar`() = runTest {
        val fixture = fixture(listOf("alpha"))

        assertEquals(false, fixture.viewModel.state.value.verifying)

        fixture.service.poll()

        // Cuando poll() retorna, el snapshot ya llegó: lo que queda es el estado de reposo.
        assertEquals(false, fixture.viewModel.state.value.verifying)
    }

    @Test
    fun `el aviso de comienzo enciende la verificacion`() {
        val fixture = fixture(listOf("alpha"))

        // Se invoca el puerto directamente porque poll() enciende y apaga en la misma llamada:
        // el estado intermedio no es observable desde fuera de otro modo.
        fixture.viewModel.onPollStarted()

        assertEquals(true, fixture.viewModel.state.value.verifying)
    }

    @Test
    fun `cerrar el view model lo desengancha del servicio`() = runTest {
        val fixture = fixture(listOf("alpha"))
        fixture.service.poll()
        val before = fixture.viewModel.state.value

        fixture.viewModel.close()
        fixture.service.poll()

        assertEquals(before, fixture.viewModel.state.value)
    }

    @Test
    fun `una fila con error conserva la ultima version conocida`() = runTest {
        val fixture = fixture(listOf("alpha"))
        fixture.state.seed("alpha", "registry.local/alpha:1.0.0")
        fixture.source.failOn("alpha", "HTTP 503")

        fixture.service.poll()

        val row = fixture.viewModel.state.value.rows.single()
        assertEquals(ImageStatus.ERROR, row.status)
        assertEquals("HTTP 503", row.error)
        assertEquals("1.0.0", row.local, "La version conocida no desaparece porque falle la consulta")
    }

    @Test
    fun `si fallan todas se marca el aviso global`() = runTest {
        val fixture = fixture(listOf("alpha", "beta"))
        fixture.source.failOn("alpha", "HTTP 503")
        fixture.source.failOn("beta", "HTTP 503")

        fixture.service.poll()

        assertTrue(fixture.viewModel.state.value.allFailing)
    }

    @Test
    fun `una sola imagen caida no dispara el aviso global`() = runTest {
        // "El origen no responde" y "esta imagen no existe" son problemas distintos y el aviso
        // solo cubre el primero.
        val fixture = fixture(listOf("alpha", "beta"))
        fixture.source.failOn("alpha", "HTTP 404")

        fixture.service.poll()

        assertEquals(false, fixture.viewModel.state.value.allFailing)
    }

    @Test
    fun `activar o parar el sondeo reproduce el sonido de conmutacion`() {
        val reproducidos = mutableListOf<Sound>()
        val fixture = fixture(listOf("alpha"), sounds = espia(reproducidos))

        fixture.viewModel.togglePolling()

        assertEquals(listOf(Sound.TOGGLE), reproducidos)
    }

    @Test
    fun `eliminar una imagen reproduce el sonido de exito`() {
        val reproducidos = mutableListOf<Sound>()
        val fixture = fixture(listOf("alpha", "beta"), sounds = espia(reproducidos))

        fixture.viewModel.removeImage("alpha")

        assertEquals(listOf(Sound.SUCCESS), reproducidos)
    }

    @Test
    fun `agregar una imagen valida reproduce el sonido de exito`() {
        val reproducidos = mutableListOf<Sound>()
        val fixture = fixture(listOf("alpha"), sounds = espia(reproducidos))

        fixture.viewModel.addImage("beta")

        assertEquals(listOf(Sound.SUCCESS), reproducidos)
    }

    @Test
    fun `si fallan todas y siguen siendo las mismas imagenes, el error no repite en el segundo ciclo`() = runTest {
        // Es el comportamiento que evita que el usuario acabe apagando todos los sonidos: repetir
        // el aviso mientras el origen sigue caido, sin que cambie nada, es justo lo que no debe
        // pasar. Cubre la regla de la transicion, no el bug del campo duplicado -para eso esta el
        // test siguiente, que cambia el conjunto vigilado entre ciclos-.
        val reproducidos = mutableListOf<Sound>()
        val fixture = fixture(listOf("alpha", "beta"), sounds = espiaSinFoco(reproducidos))
        fixture.source.failOn("alpha", "HTTP 503")
        fixture.source.failOn("beta", "HTTP 503")

        fixture.service.poll()
        fixture.service.poll()

        assertEquals(listOf(Sound.ERROR), reproducidos)
    }

    @Test
    fun `una caida continua suena una sola vez aunque cambien las imagenes vigiladas`() = runTest {
        // Antes esto sonaba dos veces: dar de alta una imagen retraia el aviso -la nueva entraba
        // como no consultada y contaba para el calculo-, y al ciclo siguiente habia transicion
        // otra vez. El origen no dejo de estar caido en ningun momento, asi que un aviso basta.
        val reproducidos = mutableListOf<Sound>()
        val fixture = fixture(listOf("alpha", "beta"), sounds = espiaSinFoco(reproducidos))
        fixture.source.failOn("alpha", "HTTP 503")
        fixture.source.failOn("beta", "HTTP 503")
        fixture.service.poll()
        val erroresTrasElPrimerCiclo = reproducidos.count { it == Sound.ERROR }

        fixture.viewModel.removeImage("alpha")
        fixture.viewModel.addImage("gamma")
        fixture.source.failOn("gamma", "HTTP 503")
        fixture.service.poll()

        assertEquals(1, erroresTrasElPrimerCiclo)
        assertTrue(fixture.viewModel.state.value.allFailing, "El origen sigue sin responder")
        assertEquals(
            1,
            reproducidos.count { it == Sound.ERROR },
            "La caida nunca se interrumpio: avisar dos veces es lo que hace que se apague el sonido",
        )
    }

    @Test
    fun `dar de alta una imagen durante una caida no retrae el aviso ni repite el sonido`() = runTest {
        // La imagen recien anadida entra como UNKNOWN. Contandola, allFailing pasaba a falso: la
        // barra roja se retraia y la bandeja volvia al icono normal con el origen todavia caido,
        // y al ciclo siguiente habia transicion otra vez y el error sonaba por segunda vez para
        // la misma caida.
        val reproducidos = mutableListOf<Sound>()
        val fixture = fixture(listOf("alpha", "beta"), sounds = espiaSinFoco(reproducidos))
        fixture.source.failOn("alpha", "HTTP 503")
        fixture.source.failOn("beta", "HTTP 503")
        fixture.service.poll()

        fixture.viewModel.addImage("gamma")

        assertTrue(fixture.viewModel.state.value.allFailing, "El origen sigue caido")
        assertEquals(1, reproducidos.count { it == Sound.ERROR })
    }

    @Test
    fun `reconocer una imagen retira su aviso en pantalla`() = runTest {
        val retirados = mutableListOf<String>()
        val fixture = fixture(
            listOf("alpha"),
            localVersion = "1.0.0",
            remoteVersion = "2.0.0",
            dismissToastsFor = retirados::add,
        )
        fixture.service.poll()

        fixture.viewModel.acknowledge("alpha")

        assertEquals(listOf("alpha"), retirados)
    }

    @Test
    fun `reconocer todas retira los avisos de todas las pendientes`() = runTest {
        val retirados = mutableListOf<String>()
        val fixture = fixture(
            listOf("alpha", "beta"),
            localVersion = "1.0.0",
            remoteVersion = "2.0.0",
            dismissToastsFor = retirados::add,
        )
        fixture.service.poll()

        fixture.viewModel.acknowledgeAll()

        // Los nombres se leen antes de reconocer: despues ya no hay pendientes que consultar.
        assertEquals(listOf("alpha", "beta"), retirados.sorted())
    }

    @Test
    fun `una version mas nueva sobre una pendiente sin reconocer marca la fila`() = runTest {
        val fixture = fixture(listOf("alpha"), localVersion = "1.0.0", remoteVersion = "2.0.0")
        fixture.service.poll()
        assertEquals(
            RowEmphasis.NINGUNO,
            fixture.viewModel.state.value.rows.first().emphasis,
            "El paso a pendiente no la marca",
        )

        fixture.source.version = "3.0.0"
        fixture.service.poll()

        assertEquals(RowEmphasis.NOVEDAD, fixture.viewModel.state.value.rows.first().emphasis)
    }

    @Test
    fun `una pendiente con la misma version no vuelve a marcar la fila`() = runTest {
        val fixture = fixture(listOf("alpha"), localVersion = "1.0.0", remoteVersion = "2.0.0")
        fixture.service.poll()
        fixture.service.poll()

        assertEquals(RowEmphasis.NINGUNO, fixture.viewModel.state.value.rows.first().emphasis)
    }

    @Test
    fun `el resaltado se apaga solo al cumplirse su tiempo`() = runTest {
        val fixture = fixture(listOf("alpha"), scope = backgroundScope)
        fixture.viewModel.highlight("alpha")
        assertEquals(RowEmphasis.SENALADA, fixture.viewModel.state.value.rows.single().emphasis)

        advanceTimeBy(Dwell.HIGHLIGHT_MILLIS + 100)

        assertEquals(RowEmphasis.NINGUNO, fixture.viewModel.state.value.rows.single().emphasis)
    }

    @Test
    fun `limpiar el resaltado lo apaga sin esperar a su tiempo`() = runTest {
        // H-76: cerrar la ventana da por mirada la fila. Sin esto, reabrirla dentro de los
        // Dwell.HIGHLIGHT_MILLIS la devuelve resaltada y la lista se desplaza otra vez.
        val fixture = fixture(listOf("alpha"), scope = backgroundScope)
        fixture.viewModel.highlight("alpha")
        assertEquals(RowEmphasis.SENALADA, fixture.viewModel.state.value.rows.single().emphasis)

        fixture.viewModel.clearHighlight()

        assertEquals(RowEmphasis.NINGUNO, fixture.viewModel.state.value.rows.single().emphasis)
        advanceTimeBy(Dwell.HIGHLIGHT_MILLIS + 100)
        assertEquals(
            RowEmphasis.NINGUNO,
            fixture.viewModel.state.value.rows.single().emphasis,
            "El temporizador cancelado no vuelve a tocar nada",
        )
    }

    @Test
    fun `el aviso de sin conexion reparte el tiempo en sus tres umbrales`() {
        // El texto lo decide el view model, no el banner: aqui se puede fijar el reloj y
        // comprobar las tres franjas, que con Instant.now() dentro del composable no se podia.
        val ahora = Instant.parse("2026-09-05T12:00:00Z")

        assertEquals(
            "No se ha verificado nada desde que arrancó.",
            lastSuccessLabel(null, ahora),
        )
        assertEquals(
            "Última verificación correcta: hace menos de un minuto.",
            lastSuccessLabel(ahora - 59.seconds, ahora),
        )
        assertEquals(
            "Última verificación correcta: hace 59 min.",
            lastSuccessLabel(ahora - (59 * 60).seconds, ahora),
        )
        assertEquals(
            "Última verificación correcta: hace 3 h.",
            lastSuccessLabel(ahora - (3 * 60 * 60).seconds, ahora),
        )
    }

    @Test
    fun `una novedad late su tiempo aunque entre otro ciclo`() = runTest {
        val fixture = fixture(
            listOf("alpha", "beta"),
            localVersion = "1.0.0",
            remoteVersion = "2.0.0",
            scope = backgroundScope,
        )
        fixture.service.poll()
        fixture.source.version = "3.0.0"
        fixture.service.poll()
        assertEquals(RowEmphasis.NOVEDAD, fixture.viewModel.state.value.rows.first().emphasis)

        // Un ciclo sin novedades no puede apagar el latido del anterior antes de tiempo.
        advanceTimeBy(500)
        fixture.service.poll()

        assertEquals(RowEmphasis.NOVEDAD, fixture.viewModel.state.value.rows.first().emphasis)
    }

    @Test
    fun `resaltar una fila la marca y limpia el filtro de busqueda`() = runTest {
        // Cubre solo la mitad de H-55 que vive en el view model: hacer visible la ventana
        // (Main.kt) y desplazar hasta la fila (ImagesScreen) son cableado de composable, y
        // probarlo exigiria la infraestructura de test de interfaz que el proyecto no tiene.
        val fixture = fixture(listOf("alpha", "beta"))
        fixture.service.poll()
        fixture.viewModel.onSearchChange("alpha")
        assertEquals(listOf("alpha"), fixture.viewModel.state.value.rows.map { it.name })

        fixture.viewModel.highlight("beta")

        val state = fixture.viewModel.state.value
        assertEquals(
            RowEmphasis.SENALADA,
            state.rows.first { it.name == "beta" }.emphasis,
        )
        assertEquals("", state.search)
        assertEquals(
            listOf("alpha", "beta"),
            state.rows.map { it.name },
            "El filtro se limpia: si siguiera activo, 'Ver' podria abrir la ventana sin ensenar la fila",
        )
    }

    @Test
    fun `reconocer una imagen reproduce el sonido de exito`() = runTest {
        // Parte de H-69: la rama de `Wiring.applyConfig` (guardar ajustes) queda fuera porque
        // depende de la familia SettingsViewModel.save/boton Guardar que reescribe la Tarea 10.
        val reproducidos = mutableListOf<Sound>()
        val fixture = fixture(
            listOf("alpha"),
            localVersion = "1.0.0",
            remoteVersion = "2.0.0",
            sounds = espia(reproducidos),
        )
        fixture.service.poll()

        fixture.viewModel.acknowledge("alpha")

        assertEquals(listOf(Sound.SUCCESS), reproducidos)
    }

    @Test
    fun `reconocer todas reproduce el sonido de exito`() = runTest {
        val reproducidos = mutableListOf<Sound>()
        val fixture = fixture(
            listOf("alpha", "beta"),
            localVersion = "1.0.0",
            remoteVersion = "2.0.0",
            sounds = espia(reproducidos),
        )
        fixture.service.poll()

        fixture.viewModel.acknowledgeAll()

        assertEquals(listOf(Sound.SUCCESS), reproducidos)
    }

    @Test
    fun `silenciar una imagen la marca sin tocar su version ni su estado`() = runTest {
        val fixture = fixture(listOf("alpha"), localVersion = "1.0.0", remoteVersion = "1.0.0")
        fixture.service.poll()

        fixture.viewModel.toggleSilence("alpha")

        val fila = fixture.viewModel.state.value.rows.single()
        assertTrue(fila.muted)
        assertEquals(ImageStatus.OK, fila.status)
        assertTrue(fixture.silenced.findAll().contains("alpha"))
    }

    @Test
    fun `silenciar dos veces reactiva los avisos`() {
        val fixture = fixture(listOf("alpha"))
        fixture.viewModel.toggleSilence("alpha")

        fixture.viewModel.toggleSilence("alpha")

        assertTrue(!fixture.viewModel.state.value.rows.single().muted)
        assertTrue(fixture.silenced.findAll().isEmpty())
    }

    @Test
    fun `reconocer una imagen la mueve a «Al dia» y deja un rastro que se apaga solo`() = runTest {
        val fixture = fixture(
            listOf("alpha"),
            localVersion = "1.0.0",
            remoteVersion = "2.0.0",
            scope = backgroundScope,
        )
        fixture.service.poll()

        fixture.viewModel.acknowledge("alpha")

        val fila = fixture.viewModel.state.value.rows.single()
        assertEquals(ImageStatus.OK, fila.status)
        assertEquals("alpha", fixture.viewModel.state.value.trace)

        advanceTimeBy(Dwell.TRACE_MILLIS + 100)
        assertNull(fixture.viewModel.state.value.trace, "El rastro se apaga solo")
    }

    @Test
    fun `una imagen que pasa a pendiente en un ciclo posterior aparece arriba sin esperar`() = runTest {
        val fixture = fixture(listOf("alpha"), localVersion = "1.0.0", remoteVersion = "1.0.0")
        fixture.service.poll()
        assertEquals(ImageStatus.OK, fixture.viewModel.state.value.rows.single().status)

        fixture.source.version = "2.0.0"
        fixture.service.poll()

        val state = fixture.viewModel.state.value
        assertEquals(listOf("alpha"), state.rows.map { it.name }, "La novedad se ve ya, no se encola")
        assertEquals(1, state.pending)
    }

    @Test
    fun `el primer ciclo de la sesion enseña el pendiente que ya habia`() = runTest {
        val fixture = fixture(listOf("alpha"), localVersion = "1.0.0", remoteVersion = "2.0.0")

        fixture.service.poll()

        assertEquals(listOf("alpha"), fixture.viewModel.state.value.rows.map { it.name })
    }

    @Test
    fun `comprobar una sola fila la marca como comprobando hasta que llega la respuesta`() = runTest {
        val fixture = fixture(listOf("alpha", "beta"))
        fixture.service.poll()

        // El controlador consume en su propio hilo (`Dispatchers.Default`): se cierra ANTES de
        // `refreshNow` para que la consulta real nunca se encole y no compita contra estas
        // aserciones ni contra el `FakeImageStateStore`, que no es thread-safe. Lo que se
        // comprueba es el estado que publica el view model -que `refreshNow` marca la fila y que
        // un snapshot lo limpia-, no el sondeo en sí, que ya cubre `PollingControllerTest`.
        fixture.controller.close()
        fixture.viewModel.refreshNow("alpha")

        val antes = fixture.viewModel.state.value.rows.associateBy { it.name }
        assertTrue(antes.getValue("alpha").checking)
        assertTrue(!antes.getValue("beta").checking)

        fixture.service.poll()

        assertTrue(!fixture.viewModel.state.value.rows.first { it.name == "alpha" }.checking)
    }

    @Test
    fun `abrir una fila fija expandedRow y volver a pulsarla lo limpia`() = runTest {
        val fixture = fixture(listOf("alpha"))
        fixture.service.poll()

        fixture.viewModel.toggleExpand("alpha")
        assertEquals("alpha", fixture.viewModel.state.value.expandedRow)

        fixture.viewModel.toggleExpand("alpha")
        assertNull(fixture.viewModel.state.value.expandedRow)
    }

    @Test
    fun `abrir otra fila cierra la anterior`() = runTest {
        val fixture = fixture(listOf("alpha", "beta"))
        fixture.service.poll()

        fixture.viewModel.toggleExpand("alpha")
        fixture.viewModel.toggleExpand("beta")

        assertEquals("beta", fixture.viewModel.state.value.expandedRow)
    }

    // --- andamiaje ---

    private class Fixture(
        val service: VersionPollingService,
        val names: TrackedImageStore,
        val controller: PollingController,
        val viewModel: ImagesViewModel,
        val state: FakeImageStateStore,
        val source: FakeImageSource,
        val silenced: FakeSilencedImageStore,
    )

    /** Sonido mudo por defecto: los tests que no prueban sonido no dependen de que haya audio. */
    private val silencioso = Sounds(enabled = { false }, volume = { 0.0 }, windowFocused = { true })

    /**
     * Espia con la **ventana enfocada**, que es la unica situacion en la que puede ocurrir una
     * accion del usuario: pulsar Detener o eliminar una imagen exige tener la ventana delante. Un
     * espia con el foco ausente probaria una configuracion que la aplicacion no produce, y fue lo
     * que dejo pasar que los sonidos de confirmacion no sonaran nunca.
     */
    private fun espia(reproducidos: MutableList<Sound>) = Sounds(
        enabled = { true },
        volume = { 1.0 },
        windowFocused = { true },
        onPlay = { reproducidos.add(it) },
    )

    /** Espia para los avisos que llegan solos, que solo suenan si el usuario no esta mirando. */
    private fun espiaSinFoco(reproducidos: MutableList<Sound>) = Sounds(
        enabled = { true },
        volume = { 1.0 },
        windowFocused = { false },
        onPlay = { reproducidos.add(it) },
    )

    private fun fixture(
        images: List<String>,
        localVersion: String? = null,
        remoteVersion: String = "1.0.0",
        sounds: Sounds = silencioso,
        dismissToastsFor: (String) -> Unit = {},
        scope: CoroutineScope = CoroutineScope(Dispatchers.Unconfined),
    ): Fixture {
        val names = FakeTrackedImageStore(images.toMutableList())
        val store = FakeImageStateStore()
        localVersion?.let { version -> images.forEach { store.save(listOf(release(it, version))) } }
        val source = FakeImageSource(remoteVersion)
        val service = VersionPollingService(source, store, emptyList(), names)
        val controller = PollingController(service, 30.seconds)
        val silenced = FakeSilencedImageStore()
        return Fixture(
            service,
            names,
            controller,
            ImagesViewModel(service, names, silenced, controller, sounds, scope, dismissToastsFor),
            store,
            source,
            silenced,
        )
    }

    private class FakeSilencedImageStore(seed: Set<String> = emptySet()) : SilencedImageStore {
        private var names = seed

        override fun findAll(): Set<String> = names

        override fun save(names: Set<String>) {
            this.names = names
        }
    }

    private class FakeTrackedImageStore(private val names: MutableList<String>) : TrackedImageStore {
        override fun findAll(): List<String> = names.toList()

        override fun save(updated: List<String>) {
            names.clear()
            names.addAll(updated)
        }
    }

    private class FakeImageStateStore : ImageStateStore {
        private val byName = mutableMapOf<String, ImageRelease>()

        override fun find(name: String): ImageRelease? = byName[name]

        override fun save(releases: List<ImageRelease>) {
            releases.forEach { byName[it.name] = it }
        }

        override fun rename(previous: String, current: String) {
            byName.remove(previous)?.let {
                byName[current] = ImageRelease(current, it.reference, it.publishedAt)
            }
        }

        /** Establece la última publicación vista de una imagen sin pasar por `save`. */
        fun seed(name: String, reference: String) {
            byName[name] = ImageRelease(name, reference, ahoraLocal())
        }
    }

    /** Origen simulado que devuelve la misma versión remota, salvo las imágenes marcadas con [failOn]. */
    private class FakeImageSource(var version: String) : ImageSource {
        private val failures = mutableMapOf<String, String>()

        fun failOn(name: String, message: String) {
            failures[name] = message
        }

        override suspend fun findByNames(names: List<String>): List<ImageResult> = names.map { name ->
            val failure = failures[name]
            if (failure != null) {
                ImageResult.failed(name, failure)
            } else {
                ImageResult.found(release(name, version))
            }
        }
    }
}
