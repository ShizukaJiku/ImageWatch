plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.kover) apply false
    alias(libs.plugins.spotless)
}

spotless {
    kotlin {
        // El estilo es el de IntelliJ IDEA, no el oficial de ktlint: es el que produce el codigo
        // que el IDE deja al formatear, y evita que cada cadena de llamadas se parta en varios
        // renglones. La excepcion de nombrado es porque los composables van en PascalCase por
        // convencion de Compose, y ktlint no lo sabe por si solo.
        ktlint().editorConfigOverride(
            mapOf(
                "ktlint_code_style" to "intellij_idea",
                // El margen derecho por defecto del IDE. Sin limite, ktlint junta en un renglon
                // cualquier cuerpo de expresion que quepa, por largo que sea.
                "max_line_length" to "120",
                "ktlint_function_naming_ignore_when_annotated_with" to "Composable",
            ),
        )
        target("**/src/**/*.kt")
    }
    kotlinGradle {
        ktlint()
    }
}
