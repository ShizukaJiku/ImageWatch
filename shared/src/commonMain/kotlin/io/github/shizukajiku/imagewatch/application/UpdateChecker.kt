package io.github.shizukajiku.imagewatch.application

import io.github.shizukajiku.imagewatch.domain.SemanticVersion
import io.github.shizukajiku.imagewatch.domain.UpdateManifest
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Pregunta a la API pública de GitHub si hay un Release más nuevo que `current` y si trae MSI
 * verificable. Sin token -límite anónimo de 60/h, de sobra-. Nunca lanza: todo error del camino
 * -sin red, HTTP raro, JSON ilegible, Release a medias- se devuelve como [UpdateStatus.CheckFailed]
 * con un motivo en castellano.
 */
class UpdateChecker(
    private val client: HttpClient,
    private val releasesUrl: String = "https://api.github.com/repos/ShizukaJiku/ImageWatch/releases/latest",
) : UpdateCheck {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun check(current: SemanticVersion): UpdateStatus {
        val fetched = fetch() ?: return UpdateStatus.CheckFailed("No se pudo contactar con GitHub")
        if (fetched is Failure) return UpdateStatus.CheckFailed(fetched.reason)

        val release = runCatching { json.decodeFromString<ReleaseJson>((fetched as Success).text) }
            .getOrElse { return UpdateStatus.CheckFailed("Respuesta de GitHub inesperada") }

        val latest = SemanticVersion.parseOrNull(release.tagName)
            ?: return UpdateStatus.CheckFailed("Respuesta de GitHub inesperada")
        if (latest <= current) return UpdateStatus.UpToDate

        val msiName = "ImageWatch-$latest.msi"
        val msi = release.assets.firstOrNull { it.name == msiName }
        val sha = release.assets.firstOrNull { it.name == "$msiName.sha256" }
        if (msi == null || sha == null) {
            return UpdateStatus.CheckFailed("El Release v$latest no trae MSI verificable")
        }
        if (!msi.url.startsWith("https://") || !sha.url.startsWith("https://")) {
            return UpdateStatus.CheckFailed("El Release v$latest tiene enlaces no seguros")
        }
        return UpdateStatus.Available(UpdateManifest(latest, release.body.orEmpty(), msi.url, sha.url))
    }

    private sealed interface Fetched
    private data class Success(val text: String) : Fetched
    private data class Failure(val reason: String) : Fetched

    private suspend fun fetch(): Fetched? = try {
        val response = client.get(releasesUrl) {
            header("Accept", "application/vnd.github+json")
            header("User-Agent", "ImageWatch")
        }
        when (response.status.value) {
            403 -> Failure("GitHub limitó las consultas; reintenta más tarde")

            404 -> Failure("No hay ningún Release publicado")

            else -> if (response.status.isSuccess()) {
                Success(response.bodyAsText())
            } else {
                Failure("GitHub respondió con HTTP ${response.status.value}")
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    @Serializable
    private data class ReleaseJson(
        @SerialName("tag_name") val tagName: String,
        val body: String? = null,
        val assets: List<AssetJson> = emptyList(),
    )

    @Serializable
    private data class AssetJson(val name: String, @SerialName("browser_download_url") val url: String)
}
