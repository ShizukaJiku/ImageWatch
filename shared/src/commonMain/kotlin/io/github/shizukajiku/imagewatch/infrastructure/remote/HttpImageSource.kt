package io.github.shizukajiku.imagewatch.infrastructure.remote

import io.github.shizukajiku.imagewatch.application.ImageResult
import io.github.shizukajiku.imagewatch.application.ImageSource
import io.github.shizukajiku.imagewatch.domain.ImageRelease
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Url
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json

class HttpImageSource(private val client: HttpClient, baseUrl: String) : ImageSource {
    private val baseUrl: String
    private val json = Json

    init {
        val endpoint = baseUrl.replace(Regex("/$"), "")
        val url = Url(endpoint)
        // El bucle local queda exento a propósito: no hay tráfico que interceptar y es lo que
        // permite probar este adaptador contra un servidor de pruebas sin dependencias extra.
        // Cualquier host remoto sigue obligado a HTTPS.
        val loopback = url.host == "127.0.0.1" || url.host == "localhost"
        require(url.protocol.name.equals("https", ignoreCase = true) || loopback) {
            "El endpoint remoto debe utilizar HTTPS"
        }
        this.baseUrl = endpoint
    }

    /**
     * Las consultas se lanzan todas antes de esperar a ninguna: recogerlas dentro del mismo `map`
     * las volvería a serializar. `awaitAll` devuelve en orden de envío, no de finalización, que es
     * lo que evita que las filas de la tabla se reordenen en cada ciclo.
     */
    override suspend fun findByNames(names: List<String>): List<ImageResult> = coroutineScope {
        names.map { name -> async { findSafely(name) } }.awaitAll()
    }

    /** El fallo de una imagen viaja dentro de su resultado, no como excepción del lote. */
    private suspend fun findSafely(name: String): ImageResult {
        if (!name.matches(VALID_NAME)) {
            return ImageResult.failed(name, "Nombre de imagen inválido: $name")
        }
        return try {
            ImageResult.found(find(name))
        } catch (e: CancellationException) {
            // Se relanza sin envolver: una cancelación no es el fallo de una imagen, es que el
            // ciclo entero se está cortando. Tragársela dejaría corrutinas zombis y haría que
            // cerrar la aplicación pareciera un error de red.
            throw e
        } catch (e: Exception) {
            // Más ancho que el `RuntimeException` de antes, y a propósito: los fallos de red de
            // Ktor no comparten una raíz común que se pueda nombrar en código multiplataforma.
            ImageResult.failed(name, e.message ?: "Fallo sin detalle")
        }
    }

    private suspend fun find(name: String): ImageRelease {
        val response = client.get("$baseUrl/$name")
        check(response.status.isSuccess()) { "HTTP ${response.status.value}" }
        return json.decodeFromString<ReleaseDto>(response.bodyAsText()).toDomain()
    }

    private companion object {
        private val VALID_NAME = Regex("[A-Za-z0-9._-]+")
    }
}
