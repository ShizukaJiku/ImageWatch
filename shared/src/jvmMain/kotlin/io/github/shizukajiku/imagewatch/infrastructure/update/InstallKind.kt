package io.github.shizukajiku.imagewatch.infrastructure.update

import org.slf4j.LoggerFactory

enum class InstallKind { MSI, PORTABLE }

/** Lee el registro de Windows. Inyectable para test. */
fun interface RegistryReader {
    /** `true` si la clave existe. Puede lanzar; el llamador lo trata como "no está". */
    fun keyExists(path: String): Boolean
}

/**
 * El MSI por-usuario de jpackage deja una clave de desinstalación bajo
 * `HKCU\Software\Microsoft\Windows\CurrentVersion\Uninstall\`. El portable no toca el registro.
 * Consulta `reg.exe`, siempre en el PATH.
 */
object WindowsRegistryReader : RegistryReader {
    const val UNINSTALL_ROOT = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall"

    override fun keyExists(path: String): Boolean {
        val process = ProcessBuilder("reg", "query", path)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
        return process.waitFor() == 0
    }
}

private val log = LoggerFactory.getLogger("InstallKind")

/**
 * `MSI` si hay una entrada de ImageWatch bajo `Uninstall`; en cualquier otro caso -no hay clave, o
 * `reg.exe` falla- `PORTABLE`. Conservador a propósito: no ejecutar un MSI si no sabemos que esto
 * vino de un MSI.
 *
 * El nombre exacto de la subclave lo fija jpackage; se comprueba con una prueba manual sobre un
 * MSI real y se ajusta [SUBKEY] si difiere.
 */
fun detectInstallKind(reader: RegistryReader = WindowsRegistryReader): InstallKind = try {
    if (reader.keyExists("${WindowsRegistryReader.UNINSTALL_ROOT}\\$SUBKEY")) {
        InstallKind.MSI
    } else {
        InstallKind.PORTABLE
    }
} catch (e: Exception) {
    log.warn("No se pudo determinar el tipo de instalación; se asume portable", e)
    InstallKind.PORTABLE
}

private const val SUBKEY = "ImageWatch"
