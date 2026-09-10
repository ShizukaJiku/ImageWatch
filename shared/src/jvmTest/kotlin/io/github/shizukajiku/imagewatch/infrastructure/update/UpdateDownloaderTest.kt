package io.github.shizukajiku.imagewatch.infrastructure.update

import io.github.shizukajiku.imagewatch.domain.SemanticVersion
import io.github.shizukajiku.imagewatch.domain.UpdateManifest
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import okio.ByteString.Companion.toByteString
import okio.FileSystem
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

class UpdateDownloaderTest {

    private val fs = FileSystem.SYSTEM
    private val dir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "iw-update-test-${System.nanoTime()}"

    private val msiBytes = "contenido-msi-de-prueba".encodeToByteArray()
    private val msiHash = msiBytes.toByteString().sha256().hex()

    private fun manifest() = UpdateManifest(
        SemanticVersion(1, 1, 0),
        "notas",
        "https://d.test/ImageWatch-1.1.0.msi",
        "https://d.test/ImageWatch-1.1.0.msi.sha256",
    )

    private fun downloader(engine: MockEngine) = UpdateDownloader(HttpClient(engine), dir, fs)

    private fun engine(sha256Body: String) = MockEngine { req ->
        if (req.url.encodedPath.endsWith(".sha256")) {
            respond(sha256Body, HttpStatusCode.OK)
        } else {
            respond(msiBytes, HttpStatusCode.OK, headersOf("Content-Length", msiBytes.size.toString()))
        }
    }

    @AfterTest
    fun cleanup() = fs.deleteRecursively(dir, mustExist = false)

    @Test
    fun `msi con checksum correcto queda Ready sin part`() = runTest {
        val result = downloader(engine("$msiHash  ImageWatch-1.1.0.msi")).download(manifest()) {}

        val ready = result as? DownloadResult.Ready ?: fail("esperaba Ready, fue $result")
        assertTrue(fs.exists(ready.msi))
        assertFalse(fs.exists(dir / "ImageWatch-1.1.0.msi.part"))
    }

    @Test
    fun `checksum que no coincide borra el part y falla`() = runTest {
        val result = downloader(engine("${"0".repeat(64)}  ImageWatch-1.1.0.msi")).download(manifest()) {}

        val failed = result as? DownloadResult.Failed ?: fail("esperaba Failed, fue $result")
        assertTrue(failed.reason.contains("checksum"))
        assertFalse(fs.exists(dir / "ImageWatch-1.1.0.msi"))
        assertFalse(fs.exists(dir / "ImageWatch-1.1.0.msi.part"))
    }

    @Test
    fun `checksum ilegible falla`() = runTest {
        val result = downloader(engine("no-es-un-hash")).download(manifest()) {}
        assertTrue(result is DownloadResult.Failed)
    }

    @Test
    fun `HTTP 500 en el MSI falla y deja la carpeta limpia`() = runTest {
        val engine = MockEngine { req ->
            if (req.url.encodedPath.endsWith(".sha256")) {
                respond("$msiHash  ImageWatch-1.1.0.msi", HttpStatusCode.OK)
            } else {
                respondError(HttpStatusCode.InternalServerError)
            }
        }
        val result = downloader(engine).download(manifest()) {}
        assertTrue(result is DownloadResult.Failed)
        assertFalse(fs.exists(dir / "ImageWatch-1.1.0.msi"))
    }

    @Test
    fun `una descarga nueva borra un part viejo`() = runTest {
        fs.createDirectories(dir)
        fs.write(dir / "ImageWatch-1.1.0.msi.part") { writeUtf8("basura") }

        val result = downloader(engine("$msiHash  ImageWatch-1.1.0.msi")).download(manifest()) {}
        assertTrue(result is DownloadResult.Ready)
        assertFalse(fs.exists(dir / "ImageWatch-1.1.0.msi.part"))
    }

    @Test
    fun `un MSI ya descargado con hash correcto salta la descarga`() = runTest {
        fs.createDirectories(dir)
        fs.write(dir / "ImageWatch-1.1.0.msi") { write(msiBytes.toByteString()) }

        var msiRequested = false
        val engine = MockEngine { req ->
            if (req.url.encodedPath.endsWith(".sha256")) {
                respond("$msiHash  ImageWatch-1.1.0.msi", HttpStatusCode.OK)
            } else {
                msiRequested = true
                respond(msiBytes, HttpStatusCode.OK)
            }
        }
        val result = downloader(engine).download(manifest()) {}
        assertTrue(result is DownloadResult.Ready)
        assertFalse(msiRequested, "no debe volver a bajar el MSI")
    }

    @Test
    fun `reporta progreso y termina en 1`() = runTest {
        val fractions = mutableListOf<Float?>()
        downloader(engine("$msiHash  ImageWatch-1.1.0.msi")).download(manifest()) { fractions.add(it) }
        assertTrue(fractions.isNotEmpty())
        assertEquals(1f, fractions.last())
    }
}
