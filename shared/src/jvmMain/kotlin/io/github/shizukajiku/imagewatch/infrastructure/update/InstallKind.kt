package io.github.shizukajiku.imagewatch.infrastructure.update

import org.slf4j.LoggerFactory

enum class InstallKind { MSI, PORTABLE }

/** Lee el registro de Windows. Inyectable para test. */
fun interface RegistryReader {
    /**
     * `InstallLocation` de la entrada de «Programas instalados» cuyo `DisplayName` es
     * [displayName], o `null` si no hay ninguna. Puede lanzar; el llamador lo trata como "no está".
     */
    fun installLocationForDisplayName(displayName: String): String?
}

/**
 * Windows Installer registra el MSI de jpackage bajo una subclave con el *product code* (un GUID),
 * así que se identifica por `DisplayName` -que jpackage fija al `packageName`, «ImageWatch»-.
 *
 * Se miran **tres** ramas: aunque el instalador es por usuario y deja los ficheros en
 * `%LOCALAPPDATA%`, jpackage genera un MSI cuya entrada de «Programas y características» acaba en
 * `HKLM` -comprobado en una instalación real de la 1.0.3-. `HKCU` y `WOW6432Node` se cubren por si
 * una versión de jpackage o WiX cambia de sitio.
 *
 * Devuelve `InstallLocation` -no un booleano- para que [detectInstallKind] pueda comprobar que el
 * ejecutable en marcha **es** esa instalación, y no una copia portable conviviendo con un MSI de
 * otra cuenta.
 */
object WindowsRegistryReader : RegistryReader {
    private val UNINSTALL_ROOTS = listOf(
        "HKLM\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Uninstall",
        "HKLM\\SOFTWARE\\WOW6432Node\\Microsoft\\Windows\\CurrentVersion\\Uninstall",
        "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall",
    )

    // `    DisplayName    REG_SZ    ImageWatch` — nombre, tipo y dato separados por >=2 espacios.
    // El tipo (`REG_SZ`, ...) y el prefijo `HKEY_` no se traducen, así que el parseo es estable
    // en cualquier idioma de Windows.
    private val VALUE_LINE = Regex("""^\s+(\S+)\s{2,}REG_[A-Z_]+\s{2,}(.*\S)\s*$""")

    override fun installLocationForDisplayName(displayName: String): String? {
        for (root in UNINSTALL_ROOTS) {
            val dump = runReg(root) ?: continue
            var displayNameHere: String? = null
            var installLocationHere: String? = null
            for (line in dump.lineSequence()) {
                if (line.startsWith("HKEY_")) {
                    if (displayNameHere == displayName && installLocationHere != null) {
                        return installLocationHere
                    }
                    displayNameHere = null
                    installLocationHere = null
                    continue
                }
                val match = VALUE_LINE.matchEntire(line) ?: continue
                when (match.groupValues[1]) {
                    "DisplayName" -> displayNameHere = match.groupValues[2]
                    "InstallLocation" -> installLocationHere = match.groupValues[2]
                }
            }
            if (displayNameHere == displayName && installLocationHere != null) {
                return installLocationHere
            }
        }
        return null
    }

    private fun runReg(root: String): String? {
        val process = ProcessBuilder("reg", "query", root, "/s")
            .redirectErrorStream(false)
            .start()
        val text = process.inputStream.bufferedReader().use { it.readText() }
        return if (process.waitFor() == 0) text else null
    }
}

private val log = LoggerFactory.getLogger("InstallKind")

/**
 * `MSI` solo si hay una entrada de desinstalación de ImageWatch **y** el ejecutable en marcha vive
 * dentro de su `InstallLocation`. En cualquier otro caso -no hay entrada, la ruta no cuadra,
 * `reg.exe` falla, o no se conoce la ruta del ejecutable- `PORTABLE`. Conservador a propósito: no
 * ejecutar un MSI si no sabemos que **esta** copia vino de un MSI.
 */
fun detectInstallKind(
    currentExePath: String? = ProcessHandle.current().info().command().orElse(null),
    reader: RegistryReader = WindowsRegistryReader,
): InstallKind = try {
    val installLocation = reader.installLocationForDisplayName(DISPLAY_NAME)
    val exePath = currentExePath
    when {
        installLocation == null || exePath == null -> InstallKind.PORTABLE
        isInside(exePath, installLocation) -> InstallKind.MSI
        else -> InstallKind.PORTABLE
    }
} catch (e: Exception) {
    log.warn("No se pudo determinar el tipo de instalación; se asume portable", e)
    InstallKind.PORTABLE
}

/** `true` si `exePath` cuelga de `dir` (comparación tolerante a `/`, `\` finales y mayúsculas). */
private fun isInside(exePath: String, dir: String): Boolean {
    fun norm(p: String) = p.trim().replace('/', '\\').trimEnd('\\').lowercase()
    return norm(exePath).startsWith(norm(dir) + "\\")
}

/** El `DisplayName` que jpackage escribe, igual a `packageName` en `desktopApp/build.gradle.kts`. */
private const val DISPLAY_NAME = "ImageWatch"
