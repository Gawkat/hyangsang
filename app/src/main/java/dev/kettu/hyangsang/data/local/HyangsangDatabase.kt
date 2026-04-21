package dev.kettu.hyangsang.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import dev.kettu.hyangsang.data.local.dao.ArticleDao
import dev.kettu.hyangsang.data.local.dao.DictionaryDao
import dev.kettu.hyangsang.data.local.dao.OfflineDictionaryDao
import dev.kettu.hyangsang.data.local.dao.VocabularyDao
import dev.kettu.hyangsang.data.local.entity.Article
import dev.kettu.hyangsang.data.local.entity.DictionaryEntry
import dev.kettu.hyangsang.data.local.entity.DictionaryExample
import dev.kettu.hyangsang.data.local.entity.DictionarySense
import dev.kettu.hyangsang.data.local.entity.VocabularyWord
import dev.kettu.hyangsang.data.local.entity.WordEntry

@Database(
    entities = [
        Article::class,
        WordEntry::class,
        VocabularyWord::class,
        DictionaryEntry::class,
        DictionarySense::class,
        DictionaryExample::class
    ],
    version = 3,
    exportSchema = false
)
abstract class HyangsangDatabase : RoomDatabase() {
    abstract fun articleDao(): ArticleDao
    abstract fun dictionaryDao(): DictionaryDao
    abstract fun vocabularyDao(): VocabularyDao
    abstract fun offlineDictionaryDao(): OfflineDictionaryDao

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
                    .build()
                    .also { Instance = it }
            }
        }
    }
}
