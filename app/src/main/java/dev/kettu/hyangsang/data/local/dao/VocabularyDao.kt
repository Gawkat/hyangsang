package dev.kettu.hyangsang.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.kettu.hyangsang.data.local.entity.VocabularyWord
import kotlinx.coroutines.flow.Flow

@Dao
interface VocabularyDao {
    @Query("SELECT * FROM vocabulary ORDER BY lastEncounteredDate DESC")
    fun getAllVocabulary(): Flow<List<VocabularyWord>>

    @Query("SELECT * FROM vocabulary WHERE word = :word")
    suspend fun getWord(word: String): VocabularyWord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWord(word: VocabularyWord)

    @Query("UPDATE vocabulary SET timesEncountered = timesEncountered + 1, lastEncounteredDate = :timestamp WHERE word = :word")
    suspend fun incrementEncounter(word: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE vocabulary SET status = :status WHERE word = :word")
    suspend fun updateStatus(word: String, status: Int)
}
