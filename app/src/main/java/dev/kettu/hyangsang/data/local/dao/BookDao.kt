package dev.kettu.hyangsang.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import dev.kettu.hyangsang.data.local.entity.Book
import dev.kettu.hyangsang.data.local.entity.BookChapter
import dev.kettu.hyangsang.data.local.entity.BookWithChapter
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

    @Query("UPDATE books SET title = :title, author = :author WHERE id = :id")
    suspend fun updateTitleAndAuthor(id: Long, title: String, author: String?)

    // Chapters go with it, by their foreign key
    @Query("DELETE FROM books WHERE id = :id")
    suspend fun deleteBook(id: Long)
}
