package dev.kettu.hyangsang.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import dev.kettu.hyangsang.data.local.dao.ArticleDao
import dev.kettu.hyangsang.data.local.dao.RssFeedDao
import dev.kettu.hyangsang.data.local.dao.VocabularyDao
import dev.kettu.hyangsang.data.local.entity.Article
import dev.kettu.hyangsang.data.local.entity.RssFeed
import dev.kettu.hyangsang.data.local.entity.VocabularyWord
import dev.kettu.hyangsang.data.prefs.UserPreferencesRepository
import kotlinx.coroutines.runBlocking

@Database(
    entities = [
        Article::class,
        VocabularyWord::class,
        RssFeed::class
    ],
    version = 20,
    exportSchema = true
)
@TypeConverters(ContentBlockTypeConverter::class)
abstract class HyangsangDatabase : RoomDatabase() {
    abstract fun articleDao(): ArticleDao
    abstract fun vocabularyDao(): VocabularyDao
    abstract fun rssFeedDao(): RssFeedDao

    companion object {
        @Volatile
        private var Instance: HyangsangDatabase? = null

        fun getDatabase(context: Context): HyangsangDatabase {
            return Instance ?: synchronized(this) {
                Room.databaseBuilder(
                    context,
                    HyangsangDatabase::class.java,
                    "hyangsang_database"
                )
                    .addMigrations(*ALL_MIGRATIONS)
                    // No migrations exist from before v13, and saved articles didn't exist yet either
                    .fallbackToDestructiveMigrationFrom(dropAllTables = true, *(1..12).toList().toIntArray())
                    .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
                    .addCallback(object : Callback() {
                        // A new install starts without feeds: the user picks them in the
                        // first-run setup. Stored before the open finishes, so the app can tell
                        // whether to show the setup as soon as the database is open
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            runBlocking {
                                UserPreferencesRepository(context.applicationContext).setFirstRunSetupPending(true)
                            }
                        }

                        override fun onOpen(db: SupportSQLiteDatabase) {
                            super.onOpen(db)
                            // Dropping the dictionary tables (MIGRATION_15_16) leaves the file at
                            // its old size. VACUUM can't run inside a migration's transaction.
                            if (mostlyFreePages(db)) db.execSQL("VACUUM")
                        }
                    })
                    .build()
                    .also { Instance = it }
            }
        }

        private fun mostlyFreePages(db: SupportSQLiteDatabase): Boolean {
            val free = db.query("PRAGMA freelist_count").use { it.moveToFirst(); it.getLong(0) }
            val total = db.query("PRAGMA page_count").use { it.moveToFirst(); it.getLong(0) }
            return free * 2 > total
        }
    }
}
