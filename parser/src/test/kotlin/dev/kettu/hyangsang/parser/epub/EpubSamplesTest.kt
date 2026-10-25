package dev.kettu.hyangsang.parser.epub

import dev.kettu.hyangsang.parser.ContentBlock
import dev.kettu.hyangsang.parser.SpanType
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Parses real books saved in `src/test/resources/epub`, which isn't committed, and prints what
 * it found in each. Skipped without the folder. See CONTRIBUTING.md.
 */
class EpubSamplesTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun parsesSavedBooks() {
        val dir = javaClass.classLoader.getResource("epub")?.let { File(it.toURI()) }
        val books = dir?.listFiles { file -> file.extension == "epub" }?.sortedBy { it.name }.orEmpty()
        assumeTrue("No books in src/test/resources/epub, skipping", books.isNotEmpty())

        val failures = mutableListOf<String>()
        for (file in books) {
            val imageDir = folder.newFolder()
            when (val result = EpubParser().parse(file, imageDir)) {
                is EpubResult.Failure -> failures.add("${file.name}: ${result.reason}")
                is EpubResult.Success -> println(describe(file, result.book, imageDir))
            }
        }
        assertTrue("Couldn't read ${failures.joinToString()}", failures.isEmpty())
    }

    private fun describe(file: File, book: EpubBook, imageDir: File): String = buildString {
        val texts = book.chapters.flatMap { it.blocks }.filterIsInstance<ContentBlock.Text>()
        val notes = texts.sumOf { text -> text.spans.count { it.type == SpanType.NOTE } }
        appendLine("${file.name}: ${book.title} / ${book.author} / ${book.language}, cover ${book.cover}")
        appendLine(
            "  ${book.chapters.size} chapters, ${texts.size} paragraphs, ${texts.sumOf { it.text.length }} " +
                "characters, $notes notes, ${imageDir.list()?.size ?: 0} images"
        )
        for ((index, chapter) in book.chapters.withIndex()) {
            val first = chapter.blocks.filterIsInstance<ContentBlock.Text>().firstOrNull()?.text.orEmpty()
            appendLine("  ${index + 1}. ${chapter.title} (${chapter.blocks.size} blocks) ${first.take(40)}")
        }
    }
}
