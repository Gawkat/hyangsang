package dev.kettu.hyangsang.data.repository

import dev.kettu.hyangsang.data.local.dao.BookDao
import dev.kettu.hyangsang.data.local.entity.Book
import dev.kettu.hyangsang.data.local.entity.BookChapter
import dev.kettu.hyangsang.data.local.entity.BookWithChapter
import dev.kettu.hyangsang.data.local.entity.ChapterInfo
import dev.kettu.hyangsang.parser.ContentBlock
import dev.kettu.hyangsang.parser.epub.EpubParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BookRepositoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val dao = FakeBookDao()
    private var time = 1_000_000_000L
    private val booksDir by lazy { File(folder.root, "books") }
    private val repository by lazy { BookRepository(dao, booksDir, maxFileBytes = 50_000, now = { time }) }

    private fun prepare(bytes: ByteArray, name: String? = "책.epub"): BookImport =
        runBlocking { repository.prepare({ bytes.inputStream() }, name) }

    @Test
    fun importsABook() = runBlocking {
        val ready = prepare(epub()) as BookImport.Ready
        assertEquals("작은 책", ready.book.title)
        assertNull(ready.existingId)

        val id = repository.commit(ready, "제목 없음")!!
        val book = dao.books.value.single()
        assertEquals(id, book.id)
        assertEquals("작은 책", book.title)
        assertEquals("김작가", book.author)
        assertEquals("책.epub", book.sourceFileName)
        assertEquals(2, book.chapterCount)
        assertEquals(time, book.addedDate)
        // "첫 문단입니다." and "둘째 장의 문단."
        assertEquals(8 + 9, book.totalChars)
        assertEquals(5, book.wordCount)
        assertEquals(listOf(0, 8), dao.chapters.map { it.charsBefore })
        assertTrue(File(booksDir, "${book.folder}/source.epub").exists())
        assertTrue(book.sizeBytes > File(booksDir, "${book.folder}/source.epub").length())
    }

    @Test
    fun noticesTheSameFileImportedAgain() = runBlocking {
        // The same bytes: zip entries carry the time they were written
        val file = epub()
        val first = prepare(file) as BookImport.Ready
        val id = repository.commit(first, "")
        val second = prepare(file) as BookImport.Ready
        assertEquals(id, second.existingId)
    }

    @Test
    fun titlesABookWithoutOne() = runBlocking {
        val ready = prepare(epub(title = "")) as BookImport.Ready
        repository.commit(ready, "제목 없음")
        assertEquals("제목 없음", dao.books.value.single().title)
    }

    @Test
    fun removesTheFilesOfFailedAndCancelledImports() = runBlocking {
        assertEquals(BookImport.Failed(BookImportFailure.UNREADABLE), prepare("not a zip".toByteArray()))
        assertEquals(BookImport.Failed(BookImportFailure.TOO_LARGE), prepare(ByteArray(60_000)))
        assertEquals(
            BookImport.Failed(BookImportFailure.UNREADABLE),
            runBlocking { repository.prepare({ throw IOException("gone") }, null) }
        )
        assertEquals(0, booksDir.listFiles()!!.size)

        val ready = prepare(epub()) as BookImport.Ready
        repository.discard(ready)
        assertEquals(0, booksDir.listFiles()!!.size)
    }

    @Test
    fun refusesCopyProtectedBooks() {
        val result = prepare(epub(extra = mapOf("META-INF/rights.xml" to "<rights/>")))
        assertEquals(BookImport.Failed(BookImportFailure.DRM), result)
    }

    @Test
    fun doesNotImportABookWhoseFilesAreGone() = runBlocking {
        val ready = prepare(epub()) as BookImport.Ready
        File(booksDir, ready.folder).deleteRecursively()
        assertNull(repository.commit(ready, ""))
        assertTrue(dao.books.value.isEmpty())
    }

    @Test
    fun deletesABookWithItsFiles() = runBlocking {
        repository.commit(prepare(epub()) as BookImport.Ready, "")
        val book = dao.books.value.single()
        repository.delete(book)
        assertTrue(dao.books.value.isEmpty())
        assertFalse(File(booksDir, book.folder).exists())
    }

    @Test
    fun cleansUpOldFoldersNoBookUses() = runBlocking {
        repository.commit(prepare(epub()) as BookImport.Ready, "")
        val abandoned = (prepare(epub()) as BookImport.Ready).folder
        val waiting = (prepare(epub()) as BookImport.Ready).folder
        File(booksDir, abandoned).setLastModified(time - 25 * 60 * 60 * 1000)
        File(booksDir, waiting).setLastModified(time - 60 * 1000)
        File(booksDir, dao.books.value.single().folder).setLastModified(0)

        repository.cleanUp()
        assertEquals(
            setOf(dao.books.value.single().folder, waiting),
            booksDir.list()!!.toSet()
        )
    }

    @Test
    fun parsesABookAgainForANewerParser() = runBlocking {
        repository.commit(prepare(epub()) as BookImport.Ready, "")
        val old = dao.books.value.single()
        // As if imported by an older parser, reading the second chapter
        dao.updateBook(old.copy(parserVersion = 1, chapterIndex = 1, blockIndex = 0, totalChars = 1))
        dao.chapters.replaceAll { it.copy(title = "옛 제목") }

        assertTrue(repository.reparse(dao.books.value.single()))
        val book = dao.books.value.single()
        assertEquals(EpubParser.VERSION, book.parserVersion)
        assertEquals(1, book.chapterIndex)
        assertEquals(17, book.totalChars)
        assertEquals(listOf("첫째 장", "둘째 장"), dao.chapters.map { it.title })
    }

    @Test
    fun staysAtTheSameTextWhenBlocksChange() = runBlocking {
        repository.commit(prepare(epub()) as BookImport.Ready, "")
        val book = dao.books.value.single()
        // As if an older parser had kept a block of 3 characters before the second paragraph
        val old = dao.chapters.first()
        dao.chapters[0] = old.copy(content = listOf(ContentBlock.Text("머리말")) + old.content, charCount = old.charCount + 3)
        dao.updateBook(book.copy(parserVersion = 1, blockIndex = 1, progressChars = 3))

        repository.reparse(dao.books.value.single())
        val updated = dao.books.value.single()
        assertEquals(0, updated.blockIndex)
        assertEquals(0, updated.progressChars)
    }

    @Test
    fun keepsABookThatCanNoLongerBeParsed() = runBlocking {
        repository.commit(prepare(epub()) as BookImport.Ready, "")
        val book = dao.books.value.single()
        dao.updateBook(book.copy(parserVersion = 1))
        File(booksDir, "${book.folder}/source.epub").writeText("damaged")

        assertFalse(repository.reparse(dao.books.value.single()))
        assertEquals(2, dao.chapters.size)
        // Not tried again until the parser changes again
        assertEquals(EpubParser.VERSION, dao.books.value.single().parserVersion)
    }

    @Test
    fun savesTheReadingPosition() = runBlocking {
        val id = repository.commit(prepare(epub()) as BookImport.Ready, "")!!
        time += 1_000
        repository.savePosition(id, chapterIndex = 1, blockIndex = 0, progressChars = 8)
        val book = dao.books.value.single()
        assertEquals(1, book.chapterIndex)
        assertEquals(8, book.progressChars)
        assertEquals(time, book.lastReadDate)
        assertEquals(8f / 17, book.progress)
    }

    @Test
    fun marksABookAsReadAndUnread() = runBlocking {
        val id = repository.commit(prepare(epub()) as BookImport.Ready, "")!!
        time += 1_000
        repository.setRead(dao.books.value.single(), read = true)
        val read = dao.books.value.single()
        assertEquals(1, read.chapterIndex)
        assertEquals(dao.chapters.last().content.size, read.blockIndex)
        assertEquals(1f, read.progress)
        assertEquals(time, read.lastReadDate)

        repository.setRead(read, read = false)
        val unread = dao.books.value.single()
        assertEquals(id, unread.id)
        assertEquals(0, unread.chapterIndex)
        assertEquals(0, unread.blockIndex)
        assertEquals(0f, unread.progress)
        assertNull(unread.lastReadDate)

        repository.restorePosition(read)
        assertEquals(read, dao.books.value.single())
    }

    private fun epub(title: String = "작은 책", extra: Map<String, String> = emptyMap()): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            fun put(path: String, text: String) {
                zip.putNextEntry(ZipEntry(path))
                zip.write(text.toByteArray())
                zip.closeEntry()
            }
            put("mimetype", "application/epub+zip")
            put(
                "META-INF/container.xml",
                """<container><rootfiles><rootfile full-path="content.opf" media-type="application/oebps-package+xml"/></rootfiles></container>"""
            )
            put(
                "content.opf",
                """<package xmlns:dc="http://purl.org/dc/elements/1.1/">
                <metadata><dc:title>$title</dc:title><dc:creator>김작가</dc:creator></metadata>
                <manifest><item id="a" href="a.xhtml" media-type="application/xhtml+xml"/>
                <item id="b" href="b.xhtml" media-type="application/xhtml+xml"/></manifest>
                <spine><itemref idref="a"/><itemref idref="b"/></spine></package>"""
            )
            put("a.xhtml", "<html><body><h1>첫째 장</h1><p>첫 문단입니다.</p></body></html>")
            put("b.xhtml", "<html><body><h1>둘째 장</h1><p>둘째 장의 문단.</p></body></html>")
            extra.forEach { (path, text) -> put(path, text) }
        }
        return out.toByteArray()
    }
}

private class FakeBookDao : BookDao {
    val books = MutableStateFlow<List<Book>>(emptyList())
    val chapters = mutableListOf<BookChapter>()

    override fun getBooks(): Flow<List<BookWithChapter>> = books.map { list ->
        list.map { book -> BookWithChapter(book, chapters.firstOrNull { it.bookId == book.id }?.title) }
    }

    override fun getBook(id: Long): Flow<Book?> = books.map { list -> list.firstOrNull { it.id == id } }

    override suspend fun getChapter(bookId: Long, index: Int): BookChapter? =
        chapters.firstOrNull { it.bookId == bookId && it.chapterIndex == index }

    override fun getChapterInfos(bookId: Long): Flow<List<ChapterInfo>> = books.map {
        chapters.filter { it.bookId == bookId }.map { ChapterInfo(it.chapterIndex, it.title, it.charsBefore, it.charCount) }
    }

    override suspend fun updatePosition(
        id: Long,
        chapterIndex: Int,
        blockIndex: Int,
        progressChars: Int,
        lastReadDate: Long?
    ) {
        books.value = books.value.map {
            if (it.id == id) it.copy(
                chapterIndex = chapterIndex,
                blockIndex = blockIndex,
                progressChars = progressChars,
                lastReadDate = lastReadDate
            ) else it
        }
    }

    override suspend fun updateBook(book: Book) {
        books.value = books.value.map { if (it.id == book.id) book else it }
    }

    override suspend fun deleteChapters(bookId: Long) {
        chapters.removeAll { it.bookId == bookId }
    }

    override suspend fun findBySource(sha256: String): Long? =
        books.value.firstOrNull { it.sourceSha256 == sha256 }?.id

    override suspend fun getFolders(): List<String> = books.value.map { it.folder }

    override suspend fun insertBook(book: Book): Long {
        val id = (books.value.maxOfOrNull { it.id } ?: 0) + 1
        books.value += book.copy(id = id)
        return id
    }

    override suspend fun insertChapters(chapters: List<BookChapter>) {
        this.chapters.addAll(chapters)
    }

    override suspend fun updateTitleAndAuthor(id: Long, title: String, author: String?) {
        books.value = books.value.map { if (it.id == id) it.copy(title = title, author = author) else it }
    }

    override suspend fun deleteBook(id: Long) {
        books.value = books.value.filterNot { it.id == id }
        chapters.removeAll { it.bookId == id }
    }
}
