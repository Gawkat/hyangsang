package dev.kettu.hyangsang.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import dev.kettu.hyangsang.data.local.entity.DictionaryEntry
import dev.kettu.hyangsang.data.local.entity.DictionaryExample
import dev.kettu.hyangsang.data.local.entity.DictionarySense
import kotlinx.coroutines.flow.Flow

@Dao
interface DictionaryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: DictionaryEntry)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSenses(senses: List<DictionarySense>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExamples(examples: List<DictionaryExample>)

    @Query("SELECT * FROM dictionary_entries WHERE word = :word")
    fun getEntriesByWord(word: String): Flow<List<DictionaryEntry>>

    @Query("SELECT * FROM dictionary_senses WHERE entryId = :entryId")
    fun getSensesForEntry(entryId: Long): Flow<List<DictionarySense>>

    @Transaction
    @Query("SELECT * FROM dictionary_entries WHERE word = :word")
    fun getFullEntriesByWord(word: String): Flow<List<DictionaryWithSenses>>
    
    @Transaction
    @Query("SELECT * FROM dictionary_entries WHERE word IN (:words)")
    fun getEntriesForTerms(words: List<String>): Flow<List<DictionaryWithSenses>>
}

data class DictionaryWithSenses(
    @androidx.room.Embedded val entry: DictionaryEntry,
    @androidx.room.Relation(
        entity = DictionarySense::class,
        parentColumn = "id",
        entityColumn = "entryId"
    )
    val senses: List<SenseWithExamples>
)

data class SenseWithExamples(
    @androidx.room.Embedded val sense: DictionarySense,
    @androidx.room.Relation(
        parentColumn = "senseId",
        entityColumn = "senseId"
    )
    val examples: List<DictionaryExample>
)
