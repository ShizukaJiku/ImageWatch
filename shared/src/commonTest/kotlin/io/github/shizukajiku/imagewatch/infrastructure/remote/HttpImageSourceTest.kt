package io.github.shizukajiku.imagewatch.infrastructure.remote

import io.github.shizukajiku.imagewatch.application.ImageResult
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Antes montaba un `com.sun.net.httpserver.HttpServer` en el bucle local. Ahora usa el `MockEngine`
 * de Ktor, que además de ser multiplataforma hace deterministas las dos pruebas que dependían del
 * reloj: los retardos corren en el tiempo virtual de `runTest`, no en el de pared.
 */
internal class HttpImageSourceTest {
    @Test
    fun mapsTheWireFormatOntoTheDomain() = runTest {
        val results = source(respondingOk()).findByNames(listOf("alpha"))

        assertEquals(1, results.size)
        val release = assertNotNull(results.first().release)
        assertEquals("alpha", release.name)
        assertEquals("registry.local/alpha:1.2.3", release.reference)
    }

    @Test
    fun ignoresUnknownKeysInTheResponse() = runTest {
        val engine = MockEngine { request ->
            respond(
                content = """{"productName":"alpha","extra":42,"nested":{"a":1},""" +
                    """"lastRelease":"registry.local/alpha:1.2.3","update_time":"2026-09-03T10:00:00Z"}""",
                status = HttpStatusCode.OK,
                headers = headersOf("Content-Type", "application/json"),
            )
        }

        val results = source(engine).findByNames(listOf("alpha"))

        assertNull(results.first().error)
        assertEquals("registry.local/alpha:1.2.3", assertNotNull(results.first().release).reference)
    }

    @Test
    fun parsesFractionalSecondsAndZuluOffset() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"productName":"alpha","lastRelease":"registry.local/alpha:1.0.0",""" +
                    """"update_time":"2027-01-15T08:30:00.123Z"}""",
                status = HttpStatusCode.OK,
                headers = headersOf("Content-Type", "application/json"),
            )
        }

        val results = source(engine).findByNames(listOf("alpha"))

        assertNull(results.first().error)
        assertNotNull(results.first().release)
    }

    @Test
    fun parsesTimestampWithoutFractionalSeconds() = runTest {
        val engine = MockEngine { request -> ok(nameOf(request.url.encodedPath), "1.0.0") }

        val results = source(engine).findByNames(listOf("alpha"))

        assertNull(results.first().error)
    }

    @Test
    fun reportsAnErrorPerImageWithoutAffectingTheOthers() = runTest {
        val engine = MockEngine { request ->
            if (request.url.encodedPath.endsWith("/beta")) {
                respondError(HttpStatusCode.ServiceUnavailable)
            } else {
                ok(nameOf(request.url.encodedPath), "1.2.3")
            }
        }

        val results = source(engine).findByNames(listOf("alpha", "beta"))

        assertEquals(2, results.size)
        assertNull(results[0].error)
        assertNotNull(results[1].error)
    }

    @Test
    fun preservesTheRequestedOrder() = runTest {
        // Retardos invertidos respecto al orden de petición: la primera imagen pedida es la última
        // en responder. Una implementación que recogiese los resultados según van llegando
        // devolvería alpha, beta, gamma en lugar de gamma, alpha, beta, y las filas de la tabla se
        // reordenarían en cada ciclo.
        val retardos = mapOf("gamma" to 300L, "beta" to 150L, "alpha" to 0L)
        val engine = MockEngine { request ->
            val name = nameOf(request.url.encodedPath)
            delay(retardos.getValue(name))
            ok(name, "1.0.0")
        }

        val results = source(engine).findByNames(listOf("gamma", "alpha", "beta"))

        assertEquals(listOf("gamma", "alpha", "beta"), results.map(ImageResult::name))
    }

    @Test
    fun queriesTheImagesConcurrently() = runTest {
        // Se cuentan las consultas en vuelo en lugar de cronometrar el lote. El reloj virtual de
        // `runTest` no sirve aquí —Ktor ejecuta el motor en su propio dispatcher, así que estos
        // `delay` son de tiempo real y el reloj virtual ni los ve— y medir tiempo de pared, que es
        // lo que hacía la versión anterior de este test, depende de la carga de la máquina. Contar
        // solapamientos no depende de ninguna de las dos cosas: en serie el máximo sería 1.
        val enVuelo = MutableStateFlow(0)
        val maximoSolapado = MutableStateFlow(0)
        val engine = MockEngine { request ->
            val ahora = enVuelo.updateAndGet { it + 1 }
            maximoSolapado.update { maxOf(it, ahora) }
            delay(200)
            enVuelo.update { it - 1 }
            ok(nameOf(request.url.encodedPath), "1.0.0")
        }

        val results = source(engine).findByNames(listOf("alpha", "beta", "gamma"))

        assertEquals(3, results.size)
        assertTrue(results.all { it.error == null })
        assertEquals(3, maximoSolapado.value, "Las tres consultas deben solaparse")
    }

    @Test
    fun rejectsImageNamesThatCouldEscapeThePath() = runTest {
        val results = source(respondingOk()).findByNames(listOf("../admin"))

        assertNotNull(results.first().error)
        assertNull(results.first().release)
    }

    @Test
    fun rejectsANonHttpsEndpoint() {
        val fallo = kotlin.runCatching { HttpImageSource(HttpClient(respondingOk()), "http://origen.ejemplo/api") }

        assertNotNull(fallo.exceptionOrNull(), "Un endpoint remoto sin HTTPS debe rechazarse")
    }

    private fun source(engine: MockEngine) = HttpImageSource(HttpClient(engine), BASE_URL)

    private companion object {
        private const val BASE_URL = "http://127.0.0.1:8080"

        private fun respondingOk() = MockEngine { request ->
            ok(nameOf(request.url.encodedPath), "1.2.3")
        }

        /** El último segmento de la ruta es el nombre de la imagen consultada. */
        private fun nameOf(encodedPath: String) = encodedPath.substringAfterLast('/')

        private fun body(name: String, version: String, updateTime: String = "2026-09-03T10:00:00Z") =
            """{"productName":"$name","lastRelease":"registry.local/$name:$version",""" +
                """"update_time":"$updateTime"}"""

        private fun io.ktor.client.engine.mock.MockRequestHandleScope.ok(name: String, version: String) = respond(
            content = body(name, version),
            status = HttpStatusCode.OK,
            headers = headersOf("Content-Type", "application/json"),
        )
    }
}
