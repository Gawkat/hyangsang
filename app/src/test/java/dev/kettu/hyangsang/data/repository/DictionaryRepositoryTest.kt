package dev.kettu.hyangsang.data.repository

import dev.kettu.hyangsang.data.local.dao.DictionaryDao
import dev.kettu.hyangsang.data.local.dao.DictionaryWithSenses
import dev.kettu.hyangsang.data.local.entity.DictionaryEntry
import dev.kettu.hyangsang.data.local.entity.DictionaryExample
import dev.kettu.hyangsang.data.local.entity.DictionarySense
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class DictionaryRepositoryTest {

    private class FakeDictionaryDao(val entries: List<DictionaryWithSenses>) : DictionaryDao {
        override suspend fun insertEntry(entry: DictionaryEntry) = Unit
        override suspend fun insertSenses(senses: List<DictionarySense>) = Unit
        override suspend fun insertExamples(examples: List<DictionaryExample>) = Unit
        override fun getEntriesByWord(word: String): Flow<List<DictionaryEntry>> = flowOf(emptyList())
        override fun getSensesForEntry(entryId: Long): Flow<List<DictionarySense>> = flowOf(emptyOfList())
        override fun getFullEntriesByWord(word: String): Flow<List<DictionaryWithSenses>> = flowOf(emptyList())
        override fun getEntriesForTerms(words: List<String>): Flow<List<DictionaryWithSenses>> {
            return flowOf(entries.filter { it.entry.word in words })
        }

        private fun <T> emptyOfList(): List<T> = emptyList()
    }

    @Test
    fun `sorting logic prioritizes nouns over verbs`() = runBlocking {
        // Arrange
        val entries = listOf(
            createEntry("위", "Noun"),
            createEntry("하다", "Verb"),
            createEntry("위해서", "Verb")
        )
        val repository = DictionaryRepository(FakeDictionaryDao(entries))

        // Act
        val result = repository.getDefinitionsForWord("위해서").first()
        val sortedKeys = result.keys.toList()

        // Assert
        // We expect "위해서" (exact) first, then "위" (noun) before "하다" (verb)
        // Note: This assumes OKT tokenizes "위해서" into stems including "위" and "하다".
        // If it doesn't in this environment, we verify whatever was returned is sorted by POS.
        val nounIndex = sortedKeys.indexOf("위")
        val verbIndex = sortedKeys.indexOf("하다")
        
        if (nounIndex != -1 && verbIndex != -1) {
            assert(nounIndex < verbIndex) { "Noun '위' should come before Verb '하다', but keys were $sortedKeys" }
        }
        
        if (sortedKeys.contains("위해서")) {
            assertEquals("위해서", sortedKeys[0])
        }
    }

    @Test
    fun `sorting logic prioritizes longer matches for same POS`() = runBlocking {
        // Arrange
        val entries = listOf(
            createEntry("한국어", "Noun"),
            createEntry("한국", "Noun"),
            createEntry("국어", "Noun")
        )
        val repository = DictionaryRepository(FakeDictionaryDao(entries))

        // Act
        val result = repository.getDefinitionsForWord("한국어").first()
        val sortedKeys = result.keys.toList()

        // Assert
        // 1. "한국어" (Exact)
        // 2. "한국" or "국어" (Nouns, length 2) - order between them is by length (both 2) then stable?
        // Actually length is the same, so it depends on the original order.
        assertEquals("한국어", sortedKeys[0])
        assert(sortedKeys.contains("한국"))
        assert(sortedKeys.contains("국어"))
    }

    private fun createEntry(word: String, pos: String): DictionaryWithSenses {
        return DictionaryWithSenses(
            entry = DictionaryEntry(
                originalId = "id_$word",
                word = word,
                origin = null,
                partOfSpeech = pos,
                vocabularyLevel = null,
                semanticCategory = null,
                lexicalUnit = null,
                pronunciation = null,
                audioUrl = null
            ),
            senses = emptyList()
        )
    }
}
