package dev.kettu.hyangsang.data.repository

import dev.kettu.hyangsang.analyzer.Tag
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
    /** A noun without a particle, a determiner, or a verb in modifier form (정부 간, 맛볼 수) */
    MODIFIER,

    /** The start of the sentence, punctuation or a particle, where bound nouns can't follow */
    BREAK,

    UNKNOWN
}

/**
 * What a lookup knows when ranking the homonyms of one search term.
 *
 * @param tokenPos the analyzer's tag for the token the term came from, or null if unknown
 * @param contextWords nouns and predicate stems from the surrounding sentence
 * @param categoryPrefixes semantic categories that fit the feed, matched as prefixes
 * @param isCounter whether the term follows a number, as in 3명, 26일 or 두 바퀴
 * @param afterNumberWord whether that number is a word (두 바퀴, 몇 명) rather than digits
 * @param preceding what comes before the term in the sentence, if the term starts the word
 */
internal class HomonymRanking(
    private val tokenPos: Tag?,
    private val contextWords: Set<String>,
    private val categoryPrefixes: List<String>,
    private val isCounter: Boolean = false,
    private val afterNumberWord: Boolean = false,
    private val preceding: PrecedingWord = PrecedingWord.UNKNOWN
) {
    /**
     * Ranks by, in order: being a counter when the term follows a number, being a bound noun
     * when the preceding word allows one, POS matching the tapped token, words shared between
     * the sentence and the entry's examples and definitions, a semantic category fitting the
     * feed, vocabulary level (easier words are more likely the common meaning), and homonym
     * number.
     */
    val comparator: Comparator<DictionaryWithSenses> =
        compareBy<DictionaryWithSenses> { counterRank(it) }
            .thenBy { boundNounRank(it) }
            .thenBy { posMismatch(it) }
            .thenByDescending { contextOverlap(it) }
            .thenBy { categoryMismatch(it) }
            .thenBy { levelRank(it.entry.vocabularyLevel) }
            .thenBy { it.entry.homonymNumber }

    // Counters are defined as a unit for counting (명: 사람을 세는 단위), and after digits are
    // mostly bound nouns. After a number word, units of any kind come first (두 통, two buckets).
    // After a number, the large number words (천, 만, 억, 조) are numerals instead.
    private fun counterRank(entry: DictionaryWithSenses): Int {
        if (!isCounter) return 0
        val pos = entry.entry.partOfSpeech
        if (entry.entry.word in NUMBER_WORDS) return if (pos == "Numeral") 0 else 1
        if (afterNumberWord) return when {
            isUnit(entry) -> 0
            pos == "Bound Noun" -> 1
            else -> 2
        }
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
        val expected = tokenPos?.let { DICTIONARY_POS[it] } ?: return 0
        return if (entry.entry.partOfSpeech in expected) 0 else 1
    }

    // Counts the sentence's words found in the entry's examples, and half as much in its
    // definitions, which are written in more general words. Substring matching sidesteps
    // analyzing the dictionary side, where the nouns carry particles (바다로, 배를) and the stems
    // endings (먹었다).
    private fun contextOverlap(entry: DictionaryWithSenses): Int {
        if (contextWords.isEmpty()) return 0
        val examples = entry.senses.flatMap { sense -> sense.examples.map { it.example } }
        val definitions = entry.senses.map { it.sense.definitionKo }
        return contextWords.sumOf { word ->
            (if (examples.any { it.contains(word) }) 2 else 0) + (if (definitions.any { it.contains(word) }) 1 else 0)
        }
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

        // The analyzer gives a word the class of its most basic nominal entry, not of the one
        // the sentence uses, so nominals only tell nominals from the rest. Which bound noun or
        // noun it is comes from the words around it (see boundNounRank).
        private val NOMINAL_POS = setOf("Noun", "Pronoun", "Numeral", "Bound Noun")

        // The analyzer's tags to the dictionary's (translated) POS names
        private val DICTIONARY_POS = mapOf(
            Tag.NOUN to NOMINAL_POS,
            Tag.PRONOUN to NOMINAL_POS,
            Tag.NUMERAL to NOMINAL_POS,
            Tag.BOUND_NOUN to NOMINAL_POS,
            Tag.UNKNOWN to setOf("Noun"),
            Tag.VERB to setOf("Verb"),
            Tag.AUX_VERB to setOf("Auxiliary Verb"),
            Tag.ADJECTIVE to setOf("Adjective"),
            Tag.AUX_ADJECTIVE to setOf("Auxiliary Adjective"),
            Tag.ADVERB to setOf("Adverb"),
            Tag.DETERMINER to setOf("Determiner"),
            Tag.INTERJECTION to setOf("Interjection"),
            Tag.PARTICLE to setOf("Particle"),
            Tag.ENDING to setOf("Ending"),
            Tag.PREFIX to setOf("Affix"),
            Tag.SUFFIX to setOf("Affix"),
            Tag.PREDICATE_SUFFIX to setOf("Affix")
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
            DefaultCategory.LAW -> listOf("정치와 행정 > 사법 및 치안", "정치와 행정 > 정치 및 행정")
            DefaultCategory.LIFE -> listOf("식생활", "의생활", "주생활", "삶 > 여가")
            DefaultCategory.PEOPLE -> listOf(
                "인간 > 사람의 종류", "사회 생활 > 직업", "사회 생활 > 직위", "삶 > 친족 관계"
            )
            DefaultCategory.NEWS,
            DefaultCategory.LOCAL,
            DefaultCategory.OPINION,
            DefaultCategory.EXPLAINERS,
            DefaultCategory.KIDS,
            null -> emptyList()
        }
    }
}
