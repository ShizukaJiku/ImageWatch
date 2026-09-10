package io.github.shizukajiku.imagewatch.infrastructure.update

import io.github.shizukajiku.imagewatch.domain.UpdateManifest
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.readByteArray
import okio.FileSystem
import okio.HashingSink
import okio.Path
import okio.blackholeSink
import okio.buffer
import org.slf4j.LoggerFactory

sealed interface DownloadResult {
    data class Ready(val msi: Path) : DownloadResult

    data class Failed(val reason: String) : DownloadResult
}

/**
 * Baja el MSI y su `.sha256` a [updatesDir], verificando el hash **al vuelo** con `HashingSink` de
 * okio -sin cargar los ~100 MB en memoria-. Al empezar borra el contenido previo de la carpeta:
 * solo vive un candidato. Si ya hay un `.msi` con el hash correcto, no vuelve a bajarlo. Nunca
 * lanza -salvo cancelación-: todo error es un [DownloadResult.Failed] con motivo en castellano.
 */
class UpdateDownloader(
    private val client: HttpClient,
    private val updatesDir: Path,
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
) {
    private val log = LoggerFactory.getLogger(UpdateDownloader::class.java)

    suspend fun download(manifest: UpdateManifest, onProgress: (Float?) -> Unit): DownloadResult =
        withContext(Dispatchers.IO) {
            try {
                val msiName = "ImageWatch-${manifest.latestVersion}.msi"
                val msiPath = updatesDir / msiName
                val partPath = updatesDir / "$msiName.part"

                val expectedHash = fetchExpectedHash(manifest.sha256Url)
                    ?: return@withContext DownloadResult.Failed("Checksum ilegible")

                if (fileSystem.exists(msiPath) && hashOf(msiPath) == expectedHash) {
                    onProgress(1f)
                    return@withContext DownloadResult.Ready(msiPath)
                }

                prepareDir()

                val actualHash = streamToFile(manifest.msiUrl, partPath, onProgress)
                    ?: run {
                        fileSystem.delete(partPath, mustExist = false)
                        return@withContext DownloadResult.Failed("GitHub no sirvió el archivo")
                    }

                if (actualHash != expectedHash) {
                    fileSystem.delete(partPath, mustExist = false)
                    return@withContext DownloadResult.Failed("El MSI descargado no coincide con su checksum")
                }
                fileSystem.atomicMove(partPath, msiPath)
                DownloadResult.Ready(msiPath)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.warn("Fallo al descargar la actualización", e)
                DownloadResult.Failed(e.messageForUser())
            }
        }

    private fun prepareDir() {
        if (fileSystem.exists(updatesDir)) {
            fileSystem.list(updatesDir).forEach { fileSystem.delete(it, mustExist = false) }
        } else {
            fileSystem.createDirectories(updatesDir)
        }
    }

    private suspend fun fetchExpectedHash(url: String): String? {
        val response = client.get(url)
        if (!response.status.isSuccess()) return null
        val token = response.bodyAsText().trim().split(Regex("\\s+")).firstOrNull() ?: return null
        return token.lowercase().takeIf { it.matches(Regex("[0-9a-f]{64}")) }
    }

    private fun hashOf(path: Path): String {
        val hashingSink = HashingSink.sha256(blackholeSink())
        fileSystem.source(path).use { source ->
            hashingSink.buffer().use { it.writeAll(source) }
        }
        return hashingSink.hash.hex()
    }

    /** Devuelve el hash hex si la descarga completó; `null` si el HTTP no fue 2xx. */
    private suspend fun streamToFile(url: String, dest: Path, onProgress: (Float?) -> Unit): String? {
        var hash: String? = null
        client.prepareGet(url) { timeout { requestTimeoutMillis = 10 * 60 * 1000 } }.execute { response ->
            if (!response.status.isSuccess()) return@execute
            val total = response.contentLength()
            val channel = response.bodyAsChannel()
            val hashingSink = HashingSink.sha256(fileSystem.sink(dest))
            hashingSink.buffer().use { out ->
                var readSoFar = 0L
                while (true) {
                    val chunk = channel.readRemaining(64 * 1024L).readByteArray()
                    if (chunk.isEmpty()) break
                    out.write(chunk)
                    readSoFar += chunk.size
                    onProgress(total?.let { (readSoFar.toFloat() / it).coerceIn(0f, 1f) })
                }
            }
            hash = hashingSink.hash.hex()
            onProgress(1f)
        }
        return hash
    }

    private fun Exception.messageForUser(): String = when {
        this is okio.IOException && message?.contains("space", ignoreCase = true) == true ->
            "No hay espacio para la actualización"

        this is okio.IOException -> "No se pudo escribir la actualización en disco"

        else -> "Se interrumpió la descarga"
    }
}
