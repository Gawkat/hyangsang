package dev.kettu.hyangsang.data.repository

import dev.kettu.hyangsang.data.local.dao.VocabularyDao
import dev.kettu.hyangsang.data.local.entity.VocabularyWord
import kotlinx.coroutines.flow.Flow

class VocabularyRepository(private val vocabularyDao: VocabularyDao) {
    fun getAllVocabulary(): Flow<List<VocabularyWord>> = vocabularyDao.getAllVocabulary()

    suspend fun getWord(word: String): VocabularyWord? = vocabularyDao.getWord(word)

    suspend fun upsertWord(word: VocabularyWord) = vocabularyDao.upsertWord(word)

    suspend fun incrementEncounter(word: String) = vocabularyDao.incrementEncounter(word)

    suspend fun updateStatus(word: String, status: Int) = vocabularyDao.updateStatus(word, status)
}
