package io.github.shizukajiku.imagewatch.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * El test que comprobaba el rechazo de `null` desapareció: `Version(value: String)` no admite
 * nulos y el compilador lo impide, así que ya no hay nada que ejercitar en tiempo de ejecución.
 *
 * Los casos a partir de [preReleasesOrderBeforeTheirRelease] registraban lo que hacía semver4j.
 * Se conservan intactos: ahora son la prueba de que la implementación propia lo reproduce.
 *
 * Los del final se añadieron al retirar la librería. Fijan las reglas que antes venían de fuera y
 * que nadie comprobaba aquí: qué se rechaza, y el orden entre identificadores de pre-release.
 */
class VersionTest {
    @Test
    fun unparseableValueIsRejectedAtConstruction() {
        val error = assertFailsWith<IllegalArgumentException> { Version("no-es-una-version") }
        assertTrue("no-es-una-version" in error.message!!)
    }

    @Test
    fun releaseSuffixIsNormalisedBeforeParsing() {
        assertEquals("1.2.3.RELEASE", Version("1.2.3.RELEASE").value)
    }

    @Test
    fun versionsCompareBySemanticOrderNotByText() {
        assertTrue(Version("1.10.0") > Version("1.9.0"))
    }

    @Test
    fun equalityIsBasedOnTheOriginalText() {
        assertEquals(Version("1.2.3"), Version("1.2.3"))
    }

    @Test
    fun preReleasesOrderBeforeTheirRelease() {
        assertTrue(Version("1.2.3-alpha") < Version("1.2.3"))
    }

    @Test
    fun preReleaseIdentifiersOrderAmongThemselves() {
        assertTrue(Version("1.2.3-alpha") < Version("1.2.3-beta"))
    }

    @Test
    fun buildMetadataDoesNotAffectOrder() {
        assertEquals(0, Version("1.2.3+build1").compareTo(Version("1.2.3+build2")))
    }

    @Test
    fun buildMetadataStillCountsForEquality() {
        assertTrue(Version("1.2.3+build1") != Version("1.2.3+build2"))
    }

    @Test
    fun tagsWithSuffixAreAccepted() {
        assertEquals("1.2.3-alpine", Version("1.2.3-alpine").value)
    }

    @Test
    fun incompleteOrMalformedValuesAreRejected() {
        // `VersionPollingService.parse` cuenta con este rechazo para degradar la imagen a estado de
        // error. Si alguna colara, la comparación se haría sobre algo que no es una versión.
        listOf("1.2", "1", "1.2.3.4", "01.2.3", "1.02.3", "latest", "1.2.3-", "1.2.3+", "")
            .forEach { assertFailsWith<IllegalArgumentException>("Debería rechazar '$it'") { Version(it) } }
    }

    @Test
    fun leadingVAndSurroundingSpacesAreTolerated() {
        assertEquals(0, Version("v1.2.3").compareTo(Version("  1.2.3  ")))
    }

    @Test
    fun numericPreReleaseIdentifiersOrderByValueNotByText() {
        assertTrue(Version("1.2.3-alpha.9") < Version("1.2.3-alpha.10"))
    }

    @Test
    fun numericPreReleaseIdentifiersOrderBeforeAlphanumericOnes() {
        assertTrue(Version("1.2.3-1") < Version("1.2.3-alpha"))
    }

    @Test
    fun aLongerPreReleaseWinsWhenThePrefixMatches() {
        assertTrue(Version("1.2.3-rc.1") < Version("1.2.3-rc.1.1"))
    }
}
