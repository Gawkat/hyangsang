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

/**
 * What a lookup knows when ranking the homonyms of one search term.
 *
 * @param tokenPos the Open Korean Text POS of the token the term came from, or null if unknown
 * @param contextNouns nouns from the surrounding sentence
 * @param categoryPrefixes semantic categories that fit the feed, matched as prefixes
 */
internal class HomonymRanking(
    private val tokenPos: String?,
    private val contextNouns: Set<String>,
    private val categoryPrefixes: List<String>
) {
    /**
     * Ranks by, in order: POS matching the tapped token, nouns shared between the sentence and
     * the entry's definitions and examples, a semantic category fitting the feed, vocabulary
     * level (easier words are more likely the common meaning), and homonym number.
     */
    val comparator: Comparator<DictionaryWithSenses> =
        compareBy<DictionaryWithSenses> { posMismatch(it) }
            .thenByDescending { contextOverlap(it) }
            .thenBy { categoryMismatch(it) }
            .thenBy { levelRank(it.entry.vocabularyLevel) }
            .thenBy { it.entry.homonymNumber }

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
