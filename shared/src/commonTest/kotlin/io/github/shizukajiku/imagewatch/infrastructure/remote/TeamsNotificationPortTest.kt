package io.github.shizukajiku.imagewatch.infrastructure.remote

import io.github.shizukajiku.imagewatch.application.SilencedImageStore
import io.github.shizukajiku.imagewatch.application.TeamsNotifiedStore
import io.github.shizukajiku.imagewatch.config.AppConfig
import io.github.shizukajiku.imagewatch.config.ThemePreference
import io.github.shizukajiku.imagewatch.domain.ImageState
import io.github.shizukajiku.imagewatch.domain.ImageStatus
import io.github.shizukajiku.imagewatch.domain.PollSnapshot
import io.github.shizukajiku.imagewatch.domain.Version
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private const val WEBHOOK_URL = "https://prod-01.westus.logic.azure.com/workflows/abc/triggers/manual/paths/invoke"

/** Pendiente para el escritorio: remota por encima de la local reconocida. */
private fun pending(name: String, remote: String = "1.1.0") = ImageState(
    name,
    Version("1.0.0"),
    Version(remote),
    "registry.local/$name",
    ImageStatus.PENDING,
    null,
    Instant.fromEpochSeconds(0),
)

/** Al día para el escritorio -local == remote, ya reconocida-, pero con una versión remota real. */
private fun acknowledgedByDesktop(name: String, version: String) = ImageState(
    name,
    Version(version),
    Version(version),
    "registry.local/$name",
    ImageStatus.OK,
    null,
    Instant.fromEpochSeconds(0),
)

private fun failing(name: String) = ImageState(
    name,
    null,
    null,
    "registry.local/$name",
    ImageStatus.ERROR,
    "HTTP 503",
    Instant.fromEpochSeconds(0),
)

private fun snapshotOf(vararg images: ImageState) = PollSnapshot(images.toList(), Instant.fromEpochSeconds(0))

private fun config(teamsEnabled: Boolean, teamsWebhookUrl: String = WEBHOOK_URL, mutedAll: Boolean = false) = AppConfig(
    "images.json",
    "https://origen.ejemplo/api",
    300.seconds,
    listOf("alpha"),
    true,
    true,
    ThemePreference.SYSTEM,
    true,
    8.seconds,
    true,
    0.5,
    mutedAll,
    teamsEnabled,
    teamsWebhookUrl,
)

private class FakeSilencedImageStore(private val silenced: Set<String> = emptySet()) : SilencedImageStore {
    override fun findAll(): Set<String> = silenced

    override fun save(names: Set<String>) = error("no usado en estas pruebas")
}

/** Doble en memoria de la línea base propia de Teams -nunca la de `ImageStateStore`-. */
private class FakeTeamsNotifiedStore(seed: Map<String, String> = emptyMap()) : TeamsNotifiedStore {
    private val versions = seed.toMutableMap()

    override fun find(name: String): String? = versions[name]

    override fun save(versions: Map<String, String>) {
        this.versions.putAll(versions)
    }
}

/** El cuerpo de una petición mandada con `setBody(String)` y `contentType(Json)`. */
private fun bodyOf(request: HttpRequestData): String = (request.body as TextContent).text

/**
 * `onSnapshot` no bloquea: manda la petición en una corrutina de `scope`, que en Ktor corre en su
 * propio dispatcher, no en el del test. `runCurrent()` no espera ese trabajo -solo agota la cola
 * del dispatcher de test-, así que las pruebas que sí esperan una petición usan este `Deferred` en
 * vez de contar llamadas y mirar el contador justo después de `runCurrent()`.
 *
 * `await()` no lleva `withTimeout`: programaría su propio `delay` en el dispatcher de test, y
 * `runTest` adelanta el reloj virtual en cuanto ese dispatcher se queda sin nada más que hacer -que
 * es justo lo que pasa aquí, porque lo único pendiente es la respuesta real de `MockEngine` en su
 * propio dispatcher-, así que el timeout saltaría antes de que la petición real llegara a
 * completarse.
 */
private class RecordingEngine {
    private val received = CompletableDeferred<HttpRequestData>()

    val engine = MockEngine { request ->
        received.complete(request)
        respond("", HttpStatusCode.OK, headersOf("Content-Type", "application/json"))
    }

    suspend fun await(): HttpRequestData = received.await()
}

/**
 * Espera a que toda corrutina lanzada bajo [scope] termine de verdad -éxito o fallo-, con
 * `Job.join()` y no con `withTimeout`: un `join()` es una suspensión real, no un `delay`
 * programado en el dispatcher de test, así que no dispara el adelanto de reloj virtual que
 * comenta [RecordingEngine]. Hace falta en cualquier prueba que compruebe [TeamsNotifiedStore]
 * después de un envío: `recorder.await()` solo confirma que la petición llegó al motor, no que la
 * respuesta ya volvió y `onSuccess`/`onFailure` -que es quien guarda- ya corrió.
 */
private suspend fun awaitAllLaunched(scope: CoroutineScope) {
    scope.coroutineContext[Job]!!.children.forEach { it.join() }
}

private fun port(
    engine: MockEngine,
    scope: CoroutineScope,
    silenced: Set<String> = emptySet(),
    store: TeamsNotifiedStore = FakeTeamsNotifiedStore(),
    config: () -> AppConfig,
): TeamsNotificationPort = TeamsNotificationPort(
    TeamsWebhookClient(HttpClient(engine)),
    store,
    FakeSilencedImageStore(silenced),
    scope,
    config,
)

@OptIn(ExperimentalCoroutinesApi::class)
internal class TeamsNotificationPortTest {

    @Test
    fun `con la integracion desactivada no llama al webhook`() = runTest {
        var llamadas = 0
        val engine = MockEngine {
            llamadas++
            respond("", HttpStatusCode.OK)
        }
        val port = port(engine, backgroundScope) { config(teamsEnabled = false) }

        port.onSnapshot(snapshotOf(pending("alpha")))
        runCurrent()

        assertEquals(0, llamadas)
    }

    @Test
    fun `con la integracion activada pero sin URL no llama al webhook`() = runTest {
        var llamadas = 0
        val engine = MockEngine {
            llamadas++
            respond("", HttpStatusCode.OK)
        }
        val port = port(engine, backgroundScope) { config(teamsEnabled = true, teamsWebhookUrl = "") }

        port.onSnapshot(snapshotOf(pending("alpha")))
        runCurrent()

        assertEquals(0, llamadas)
    }

    @Test
    fun `el silencio general corta el envio a Teams`() = runTest {
        var llamadas = 0
        val engine = MockEngine {
            llamadas++
            respond("", HttpStatusCode.OK)
        }
        val port = port(engine, backgroundScope) { config(teamsEnabled = true, mutedAll = true) }

        port.onSnapshot(snapshotOf(pending("alpha")))
        runCurrent()

        assertEquals(0, llamadas)
    }

    @Test
    fun `una imagen silenciada no se notifica ni se establece como vista`() = runTest {
        var llamadas = 0
        val engine = MockEngine {
            llamadas++
            respond("", HttpStatusCode.OK)
        }
        val store = FakeTeamsNotifiedStore()
        val port =
            port(engine, backgroundScope, silenced = setOf("alpha"), store = store) { config(teamsEnabled = true) }

        port.onSnapshot(snapshotOf(pending("alpha")))
        runCurrent()

        assertEquals(0, llamadas)
        assertNull(store.find("alpha"), "Silenciada, ni siquiera línea base")
    }

    @Test
    fun `una imagen sin version remota -ERROR- no se notifica ni se establece como vista`() = runTest {
        var llamadas = 0
        val engine = MockEngine {
            llamadas++
            respond("", HttpStatusCode.OK)
        }
        val store = FakeTeamsNotifiedStore()
        val port = port(engine, backgroundScope, store = store) { config(teamsEnabled = true) }

        port.onSnapshot(snapshotOf(failing("alpha")))
        runCurrent()

        assertEquals(0, llamadas)
        assertNull(store.find("alpha"))
    }

    @Test
    fun `la primera vez que Teams ve una imagen establece linea base en silencio, sin avisar`() = runTest {
        var llamadas = 0
        val engine = MockEngine {
            llamadas++
            respond("", HttpStatusCode.OK)
        }
        val store = FakeTeamsNotifiedStore()
        val port = port(engine, backgroundScope, store = store) { config(teamsEnabled = true) }

        port.onSnapshot(snapshotOf(pending("alpha", remote = "1.1.0")))
        runCurrent()

        assertEquals(0, llamadas, "La primera vez no avisa")
        assertEquals("1.1.0", store.find("alpha"), "Pero sí deja registrada la línea base")
    }

    @Test
    fun `tras la linea base, una version mas nueva si avisa y actualiza lo ya avisado`() = runTest {
        // "alpha" ya lo vio Teams antes, en la 1.1.0.
        val store = FakeTeamsNotifiedStore(mapOf("alpha" to "1.1.0"))
        val recorder = RecordingEngine()
        val port = port(recorder.engine, backgroundScope, store = store) { config(teamsEnabled = true) }

        port.onSnapshot(snapshotOf(pending("alpha", remote = "1.2.0")))

        // El mensaje solo lleva la version nueva -1.2.0-. La comparacion interna que decide si
        // avisar sigue siendo contra lo último que avisó Teams -1.1.0-, no contra el `local` del
        // escritorio -1.0.0 en el ImageState de `pending()`-; confundir los dos es justo el bug
        // reportado, y lo cubre la línea base guardada abajo.
        assertTrue("1.2.0" in bodyOf(recorder.await()))
        awaitAllLaunched(backgroundScope)
        assertEquals("1.2.0", store.find("alpha"))
    }

    @Test
    fun `si el envio falla la linea base no avanza, para no dejar un hueco en el siguiente aviso`() = runTest {
        // Reproduce el bug reportado: si se marcara como avisada antes de confirmar el envío, un
        // fallo de red -o la app cerrándose a mitad del envío- dejaría la línea base adelantada sin
        // que el mensaje hubiera salido nunca, y el siguiente aviso real mostraría un "desde" que
        // no se corresponde con lo último que de verdad llegó a Teams.
        val store = FakeTeamsNotifiedStore(mapOf("alpha" to "1.0.35"))
        val engine = MockEngine { respondError(HttpStatusCode.InternalServerError) }
        val port = port(engine, backgroundScope, store = store) { config(teamsEnabled = true) }

        port.onSnapshot(snapshotOf(pending("alpha", remote = "1.0.37")))
        awaitAllLaunched(backgroundScope)

        assertEquals("1.0.35", store.find("alpha"))
    }

    @Test
    fun `un segundo aviso parte de lo que avisó el primero, no de la linea base original`() = runTest {
        // Reproduce el caso reportado: línea base 1.0.15, primer aviso hasta 1.0.16, segundo aviso
        // hasta 1.0.17. El segundo mensaje debe avisar solo de 1.0.17 -y la línea base guardada debe
        // reflejarlo-, sin quedarse pegado a la 1.0.15 original.
        val store = FakeTeamsNotifiedStore(mapOf("alpha" to "1.0.15"))
        val primero = RecordingEngine()
        val port = port(primero.engine, backgroundScope, store = store) { config(teamsEnabled = true) }
        port.onSnapshot(snapshotOf(pending("alpha", remote = "1.0.16")))
        assertTrue("1.0.16" in bodyOf(primero.await()))
        awaitAllLaunched(backgroundScope)
        assertEquals("1.0.16", store.find("alpha"))

        val segundo = RecordingEngine()
        val port2 = port(segundo.engine, backgroundScope, store = store) { config(teamsEnabled = true) }
        port2.onSnapshot(snapshotOf(pending("alpha", remote = "1.0.17")))

        assertTrue("1.0.17" in bodyOf(segundo.await()))
        awaitAllLaunched(backgroundScope)
        assertEquals("1.0.17", store.find("alpha"))
    }

    @Test
    fun `una version de la que ya se avisó no se repite`() = runTest {
        var llamadas = 0
        val engine = MockEngine {
            llamadas++
            respond("", HttpStatusCode.OK)
        }
        val store = FakeTeamsNotifiedStore(mapOf("alpha" to "1.1.0"))
        val port = port(engine, backgroundScope, store = store) { config(teamsEnabled = true) }

        // Mismo ciclo repetido, sin que la remota haya cambiado: como si el usuario no hubiera
        // hecho nada, ni en Teams ni en el escritorio.
        port.onSnapshot(snapshotOf(pending("alpha", remote = "1.1.0")))
        runCurrent()

        assertEquals(0, llamadas)
    }

    @Test
    fun `avisa aunque el escritorio ya haya reconocido esa version -no depende del estado local-`() = runTest {
        // El punto central del diseño: Teams no lee `status` ni `local`, solo compara contra su
        // propia línea base. Una imagen que el escritorio ya dio por vista -local == remote, OK-
        // sigue siendo una versión nueva para Teams si Teams no la ha visto todavía.
        val store = FakeTeamsNotifiedStore(mapOf("alpha" to "1.0.0"))
        val recorder = RecordingEngine()
        val port = port(recorder.engine, backgroundScope, store = store) { config(teamsEnabled = true) }

        port.onSnapshot(snapshotOf(acknowledgedByDesktop("alpha", "2.0.0")))

        // Se avisa de la 2.0.0 aunque el `local` del escritorio ya coincida con la remota -porque
        // ya la reconoció-: la decisión de avisar no mira ese campo, solo la línea base de Teams.
        assertTrue("2.0.0" in bodyOf(recorder.await()))
        awaitAllLaunched(backgroundScope)
        assertEquals("2.0.0", store.find("alpha"))
    }

    @Test
    fun `una imagen silenciada no manda su propio aviso, pero las demas de la tanda si`() = runTest {
        val store = FakeTeamsNotifiedStore(mapOf("alpha" to "1.0.0", "beta" to "1.0.0"))
        val recorder = RecordingEngine()
        val port = port(recorder.engine, backgroundScope, silenced = setOf("alpha"), store = store) {
            config(teamsEnabled = true)
        }

        port.onSnapshot(snapshotOf(pending("alpha", remote = "2.0.0"), pending("beta", remote = "2.0.0")))

        val body = bodyOf(recorder.await())
        assertTrue("beta" in body)
        assertTrue("alpha" !in body)
    }
}
