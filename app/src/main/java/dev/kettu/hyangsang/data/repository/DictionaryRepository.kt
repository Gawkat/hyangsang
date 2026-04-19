package dev.kettu.hyangsang.data.repository

import dev.kettu.hyangsang.data.local.dao.DictionaryDao
import dev.kettu.hyangsang.data.local.entity.WordEntry

class DictionaryRepository(private val dictionaryDao: DictionaryDao) {
    suspend fun getEntryByWord(word: String): WordEntry? = dictionaryDao.getEntryByWord(word)

    suspend fun searchWords(query: String): List<WordEntry> = dictionaryDao.searchWords(query)

    suspend fun insertEntries(entries: List<WordEntry>) = dictionaryDao.insertEntries(entries)
}
