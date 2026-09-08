plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.kover)
}

kotlin {
    jvm()

    // Las clases expect/actual siguen marcadas como Beta y el compilador avisa por cada una. El
    // mecanismo es estable en la practica y las cuatro costuras de este proyecto dependen de el.
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.kotlinx.datetime)
            // api por lo mismo que okio: `HttpImageSource` recibe un `HttpClient` de Ktor por
            // constructor, asi que el tipo forma parte de la API publica de `shared`.
            api(libs.ktor.clientCore)
            // api y no implementation: los constructores de los almacenes JSON exponen
            // `okio.Path`, asi que es parte de la API publica de `shared`, no un detalle suyo.
            api(libs.okio)
            implementation(libs.kotlinx.serializationJson)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutinesTest)
            implementation(libs.ktor.clientMock)
        }
        jvmMain.dependencies {
            implementation(libs.slf4j.api)
            implementation(libs.kotlinx.coroutinesSwing)
            implementation(libs.ktor.clientOkhttp)
        }
        jvmTest.dependencies {
            // Los binarios nativos de Skia. `SvgIconTest` llama a loadSvgPainter, que los carga
            // en tiempo de ejecucion; el resto de `shared` compila solo contra la API de Compose
            // y no los necesita. runtimeOnly y no implementation: no hay nada contra lo que
            // compilar.
            runtimeOnly(compose.desktop.currentOs)
            // JUnit y AssertJ se fueron con T20: los dos unicos tests que quedan aqui, los de
            // `Sounds` y `SvgIcon`, ya usaban `kotlin.test`. El lanzador sigue porque
            // `useJUnitPlatform()` lo necesita para ejecutar lo que `kotlin.test` resuelve a
            // JUnit 5 por variantes.
            runtimeOnly(libs.junit.platform.launcher)
        }
    }
}

// Con la plataforma JUnit configurada, `kotlin-test` resuelve por variantes a su version de
// JUnit 5. Sin esta linea caeria a JUnit 4 y los tests de `jvmTest`, escritos con Jupiter, no se
// ejecutarian.
tasks.named<Test>("jvmTest") {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

kover {
    reports {
        // El filtro va aqui y no dentro de `rule`: el marcador de DSL de kover impide llamar a
        // `filters` desde una regla, asi que el alcance se acota una vez para todos los informes.
        filters {
            includes {
                // El umbral mide el nucleo, no la interfaz: la cobertura de composables cuenta
                // recomposiciones, no comportamiento, y poner un numero ahi es teatro.
                classes("io.github.shizukajiku.imagewatch.domain.*")
                classes("io.github.shizukajiku.imagewatch.application.*")
            }
        }
        verify {
            rule {
                minBound(80)
            }
        }
    }
}
