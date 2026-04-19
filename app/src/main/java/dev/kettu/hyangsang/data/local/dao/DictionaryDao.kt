package dev.kettu.hyangsang.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.kettu.hyangsang.data.local.entity.WordEntry

@Dao
interface DictionaryDao {
    @Query("SELECT * FROM dictionary WHERE word = :word LIMIT 1")
    suspend fun getEntryByWord(word: String): WordEntry?

    @Query("SELECT * FROM dictionary WHERE word LIKE :query || '%' LIMIT 20")
    suspend fun searchWords(query: String): List<WordEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntries(entries: List<WordEntry>)
}
