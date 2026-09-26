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
    fun `after a number counters come first`() {
        val work = homonym("일", 1, "Noun", "Beginner", "삶 > 일상 행위", "무엇을 이루려고 하는 활동.")
        val sunday = homonym("일", 2, "Noun", "Beginner", "개념 > 시간", "일주일의 마지막 날.")
        val date = homonym("일", 4, "Bound Noun", "Beginner", "개념 > 세는 말", "날이나 날짜를 세는 단위.")
        val ranking = HomonymRanking("Noun", emptySet(), emptyList(), isCounter = true)
        assertEquals(4, listOf(work, sunday, date).sortedWith(ranking.comparator).first().entry.homonymNumber)
    }

    @Test
    fun `after a number large number words are numerals`() {
        val group = homonym("조", 4, "Bound Noun", "Advanced", "개념 > 세는 말", "무리를 세는 단위.")
        val trillion = homonym("조", 6, "Numeral", "Intermediate", "개념 > 수", "억의 만 배가 되는 수.")
        val ranking = HomonymRanking(null, emptySet(), emptyList(), isCounter = true)
        assertEquals(6, listOf(group, trillion).sortedWith(ranking.comparator).first().entry.homonymNumber)
    }

    private val wednesday = homonym("수", 5, "Noun", "Beginner", "개념 > 시간", "수요일.")
    private val means = homonym("수", 3, "Bound Noun", "Beginner", "개념 > 성질", "어떤 일을 할 만한 능력이나 가능성.")
    private val sackMeasure = homonym("수", 11, "Bound Noun", "Advanced", "개념 > 세는 말", "시를 세는 단위.")

    @Test
    fun `after a modifier bound nouns come first, except counting units`() {
        val ranking = HomonymRanking("Noun", emptySet(), emptyList(), preceding = PrecedingWord.MODIFIER)
        assertEquals(
            listOf(3, 5, 11),
            listOf(sackMeasure, wednesday, means).sortedWith(ranking.comparator).map { it.entry.homonymNumber }
        )
    }

    @Test
    fun `after a break bound nouns come last`() {
        val ranking = HomonymRanking("Noun", emptySet(), emptyList(), preceding = PrecedingWord.BREAK)
        assertEquals(5, listOf(means, sackMeasure, wednesday).sortedWith(ranking.comparator).first().entry.homonymNumber)
    }

    @Test
    fun `lookup reads the preceding word from the sentence`() = runBlocking {
        val repository = DictionaryRepository(FakeDao(listOf(wednesday, means)))
        fun firstFor(sentence: String) = runBlocking {
            repository.getDefinitionsForWord("수", LookupContext(sentence)).first().getValue("수").first().entry.homonymNumber
        }
        assertEquals(3, firstFor("다양한 국수를 맛볼 수 있는 축제가 열린다."))
        assertEquals(5, firstFor("수 요일에 만나자."))
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
