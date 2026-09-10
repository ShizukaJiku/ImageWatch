package io.github.shizukajiku.imagewatch.application

import io.github.shizukajiku.imagewatch.domain.SemanticVersion
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class UpdateCheckerTest {

    private val current = SemanticVersion(1, 0, 0)

    private fun checker(handler: MockRequestHandler) =
        UpdateChecker(HttpClient(MockEngine(handler)), releasesUrl = "https://api.example.test/latest")

    private fun jsonBody(tag: String, assets: List<Pair<String, String>>, notes: String = "notas"): String {
        val assetsJson = assets.joinToString(",") { (name, url) ->
            """{"name":"$name","browser_download_url":"$url","size":123}"""
        }
        return """{"tag_name":"$tag","name":"ImageWatch $tag","body":"$notes","assets":[$assetsJson]}"""
    }

    private fun jsonHeaders() = headersOf("Content-Type", "application/json")

    @Test
    fun `version mayor con MSI y checksum da Available`() = runTest {
        val body = jsonBody(
            "v1.2.0",
            listOf(
                "ImageWatch-1.2.0.msi" to "https://dl.example.test/ImageWatch-1.2.0.msi",
                "ImageWatch-1.2.0.msi.sha256" to "https://dl.example.test/ImageWatch-1.2.0.msi.sha256",
            ),
        )
        val status = checker { respond(body, HttpStatusCode.OK, jsonHeaders()) }.check(current)

        val available = status as? UpdateStatus.Available ?: fail("esperaba Available, fue $status")
        assertEquals(SemanticVersion(1, 2, 0), available.manifest.latestVersion)
        assertEquals("https://dl.example.test/ImageWatch-1.2.0.msi", available.manifest.msiUrl)
        assertEquals("https://dl.example.test/ImageWatch-1.2.0.msi.sha256", available.manifest.sha256Url)
        assertEquals("notas", available.manifest.notes)
    }

    @Test
    fun `misma version o menor da UpToDate`() = runTest {
        val body = jsonBody(
            "v1.0.0",
            listOf(
                "ImageWatch-1.0.0.msi" to "https://dl.example.test/a.msi",
                "ImageWatch-1.0.0.msi.sha256" to "https://dl.example.test/a.msi.sha256",
            ),
        )
        val status = checker { respond(body, HttpStatusCode.OK, jsonHeaders()) }.check(current)
        assertEquals(UpdateStatus.UpToDate, status)
    }

    @Test
    fun `falta el asset del checksum da CheckFailed`() = runTest {
        val body = jsonBody("v1.2.0", listOf("ImageWatch-1.2.0.msi" to "https://dl.example.test/a.msi"))
        val status = checker { respond(body, HttpStatusCode.OK, jsonHeaders()) }.check(current)
        val failed = status as? UpdateStatus.CheckFailed ?: fail("esperaba CheckFailed, fue $status")
        assertTrue(failed.reason.contains("verificable"))
    }

    @Test
    fun `asset con URL no https da CheckFailed`() = runTest {
        val body = jsonBody(
            "v1.2.0",
            listOf(
                "ImageWatch-1.2.0.msi" to "http://dl.example.test/a.msi",
                "ImageWatch-1.2.0.msi.sha256" to "http://dl.example.test/a.msi.sha256",
            ),
        )
        val status = checker { respond(body, HttpStatusCode.OK, jsonHeaders()) }.check(current)
        assertTrue(status is UpdateStatus.CheckFailed)
    }

    @Test
    fun `HTTP 403 da el motivo de rate limit`() = runTest {
        val status = checker { respond("", HttpStatusCode.Forbidden) }.check(current)
        val failed = status as? UpdateStatus.CheckFailed ?: fail("esperaba CheckFailed")
        assertTrue(failed.reason.contains("limitó"))
    }

    @Test
    fun `HTTP 404 da el motivo de sin releases`() = runTest {
        val status = checker { respond("", HttpStatusCode.NotFound) }.check(current)
        val failed = status as? UpdateStatus.CheckFailed ?: fail("esperaba CheckFailed")
        assertTrue(failed.reason.contains("Release"))
    }

    @Test
    fun `cuerpo no JSON da CheckFailed`() = runTest {
        val status = checker { respond("<html>nope</html>", HttpStatusCode.OK) }.check(current)
        assertTrue(status is UpdateStatus.CheckFailed)
    }

    @Test
    fun `tag no semver da CheckFailed`() = runTest {
        val body = jsonBody("nightly", emptyList())
        val status = checker { respond(body, HttpStatusCode.OK, jsonHeaders()) }.check(current)
        assertTrue(status is UpdateStatus.CheckFailed)
    }

    @Test
    fun `llaves extra en el JSON se ignoran`() = runTest {
        val body = """
            {"tag_name":"v1.2.0","body":"n","draft":false,"prerelease":false,"extra":{"x":1},
             "assets":[
               {"name":"ImageWatch-1.2.0.msi","browser_download_url":"https://d.test/a.msi","uploader":{"id":1}},
               {"name":"ImageWatch-1.2.0.msi.sha256","browser_download_url":"https://d.test/a.msi.sha256"}
             ]}
        """.trimIndent()
        val status = checker { respond(body, HttpStatusCode.OK, jsonHeaders()) }.check(current)
        assertTrue(status is UpdateStatus.Available)
    }

    @Test
    fun `sin red da el motivo de no se pudo contactar`() = runTest {
        val status = checker { throw RuntimeException("sin red") }.check(current)
        val failed = status as? UpdateStatus.CheckFailed ?: fail("esperaba CheckFailed")
        assertTrue(failed.reason.contains("contactar"))
    }
}
