package io.github.shizukajiku.imagewatch.infrastructure.update

import kotlin.test.Test
import kotlin.test.assertEquals

class InstallKindTest {

    private val installLocation = "C:\\Users\\quien\\AppData\\Local\\ImageWatch\\"
    private val installedExe = "C:\\Users\\quien\\AppData\\Local\\ImageWatch\\ImageWatch.exe"

    @Test
    fun `el ejecutable dentro del InstallLocation registrado es MSI`() {
        val kind = detectInstallKind(
            currentExePath = installedExe,
            reader = RegistryReader { name -> if (name == "ImageWatch") installLocation else null },
        )
        assertEquals(InstallKind.MSI, kind)
    }

    @Test
    fun `hay entrada MSI pero el ejecutable esta en otra carpeta (portable conviviendo)`() {
        val kind = detectInstallKind(
            currentExePath = "D:\\portatil\\ImageWatch\\ImageWatch.exe",
            reader = RegistryReader { installLocation },
        )
        assertEquals(InstallKind.PORTABLE, kind)
    }

    @Test
    fun `sin entrada de desinstalacion es PORTABLE`() {
        val kind = detectInstallKind(currentExePath = installedExe, reader = RegistryReader { null })
        assertEquals(InstallKind.PORTABLE, kind)
    }

    @Test
    fun `sin ruta del ejecutable es PORTABLE`() {
        val kind = detectInstallKind(currentExePath = null, reader = RegistryReader { installLocation })
        assertEquals(InstallKind.PORTABLE, kind)
    }

    @Test
    fun `si leer el registro lanza, es PORTABLE`() {
        val kind = detectInstallKind(
            currentExePath = installedExe,
            reader = RegistryReader { error("reg.exe petó") },
        )
        assertEquals(InstallKind.PORTABLE, kind)
    }

    @Test
    fun `tolera barra final y mayusculas al comparar rutas`() {
        val kind = detectInstallKind(
            currentExePath = "C:\\Users\\Quien\\AppData\\Local\\ImageWatch\\ImageWatch.exe",
            reader = RegistryReader { "c:/users/quien/appdata/local/imagewatch" },
        )
        assertEquals(InstallKind.MSI, kind)
    }
}
