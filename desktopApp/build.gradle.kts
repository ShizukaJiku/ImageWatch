import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(project(":shared"))

    implementation(compose.desktop.currentOs)
    // Main.kt usa Surface directamente. `shared` declara material3 como implementation, asi que
    // no se hereda: cada modulo declara aquello contra lo que compila.
    implementation(libs.compose.material3)
    implementation(libs.kotlinx.coroutinesSwing)
    implementation(libs.compose.uiToolingPreview)
    implementation(libs.slf4j.api)
    // runtimeOnly a proposito: el codigo compila solo contra la fachada, asi no puede acoplarse a
    // la implementacion por accidente.
    runtimeOnly(libs.logback.classic)
}

compose.desktop {
    application {
        mainClass = "io.github.shizukajiku.imagewatch.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi)
            // El runtime que arma jlink es minimo, y sin estos cuatro modulos la aplicacion
            // instalada muere en el arranque con "Failed to launch JVM". Los tres primeros los
            // dice `./gradlew :desktopApp:suggestRuntimeModules`; el cuarto NO, y ese es el que
            // rompia: logback llega a JNDI por reflexion al inicializarse, jdeps no lo ve, y sin
            // `java.naming` la primera llamada a LoggerFactory lanza ClassNotFoundException.
            //
            // La lista cambio con Ktor: `java.net.http` salio -ya nadie usa el cliente del JDK- y
            // entro `java.management`, que arrastra el motor OkHttp. Conviene revisarla cada vez
            // que cambie una dependencia de red o de registro.
            modules("java.instrument", "java.management", "jdk.unsupported", "java.naming")
            packageName = "ImageWatch"
            // Aparte de la version del proyecto a proposito: MSI exige MAYOR.MENOR.PARCHE con
            // mayor > 0, y "1.0-SNAPSHOT" no lo es.
            packageVersion = "1.0.1"
            description = "Vigila las versiones de imagenes de contenedor publicadas en un registry"
            vendor = "ShizukaJiku"

            windows {
                menu = true
                shortcut = true
                dirChooser = true
                // Instala bajo el perfil del usuario (AppData\Local) en vez de Program Files: sin
                // esto jpackage pide elevacion de administrador para escribir en Program Files
                // aunque la aplicacion no la necesite para nada mas.
                perUserInstall = true
                // Fijo para siempre: si cambia, Windows instala un segundo ImageWatch en lugar de
                // actualizar el que ya hay.
                upgradeUuid = "fd333cd8-6981-4e85-8e89-db2f23608c85"
            }
        }
    }
}
