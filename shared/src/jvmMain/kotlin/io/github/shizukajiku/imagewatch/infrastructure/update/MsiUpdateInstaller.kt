package io.github.shizukajiku.imagewatch.infrastructure.update

import okio.FileSystem
import okio.Path
import org.slf4j.LoggerFactory
import java.io.File

/** Lanza un proceso sin esperarlo ni heredar sus streams. Inyectable para test. */
fun interface ProcessLauncher {
    fun launchDetached(command: List<String>, workingDir: File)
}

object RealProcessLauncher : ProcessLauncher {
    override fun launchDetached(command: List<String>, workingDir: File) {
        ProcessBuilder(command)
            .directory(workingDir)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
    }
}

/**
 * Escribe `apply-update.cmd` en [updatesDir] y lo lanza suelto. El script espera a que el PID de
 * la app muera, corre `msiexec /qb` -que en un MSI por-usuario no pide UAC-, relanza el exe nuevo,
 * y se autoborra con los descargables. Si `msiexec` falla, deja `apply-update.log` y **no** relanza
 * nada: la instalación previa sigue intacta.
 *
 * [apply] devuelve `failure` solo si no pudo dejar el script en disco. En ese caso el llamador
 * **no** debe cerrar la app.
 */
class MsiUpdateInstaller(
    private val updatesDir: Path,
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
    private val launcher: ProcessLauncher = RealProcessLauncher,
    private val installedExe: String = defaultInstalledExe(),
) {
    private val log = LoggerFactory.getLogger(MsiUpdateInstaller::class.java)

    fun apply(msi: Path, appPid: Long): Result<Unit> = runCatching {
        fileSystem.createDirectories(updatesDir)
        val scriptPath = updatesDir / SCRIPT_NAME
        fileSystem.write(scriptPath) { writeUtf8(scriptText(msi.name)) }

        launcher.launchDetached(
            command = listOf("cmd", "/c", scriptPath.toString(), appPid.toString()),
            workingDir = File(updatesDir.toString()),
        )
    }.onFailure { log.error("No se pudo preparar la instalación de la actualización", it) }
        .map { }

    private fun scriptText(msiName: String): String = buildString {
        appendLine("@echo off")
        appendLine("rem %1 = PID de la app que se esta cerrando")
        appendLine(":wait")
        appendLine("tasklist /fi \"PID eq %1\" | find \"%1\" >nul && (timeout /t 1 /nobreak >nul & goto wait)")
        appendLine()
        appendLine("msiexec /i \"%~dp0$msiName\" /qb /norestart")
        appendLine("if errorlevel 1 (")
        appendLine("  echo La instalacion fallo con codigo %errorlevel% > \"%~dp0apply-update.log\"")
        appendLine("  exit /b %errorlevel%")
        appendLine(")")
        appendLine()
        appendLine("start \"\" \"$installedExe\"")
        appendLine("del \"%~dp0$msiName\" \"%~dp0$msiName.sha256\" 2>nul")
        appendLine("(goto) 2>nul & del \"%~f0\"")
    }

    private companion object {
        const val SCRIPT_NAME = "apply-update.cmd"

        fun defaultInstalledExe(): String {
            val localAppData = System.getenv("LOCALAPPDATA")
                ?: (System.getProperty("user.home") + "\\AppData\\Local")
            return "$localAppData\\ImageWatch\\ImageWatch.exe"
        }
    }
}
