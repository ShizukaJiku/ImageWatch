package io.github.shizukajiku.imagewatch.infrastructure.remote

import com.sun.net.httpserver.HttpsConfigurator
import com.sun.net.httpserver.HttpsServer
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.test.runTest
import java.io.File
import java.net.InetSocketAddress
import java.nio.file.Files
import java.security.KeyStore
import java.util.concurrent.TimeUnit
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLHandshakeException
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * `HttpClientFactory` es la única pieza que configura de verdad la validación TLS: el resto de la
 * suite usa el `MockEngine` de Ktor, que sustituye el motor entero y nunca llega a ejercitar un
 * `SSLSocketFactory` real. Por eso esta prueba monta un servidor HTTPS de verdad -certificados
 * generados con `keytool` al vuelo, nada en el repo ni en el disco del desarrollador- y comprueba
 * contra él lo mismo que reportó el usuario en `imagewatch.log`: sin `extraTrustedCaFile`, un
 * certificado firmado por una CA que el JDK no trae falla con
 * `PKIX path building failed`; con ella, conecta; y una CA ajena a la del servidor sigue sin
 * colar, que es lo que distingue esta opción de `ignoreSslErrors`.
 */
internal class HttpClientFactoryCaProbeTest {
    private lateinit var server: HttpsServer

    private fun startServer(): Int {
        val keyStore = KeyStore.getInstance("PKCS12").apply {
            certs.serverKeystore.inputStream().use { load(it, KEYSTORE_PASSWORD) }
        }
        val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
            init(keyStore, KEYSTORE_PASSWORD)
        }
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(keyManagerFactory.keyManagers, null, null)
        }
        server = HttpsServer.create(InetSocketAddress(LOOPBACK, 0), 0).apply {
            httpsConfigurator = HttpsConfigurator(sslContext)
            createContext("/") { exchange ->
                val body = "ok".toByteArray()
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            start()
        }
        return server.address.port
    }

    @AfterTest
    fun tearDown() {
        if (::server.isInitialized) server.stop(0)
    }

    @Test
    fun `sin la CA extra falla igual que en el log`() = runTest {
        val port = startServer()
        val client = HttpClientFactory.create(ignoreSslErrors = false)
        try {
            client.get("https://$LOOPBACK:$port/")
            fail("Se esperaba que la validación TLS rechazara el certificado")
        } catch (e: Exception) {
            val chain = generateSequence(e as Throwable?) { it.cause }
            assertTrue(
                chain.any { it is SSLHandshakeException },
                "Se esperaba una SSLHandshakeException en la cadena de causas, fue: " +
                    chain.joinToString(" <- ") { it::class.simpleName ?: "?" },
            )
        } finally {
            client.close()
        }
    }

    @Test
    fun `con la CA extra conecta`() = runTest {
        val port = startServer()
        val client = HttpClientFactory.create(
            ignoreSslErrors = false,
            extraTrustedCaFile = certs.caPem.absolutePath,
        )
        try {
            assertTrue(client.get("https://$LOOPBACK:$port/").bodyAsText() == "ok")
        } finally {
            client.close()
        }
    }

    @Test
    fun `una CA extra que no coincide sigue rechazando`() = runTest {
        val port = startServer()
        // CA real pero sin relación con la del servidor: no debe "colarse" como un trust-all.
        val client = HttpClientFactory.create(
            ignoreSslErrors = false,
            extraTrustedCaFile = certs.unrelatedCaPem.absolutePath,
        )
        try {
            client.get("https://$LOOPBACK:$port/")
            fail("Una CA ajena no debe permitir conectar; el trust manager se estaría comportando como trust-all")
        } catch (e: Exception) {
            assertTrue(generateSequence(e as Throwable?) { it.cause }.any { it is SSLHandshakeException })
        } finally {
            client.close()
        }
    }

    private class GeneratedCerts(val caPem: File, val serverKeystore: File, val unrelatedCaPem: File)

    private companion object {
        private const val LOOPBACK = "127.0.0.1"
        private val KEYSTORE_PASSWORD = "changeit".toCharArray()

        /**
         * Se genera una sola vez por proceso -no en `@BeforeTest`- porque son seis invocaciones de
         * `keytool`: repetirlas por cada uno de los tres tests solo alargaría la suite sin probar
         * nada distinto. El directorio queda en el temporal del sistema operativo y se borra al
         * cerrar la JVM, igual que cualquier fichero de prueba que no vive en el repo.
         */
        private val certs: GeneratedCerts by lazy { generateCerts() }

        private fun generateCerts(): GeneratedCerts {
            val dir = Files.createTempDirectory("iw-ca-test").toFile()
            Runtime.getRuntime().addShutdownHook(Thread { dir.deleteRecursively() })
            val keytool = keytoolPath()

            fun run(vararg args: String) {
                val process = ProcessBuilder(keytool, *args).redirectErrorStream(true).start()
                val output = process.inputStream.bufferedReader().readText()
                val finished = process.waitFor(30, TimeUnit.SECONDS)
                check(finished && process.exitValue() == 0) {
                    "keytool ${args.joinToString(
                        " ",
                    )} falló (exit=${if (finished) process.exitValue() else "timeout"}):\n$output"
                }
            }

            val caJks = File(dir, "ca.jks")
            val caPem = File(dir, "ca.pem")
            run(
                "-genkeypair", "-alias", "ca", "-keyalg", "RSA", "-keysize", "2048", "-validity", "3650",
                "-keystore", caJks.path, "-storepass", "changeit", "-dname", "CN=Test CA",
                "-ext", "bc:c=ca:true", "-storetype", "PKCS12",
            )
            run(
                "-exportcert", "-alias", "ca", "-keystore", caJks.path, "-storepass", "changeit",
                "-rfc", "-file", caPem.path,
            )

            val serverJks = File(dir, "server.jks")
            run(
                "-genkeypair", "-alias", "server", "-keyalg", "RSA", "-keysize", "2048", "-validity", "3650",
                "-keystore", serverJks.path, "-storepass", "changeit", "-dname", "CN=$LOOPBACK",
                "-ext", "san=ip:$LOOPBACK", "-storetype", "PKCS12",
            )
            val csr = File(dir, "server.csr")
            run(
                "-certreq", "-alias", "server", "-keystore", serverJks.path, "-storepass", "changeit",
                "-file", csr.path,
            )
            val signedCert = File(dir, "server.cer")
            run(
                "-gencert", "-alias", "ca", "-keystore", caJks.path, "-storepass", "changeit",
                "-infile", csr.path, "-outfile", signedCert.path, "-rfc", "-ext", "san=ip:$LOOPBACK",
                "-validity", "3650",
            )
            run(
                "-importcert", "-alias", "ca", "-keystore", serverJks.path, "-storepass", "changeit",
                "-file", caPem.path, "-noprompt",
            )
            run(
                "-importcert", "-alias", "server", "-keystore", serverJks.path, "-storepass", "changeit",
                "-file", signedCert.path,
            )

            val unrelatedJks = File(dir, "unrelated.jks")
            val unrelatedPem = File(dir, "unrelated.pem")
            run(
                "-genkeypair", "-alias", "unrelated", "-keyalg", "RSA", "-keysize", "2048", "-validity", "3650",
                "-keystore", unrelatedJks.path, "-storepass", "changeit", "-dname", "CN=Unrelated CA",
                "-ext", "bc:c=ca:true", "-storetype", "PKCS12",
            )
            run(
                "-exportcert", "-alias", "unrelated", "-keystore", unrelatedJks.path, "-storepass", "changeit",
                "-rfc", "-file", unrelatedPem.path,
            )

            return GeneratedCerts(caPem, serverJks, unrelatedPem)
        }

        /** `keytool(.exe)` vive junto al `java` que ejecuta el test, en cualquier plataforma. */
        private fun keytoolPath(): String {
            val binDir = File(System.getProperty("java.home"), "bin")
            val windows = File(binDir, "keytool.exe")
            return if (windows.exists()) windows.path else File(binDir, "keytool").path
        }
    }
}
