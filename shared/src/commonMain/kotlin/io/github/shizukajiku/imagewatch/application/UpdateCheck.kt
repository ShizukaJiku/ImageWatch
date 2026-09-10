package io.github.shizukajiku.imagewatch.application

import io.github.shizukajiku.imagewatch.domain.SemanticVersion

/** El contrato que consume [UpdateService]. Lo implementa [UpdateChecker]. */
fun interface UpdateCheck {
    suspend fun check(current: SemanticVersion): UpdateStatus
}
