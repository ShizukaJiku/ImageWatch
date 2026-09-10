package io.github.shizukajiku.imagewatch.domain

/**
 * Versión de entrega en `MAYOR.MENOR.PARCHE`. Acepta un prefijo `v` opcional -los tags de GitHub
 * lo llevan-. Cualquier otra cosa -sin exactamente tres números, con texto, vacía- da `null`: el
 * llamador decide qué significa "esto no es una versión".
 */
data class SemanticVersion(val major: Int, val minor: Int, val patch: Int) : Comparable<SemanticVersion> {

    override fun compareTo(other: SemanticVersion): Int =
        compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch })

    override fun toString(): String = "$major.$minor.$patch"

    companion object {
        private val FORMAT = Regex("""v?(\d{1,9})\.(\d{1,9})\.(\d{1,9})""")

        fun parseOrNull(text: String): SemanticVersion? {
            val match = FORMAT.matchEntire(text.trim()) ?: return null
            val (major, minor, patch) = match.destructured
            return SemanticVersion(major.toInt(), minor.toInt(), patch.toInt())
        }
    }
}
