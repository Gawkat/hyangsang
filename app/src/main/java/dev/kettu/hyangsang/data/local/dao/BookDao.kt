package dev.kettu.hyangsang.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import dev.kettu.hyangsang.data.local.entity.Book
import dev.kettu.hyangsang.data.local.entity.BookChapter
import dev.kettu.hyangsang.data.local.entity.BookWithChapter
import dev.kettu.hyangsang.data.local.entity.ChapterInfo
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query(
        """
        SELECT books.*, book_chapters.title AS chapterTitle FROM books
        LEFT JOIN book_chapters
            ON book_chapters.bookId = books.id AND book_chapters.chapterIndex = books.chapterIndex
        """
    )
    fun getBooks(): Flow<List<BookWithChapter>>

    @Query("SELECT * FROM books WHERE id = :id")
    fun getBook(id: Long): Flow<Book?>

    @Query("SELECT * FROM book_chapters WHERE bookId = :bookId AND chapterIndex = :index")
    suspend fun getChapter(bookId: Long, index: Int): BookChapter?

    @Query(
        """
        SELECT chapterIndex, title, charsBefore, charCount FROM book_chapters
        WHERE bookId = :bookId ORDER BY chapterIndex
        """
    )
    fun getChapterInfos(bookId: Long): Flow<List<ChapterInfo>>

    @Query("SELECT id FROM books WHERE sourceSha256 = :sha256 LIMIT 1")
    suspend fun findBySource(sha256: String): Long?

    @Query("SELECT folder FROM books")
    suspend fun getFolders(): List<String>

    @Insert
    suspend fun insertBook(book: Book): Long

    @Insert
    suspend fun insertChapters(chapters: List<BookChapter>)

    @Transaction
    suspend fun insertBookWithChapters(book: Book, chapters: List<BookChapter>): Long {
        val id = insertBook(book)
        insertChapters(chapters.map { it.copy(bookId = id) })
        return id
    }

    @Query(
        """
        UPDATE books SET chapterIndex = :chapterIndex, blockIndex = :blockIndex,
            progressChars = :progressChars, lastReadDate = :lastReadDate
        WHERE id = :id
        """
    )
    suspend fun updatePosition(id: Long, chapterIndex: Int, blockIndex: Int, progressChars: Int, lastReadDate: Long?)

    @Update
    suspend fun updateBook(book: Book)

    @Query("DELETE FROM book_chapters WHERE bookId = :bookId")
    suspend fun deleteChapters(bookId: Long)

    /** Replaces a book's chapters, as when it's parsed again, along with the book's row. */
    @Transaction
    suspend fun replaceChapters(book: Book, chapters: List<BookChapter>) {
        updateBook(book)
        deleteChapters(book.id)
        insertChapters(chapters.map { it.copy(bookId = book.id) })
    }

    @Query("UPDATE books SET title = :title, author = :author WHERE id = :id")
    suspend fun updateTitleAndAuthor(id: Long, title: String, author: String?)

    // Chapters go with it, by their foreign key
    @Query("DELETE FROM books WHERE id = :id")
    suspend fun deleteBook(id: Long)
}
