package io.github.shizukajiku.imagewatch.infrastructure.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import org.slf4j.LoggerFactory
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Se queda en `jvmMain` aunque [HttpImageSource] haya subido a `commonMain`, y no es un descuido:
 * el motor de Ktor y la configuración TLS son de la plataforma por definición. Cada objetivo que se
 * añada traerá su propio motor; el adaptador que los usa ya no tiene que enterarse.
 */
object HttpClientFactory {
    private val log = LoggerFactory.getLogger(HttpClientFactory::class.java)

    fun create(ignoreSslErrors: Boolean): HttpClient = HttpClient(OkHttp) {
        install(HttpTimeout) {
            connectTimeoutMillis = CONNECT_TIMEOUT_MILLIS
            requestTimeoutMillis = REQUEST_TIMEOUT_MILLIS
        }
        if (ignoreSslErrors) {
            log.warn("La validación TLS está desactivada (IGNORE_SSL_ERRORS=true).")
            val trustAll = trustEverything()
            engine {
                config {
                    sslSocketFactory(insecureContext(trustAll).socketFactory, trustAll)
                    // OkHttp valida el nombre del host aparte del certificado: sin esto, desactivar
                    // la validación seguiría rechazando un certificado emitido para otro host, que
                    // es justo el caso que este interruptor existe para permitir.
                    hostnameVerifier { _, _ -> true }
                }
            }
        }
    }

    private fun trustEverything() = object : X509TrustManager {
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()

        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit

        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = Unit
    }

    private fun insecureContext(trustAll: X509TrustManager): SSLContext = try {
        SSLContext.getInstance("TLS").apply { init(null, arrayOf<TrustManager>(trustAll), SecureRandom()) }
    } catch (e: GeneralSecurityException) {
        throw IllegalStateException("No se pudo configurar SSL inseguro", e)
    }

    private const val CONNECT_TIMEOUT_MILLIS = 10_000L
    private const val REQUEST_TIMEOUT_MILLIS = 30_000L
}
