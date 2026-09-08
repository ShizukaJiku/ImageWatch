package io.github.shizukajiku.imagewatch.infrastructure.persistence

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

internal class JsonFilesTest {
    @Test
    fun writesAndReadsBackARoundTrip() = withTempDir { dir ->
        val file = dir.resolve("valores.json")

        JsonFiles.write(file, listOf("uno", "dos"), "valores")

        assertEquals(listOf("uno", "dos"), JsonFiles.read<List<String>>(file, "valores"))
    }

    @Test
    fun createsMissingParentDirectories() = withTempDir { dir ->
        val file = dir.resolve("aun/no/existe/valores.json")

        JsonFiles.write(file, listOf("uno"), "valores")

        assertTrue(JsonFiles.fileSystem.exists(file))
    }

    @Test
    fun replacesTheWholeContentInsteadOfAppending() = withTempDir { dir ->
        val file = dir.resolve("valores.json")
        JsonFiles.write(file, listOf("uno", "dos", "tres"), "valores")

        JsonFiles.write(file, listOf("cuatro"), "valores")

        assertEquals(listOf("cuatro"), JsonFiles.read<List<String>>(file, "valores"))
    }

    @Test
    fun leavesNoTemporaryFileBehind() = withTempDir { dir ->
        val file = dir.resolve("valores.json")

        JsonFiles.write(file, listOf("uno"), "valores")

        assertEquals(listOf("valores.json"), JsonFiles.fileSystem.list(dir).map { it.name })
    }

    @Test
    fun reportsTheDescriptionWhenTheContentIsNotReadable() = withTempDir { dir ->
        val file = dir.resolve("roto.json")
        JsonFiles.fileSystem.write(file) { writeUtf8("{ esto no es json válido") }

        val error = assertFailsWith<IllegalStateException> {
            JsonFiles.read<List<String>>(file, "la lista de prueba")
        }
        assertTrue("la lista de prueba" in error.message!!)
    }
}
