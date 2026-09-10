package io.github.shizukajiku.imagewatch.infrastructure.update

import org.slf4j.LoggerFactory

enum class InstallKind { MSI, PORTABLE }

/** Lee el registro de Windows. Inyectable para test. */
fun interface RegistryReader {
    /**
     * `true` si `displayName` aparece como dato exacto de algún valor -normalmente `DisplayName`-
     * bajo alguna de las ramas de «Programas instalados». Puede lanzar; el llamador lo trata como
     * "no está".
     */
    fun uninstallEntryExists(displayName: String): Boolean
}

/**
 * Windows Installer registra el MSI de jpackage bajo una subclave con el *product code* (un GUID),
 * no bajo un nombre legible, así que se busca por `DisplayName` -que jpackage fija al `packageName`,
 * «ImageWatch»- en todo el subárbol.
 *
 * Se miran **tres** ramas: aunque el instalador es por usuario y deja los ficheros en
 * `%LOCALAPPDATA%`, jpackage genera un MSI cuya entrada de «Programas y características» acaba en
 * `HKLM` -comprobado en una instalación real de la 1.0.3-. `HKCU` y `WOW6432Node` se cubren por si
 * una versión de jpackage o WiX cambia de sitio.
 */
object WindowsRegistryReader : RegistryReader {
    private val UNINSTALL_ROOTS = listOf(
        "HKLM\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Uninstall",
        "HKLM\\SOFTWARE\\WOW6432Node\\Microsoft\\Windows\\CurrentVersion\\Uninstall",
        "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall",
    )

    override fun uninstallEntryExists(displayName: String): Boolean =
        UNINSTALL_ROOTS.any { root -> matchesExactData(root, displayName) }

    /** `reg query <root> /s /f <term> /d /e` -recursivo, dato exacto-: sale 0 si algo casa. */
    private fun matchesExactData(root: String, term: String): Boolean {
        val process = ProcessBuilder("reg", "query", root, "/s", "/f", term, "/d", "/e")
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
        return process.waitFor() == 0
    }
}

private val log = LoggerFactory.getLogger("InstallKind")

/**
 * `MSI` si hay una entrada de desinstalación de ImageWatch; en cualquier otro caso -no hay entrada,
 * o `reg.exe` falla- `PORTABLE`. Conservador a propósito: no ejecutar un MSI si no sabemos que esto
 * vino de un MSI.
 */
fun detectInstallKind(reader: RegistryReader = WindowsRegistryReader): InstallKind = try {
    if (reader.uninstallEntryExists(DISPLAY_NAME)) InstallKind.MSI else InstallKind.PORTABLE
} catch (e: Exception) {
    log.warn("No se pudo determinar el tipo de instalación; se asume portable", e)
    InstallKind.PORTABLE
}

/** El `DisplayName` que jpackage escribe, igual a `packageName` en `desktopApp/build.gradle.kts`. */
private const val DISPLAY_NAME = "ImageWatch"
