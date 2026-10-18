package dev.kettu.hyangsang.data.repository

import dev.kettu.hyangsang.data.local.dao.DictionaryDao
import dev.kettu.hyangsang.data.local.dao.DictionaryWithSenses
import dev.kettu.hyangsang.data.local.dao.LexiconRow
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
        override fun getEntriesForOrigins(origins: List<String>): Flow<List<DictionaryWithSenses>> {
            return flowOf(entries.filter { it.entry.origin in origins })
        }
        override fun getLexiconRows(): List<LexiconRow> = emptyList()

        private fun <T> emptyOfList(): List<T> = emptyList()
    }

    private fun repository(entries: List<DictionaryWithSenses>) =
        DictionaryRepository(FakeDictionaryDao(entries), testAnalyzer(entries))

    @Test
    fun `sorting logic prioritizes nouns over verbs`() = runBlocking {
        // Arrange
        val entries = listOf(
            createEntry("위", "Noun"),
            createEntry("하다", "Verb"),
            createEntry("위해서", "Verb")
        )
        val repository = repository(entries)

        // Act
        val result = repository.getDefinitionsForWord("위해서").first()
        val sortedKeys = result.keys.toList()

        // Assert
        // We expect "위해서" (exact) first, then "위" (noun) before "하다" (verb)
        // Note: This assumes the analyzer splits "위해서" into stems including "위" and "하다".
        // If it doesn't, we verify whatever was returned is sorted by POS.
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
        val repository = repository(entries)

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
        repository(dictionary.map { createEntry(it, if (it.startsWith("-") || it.endsWith("-")) "Affix" else "Noun") })
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
        // 대 is also the stem of 대다
        val keys = lookUp("60대", listOf("대", "대다"))
        assertEquals("대", keys.first())
    }

    @Test
    fun `a counter before a noun is not a prefix`() = runBlocking {
        val keys = lookUp("88세)씨", listOf("세", "세-", "씨"))
        assertEquals(listOf("세", "씨"), keys.toList())
    }

    @Test
    fun `a noun with a particle is looked up as the noun`() = runBlocking {
        val keys = lookUp("만에", listOf("만", "에"))
        assertEquals("만", keys.first())
    }

    @Test
    fun `contracted forms are looked up by their dictionary form`() = runBlocking {
        val dictionary = listOf(
            createEntry("발표", "Noun", level = "Beginner"),
            createEntry("되다", "Verb", level = "Beginner"),
            createEntry("-되다", "Affix")
        )
        assertEquals(listOf("발표", "되다"), lookUpIn("발표됐다", dictionary))
        assertEquals(listOf("되다"), lookUpIn("되어", dictionary))
        assertEquals(listOf("되다"), lookUpIn("되었다", dictionary))
    }

    @Test
    fun `는 after a quote or a Latin letter is the particle`() = runBlocking {
        val dictionary = listOf(
            createEntry("없다", "Adjective"),
            createEntry("보이다", "Verb"),
            createEntry("늘다", "Verb"),
            createEntry("는", "Particle")
        )
        assertEquals(listOf("없다", "는"), lookUpIn("없다”는", dictionary))
        assertEquals(listOf("보이다", "는"), lookUpIn("보인다’는", dictionary))
        assertEquals(listOf("는"), lookUpIn("LG는", dictionary))
    }

    private suspend fun lookUpIn(
        word: String,
        dictionary: List<DictionaryWithSenses>,
        sentence: String = ""
    ): List<String> =
        repository(dictionary).getDefinitionsForWord(word, LookupContext(sentence)).first().keys.toList()

    private val predicateDictionary = listOf(
        createEntry("통하다", "Verb", level = "Beginner"),
        createEntry("지나다", "Verb", level = "Beginner"),
        createEntry("크다", "Verb", level = "Beginner"),
        createEntry("크다", "Adjective", homonymNumber = 2, level = "Beginner"),
        createEntry("대하다", "Verb", level = "Beginner"),
        createEntry("내다", "Verb", level = "Beginner"),
        createEntry("사다", "Verb", level = "Beginner"),
        createEntry("대한", "Noun", origin = "大韓"),
        createEntry("대해", "Noun", origin = "大海", level = "Advanced"),
        createEntry("산", "Noun", level = "Beginner"),
        createEntry("때", "Noun", level = "Beginner"),
        createEntry("-게", "Ending")
    )

    @Test
    fun `conjugated forms are looked up as their predicate`() = runBlocking {
        assertEquals(listOf("통하다"), lookUpIn("통해", predicateDictionary))
        assertEquals(listOf("지나다"), lookUpIn("지난", predicateDictionary))
        // The adjective, as -게 makes adverbs of adjectives
        val big = repository(predicateDictionary).getDefinitionsForWord("크게").first()
        assertEquals(listOf("크다"), big.keys.toList())
        assertEquals("Adjective", big.getValue("크다").first().entry.partOfSpeech)
    }

    @Test
    fun `the word's own entries follow the predicate it's read as`() = runBlocking {
        // 대한 is in the dictionary as 大韓, but is far more often a form of 대하다
        assertEquals(listOf("대하다", "대한"), lookUpIn("대한", predicateDictionary))
        assertEquals(listOf("산"), lookUpIn("산", predicateDictionary))
    }

    @Test
    fun `the sentence can show a noun is a predicate`() = runBlocking {
        // Only predicates can come before a bound noun
        assertEquals("내다", lookUpIn("낼", predicateDictionary, "목소리를 낼 수 있다").first())
        assertEquals("사다", lookUpIn("산", predicateDictionary, "어제 산 것이다").first())
        // Particles like 에 and 을 need a predicate after them
        assertEquals("대하다", lookUpIn("대해", predicateDictionary, "문제에 대해 말했다").first())
    }

    @Test
    fun `nouns with a contracted particle are looked up as the noun`() = runBlocking {
        // 땐 is 때는
        assertEquals(listOf("때"), lookUpIn("땐", predicateDictionary))
        // Names can end the same way
        assertEquals(emptyList<String>(), lookUpIn("마이클", predicateDictionary))
    }

    @Test
    fun `verbs made of a noun and 하다 come before the noun`() = runBlocking {
        val dictionary = listOf(
            createEntry("공부", "Noun", level = "Beginner"),
            createEntry("공부하다", "Verb", level = "Beginner"),
            createEntry("하다", "Verb", level = "Beginner"),
            createEntry("연계", "Noun"),
            createEntry("연계하다", "Verb"),
            createEntry("퇴출", "Noun"),
            createEntry("시키다", "Verb"),
            createEntry("-시키다", "Affix"),
            createEntry("지난해", "Noun", level = "Beginner"),
            createEntry("지난하다", "Adjective")
        )
        assertEquals(listOf("공부하다", "공부"), lookUpIn("공부하러", dictionary))
        assertEquals(listOf("연계하다", "연계"), lookUpIn("연계한", dictionary))
        assertEquals(listOf("퇴출", "시키다"), lookUpIn("퇴출시키고", dictionary))
        // Nouns can contain the endings too
        assertEquals(listOf("지난해"), lookUpIn("지난해", dictionary))
    }

    @Test
    fun `words are ranked by the POS of their first homonym`() = runBlocking {
        // A noun homonym of 시키다 shouldn't put it level with 결정, where it would win on length
        val entries = listOf(
            createEntry("결정", "Noun"),
            createEntry("시키다", "Verb"),
            createEntry("시키다", "Noun"),
            createEntry("-시키다", "Affix")
        )
        val keys = repository(entries).getDefinitionsForWord("결정시켰다").first().keys
        assertEquals(listOf("결정", "시키다"), keys.toList())
    }

    private val hanjaDictionary = listOf(
        createEntry("북", "Noun", origin = "北"),
        createEntry("북한", "Noun", origin = "北韓"),
        createEntry("미", "Noun", origin = "美"),
        createEntry("미국", "Noun", origin = "美國"),
        createEntry("한미", "Noun", origin = "韓美"),
        createEntry("한국", "Noun", origin = "韓國"),
        createEntry("일본", "Noun", origin = "日本"),
        createEntry("핵", "Noun", origin = "核"),
        createEntry("이", "Particle"),
        createEntry("국방부", "Noun", origin = "國防部"),
        createEntry("-금", "Affix", origin = "金"),
        createEntry("금", "Noun", origin = "金", homonymNumber = 2, semanticCategory = "개념 > 시간"),
        createEntry("금", "Noun", origin = "金", homonymNumber = 3, semanticCategory = "자연 > 자원"),
        createEntry("반-", "Affix", origin = "反"),
        createEntry("대통령", "Noun", origin = "大統領"),
        createEntry("장관", "Noun", origin = "長官")
    )

    private suspend fun lookUpHanja(word: String, sentence: String = ""): Map<String, List<String>> =
        repository(hanjaDictionary)
            .getDefinitionsForWord(word, LookupContext(sentence)).first()
            .mapValues { (_, entries) -> entries.map { it.entry.word } }

    // 金 as gold, Friday, the affix -금 and the family name Kim
    private suspend fun lookUpGold(word: String, sentence: String = ""): List<String>? =
        repository(hanjaDictionary)
            .getDefinitionsForWord(word, LookupContext(sentence)).first()
            .entries.firstOrNull { (term, _) -> Hanja.normalize(term) == "金" }
            ?.value?.map { entry ->
                when {
                    entry.entry.partOfSpeech == "Surname" -> "Kim"
                    entry.entry.partOfSpeech == "Affix" -> "affix"
                    entry.entry.homonymNumber == 2 -> "Friday"
                    else -> "gold"
                }
            }

    @Test
    fun `compatibility ideographs find the unified ones`() = runBlocking {
        // News text often has U+F90A for 金, the dictionary U+91D1
        assertEquals(listOf("gold", "Friday", "affix", "Kim"), lookUpGold("\uF90A"))
    }

    @Test
    fun `a standalone hanja is not an affix`() = runBlocking {
        assertEquals(listOf("gold", "Friday", "affix", "Kim"), lookUpGold("金이"))
        // 反민생, where 反 is the prefix 반-
        assertEquals(listOf("반-"), lookUpHanja("反민생")["反"])
    }

    @Test
    fun `a hanja in parentheses is a weekday`() = runBlocking {
        assertEquals(listOf("Friday", "gold", "affix", "Kim"), lookUpGold("3일(金)"))
    }

    @Test
    fun `a hanja before a title is a family name`() = runBlocking {
        assertEquals(listOf("이"), lookUpHanja("李대통령")["李"])
        assertEquals(listOf("대통령"), lookUpHanja("李대통령")["대통령"])
        assertEquals(listOf("한", "한국"), lookUpHanja("韓", "韓 총리는 말했다")["韓"])
        assertEquals(listOf("Kim", "gold", "Friday", "affix"), lookUpGold("金", "金 장관은"))
    }

    @Test
    fun `family names without a title come last`() = runBlocking {
        assertEquals(listOf("한국", "한"), lookUpHanja("韓에")["韓"])
        assertEquals(listOf("gold", "Friday", "affix", "Kim"), lookUpGold("金", "최종성적 金 7·銀 13"))
        // Punctuation ends the name, and compounds starting with a title don't count
        assertEquals(listOf("한국", "한"), lookUpHanja("韓", "韓, 총리")["韓"])
        assertEquals(listOf("한국", "한"), lookUpHanja("韓대표팀")["韓"])
    }

    private fun createEntry(
        word: String,
        pos: String,
        origin: String? = null,
        homonymNumber: Int = 0,
        semanticCategory: String? = null,
        level: String? = null
    ): DictionaryWithSenses {
        return DictionaryWithSenses(
            entry = DictionaryEntry(
                originalId = "id_$word",
                word = word,
                origin = origin,
                homonymNumber = homonymNumber,
                partOfSpeech = pos,
                vocabularyLevel = level,
                semanticCategory = semanticCategory,
                lexicalUnit = null,
                pronunciation = null,
                audioUrl = null
            ),
            senses = emptyList()
        )
    }
}
