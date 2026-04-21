package dev.kettu.hyangsang.data.repository

import dev.kettu.hyangsang.data.local.dao.DictionaryDao
import dev.kettu.hyangsang.data.local.dao.DictionaryWithSenses
import dev.kettu.hyangsang.data.local.dao.OfflineDictionaryDao
import dev.kettu.hyangsang.data.local.entity.WordEntry
import kotlinx.coroutines.flow.Flow

class DictionaryRepository(
    private val dictionaryDao: DictionaryDao,
    private val offlineDictionaryDao: OfflineDictionaryDao
) {
    suspend fun getEntryByWord(word: String): WordEntry? = dictionaryDao.getEntryByWord(word)

    suspend fun searchWords(query: String): List<WordEntry> = dictionaryDao.searchWords(query)

    suspend fun insertEntries(entries: List<WordEntry>) = dictionaryDao.insertEntries(entries)

    fun getOfflineEntriesByWord(word: String): Flow<List<DictionaryWithSenses>> =
        offlineDictionaryDao.getFullEntriesByWord(word)
}
