@file:OptIn(ExperimentalSerializationApi::class)

package io.github.shizukajiku.imagewatch.infrastructure.remote

import io.github.shizukajiku.imagewatch.domain.ImageRelease
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

/**
 * Formato de cable del origen remoto. Mantiene sus nombres de campo fuera del dominio.
 *
 * Los nombres alternativos no son decorado: el origen publica `productName` y `update_time`, y sin
 * ellos ninguna imagen mapearía y todas acabarían en estado de error.
 */
@Serializable
data class ReleaseDto(
    @JsonNames("productName") val name: String,
    @JsonNames("lastRelease") val lastRelease: String,
    @JsonNames("update_time") val updateTime: LocalDateTime,
) {
    fun toDomain() = ImageRelease(name, lastRelease, updateTime)
}
