package io.github.shizukajiku.imagewatch.infrastructure.update

import io.github.shizukajiku.imagewatch.infrastructure.remote.HttpClientFactory
import io.ktor.client.HttpClient

/**
 * Cliente HTTP del actualizador. **Siempre valida TLS**, pase lo que pase con
 * `AppConfig.ignoreSslErrors`: ese interruptor existe para el registro interno de imágenes, que
 * puede tener certificado propio. GitHub tiene certificado de una CA pública. Si la validación
 * estuviera apagada, un intermediario podría servir un MSI falso y su checksum a juego, y el
 * SHA-256 no protegería porque viaja por el mismo canal.
 *
 * `extraTrustedCaFile` no relaja eso: solo añade una CA -típicamente la de un proxy corporativo
 * con inspección TLS- a las que el JDK ya trae, para redes donde ni siquiera GitHub llega sin
 * pasar por ese proxy. Ver [HttpClientFactory.create].
 */
object UpdateHttpClient {
    fun create(extraTrustedCaFile: String? = null): HttpClient =
        HttpClientFactory.create(ignoreSslErrors = false, extraTrustedCaFile = extraTrustedCaFile)
}
