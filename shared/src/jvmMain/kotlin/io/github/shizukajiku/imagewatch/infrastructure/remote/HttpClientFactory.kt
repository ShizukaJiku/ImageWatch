package io.github.shizukajiku.imagewatch.infrastructure.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import org.slf4j.LoggerFactory
import java.io.File
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Se queda en `jvmMain` aunque [HttpImageSource] haya subido a `commonMain`, y no es un descuido:
 * el motor de Ktor y la configuración TLS son de la plataforma por definición. Cada objetivo que se
 * añada traerá su propio motor; el adaptador que los usa ya no tiene que enterarse.
 */
object HttpClientFactory {
    private val log = LoggerFactory.getLogger(HttpClientFactory::class.java)

    /**
     * `extraTrustedCaFile` es para redes corporativas con inspección TLS: el proxy resigna el
     * tráfico saliente con una CA propia que el runtime empaquetado -su `cacerts` sale de `jlink` y
     * se pierde cualquier cambio manual en la siguiente Release- no trae. A diferencia de
     * `ignoreSslErrors`, **no** deja de validar: solo añade esa CA a las que el JDK ya trae de
     * fábrica, así que un certificado que no venga de ninguna de las dos sigue rechazándose.
     */
    fun create(ignoreSslErrors: Boolean, extraTrustedCaFile: String? = null): HttpClient = HttpClient(OkHttp) {
        install(HttpTimeout) {
            connectTimeoutMillis = CONNECT_TIMEOUT_MILLIS
            requestTimeoutMillis = REQUEST_TIMEOUT_MILLIS
        }
        if (ignoreSslErrors) {
            log.warn("La validación TLS está desactivada (IGNORE_SSL_ERRORS=true).")
            val trustAll = trustEverything()
            engine {
                config {
                    sslSocketFactory(sslContextFor(trustAll).socketFactory, trustAll)
                    // OkHttp valida el nombre del host aparte del certificado: sin esto, desactivar
                    // la validación seguiría rechazando un certificado emitido para otro host, que
                    // es justo el caso que este interruptor existe para permitir.
                    hostnameVerifier { _, _ -> true }
                }
            }
        } else if (!extraTrustedCaFile.isNullOrBlank()) {
            withExtraTrustedCa(extraTrustedCaFile)?.let { trustManager ->
                engine {
                    config {
                        sslSocketFactory(sslContextFor(trustManager).socketFactory, trustManager)
                    }
                }
            }
        }
    }

    /**
     * `null` si la CA no se pudo cargar -fichero ausente, PEM inválido-: un tropiezo aquí no puede
     * tumbar el arranque ni, peor, dejar la validación TLS desactivada. Se registra y el cliente
     * sigue con la confianza por defecto del JDK, que es la que ya tenía antes de este ajuste.
     */
    private fun withExtraTrustedCa(pemFile: String): X509TrustManager? = try {
        mergedTrustManager(customTrustManagerFor(pemFile))
    } catch (e: Exception) {
        log.warn("No se pudo cargar la CA adicional de '$pemFile'; sigue la validación TLS por defecto.", e)
        null
    }

    /** Confía en un certificado si lo acepta el JDK por defecto **o** la CA adicional, no solo una. */
    private fun mergedTrustManager(extra: X509TrustManager): X509TrustManager {
        val default = systemDefaultTrustManager()
        return object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> = default.acceptedIssuers + extra.acceptedIssuers

            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) =
                default.checkClientTrusted(chain, authType)

            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                try {
                    default.checkServerTrusted(chain, authType)
                } catch (defaultFailure: CertificateException) {
                    try {
                        extra.checkServerTrusted(chain, authType)
                    } catch (e: CertificateException) {
                        throw defaultFailure
                    }
                }
            }
        }
    }

    private fun systemDefaultTrustManager(): X509TrustManager {
        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        factory.init(null as KeyStore?)
        return factory.trustManagers.filterIsInstance<X509TrustManager>().first()
    }

    private fun customTrustManagerFor(pemFile: String): X509TrustManager {
        val certificate = File(pemFile).inputStream().use {
            CertificateFactory.getInstance("X.509").generateCertificate(it) as X509Certificate
        }
        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null, null)
            setCertificateEntry("extra-trusted-ca", certificate)
        }
        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        factory.init(keyStore)
        return factory.trustManagers.filterIsInstance<X509TrustManager>().first()
    }

    private fun trustEverything() = object : X509TrustManager {
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()

        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit

        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = Unit
    }

    private fun sslContextFor(trustManager: X509TrustManager): SSLContext = try {
        SSLContext.getInstance("TLS").apply { init(null, arrayOf<TrustManager>(trustManager), SecureRandom()) }
    } catch (e: GeneralSecurityException) {
        throw IllegalStateException("No se pudo configurar el contexto SSL", e)
    }

    private const val CONNECT_TIMEOUT_MILLIS = 10_000L
    private const val REQUEST_TIMEOUT_MILLIS = 30_000L
}
