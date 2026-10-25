package dev.kettu.hyangsang.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import dev.kettu.hyangsang.data.local.dao.BookDao
import dev.kettu.hyangsang.data.local.entity.Book
import dev.kettu.hyangsang.data.local.entity.BookChapter

/**
 * Imported books, kept apart from [HyangsangDatabase] so they can be left out of cloud backups
 * (see `data_extraction_rules.xml`): a few books would pass Auto Backup's 25 MB quota, and then
 * nothing is backed up at all. Schema changes need migrations, as for [HyangsangDatabase].
 */
@Database(
    entities = [
        Book::class,
        BookChapter::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(ContentBlockTypeConverter::class)
abstract class BooksDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao

    companion object {
        const val NAME = "books.db"

        @Volatile
        private var Instance: BooksDatabase? = null

        fun getDatabase(context: Context): BooksDatabase {
            return Instance ?: synchronized(this) {
                Instance ?: Room.databaseBuilder(context, BooksDatabase::class.java, NAME)
                    .build()
                    .also { Instance = it }
            }
        }
    }
}
