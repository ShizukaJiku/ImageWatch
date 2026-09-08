package io.github.shizukajiku.imagewatch.domain

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable

/**
 * Una publicación concreta de una imagen. `reference` es la referencia OCI completa
 * (`registry/nombre:tag`) y `publishedAt` es la marca de tiempo que declara el origen remoto, no el
 * momento en que se verificó localmente.
 *
 * Es serializable porque el almacén de versiones reconocidas la escribe tal cual: el formato en
 * disco de ese fichero *es* esta forma, y darle un DTO propio solo añadiría una traducción
 * idéntica en medio.
 */
@Serializable
data class ImageRelease(val name: String, val reference: String, val publishedAt: LocalDateTime)
