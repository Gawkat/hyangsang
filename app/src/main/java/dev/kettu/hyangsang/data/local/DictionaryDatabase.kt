package dev.kettu.hyangsang.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import dev.kettu.hyangsang.data.local.dao.DictionaryDao
import dev.kettu.hyangsang.data.local.entity.DictionaryEntry
import dev.kettu.hyangsang.data.local.entity.DictionaryExample
import dev.kettu.hyangsang.data.local.entity.DictionarySense

/**
 * The bundled dictionary, kept apart from user data so it can be replaced wholesale. Room only
 * copies the asset when the installed copy is missing or its version differs from
 * [DICTIONARY_VERSION], so bump the version whenever `dictionary.db` is regenerated.
 */
@Database(
    entities = [
        DictionaryEntry::class,
        DictionarySense::class,
        DictionaryExample::class
    ],
    version = DICTIONARY_VERSION,
    // Never migrated, only replaced from the asset
    exportSchema = false
)
abstract class DictionaryDatabase : RoomDatabase() {
    abstract fun dictionaryDao(): DictionaryDao

    companion object {
        @Volatile
        private var Instance: DictionaryDatabase? = null

        fun getDatabase(context: Context): DictionaryDatabase {
            return Instance ?: synchronized(this) {
                Instance ?: Room.databaseBuilder(
                    context,
                    DictionaryDatabase::class.java,
                    "dictionary.db"
                )
                    .createFromAsset("dictionary.db")
                    // With no migrations, a version change deletes the installed copy and
                    // copies the asset again, on downgrades too. Adding
                    // fallbackToDestructiveMigrationOnDowngrade would turn this off for upgrades.
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { Instance = it }
            }
        }
    }
}

/**
 * The NIKL release date of the bundled dictionary. Must match the `user_version` the dictionary
 * generator stamps on `dictionary.db`, which DictionaryAssetTest checks.
 */
const val DICTIONARY_VERSION = 20260919
