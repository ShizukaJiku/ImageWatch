@file:OptIn(ExperimentalSerializationApi::class)

package io.github.shizukajiku.imagewatch.infrastructure.remote

import io.github.shizukajiku.imagewatch.domain.ImageRelease
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import kotlin.time.Instant

/**
 * Formato de cable del origen remoto. Mantiene sus nombres de campo fuera del dominio.
 *
 * Los nombres alternativos no son decorado: el origen publica `productName` y `update_time`, y sin
 * ellos ninguna imagen mapearía y todas acabarían en estado de error.
 *
 * `update_time` es un **instante ISO-8601 con offset** (`2026-09-10T07:17:32.946Z`), no una fecha
 * local: se parsea como [Instant] -que sí acepta la `Z` y la fracción de segundo- y se baja a la
 * hora local en [toDomain]. El dominio sigue hablando de `LocalDateTime`; el formato en disco no
 * cambia.
 */
@Serializable
data class ReleaseDto(
    @JsonNames("productName") val name: String,
    @JsonNames("lastRelease") val lastRelease: String,
    @JsonNames("update_time") val updateTime: Instant,
) {
    fun toDomain() = ImageRelease(
        name,
        lastRelease,
        updateTime.toLocalDateTime(TimeZone.currentSystemDefault()),
    )
}
