package io.github.shizukajiku.imagewatch.infrastructure.update

import kotlin.test.Test
import kotlin.test.assertEquals

class InstallKindTest {

    @Test
    fun `entrada de desinstalacion de ImageWatch es MSI`() {
        val kind = detectInstallKind(RegistryReader { displayName -> displayName == "ImageWatch" })
        assertEquals(InstallKind.MSI, kind)
    }

    @Test
    fun `sin entrada de desinstalacion es PORTABLE`() {
        val kind = detectInstallKind(RegistryReader { false })
        assertEquals(InstallKind.PORTABLE, kind)
    }

    @Test
    fun `si leer el registro lanza, es PORTABLE`() {
        val kind = detectInstallKind(RegistryReader { error("reg.exe petó") })
        assertEquals(InstallKind.PORTABLE, kind)
    }
}
