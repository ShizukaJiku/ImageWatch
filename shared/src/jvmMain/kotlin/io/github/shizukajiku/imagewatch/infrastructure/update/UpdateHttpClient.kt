package io.github.shizukajiku.imagewatch.infrastructure.update

import io.github.shizukajiku.imagewatch.infrastructure.remote.HttpClientFactory
import io.ktor.client.HttpClient

/**
 * Cliente HTTP del actualizador. **Siempre valida TLS**, pase lo que pase con
 * `AppConfig.ignoreSslErrors`: ese interruptor existe para el registro interno de imágenes, que
 * puede tener certificado propio. GitHub tiene certificado de una CA pública. Si la validación
 * estuviera apagada, un intermediario podría servir un MSI falso y su checksum a juego, y el
 * SHA-256 no protegería porque viaja por el mismo canal.
 */
object UpdateHttpClient {
    fun create(): HttpClient = HttpClientFactory.create(ignoreSslErrors = false)
}
