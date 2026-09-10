package io.github.shizukajiku.imagewatch.infrastructure.update

import org.slf4j.LoggerFactory

enum class InstallKind { MSI, PORTABLE }

/** Lee el registro de Windows. Inyectable para test. */
fun interface RegistryReader {
    /**
     * `true` si `term` aparece como dato exacto de algún valor en el subárbol de `root`
     * (`reg query <root> /s /f <term> /d /e`). Puede lanzar; el llamador lo trata como "no está".
     */
    fun subtreeContains(root: String, term: String): Boolean
}

/**
 * Windows Installer registra el MSI de jpackage bajo una subclave con el *product code* (un GUID),
 * no bajo un nombre legible: buscar `Uninstall\ImageWatch` no encuentra nada. Lo que sí es estable
 * es el `DisplayName`, que jpackage fija al `packageName` («ImageWatch»). Se busca ese dato en todo
 * el subárbol.
 *
 * Solo `HKCU`: el instalador es por usuario (`perUserInstall = true`), así que la entrada de
 * desinstalación vive en la rama del usuario, no en `HKLM`.
 */
object WindowsRegistryReader : RegistryReader {
    const val UNINSTALL_ROOT = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall"

    override fun subtreeContains(root: String, term: String): Boolean {
        val process = ProcessBuilder("reg", "query", root, "/s", "/f", term, "/d", "/e")
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
        return process.waitFor() == 0
    }
}

private val log = LoggerFactory.getLogger("InstallKind")

/**
 * `MSI` si hay una entrada de desinstalación de ImageWatch en `Uninstall`; en cualquier otro caso
 * -no hay entrada, o `reg.exe` falla- `PORTABLE`. Conservador a propósito: no ejecutar un MSI si no
 * sabemos que esto vino de un MSI.
 */
fun detectInstallKind(reader: RegistryReader = WindowsRegistryReader): InstallKind = try {
    if (reader.subtreeContains(WindowsRegistryReader.UNINSTALL_ROOT, DISPLAY_NAME)) {
        InstallKind.MSI
    } else {
        InstallKind.PORTABLE
    }
} catch (e: Exception) {
    log.warn("No se pudo determinar el tipo de instalación; se asume portable", e)
    InstallKind.PORTABLE
}

/** El `DisplayName` que jpackage escribe, igual a `packageName` en `desktopApp/build.gradle.kts`. */
private const val DISPLAY_NAME = "ImageWatch"
