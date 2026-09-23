package io.github.shizukajiku.imagewatch.infrastructure.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val WEBHOOK_URL = "https://prod-01.westus.logic.azure.com/workflows/abc/triggers/manual/paths/invoke"

internal class TeamsWebhookClientTest {
    @Test
    fun `sendUpdates manda la tarjeta al webhook y tiene en cuenta un 200`() = runTest {
        var recibida: HttpRequestData? = null
        val engine = MockEngine { request ->
            recibida = request
            respond("", HttpStatusCode.OK, headersOf("Content-Type", "application/json"))
        }

        val result = client(engine).sendUpdates(WEBHOOK_URL, listOf(TeamsVersionUpdate("alpha", "1.1.0")))

        assertTrue(result.isSuccess)
        assertEquals(WEBHOOK_URL, recibida?.url.toString())
        val body = bodyOf(recibida!!)
        assertTrue("alpha" in body, "La tarjeta debe nombrar la imagen")
        assertTrue("1.1.0" in body, "La tarjeta debe llevar la version nueva")
        assertTrue("AdaptiveCard" in body, "El sobre debe ser una Adaptive Card")
    }

    @Test
    fun `sendTest manda una tarjeta de prueba`() = runTest {
        var recibida: HttpRequestData? = null
        val engine = MockEngine { request ->
            recibida = request
            respond("", HttpStatusCode.OK, headersOf("Content-Type", "application/json"))
        }

        val result = client(engine).sendTest(WEBHOOK_URL)

        assertTrue(result.isSuccess)
        assertTrue("Mensaje de prueba" in bodyOf(recibida!!))
    }

    @Test
    fun `una respuesta que no es 2xx se reporta como fallo`() = runTest {
        val engine = MockEngine { respondError(HttpStatusCode.NotFound) }

        val result = client(engine).sendTest(WEBHOOK_URL)

        assertTrue(result.isFailure)
    }

    @Test
    fun `una URL sin https se rechaza sin llegar a hacer la peticion`() = runTest {
        var llamado = false
        val engine = MockEngine {
            llamado = true
            respond("", HttpStatusCode.OK)
        }

        val result = client(engine).sendTest("http://inseguro.ejemplo/workflow")

        assertTrue(result.isFailure)
        assertTrue(!llamado, "No debe llegar a llamar al motor HTTP")
    }

    private fun client(engine: MockEngine) = TeamsWebhookClient(HttpClient(engine))

    private fun bodyOf(request: HttpRequestData): String = (request.body as TextContent).text
}
