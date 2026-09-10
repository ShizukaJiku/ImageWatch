package io.github.shizukajiku.imagewatch.infrastructure.update

import okio.ByteString.Companion.encodeUtf8
import okio.FileSystem
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

class MsiUpdateInstallerTest {

    private val fs = FileSystem.SYSTEM
    private val dir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "iw-installer-test-${System.nanoTime()}"

    private class FakeLauncher : ProcessLauncher {
        var command: List<String>? = null

        override fun launchDetached(command: List<String>, workingDir: File) {
            this.command = command
        }
    }

    @AfterTest
    fun cleanup() = fs.deleteRecursively(dir, mustExist = false)

    private fun installer(launcher: ProcessLauncher) = MsiUpdateInstaller(
        updatesDir = dir,
        fileSystem = fs,
        launcher = launcher,
        installedExe = "C:\\Users\\quien\\AppData\\Local\\ImageWatch\\ImageWatch.exe",
    )

    @Test
    fun `escribe un cmd con la secuencia esperada`() {
        fs.createDirectories(dir)
        val msi = dir / "ImageWatch-1.1.0.msi"
        fs.write(msi) { write("x".encodeUtf8()) }

        val result = installer(FakeLauncher()).apply(msi, appPid = 4242L)

        assertTrue(result.isSuccess)
        val script = fs.read(dir / "apply-update.cmd") { readUtf8() }
        // El PID llega como argumento del script (%1), no se hornea en el texto.
        assertContains(script, "PID eq %1")
        assertContains(script, "msiexec /i")
        assertContains(script, "ImageWatch-1.1.0.msi")
        assertContains(script, "/qb")
        assertContains(script, "/norestart")
        assertContains(script, "ImageWatch.exe")
        assertContains(script, "%~f0")
    }

    @Test
    fun `lanza el cmd suelto pasando el PID`() {
        fs.createDirectories(dir)
        val msi = dir / "ImageWatch-1.1.0.msi"
        fs.write(msi) { write("x".encodeUtf8()) }
        val launcher = FakeLauncher()

        installer(launcher).apply(msi, appPid = 99L)

        val command = launcher.command ?: error("no se lanzó nada")
        val joined = command.joinToString(" ")
        assertContains(joined, "apply-update.cmd")
        assertContains(joined, "99")
    }

    @Test
    fun `si no puede escribir el script devuelve failure`() {
        fs.createDirectories(dir)
        val blocker = dir / "blocker"
        fs.write(blocker) { write("x".encodeUtf8()) }
        val bad = MsiUpdateInstaller(
            updatesDir = blocker / "sub",
            fileSystem = fs,
            launcher = FakeLauncher(),
            installedExe = "C:\\x\\ImageWatch.exe",
        )

        val result = bad.apply((dir / "ImageWatch-1.1.0.msi"), appPid = 1L)

        assertTrue(result.isFailure)
    }
}
