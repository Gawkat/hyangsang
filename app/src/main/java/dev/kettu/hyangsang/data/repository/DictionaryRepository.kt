package dev.kettu.hyangsang.data.repository

import dev.kettu.hyangsang.data.local.dao.DictionaryDao
import dev.kettu.hyangsang.data.local.dao.DictionaryWithSenses
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import org.openkoreantext.processor.OpenKoreanTextProcessorJava

class DictionaryRepository(
    private val dictionaryDao: DictionaryDao
) {
    /**
     * @param terms search terms, each with the Open Korean Text POS of the token it came from, or
     *   null when unknown (such as the whole word when it spans several tokens)
     * @param compounds noun stems whose parts were added as terms
     * @param splitParts terms only worth showing when they're part of a compound's split into
     *   words: single syllables, and affixes as the dictionary writes them (-부, 외-)
     * @param affixFallbacks tokens that look like affixes, mapped to their affix form; the token
     *   itself is only shown if the dictionary doesn't have the affix
     * @param counters terms right after a number, like 명 in 3명 or 일 in 26일
     * @param leadingNoun the noun the word starts with, like 중 in 중에서, if any
     */
    private class SearchTerms(
        val terms: Map<String, String?>,
        val compounds: List<String>,
        val splitParts: Set<String>,
        val affixFallbacks: Map<String, String>,
        val counters: Set<String>,
        val leadingNoun: String?
    )

    private fun getSearchTerms(word: String): SearchTerms {
        val javaTokens = tokenize(word)
        val terms = LinkedHashMap<String, String?>()
        val compounds = mutableListOf<String>()
        val splitParts = mutableSetOf<String>()
        val affixFallbacks = mutableMapOf<String, String>()
        val counters = mutableSetOf<String>()
        terms[word] = javaTokens.singleOrNull()?.pos?.toString()

        // A word can't start with a particle, so a leading particle token is Open Korean Text
        // misreading a noun and its particle (나흘 만에 -> 만에/Josa, meaning 만 + 에)
        val misreadNoun = javaTokens.firstOrNull()
            ?.takeIf { it.pos.toString() == "Josa" && it.text.length > 1 && it.text.drop(1) in PARTICLES }
            ?.text?.take(1)
        misreadNoun?.let { terms.putIfAbsent(it, null) }

        javaTokens.forEachIndexed { index, token ->
            val pos = token.pos.toString()
            val stem = if (!token.stem.isNullOrEmpty()) token.stem else token.text
            terms.putIfAbsent(stem, pos)

            // A word after a number is almost always a counter (3명, 134개), possibly after a unit
            // (16t급). Its own text is used since Open Korean Text can misread it (60대 -> 대다).
            val previous = javaTokens.subList(0, index).lastOrNull { it.pos.toString() != "Alpha" }
            val isCounter = previous?.pos?.toString() == "Number" && pos in listOf("Noun", "Verb", "Adjective")
            if (isCounter) {
                terms.putIfAbsent(token.text, "Noun")
                counters += token.text
            }

            // Open Korean Text splits off common suffixes (후보군 -> 후보 + 군/Suffix) and some
            // prefixes, as one-syllable nouns (초고속 -> 초 + 고속). The dictionary writes these
            // as -군 and 초-.
            val nextPos = javaTokens.getOrNull(index + 1)?.pos?.toString()
            val affix = when (pos) {
                "Suffix" -> "-$stem"
                "Noun" if stem.length == 1 && nextPos == "Noun" && !isCounter -> "$stem-"
                else -> null
            }
            if (affix != null && affix !in terms) {
                terms[affix] = pos
                if (stem != word) affixFallbacks[stem] = affix
            }

            // Regex to catch numbers followed by Hangul (e.g., "2009년", "12일")
            // This ensures "년" or "일" are added as separate search terms
            val numericSuffixMatch = Regex("^\\d+([ㄱ-ㅎㅏ-ㅣ가-힣]+)$").find(token.text)
            numericSuffixMatch?.let {
                terms.putIfAbsent(it.groupValues[1], null)
                counters += it.groupValues[1]
            }

            // Handle compound nouns by generating all sub-strings. This lets the dictionary
            // decide which parts are "real" words.
            if (pos == "Noun" && stem.length >= 2) {
                compounds += stem
                for (i in stem.indices) {
                    for (j in i + 1..stem.length) {
                        val sub = stem.substring(i, j)
                        if (sub.length < stem.length && sub !in terms) {
                            terms[sub] = "Noun"
                            if (sub.length == 1) splitParts += sub
                        }
                    }
                }
                // The start and end of a compound can also be affixes (외교부 -> 외-, -부)
                for (i in 1 until stem.length) {
                    for (affix in listOf(stem.substring(0, i) + "-", "-" + stem.substring(i))) {
                        if (affix !in terms) {
                            terms[affix] = null
                            splitParts += affix
                        }
                    }
                }
            }
        }

        // Filter out pure numbers (e.g. "2009") to keep the UI chips clean
        return SearchTerms(
            terms = terms.filterKeys { term -> term.any { !it.isDigit() } || term == word },
            compounds = compounds,
            splitParts = splitParts,
            affixFallbacks = affixFallbacks,
            counters = counters,
            leadingNoun = misreadNoun ?: javaTokens.firstOrNull()?.takeIf { it.pos.toString() == "Noun" }?.text
        )
    }

    /** What comes before [word] in [sentence], for telling whether a bound noun can follow. */
    private fun precedingWord(sentence: String, word: String): PrecedingWord {
        if (sentence.isBlank()) return PrecedingWord.UNKNOWN
        fun String.core() = trim { !it.isLetterOrDigit() }
        val words = sentence.split(Regex("\\s+"))
        val index = words.indexOfFirst { it.core() == word.core() }
        if (index < 0) return PrecedingWord.UNKNOWN
        if (index == 0) return PrecedingWord.BREAK
        // Tapped words can start with punctuation too, as in "(9월"
        if (!words[index].first().isLetterOrDigit()) return PrecedingWord.BREAK

        val previous = words[index - 1]
        if (!previous.last().isLetterOrDigit()) return PrecedingWord.BREAK
        val last = tokenize(previous).lastOrNull() ?: return PrecedingWord.UNKNOWN
        return when (last.pos.toString()) {
            // Suffixes end nouns too (1960년대 -> 대/Suffix)
            "Noun", "ProperNoun", "Number", "Determiner", "Modifier", "Suffix" -> PrecedingWord.MODIFIER
            "Josa" -> PrecedingWord.BREAK
            // What follows a number, like 대 in 1960년대 or 에 in 5조원에
            "Foreign" -> if (last.text in PARTICLES) PrecedingWord.BREAK else PrecedingWord.MODIFIER
            // Verbs modify nouns in forms ending in ㄴ or ㄹ (맛볼, 위치한, 하는)
            "Verb", "Adjective", "Eomi" ->
                if (last.text.last().finalConsonant() in listOf(FINAL_N, FINAL_L)) PrecedingWord.MODIFIER
                else PrecedingWord.BREAK
            else -> PrecedingWord.UNKNOWN
        }
    }

    // Index of a Hangul syllable's final consonant (0 for none), or -1 for other characters
    private fun Char.finalConsonant(): Int = if (this in '가'..'힣') (this - '가') % 28 else -1

    /**
     * The split parts worth showing: those in the fewest-word split of a compound into dictionary
     * [words], like 값 in 농산물값, 팀 in 대표팀 or the suffix -군 in 후보군. Nearly every
     * syllable of a Sino-Korean compound is a word of its own, so the rest (정 and 부 in 정부)
     * are noise.
     */
    private fun usefulSplitParts(compounds: List<String>, words: Set<String>): Set<String> =
        compounds.flatMap { compound -> splitIntoWords(compound, words).orEmpty().flatten() }
            .toSet()

    /**
     * Splits [compound] into the fewest [words], or null if it can't be split fully (such as
     * loanwords, where partial splits give meaningless syllables). Each part is given as the
     * dictionary forms it matched: itself, and at the start or end of the compound also as a
     * prefix or suffix. Ties go to the split with the longer first word, since compounds more
     * often end in a short suffix (외교+부, 서울+시).
     */
    private fun splitIntoWords(compound: String, words: Set<String>): List<List<String>>? {
        // splits[i] is the best split of compound.substring(i)
        val splits = arrayOfNulls<List<List<String>>>(compound.length + 1)
        splits[compound.length] = emptyList()
        for (i in compound.length - 1 downTo 0) {
            for (j in compound.length downTo i + 1) {
                val rest = splits[j] ?: continue
                val part = compound.substring(i, j)
                val forms = buildList {
                    if (part in words) add(part)
                    if (i == 0 && j < compound.length && "$part-" in words) add("$part-")
                    if (i > 0 && j == compound.length && "-$part" in words) add("-$part")
                }
                if (forms.isEmpty()) continue
                val current = splits[i]
                if (current == null || rest.size + 1 < current.size) splits[i] =
                    listOf(forms) + rest
            }
        }
        return splits[0]
    }

    private fun tokenize(text: String) = OpenKoreanTextProcessorJava.tokensToJavaKoreanTokenList(
        OpenKoreanTextProcessorJava.tokenize(OpenKoreanTextProcessorJava.normalize(text))
    ).filter { it.pos.toString() !in listOf("Space", "Punctuation") }

    // Nouns in the sentence other than the looked up word itself, for matching against entries
    private fun contextNouns(sentence: String, searchTerms: Set<String>): Set<String> =
        if (sentence.isBlank()) emptySet()
        else tokenize(sentence)
            .filter { it.pos.toString() in listOf("Noun", "ProperNoun") && it.text.length >= 2 }
            .map { it.text }
            .filter { it !in searchTerms }
            .toSet()

    private class Lookup(
        val searchTerms: SearchTerms,
        val contextNouns: Set<String>,
        val precedingWord: PrecedingWord
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getDefinitionsForWord(
        word: String,
        context: LookupContext = LookupContext()
    ): Flow<Map<String, List<DictionaryWithSenses>>> {
        val categoryPrefixes = HomonymRanking.categoryPrefixesFor(context.feedCategory)
        // Tokenizing with Open Korean Text is CPU-heavy (and the first call loads its
        // dictionaries), so it must not run on the main thread.
        return flow {
            val searchTerms = getSearchTerms(word)
            emit(
                Lookup(
                    searchTerms = searchTerms,
                    contextNouns = contextNouns(context.sentence, searchTerms.terms.keys),
                    precedingWord = precedingWord(context.sentence, word)
                )
            )
        }
            .flowOn(Dispatchers.Default)
            .flatMapLatest { lookup ->
                val terms = lookup.searchTerms.terms
                dictionaryDao.getEntriesForTerms(terms.keys.toList()).map { entries ->
                    val byWord = entries.groupBy { it.entry.word }
                    val keptParts = usefulSplitParts(lookup.searchTerms.compounds, byWord.keys)
                    byWord
                        .filterKeys { it !in lookup.searchTerms.splitParts || it in keptParts }
                        .filterKeys { term ->
                            val affix = lookup.searchTerms.affixFallbacks[term]
                            affix == null || affix !in byWord
                        }
                        .mapValues { (term, homonyms) ->
                            val ranking = HomonymRanking(
                                tokenPos = terms[term],
                                contextNouns = lookup.contextNouns,
                                categoryPrefixes = categoryPrefixes,
                                isCounter = term in lookup.searchTerms.counters,
                                preceding = if (term == lookup.searchTerms.leadingNoun) {
                                    lookup.precedingWord
                                } else {
                                    PrecedingWord.UNKNOWN
                                }
                            )
                            homonyms.sortedWith(ranking.comparator)
                        }
                        .toList()
                        // Sort:
                        // 1. Exact match first
                        // 2. Particles and endings last. Many have noun homonyms (과, 이), so
                        //    the entries' POS alone would put them first.
                        // 3. Counters first, as the word after a number is what's being counted
                        // 4. POS Priority (Nouns > Verbs > Adverbs > Grammatical markers)
                        // 5. Longest sub-strings next
                        .sortedWith(
                            compareByDescending<Pair<String, List<DictionaryWithSenses>>> {
                                it.first == word
                            }.thenBy {
                                terms[it.first] in GRAMMATICAL_POS
                            }.thenByDescending {
                                it.first in lookup.searchTerms.counters
                            }.thenBy {
                                getPosPriority(it.second)
                            }.thenByDescending {
                                it.first.length
                            }
                        )
                        .toMap()
                }
            }
            .flowOn(Dispatchers.Default)
    }

    private fun getPosPriority(entries: List<DictionaryWithSenses>): Int {
        val poses = entries.mapNotNull { it.entry.partOfSpeech }.distinct()
        if (poses.isEmpty()) return 10 // Default low priority

        return poses.minOf { pos ->
            when (pos) {
                // Nominals / Core semantic units
                "Noun", "Pronoun", "Numeral", "Bound Noun" -> 1
                // Predicates (Unsure if auxiliary verb/adjective POS are present in db)
                "Verb", "Adjective", "Auxiliary Verb", "Auxiliary Adjective" -> 2
                // Modifiers
                "Adverb", "Determiner", "Interjection" -> 3
                // Functional / Grammatical markers (Unsure if ending is present in db)
                "Affix", "Particle", "Ending", "Postpositional Particle" -> 4
                else -> 5
            }
        }
    }

    private companion object {
        // Open Korean Text POS of particles and endings. It also tags particles after numbers as
        // Foreign (5조원에 -> 에/Foreign).
        val GRAMMATICAL_POS = setOf("Josa", "Eomi", "PreEomi", "Foreign")

        val PARTICLES = setOf(
            "이", "가", "을", "를", "은", "는", "의", "에", "에서", "에게", "로", "으로", "와", "과",
            "도", "만", "까지", "부터"
        )

        // Hangul final consonant indexes
        const val FINAL_N = 4
        const val FINAL_L = 8
    }
}
