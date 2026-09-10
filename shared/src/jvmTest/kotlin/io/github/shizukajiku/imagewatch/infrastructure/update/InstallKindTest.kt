package io.github.shizukajiku.imagewatch.infrastructure.update

import kotlin.test.Test
import kotlin.test.assertEquals

class InstallKindTest {

    @Test
    fun `entrada de desinstalacion de ImageWatch es MSI`() {
        val kind = detectInstallKind(
            RegistryReader { root, term ->
                root == WindowsRegistryReader.UNINSTALL_ROOT && term == "ImageWatch"
            },
        )
        assertEquals(InstallKind.MSI, kind)
    }

    @Test
    fun `sin entrada de desinstalacion es PORTABLE`() {
        val kind = detectInstallKind(RegistryReader { _, _ -> false })
        assertEquals(InstallKind.PORTABLE, kind)
    }

    @Test
    fun `si leer el registro lanza, es PORTABLE`() {
        val kind = detectInstallKind(RegistryReader { _, _ -> error("reg.exe petó") })
        assertEquals(InstallKind.PORTABLE, kind)
    }
}
