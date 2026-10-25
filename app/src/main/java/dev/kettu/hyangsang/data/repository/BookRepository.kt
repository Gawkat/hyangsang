package dev.kettu.hyangsang.data.repository

import dev.kettu.hyangsang.data.local.ContentBlockTypeConverter
import dev.kettu.hyangsang.data.local.dao.BookDao
import dev.kettu.hyangsang.data.local.entity.Book
import dev.kettu.hyangsang.data.local.entity.BookChapter
import dev.kettu.hyangsang.data.local.entity.BookWithChapter
import dev.kettu.hyangsang.parser.ContentBlock
import dev.kettu.hyangsang.parser.epub.EpubBook
import dev.kettu.hyangsang.parser.epub.EpubFailure
import dev.kettu.hyangsang.parser.epub.EpubParser
import dev.kettu.hyangsang.parser.epub.EpubResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID

/** A book read from a file, waiting for the user to import it or cancel. */
sealed interface BookImport {
    data class Ready(
        val folder: String,
        val book: EpubBook,
        val fileName: String?,
        val sha256: String,
        val fileBytes: Long,
        // A book already imported from the same file
        val existingId: Long?
    ) : BookImport {
        val wordCount: Int get() = book.chapters.sumOf { chapter -> chapter.blocks.sumOf { it.wordCount() } }
    }

    data class Failed(val reason: BookImportFailure) : BookImport
}

enum class BookImportFailure {
    DRM,
    FIXED_LAYOUT,
    NO_TEXT,
    TOO_LARGE,

    /** Not an EPUB, damaged, or the file couldn't be read */
    UNREADABLE
}

/**
 * Imported books: their rows in [BookDao], and their files in [booksDir], a folder each with
 * the original EPUB (to parse again when [EpubParser.VERSION] changes) and its images.
 */
class BookRepository(
    private val bookDao: BookDao,
    private val booksDir: File,
    private val parser: EpubParser = EpubParser(),
    private val maxFileBytes: Long = MAX_FILE_BYTES,
    private val now: () -> Long = System::currentTimeMillis
) {
    private val converter = ContentBlockTypeConverter()

    fun getBooks(): Flow<List<BookWithChapter>> = bookDao.getBooks()

    fun getBook(id: Long): Flow<Book?> = bookDao.getBook(id)

    /** Where a book's images are, by its folder. */
    fun imageDir(folder: String): File = File(File(booksDir, folder), IMAGE_DIR)

    /**
     * Copies the file [open] gives into a new book folder and parses it. The folder stays until
     * [commit] or [discard], or [cleanUp] after the app restarts.
     */
    suspend fun prepare(open: () -> InputStream?, fileName: String?): BookImport =
        withContext(Dispatchers.IO) {
            val folder = UUID.randomUUID().toString()
            val dir = File(booksDir, folder)
            fun fail(reason: BookImportFailure): BookImport {
                dir.deleteRecursively()
                return BookImport.Failed(reason)
            }

            dir.mkdirs()
            val source = File(dir, SOURCE_FILE)
            val (sha256, fileBytes) = try {
                val input = open() ?: return@withContext fail(BookImportFailure.UNREADABLE)
                input.use { copy(it, source) } ?: return@withContext fail(BookImportFailure.TOO_LARGE)
            } catch (_: IOException) {
                return@withContext fail(BookImportFailure.UNREADABLE)
            } catch (_: SecurityException) {
                // The app that shared the file took back the permission to read it
                return@withContext fail(BookImportFailure.UNREADABLE)
            }

            when (val result = parser.parse(source, File(dir, IMAGE_DIR))) {
                is EpubResult.Failure -> fail(result.reason.toImportFailure())
                is EpubResult.Success -> BookImport.Ready(
                    folder = folder,
                    book = result.book,
                    fileName = fileName,
                    sha256 = sha256,
                    fileBytes = fileBytes,
                    existingId = bookDao.findBySource(sha256)
                )
            }
        }

    /**
     * Adds a prepared book to the library, titled [fallbackTitle] if the book has no title.
     * Returns its id, or null if its folder is gone, as when [cleanUp] took it.
     */
    suspend fun commit(ready: BookImport.Ready, fallbackTitle: String): Long? =
        withContext(Dispatchers.IO) {
            val dir = File(booksDir, ready.folder)
            if (!File(dir, SOURCE_FILE).exists()) return@withContext null

            var charsBefore = 0
            val chapters = ready.book.chapters.mapIndexed { index, chapter ->
                val chars = chapter.blocks.sumOf { it.charCount() }
                BookChapter(
                    bookId = 0,
                    chapterIndex = index,
                    title = chapter.title,
                    charsBefore = charsBefore,
                    charCount = chars,
                    wordCount = chapter.blocks.sumOf { it.wordCount() },
                    content = chapter.blocks
                ).also { charsBefore += chars }
            }
            val textBytes = chapters.sumOf { converter.fromContentBlocks(it.content)!!.toByteArray().size.toLong() }
            val book = Book(
                title = ready.book.title?.takeIf { it.isNotBlank() } ?: fallbackTitle,
                author = ready.book.author?.takeIf { it.isNotBlank() },
                language = ready.book.language,
                folder = ready.folder,
                coverFile = ready.book.cover,
                sourceFileName = ready.fileName,
                sourceSha256 = ready.sha256,
                addedDate = now(),
                chapterCount = chapters.size,
                totalChars = charsBefore,
                wordCount = chapters.sumOf { it.wordCount },
                sizeBytes = dir.walkBottomUp().filter { it.isFile }.sumOf { it.length() } + textBytes,
                parserVersion = EpubParser.VERSION
            )
            bookDao.insertBookWithChapters(book, chapters)
        }

    suspend fun discard(ready: BookImport.Ready) = withContext(Dispatchers.IO) {
        File(booksDir, ready.folder).deleteRecursively()
        Unit
    }

    suspend fun updateTitleAndAuthor(id: Long, title: String, author: String?) =
        bookDao.updateTitleAndAuthor(id, title, author?.takeIf { it.isNotBlank() })

    suspend fun delete(book: Book) = withContext(Dispatchers.IO) {
        bookDao.deleteBook(book.id)
        File(booksDir, book.folder).deleteRecursively()
        Unit
    }

    /**
     * Deletes folders no book uses, left by imports that were never finished. Recent ones are
     * kept, since an import may still be waiting for the user.
     */
    suspend fun cleanUp() = withContext(Dispatchers.IO) {
        val used = bookDao.getFolders().toSet()
        val cutoff = now() - ABANDONED_AFTER_MILLIS
        booksDir.listFiles()
            ?.filter { it.isDirectory && it.name !in used && it.lastModified() < cutoff }
            ?.forEach { it.deleteRecursively() }
        Unit
    }

    // Returns the file's SHA-256 and size, or null if it's bigger than maxFileBytes
    private fun copy(input: InputStream, target: File): Pair<String, Long>? {
        val digest = MessageDigest.getInstance("SHA-256")
        var size = 0L
        target.outputStream().use { output ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                size += n
                if (size > maxFileBytes) return null
                digest.update(buffer, 0, n)
                output.write(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) } to size
    }

    companion object {
        const val MAX_FILE_BYTES = 100L * 1024 * 1024
        private const val SOURCE_FILE = "source.epub"
        private const val IMAGE_DIR = "images"
        private const val ABANDONED_AFTER_MILLIS = 24L * 60 * 60 * 1000
    }
}

private fun EpubFailure.toImportFailure(): BookImportFailure = when (this) {
    EpubFailure.DRM -> BookImportFailure.DRM
    EpubFailure.FIXED_LAYOUT -> BookImportFailure.FIXED_LAYOUT
    EpubFailure.NO_TEXT -> BookImportFailure.NO_TEXT
    EpubFailure.TOO_LARGE -> BookImportFailure.TOO_LARGE
    EpubFailure.INVALID -> BookImportFailure.UNREADABLE
}

private val WHITESPACE = Regex("\\s+")

// The text a chapter's progress is counted in
private fun ContentBlock.charCount(): Int = when (this) {
    is ContentBlock.Text -> text.length
    is ContentBlock.Heading -> text.length
    is ContentBlock.Footer -> text.length
    is ContentBlock.Dateline -> text.length
    is ContentBlock.Image -> 0
}

private fun ContentBlock.wordCount(): Int {
    val text = when (this) {
        is ContentBlock.Text -> text
        is ContentBlock.Heading -> text
        else -> return 0
    }
    return text.split(WHITESPACE).count { it.isNotEmpty() }
}
