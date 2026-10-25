package dev.kettu.hyangsang.data.local.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.kettu.hyangsang.parser.ContentBlock

/**
 * A book imported from an EPUB file. Its files (the original EPUB and its images) are in
 * `books/<folder>` in the app's files, and its text is in [BookChapter]s.
 */
@Entity(tableName = "books", indices = [Index("sourceSha256")])
data class Book(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val author: String?,
    val language: String?,
    val folder: String,
    // The cover's file name in the book's image folder
    val coverFile: String?,
    // The name of the file it was imported from, as the app that had it called it
    val sourceFileName: String?,
    // Of the EPUB file, to notice when the same book is imported again
    val sourceSha256: String,
    val addedDate: Long,
    val lastReadDate: Long? = null,
    // The reading position: a chapter, a block in it, and how many characters come before it
    val chapterIndex: Int = 0,
    val blockIndex: Int = 0,
    val progressChars: Int = 0,
    val chapterCount: Int,
    val totalChars: Int,
    val wordCount: Int,
    // On the device, files and text together
    val sizeBytes: Long,
    val parserVersion: Int
) {
    /** How far the reader has got, from 0 to 1. */
    val progress: Float
        get() = if (totalChars == 0) 0f else (progressChars.toFloat() / totalChars).coerceIn(0f, 1f)
}

@Entity(
    tableName = "book_chapters",
    primaryKeys = ["bookId", "chapterIndex"],
    foreignKeys = [
        ForeignKey(
            entity = Book::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class BookChapter(
    val bookId: Long,
    val chapterIndex: Int,
    val title: String?,
    // Characters in the chapters before this one, for the book's progress
    val charsBefore: Int,
    val charCount: Int,
    val wordCount: Int,
    val content: List<ContentBlock>
)

/** A chapter without its content, for the table of contents. */
data class ChapterInfo(
    val chapterIndex: Int,
    val title: String?,
    val charsBefore: Int,
    val charCount: Int
)

/** A book with the title of the chapter it's open at, for the Library. */
data class BookWithChapter(
    @Embedded val book: Book,
    val chapterTitle: String?
)
