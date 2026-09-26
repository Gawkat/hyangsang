package dev.kettu.hyangsang.data.repository

import dev.kettu.hyangsang.data.defaults.DefaultCategory
import dev.kettu.hyangsang.data.local.dao.DictionaryDao
import dev.kettu.hyangsang.data.local.dao.DictionaryWithSenses
import dev.kettu.hyangsang.data.local.dao.SenseWithExamples
import dev.kettu.hyangsang.data.local.entity.DictionaryEntry
import dev.kettu.hyangsang.data.local.entity.DictionaryExample
import dev.kettu.hyangsang.data.local.entity.DictionarySense
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class HomonymRankingTest {

    // Homonyms of 경기 as in the dictionary, with made-up definitions and examples
    private val economy = homonym(
        "경기", 1, "Noun", "Intermediate", "경제 생활 > 경제 행위",
        "매매나 거래에 나타나는 경제 활동 상황.", "경기 침체로 수출이 줄었다."
    )
    private val match = homonym(
        "경기", 2, "Noun", "Beginner", "삶 > 여가 활동",
        "기술을 겨루는 일.", "선수들이 경기에서 이겼다."
    )
    private val convulsion = homonym(
        "경기", 3, "Noun", "Advanced", "삶 > 병과 증상",
        "몸이 떨리는 증상.", "아이가 경기를 일으켰다."
    )
    private val all = listOf(convulsion, economy, match)

    private fun rank(
        entries: List<DictionaryWithSenses> = all,
        tokenPos: String? = "Noun",
        contextNouns: Set<String> = emptySet(),
        category: DefaultCategory? = null
    ): List<Int> = entries
        .sortedWith(
            HomonymRanking(tokenPos, contextNouns, HomonymRanking.categoryPrefixesFor(category)).comparator
        )
        .map { it.entry.homonymNumber }

    @Test
    fun `without context easier words come first`() {
        assertEquals(listOf(2, 1, 3), rank())
    }

    @Test
    fun `feed category boosts matching semantic categories`() {
        assertEquals(listOf(1, 2, 3), rank(category = DefaultCategory.ECONOMY))
        assertEquals(listOf(2, 1, 3), rank(category = DefaultCategory.SPORTS))
        assertEquals(listOf(3, 2, 1), rank(category = DefaultCategory.HEALTH))
    }

    @Test
    fun `nouns shared with the sentence outweigh the feed category`() {
        assertEquals(
            listOf(1, 2, 3),
            rank(contextNouns = setOf("침체", "수출"), category = DefaultCategory.SPORTS)
        )
    }

    @Test
    fun `entries matching the token's POS come first`() {
        val determiner = homonym("전", 6, "Determiner", "Beginner", "개념 > 순서")
        val noun = homonym("전", 2, "Noun", "Beginner", "개념 > 순서")
        assertEquals(listOf(6, 2), rank(listOf(noun, determiner), tokenPos = "Determiner"))
        assertEquals(listOf(2, 6), rank(listOf(noun, determiner), tokenPos = "Noun"))
    }

    @Test
    fun `homonym number breaks remaining ties`() {
        val first = homonym("배", 1, "Noun", "Beginner", "인간 > 신체 부위")
        val second = homonym("배", 2, "Noun", "Beginner", "사회 생활 > 교통 수단")
        assertEquals(listOf(1, 2), rank(listOf(second, first)))
    }

    @Test
    fun `lookup ranks homonyms using the sentence and feed`() = runBlocking {
        val repository = DictionaryRepository(FakeDao(all))

        val bySentence = repository.getDefinitionsForWord(
            "경기",
            LookupContext("정부는 경기 침체로 수출이 감소했다고 밝혔다.", DefaultCategory.SPORTS)
        ).first()
        assertEquals(listOf(1, 2, 3), bySentence.getValue("경기").map { it.entry.homonymNumber })

        val byFeed = repository.getDefinitionsForWord(
            "경기",
            LookupContext("", DefaultCategory.HEALTH)
        ).first()
        assertEquals(listOf(3, 2, 1), byFeed.getValue("경기").map { it.entry.homonymNumber })
    }

    private class FakeDao(val entries: List<DictionaryWithSenses>) : DictionaryDao {
        override suspend fun insertEntry(entry: DictionaryEntry) = Unit
        override suspend fun insertSenses(senses: List<DictionarySense>) = Unit
        override suspend fun insertExamples(examples: List<DictionaryExample>) = Unit
        override fun getEntriesByWord(word: String): Flow<List<DictionaryEntry>> = flowOf(emptyList())
        override fun getSensesForEntry(entryId: Long): Flow<List<DictionarySense>> = flowOf(emptyList())
        override fun getFullEntriesByWord(word: String): Flow<List<DictionaryWithSenses>> = flowOf(emptyList())
        override fun getEntriesForTerms(words: List<String>): Flow<List<DictionaryWithSenses>> =
            flowOf(entries.filter { it.entry.word in words })
    }

    private fun homonym(
        word: String,
        number: Int,
        pos: String,
        level: String,
        category: String,
        definition: String = "",
        example: String? = null
    ) = DictionaryWithSenses(
        entry = DictionaryEntry(
            originalId = "${word}_$number",
            word = word,
            origin = null,
            homonymNumber = number,
            partOfSpeech = pos,
            vocabularyLevel = level,
            semanticCategory = category,
            lexicalUnit = "Word",
            pronunciation = null,
            audioUrl = null
        ),
        senses = listOf(
            SenseWithExamples(
                sense = DictionarySense(entryId = 0, definitionKo = definition, definitionEn = null, translationEn = null),
                examples = listOfNotNull(example?.let { DictionaryExample(senseId = 0, example = it, type = null) })
            )
        )
    )
}
