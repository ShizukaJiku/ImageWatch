package io.github.shizukajiku.imagewatch.infrastructure.remote

import io.github.shizukajiku.imagewatch.application.ImageResult
import io.github.shizukajiku.imagewatch.application.ImageSource
import io.github.shizukajiku.imagewatch.domain.ImageRelease
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * Origen de prueba cuyas versiones avanzan con el tiempo. Es lo que permite observar el
 * comportamiento en tiempo real sin acceso al origen real.
 *
 * La versión se *deriva* del tiempo transcurrido en lugar de mantener un contador, de modo que el
 * resultado es determinista para un reloj dado y los tests no necesitan esperar.
 *
 * Cada imagen arranca con un desfase derivado de su nombre, para que los incrementos queden
 * escalonados y las filas cambien de una en una en lugar de todas a la vez.
 *
 * Cualquier nombre que contenga `fail` devuelve error siempre, lo que permite ejercitar el estado
 * de error y el aislamiento por imagen sin tocar la red.
 */
class SimulatedImageSource(private val clock: Clock, private val bumpEvery: Duration) : ImageSource {
    private val startedAt: Instant = clock.now()

    override suspend fun findByNames(names: List<String>): List<ImageResult> = names.map { simulate(it) }

    private fun simulate(name: String): ImageResult {
        if (name.contains(FAILING_NAME_MARKER)) {
            return ImageResult.failed(name, "Origen simulado: fallo forzado para '$name'")
        }
        val seconds = bumpEvery.inWholeSeconds
        val elapsed = (clock.now() - startedAt).inWholeSeconds
        val offset = name.hashCode().toLong().mod(seconds)
        val patch = (elapsed + offset) / seconds
        val reference = "$REGISTRY$name:1.0.$patch"
        val publishedAt = clock.now().toLocalDateTime(TimeZone.currentSystemDefault())
        return ImageResult.found(ImageRelease(name, reference, publishedAt))
    }

    private companion object {
        private const val FAILING_NAME_MARKER = "fail"
        private const val REGISTRY = "registry.local/"
    }
}
