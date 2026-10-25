package dev.kettu.hyangsang.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.kettu.hyangsang.data.local.dao.BookDao
import dev.kettu.hyangsang.data.local.entity.Book
import dev.kettu.hyangsang.data.local.entity.BookChapter
import dev.kettu.hyangsang.parser.ContentBlock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BookDaoTest {
    private lateinit var db: BooksDatabase
    private lateinit var dao: BookDao

    @Before
    fun createDb() {
        db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            BooksDatabase::class.java
        ).build()
        dao = db.bookDao()
    }

    @After
    fun closeDb() = db.close()

    @Test
    fun listsBooksWithTheChapterTheyAreOpenAt() = runBlocking {
        val id = dao.insertBookWithChapters(
            book("abc").copy(chapterIndex = 1),
            listOf(chapter(0, "첫째 장"), chapter(1, "둘째 장"))
        )
        val listed = dao.getBooks().first().single()
        assertEquals(id, listed.book.id)
        assertEquals("둘째 장", listed.chapterTitle)
        assertEquals(id, dao.findBySource("abc"))
        assertNull(dao.findBySource("other"))
    }

    @Test
    fun keepsChapterContent() = runBlocking {
        val id = dao.insertBookWithChapters(book("abc"), listOf(chapter(0, null)))
        val content = db.query("SELECT content FROM book_chapters WHERE bookId = ?", arrayOf(id)).use {
            it.moveToFirst()
            it.getString(0)
        }
        assertEquals(listOf(ContentBlock.Text("본문")), ContentBlockTypeConverter().toContentBlocks(content))
    }

    @Test
    fun deletesChaptersWithTheirBook() = runBlocking {
        val id = dao.insertBookWithChapters(book("abc"), listOf(chapter(0, "장")))
        dao.insertBookWithChapters(book("def"), listOf(chapter(0, "장")))
        dao.deleteBook(id)
        val chapters = db.query("SELECT COUNT(*) FROM book_chapters", null).use {
            it.moveToFirst()
            it.getInt(0)
        }
        assertEquals(1, chapters)
        assertEquals(listOf("def"), dao.getBooks().first().map { it.book.sourceSha256 })
    }

    @Test
    fun updatesTitleAndAuthor() = runBlocking {
        val id = dao.insertBookWithChapters(book("abc"), listOf(chapter(0, "장")))
        dao.updateTitleAndAuthor(id, "새 제목", null)
        val book = dao.getBook(id).first()!!
        assertEquals("새 제목", book.title)
        assertNull(book.author)
    }

    @Test
    fun readsChaptersAndStoresThePosition() = runBlocking {
        val id = dao.insertBookWithChapters(book("abc"), listOf(chapter(0, "첫째"), chapter(1, "둘째")))
        assertEquals(listOf("첫째", "둘째"), dao.getChapterInfos(id).first().map { it.title })
        assertEquals("둘째", dao.getChapter(id, 1)?.title)
        assertNull(dao.getChapter(id, 2))

        dao.updatePosition(id, chapterIndex = 1, blockIndex = 3, progressChars = 2, lastReadDate = 42)
        val book = dao.getBook(id).first()!!
        assertEquals(listOf(1, 3, 2), listOf(book.chapterIndex, book.blockIndex, book.progressChars))
        assertEquals(42L, book.lastReadDate)
    }

    @Test
    fun replacesChapters() = runBlocking {
        val id = dao.insertBookWithChapters(book("abc"), listOf(chapter(0, "옛"), chapter(1, "옛")))
        val book = dao.getBook(id).first()!!
        dao.replaceChapters(book.copy(chapterCount = 1, parserVersion = 2), listOf(chapter(0, "새")))
        assertEquals(listOf("새"), dao.getChapterInfos(id).first().map { it.title })
        assertEquals(2, dao.getBook(id).first()!!.parserVersion)
    }

    private fun book(sha256: String) = Book(
        title = "책",
        author = "작가",
        language = "ko",
        folder = "folder-$sha256",
        coverFile = null,
        sourceFileName = "book.epub",
        sourceSha256 = sha256,
        addedDate = 0,
        chapterCount = 2,
        totalChars = 4,
        wordCount = 2,
        sizeBytes = 100,
        parserVersion = 1
    )

    private fun chapter(index: Int, title: String?) = BookChapter(
        bookId = 0,
        chapterIndex = index,
        title = title,
        charsBefore = index * 2,
        charCount = 2,
        wordCount = 1,
        content = listOf(ContentBlock.Text("본문"))
    )
}
