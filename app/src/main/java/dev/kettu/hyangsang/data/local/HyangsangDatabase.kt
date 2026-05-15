package dev.kettu.hyangsang.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import dev.kettu.hyangsang.data.defaults.DefaultData
import dev.kettu.hyangsang.data.local.dao.ArticleDao
import dev.kettu.hyangsang.data.local.dao.DictionaryDao
import dev.kettu.hyangsang.data.local.dao.RssFeedDao
import dev.kettu.hyangsang.data.local.dao.VocabularyDao
import dev.kettu.hyangsang.data.local.entity.Article
import dev.kettu.hyangsang.data.local.entity.DictionaryEntry
import dev.kettu.hyangsang.data.local.entity.DictionaryExample
import dev.kettu.hyangsang.data.local.entity.DictionarySense
import dev.kettu.hyangsang.data.local.entity.RssFeed
import dev.kettu.hyangsang.data.local.entity.VocabularyWord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        Article::class,
        VocabularyWord::class,
        DictionaryEntry::class,
        DictionarySense::class,
        DictionaryExample::class,
        RssFeed::class
    ],
    version = 11,
    exportSchema = false
)
abstract class HyangsangDatabase : RoomDatabase() {
    abstract fun articleDao(): ArticleDao
    abstract fun vocabularyDao(): VocabularyDao
    abstract fun dictionaryDao(): DictionaryDao
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
                    .createFromAsset("dictionary.db")
                    .fallbackToDestructiveMigration()
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)

                            CoroutineScope(Dispatchers.IO).launch {
                                val dao = getDatabase(context).rssFeedDao()
                                seedDefaultFeeds(dao)
                            }
                        }
                    })
                    .build()
                    .also { Instance = it }
            }
        }

        private suspend fun seedDefaultFeeds(dao: RssFeedDao) {
            DefaultData.defaultFeeds.forEach { dao.insertFeed(it) }
        }
    }
}
