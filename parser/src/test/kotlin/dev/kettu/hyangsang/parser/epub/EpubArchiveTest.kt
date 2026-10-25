package dev.kettu.hyangsang.parser.epub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class EpubArchiveTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun readsTheFirstOfEntriesWithTheSameName() {
        val file = zipWithDuplicate()
        for (rewrite in listOf(false, true)) {
            EpubArchive(file, EpubLimits(), rewrite).use { archive ->
                assertEquals("first", archive.readText("a.txt"))
                assertEquals("other", archive.readText("c.txt"))
            }
        }
        // The copy is removed with the archive
        assertEquals(listOf(file.name), folder.root.list()!!.toList())
    }

    @Test
    fun capsTheCopy() {
        val file = zipWithDuplicate()
        val error = runCatching { EpubArchive(file, EpubLimits(maxEntryBytes = 3), rewrite = true) }.exceptionOrNull()
        assertEquals(EpubTooLargeException::class, error?.let { it::class })
        assertFalse(folder.root.list()!!.any { it.startsWith("epub") })
    }

    // Java's ZipOutputStream refuses a second entry of the same name, so the name is changed in
    // the bytes afterwards: it's stored as is in both the entry's header and the directory
    private fun zipWithDuplicate(): File {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            for ((name, text) in listOf("a.txt" to "first", "b.txt" to "second", "c.txt" to "other")) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(text.toByteArray())
                zip.closeEntry()
            }
        }
        val bytes = String(out.toByteArray(), Charsets.ISO_8859_1).replace("b.txt", "a.txt")
        return folder.newFile("book.epub").apply { writeBytes(bytes.toByteArray(Charsets.ISO_8859_1)) }
    }
}
