package io.github.shizukajiku.imagewatch.infrastructure.persistence

import io.github.shizukajiku.imagewatch.config.AppConfig
import io.github.shizukajiku.imagewatch.config.ThemePreference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

internal class JsonConfigStoreTest {
    @Test
    fun siembra_el_fichero_en_el_primer_arranque() = withTempDir { dir ->
        val file = dir.resolve("config.json")
        val store = JsonConfigStore(file, seed(dir.resolve("images.json").toString()))

        val loaded = store.load()

        assertTrue(JsonFiles.fileSystem.exists(file))
        assertEquals(300.seconds, loaded.pollInterval)
        assertEquals(listOf("alpha", "beta"), loaded.imageNames)
    }

    @Test
    fun el_fichero_manda_sobre_la_semilla() = withTempDir { dir ->
        val file = dir.resolve("config.json")
        val stateFile = dir.resolve("images.json").toString()
        JsonConfigStore(file, seed(stateFile)).load()

        val guardado =
            AppConfig(
                stateFile = stateFile,
                remoteUrl = "https://otro.ejemplo/api",
                pollInterval = 15.seconds,
                imageNames = listOf("gamma"),
                simulationMode = false,
                ignoreSslErrors = false,
                theme = ThemePreference.DARK,
                toastsEnabled = false,
                toastDuration = 4.seconds,
                soundsEnabled = false,
                soundVolume = 0.25,
                mutedAll = true,
                teamsEnabled = true,
                teamsWebhookUrl = "https://prod-01.westus.logic.azure.com/workflows/abc/triggers/manual/paths/invoke",
            )
        JsonConfigStore(file, seed(stateFile)).save(guardado)

        // Instancia nueva: lo que se comprueba es el fichero, no el estado en memoria.
        val releido = JsonConfigStore(file, seed(stateFile)).load()

        assertEquals("https://otro.ejemplo/api", releido.remoteUrl)
        assertEquals(15.seconds, releido.pollInterval)
        assertFalse(releido.simulationMode)
        assertEquals(ThemePreference.DARK, releido.theme)
        assertEquals(0.25, releido.soundVolume)
        assertTrue(releido.mutedAll)
        assertTrue(releido.teamsEnabled)
        assertEquals(
            "https://prod-01.westus.logic.azure.com/workflows/abc/triggers/manual/paths/invoke",
            releido.teamsWebhookUrl,
        )
    }

    @Test
    fun `un config json de una version anterior sin campos de teams carga con la integracion desactivada`() =
        withTempDir { dir ->
            val file = dir.resolve("config.json")
            val stateFile = dir.resolve("images.json").toString()
            // Formato de cable de antes de que existieran teamsEnabled/teamsWebhookUrl: kotlinx-serialization
            // exige que todo campo nuevo declare un valor por defecto para que esto siga cargando.
            JsonFiles.fileSystem.write(file) {
                writeUtf8(
                    """
                    {
                        "remoteUrl": "https://origen.ejemplo/api",
                        "pollIntervalSeconds": 300,
                        "imageNames": ["alpha"],
                        "simulationMode": true,
                        "ignoreSslErrors": true,
                        "theme": "SYSTEM",
                        "toastsEnabled": true,
                        "toastSeconds": 8,
                        "soundsEnabled": true,
                        "soundVolume": 0.5
                    }
                    """.trimIndent(),
                )
            }

            val loaded = JsonConfigStore(file, seed(stateFile)).load()

            assertFalse(loaded.teamsEnabled)
            assertEquals("", loaded.teamsWebhookUrl)
        }

    @Test
    fun la_ruta_del_estado_no_se_persiste_y_llega_desde_la_semilla() = withTempDir { dir ->
        val file = dir.resolve("config.json")
        JsonConfigStore(file, seed(dir.resolve("images.json").toString())).load()

        val otraRuta = dir.resolve("otro/images.json").toString()
        val releido = JsonConfigStore(file, seed(otraRuta)).load()

        assertEquals(otraRuta, releido.stateFile)
    }

    private companion object {
        private fun seed(stateFile: String) = AppConfig(
            stateFile = stateFile,
            remoteUrl = "https://origen.ejemplo/api",
            pollInterval = 300.seconds,
            imageNames = listOf("alpha", "beta"),
            simulationMode = true,
            ignoreSslErrors = true,
            theme = ThemePreference.SYSTEM,
            toastsEnabled = true,
            toastDuration = 8.seconds,
            soundsEnabled = true,
            soundVolume = 0.5,
            mutedAll = false,
            teamsEnabled = false,
            teamsWebhookUrl = "",
        )
    }
}
