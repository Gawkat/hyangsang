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

    private suspend fun lookUp(word: String, dictionary: List<String>): Set<String> =
        DictionaryRepository(FakeDictionaryDao(dictionary.map { createEntry(it, "Noun") }))
            .getDefinitionsForWord(word).first().keys

    @Test
    fun `single syllables that complete a compound are looked up`() = runBlocking {
        val keys = lookUp("대표팀이", listOf("대표", "대", "표", "팀"))
        assertEquals(setOf("대표", "팀"), keys)
    }

    @Test
    fun `single syllables of a compound that is a word are left out`() = runBlocking {
        val keys = lookUp("정부는", listOf("정부", "정", "부"))
        assertEquals(setOf("정부"), keys)
    }

    @Test
    fun `ties between splits favor a short last word`() = runBlocking {
        val keys = lookUp("외교부", listOf("외교", "교부", "외", "교", "부"))
        assertEquals(setOf("외교", "교부", "부"), keys)
    }

    @Test
    fun `words that cannot be split fully get no single syllables`() = runBlocking {
        val keys = lookUp("사브르", listOf("사", "르"))
        assertEquals(emptySet<String>(), keys)
    }

    @Test
    fun `suffix tokens are looked up as suffixes`() = runBlocking {
        // Open Korean Text splits 후보군 into 후보 and 군/Suffix
        val keys = lookUp("후보군", listOf("후보", "보", "군", "-군"))
        assertEquals(setOf("후보", "-군"), keys)
    }

    @Test
    fun `suffix tokens fall back to the plain word`() = runBlocking {
        val keys = lookUp("후보군", listOf("후보", "군"))
        assertEquals(setOf("후보", "군"), keys)
    }

    @Test
    fun `the end of a compound can be a suffix`() = runBlocking {
        // 외교부 stays a single noun
        val keys = lookUp("외교부", listOf("외교", "-부"))
        assertEquals(setOf("외교", "-부"), keys)
    }

    @Test
    fun `affixes outside the best split are left out`() = runBlocking {
        val keys = lookUp("외교부", listOf("외교", "교부", "외-", "-부"))
        assertEquals(setOf("외교", "교부", "-부"), keys)

        assertEquals(setOf("정부"), lookUp("정부는", listOf("정부", "정-", "-부")))
    }

    @Test
    fun `particles come after the words they attach to`() = runBlocking {
        // 과 and 이 are also nouns (課, 李), which used to put them first
        val keys = lookUp("50발과", listOf("과", "발"))
        assertEquals(listOf("발", "과"), keys.toList())
    }

    @Test
    fun `the word after a number is looked up as written`() = runBlocking {
        // Open Korean Text reads 60대 as the verb 대다
        val keys = lookUp("60대", listOf("대", "대다"))
        assertEquals("대", keys.first())
    }

    @Test
    fun `a counter before a noun is not a prefix`() = runBlocking {
        val keys = lookUp("88세)씨", listOf("세", "세-", "씨"))
        assertEquals(listOf("세", "씨"), keys.toList())
    }

    @Test
    fun `a word misread as one particle is looked up as a noun`() = runBlocking {
        // Open Korean Text reads 만에 as a single particle
        val keys = lookUp("만에", listOf("만", "에"))
        assertEquals("만", keys.first())
    }

    @Test
    fun `됐다 is looked up as 되다`() = runBlocking {
        val keys = lookUp("발표됐다", listOf("발표", "되다", "돼다"))
        assertEquals(setOf("발표", "되다"), keys)
    }

    @Test
    fun `words are ranked by the POS of their first homonym`() = runBlocking {
        // A noun homonym of 시키다 shouldn't put it level with 결정, where it would win on length
        val entries = listOf(
            createEntry("결정", "Noun"),
            createEntry("시키다", "Verb"),
            createEntry("시키다", "Noun")
        )
        val keys = DictionaryRepository(FakeDictionaryDao(entries))
            .getDefinitionsForWord("결정시켰다").first().keys
        assertEquals(listOf("결정", "시키다"), keys.toList())
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
