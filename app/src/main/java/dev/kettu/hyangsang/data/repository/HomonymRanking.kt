package dev.kettu.hyangsang.data.repository

import dev.kettu.hyangsang.data.defaults.DefaultCategory
import dev.kettu.hyangsang.data.local.dao.DictionaryWithSenses

/**
 * Where a looked up word was found, used to rank homonyms (entries with the same spelling).
 *
 * @param sentence the sentence containing the word, or empty if unknown
 * @param feedCategory the built-in category of the article's feed, or null for other categories
 */
data class LookupContext(
    val sentence: String = "",
    val feedCategory: DefaultCategory? = null
)

/** What comes before a word in its sentence, as far as bound nouns (수, 중, 간) care. */
internal enum class PrecedingWord {
    /** A noun without a particle, or a verb in modifier form (맛볼 수, 정부 간) */
    MODIFIER,

    /** The start of the sentence, punctuation or a particle, where bound nouns can't follow */
    BREAK,

    UNKNOWN
}

/**
 * What a lookup knows when ranking the homonyms of one search term.
 *
 * @param tokenPos the Open Korean Text POS of the token the term came from, or null if unknown
 * @param contextNouns nouns from the surrounding sentence
 * @param categoryPrefixes semantic categories that fit the feed, matched as prefixes
 * @param isCounter whether the term follows a number, as in 3명 or 26일
 * @param preceding what comes before the term in the sentence, if the term starts the word
 */
internal class HomonymRanking(
    private val tokenPos: String?,
    private val contextNouns: Set<String>,
    private val categoryPrefixes: List<String>,
    private val isCounter: Boolean = false,
    private val preceding: PrecedingWord = PrecedingWord.UNKNOWN
) {
    /**
     * Ranks by, in order: being a counter when the term follows a number, being a bound noun
     * when the preceding word allows one, POS matching the tapped token, nouns shared between
     * the sentence and the entry's definitions and examples,
     * a semantic category fitting the feed, vocabulary level (easier words are more likely the
     * common meaning), and homonym number.
     */
    val comparator: Comparator<DictionaryWithSenses> =
        compareBy<DictionaryWithSenses> { counterRank(it) }
            .thenBy { boundNounRank(it) }
            .thenBy { posMismatch(it) }
            .thenByDescending { contextOverlap(it) }
            .thenBy { categoryMismatch(it) }
            .thenBy { levelRank(it.entry.vocabularyLevel) }
            .thenBy { it.entry.homonymNumber }

    // Counters are bound nouns defined as a unit for counting (명: 사람을 세는 단위). After a
    // number, the large number words (천, 만, 억, 조) are numerals instead.
    private fun counterRank(entry: DictionaryWithSenses): Int {
        if (!isCounter) return 0
        val pos = entry.entry.partOfSpeech
        if (entry.entry.word in NUMBER_WORDS) return if (pos == "Numeral") 0 else 1
        return when {
            pos == "Bound Noun" && isUnit(entry) -> 0
            pos == "Bound Noun" -> 1
            isUnit(entry) -> 2
            else -> 3
        }
    }

    // Bound nouns need a modifier before them. Counting units need a number, which counterRank
    // covers, so after other modifiers only the rest count (위치한 섬 is an island, not 섬 the
    // measure of sacks).
    private fun boundNounRank(entry: DictionaryWithSenses): Int {
        val isBound = entry.entry.partOfSpeech == "Bound Noun"
        return when (preceding) {
            PrecedingWord.MODIFIER -> if (isBound && !isUnit(entry)) 0 else 1
            PrecedingWord.BREAK -> if (isBound) 1 else 0
            PrecedingWord.UNKNOWN -> 0
        }
    }

    private fun isUnit(entry: DictionaryWithSenses) = entry.senses.any { "단위" in it.sense.definitionKo }

    private fun posMismatch(entry: DictionaryWithSenses): Int {
        val expected = tokenPos?.let { OKT_TO_DICTIONARY_POS[it] } ?: return 0
        return if (entry.entry.partOfSpeech in expected) 0 else 1
    }

    // Counts sentence nouns found anywhere in the entry's text. Substring matching sidesteps
    // tokenizing the dictionary side, where the nouns carry particles (바다로, 배를).
    private fun contextOverlap(entry: DictionaryWithSenses): Int {
        if (contextNouns.isEmpty()) return 0
        val texts = entry.senses.flatMap { sense ->
            listOf(sense.sense.definitionKo) + sense.examples.map { it.example }
        }
        return contextNouns.count { noun -> texts.any { it.contains(noun) } }
    }

    private fun categoryMismatch(entry: DictionaryWithSenses): Int {
        if (categoryPrefixes.isEmpty()) return 0
        val category = entry.entry.semanticCategory ?: return 1
        return if (categoryPrefixes.any { category.startsWith(it) }) 0 else 1
    }

    private fun levelRank(level: String?): Int = when (level) {
        "Beginner" -> 0
        "Intermediate" -> 1
        "Advanced" -> 2
        else -> 3
    }

    companion object {
        private val NUMBER_WORDS = setOf("십", "백", "천", "만", "억", "조")

        // Open Korean Text POS names to the dictionary's (translated) POS names
        private val OKT_TO_DICTIONARY_POS = mapOf(
            "Noun" to setOf("Noun", "Pronoun", "Numeral", "Bound Noun"),
            "ProperNoun" to setOf("Noun"),
            "Verb" to setOf("Verb", "Auxiliary Verb"),
            "Adjective" to setOf("Adjective", "Auxiliary Adjective"),
            "Adverb" to setOf("Adverb"),
            "Determiner" to setOf("Determiner"),
            "Modifier" to setOf("Determiner"),
            "Exclamation" to setOf("Interjection"),
            "Josa" to setOf("Particle"),
            "Eomi" to setOf("Ending"),
            "Suffix" to setOf("Affix"),
            "VerbPrefix" to setOf("Affix")
        )

        /**
         * The semantic categories (top level, or top > second level) that articles in each
         * built-in feed category tend to be about. General categories have none.
         */
        fun categoryPrefixesFor(category: DefaultCategory?): List<String> = when (category) {
            DefaultCategory.POLITICS,
            DefaultCategory.NORTH_KOREA,
            DefaultCategory.INTERNATIONAL -> listOf("정치와 행정")
            DefaultCategory.ECONOMY,
            DefaultCategory.MARKET -> listOf("경제 생활")
            DefaultCategory.INDUSTRY -> listOf("경제 생활", "과학")
            DefaultCategory.SOCIETY -> listOf("사회 생활", "교육", "정치와 행정 > 사법 및 치안")
            DefaultCategory.CULTURE -> listOf("문화", "종교")
            DefaultCategory.HEALTH -> listOf(
                "삶 > 병과 증상", "삶 > 약품류", "삶 > 치료", "인간 > 신체", "인간 > 생리 현상",
                "인간 > 체력 상태", "식생활 > 영양"
            )
            DefaultCategory.SCIENCE -> listOf("과학")
            DefaultCategory.ENTERTAINMENT -> listOf(
                "문화 > 대중 문화", "문화 > 음악", "문화 > 예술", "문화 > 문화 활동", "삶 > 여가"
            )
            DefaultCategory.SPORTS -> listOf("스포츠", "삶 > 여가 활동")
            DefaultCategory.PEOPLE -> listOf(
                "인간 > 사람의 종류", "사회 생활 > 직업", "사회 생활 > 직위", "삶 > 친족 관계"
            )
            DefaultCategory.NEWS,
            DefaultCategory.LOCAL,
            DefaultCategory.OPINION,
            null -> emptyList()
        }
    }
}
