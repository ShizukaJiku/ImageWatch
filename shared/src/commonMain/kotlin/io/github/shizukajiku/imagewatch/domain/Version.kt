package io.github.shizukajiku.imagewatch.domain

/**
 * Una version publicada. El orden es semantico, no textual: `1.10.0` va despues de `1.9.0`. La
 * igualdad, en cambio, es por el texto original, porque dos cadenas distintas que ordenan igual
 * siguen siendo dos etiquetas distintas del registry.
 *
 * Implementa SemVer 2.0.0 estricto, que es lo que hacia semver4j: exige las tres cifras, rechaza
 * ceros a la izquierda y admite `-prerelease` y `+build`. Antes de analizar tolera tres cosas que
 * el registry sí publica: espacios alrededor, una `v` inicial y el sufijo `.RELEASE`, que se
 * normaliza a `-RELEASE` para que ordene como la pre-release que es.
 *
 * Lo que **no** acepta, igual que antes: `1.2`, `1`, `1.2.3.4`, `01.2.3` y cualquier etiqueta sin
 * forma de version, como `latest`. `VersionPollingService.parse` cuenta con ese rechazo para
 * degradar esa imagen a estado de error en vez de tumbar el ciclo.
 */
class Version(val value: String) : Comparable<Version> {
    private val major: Long
    private val minor: Long
    private val patch: Long

    /** Los identificadores de la pre-release, ya partidos por puntos. Vacia si no la hay. */
    private val preRelease: List<String>

    init {
        val normalizado = value.trim().removePrefix("v").removePrefix("V").replace(".RELEASE", "-RELEASE")
        val match = SEMVER.matchEntire(normalizado)
            ?: throw IllegalArgumentException("Versión no reconocida: $value")
        major = match.groupValues[1].toLong()
        minor = match.groupValues[2].toLong()
        patch = match.groupValues[3].toLong()
        preRelease = match.groupValues[4].takeIf { it.isNotEmpty() }?.split('.').orEmpty()
    }

    /**
     * Los metadatos de build no entran: el estandar dice que dos versiones que solo se diferencian
     * en ellos tienen la misma precedencia. Por eso no se guardan siquiera.
     */
    override fun compareTo(other: Version): Int {
        major.compareTo(other.major).let { if (it != 0) return it }
        minor.compareTo(other.minor).let { if (it != 0) return it }
        patch.compareTo(other.patch).let { if (it != 0) return it }
        return comparePreRelease(preRelease, other.preRelease)
    }

    override fun equals(other: Any?): Boolean = other is Version && value == other.value

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = value

    private companion object {
        /**
         * La expresion oficial de semver.org, sin el grupo de build: no se captura porque no se
         * usa. Los grupos `(0|[1-9]\d*)` son los que rechazan los ceros a la izquierda, y exigir
         * al menos un identificador tras `-` o `+` es lo que rechaza `1.2.3-` y `1.2.3+`.
         */
        private val SEMVER = Regex(
            "^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)" +
                "(?:-((?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*)" +
                "(?:\\.(?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*))*))?" +
                "(?:\\+([0-9a-zA-Z-]+(?:\\.[0-9a-zA-Z-]+)*))?$",
        )

        private val SOLO_DIGITOS = Regex("\\d+")

        /**
         * Regla 11.3 y 11.4 del estandar, en este orden:
         *
         * - Tener pre-release **rebaja**: `1.2.3-alpha` va antes que `1.2.3`.
         * - Identificador a identificador: los numericos comparan por valor, los alfanumericos por
         *   ASCII, y un numerico siempre va antes que uno alfanumerico.
         * - A igualdad de prefijo, gana el que tiene mas identificadores: `rc.1` < `rc.1.1`.
         */
        private fun comparePreRelease(left: List<String>, right: List<String>): Int {
            if (left.isEmpty() || right.isEmpty()) {
                // Una lista vacia es una version final, y la final gana. Vacias las dos, empatan.
                return when {
                    left.isEmpty() && right.isEmpty() -> 0
                    left.isEmpty() -> 1
                    else -> -1
                }
            }
            left.zip(right).forEach { (a, b) ->
                compareIdentifier(a, b).let { if (it != 0) return it }
            }
            return left.size.compareTo(right.size)
        }

        private fun compareIdentifier(left: String, right: String): Int {
            val leftNumeric = SOLO_DIGITOS.matches(left)
            val rightNumeric = SOLO_DIGITOS.matches(right)
            return when {
                leftNumeric && rightNumeric -> left.toLong().compareTo(right.toLong())
                leftNumeric -> -1
                rightNumeric -> 1
                else -> left.compareTo(right)
            }
        }
    }
}
