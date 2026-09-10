package io.github.shizukajiku.imagewatch.infrastructure.os

import io.github.shizukajiku.imagewatch.application.AutostartPort
import org.slf4j.LoggerFactory

/**
 * Arranque al iniciar sesión mediante la clave `Run` del registro de usuario de Windows. Se usa
 * `reg.exe` -siempre en el PATH- y no `java.util.prefs`, cuyas preferencias viven en otra rama del
 * registro (`HKCU\Software\JavaSoft\Prefs`), no en `Run`. Así tampoco hace falta un módulo de
 * jlink nuevo.
 *
 * @param label nombre del valor bajo `Run` (p. ej. «ImageWatch»).
 * @param command ejecutable y argumentos con los que relanzar la app; se unen entre comillas.
 */
class WindowsAutostart(private val label: String, private val command: List<String>) : AutostartPort {
    private val runKey = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run"

    override fun isEnabled(): Boolean = run("reg", "query", runKey, "/v", label) == 0

    override fun setEnabled(enabled: Boolean) {
        // Sin comillas embebidas: `ProcessBuilder` en Windows re-escapa los argumentos y un `"`
        // dentro del valor de `/d` acaba mal formado. Se quota solo el ejecutable, y solo si
        // lleva un espacio. ponytail: si el exe instalado alguna vez tiene espacios en la ruta y
        // esto falla, envolver el valor entero.
        val exe = command.first().let { if (it.contains(' ')) "\"$it\"" else it }
        val value = (listOf(exe) + command.drop(1)).joinToString(" ")
        val code = if (enabled) {
            run("reg", "add", runKey, "/v", label, "/t", "REG_SZ", "/d", value, "/f")
        } else {
            // /f: borrar una clave ausente no es un error.
            run("reg", "delete", runKey, "/v", label, "/f")
        }
        if (code != 0 && enabled) {
            LOG.warn("No se pudo escribir la clave de arranque «{}» (reg.exe salió con {})", label, code)
        }
    }

    private fun run(vararg args: String): Int = try {
        ProcessBuilder(*args)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
            .waitFor()
    } catch (e: Exception) {
        LOG.warn("No se pudo ejecutar reg.exe", e)
        -1
    }

    private companion object {
        private val LOG = LoggerFactory.getLogger(WindowsAutostart::class.java)
    }
}
