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
     * @param singleSyllableParts one-syllable terms that only come from splitting [compounds]
     */
    private class SearchTerms(
        val terms: Map<String, String?>,
        val compounds: List<String>,
        val singleSyllableParts: Set<String>
    )

    private fun getSearchTerms(word: String): SearchTerms {
        val javaTokens = tokenize(word)
        val terms = LinkedHashMap<String, String?>()
        val compounds = mutableListOf<String>()
        val parts = mutableSetOf<String>()
        terms[word] = javaTokens.singleOrNull()?.pos?.toString()

        javaTokens.forEach { token ->
            val pos = token.pos.toString()
            val stem = if (!token.stem.isNullOrEmpty()) token.stem else token.text
            terms.putIfAbsent(stem, pos)

            // Regex to catch numbers followed by Hangul (e.g., "2009년", "12일")
            // This ensures "년" or "일" are added as separate search terms
            val numericSuffixMatch = Regex("^\\d+([ㄱ-ㅎㅏ-ㅣ가-힣]+)$").find(token.text)
            numericSuffixMatch?.let {
                terms.putIfAbsent(it.groupValues[1], null)
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
                            if (sub.length == 1) parts += sub
                        }
                    }
                }
            }
        }

        // Filter out pure numbers (e.g. "2009") to keep the UI chips clean
        return SearchTerms(
            terms = terms.filterKeys { term -> term.any { !it.isDigit() } || term == word },
            compounds = compounds,
            singleSyllableParts = parts
        )
    }

    /**
     * The one-syllable parts worth showing: those in the fewest-word split of a compound into
     * dictionary [words], like 값 in 농산물값 or 팀 in 대표팀. Nearly every syllable of a
     * Sino-Korean compound is a word of its own, so the rest (정 and 부 in 정부) are noise.
     */
    private fun usefulSingleSyllables(compounds: List<String>, words: Set<String>): Set<String> =
        compounds.flatMap { compound -> splitIntoWords(compound, words).orEmpty() }
            .filter { it.length == 1 }
            .toSet()

    /**
     * Splits [compound] into the fewest [words], or null if it can't be split fully (such as
     * loanwords, where partial splits give meaningless syllables). Ties go to the split with
     * the longer first word, since compounds more often end in a short suffix (외교+부, 서울+시).
     */
    private fun splitIntoWords(compound: String, words: Set<String>): List<String>? {
        // splits[i] is the best split of compound.substring(i)
        val splits = arrayOfNulls<List<String>>(compound.length + 1)
        splits[compound.length] = emptyList()
        for (i in compound.length - 1 downTo 0) {
            for (j in compound.length downTo i + 1) {
                val word = compound.substring(i, j)
                val rest = splits[j] ?: continue
                if (word !in words) continue
                val current = splits[i]
                if (current == null || rest.size + 1 < current.size) splits[i] = listOf(word) + rest
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

    private class Lookup(val searchTerms: SearchTerms, val contextNouns: Set<String>)

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
            emit(Lookup(searchTerms, contextNouns(context.sentence, searchTerms.terms.keys)))
        }
            .flowOn(Dispatchers.Default)
            .flatMapLatest { lookup ->
                val terms = lookup.searchTerms.terms
                dictionaryDao.getEntriesForTerms(terms.keys.toList()).map { entries ->
                    val byWord = entries.groupBy { it.entry.word }
                    val keptParts = usefulSingleSyllables(lookup.searchTerms.compounds, byWord.keys)
                    byWord
                        .filterKeys { it !in lookup.searchTerms.singleSyllableParts || it in keptParts }
                        .mapValues { (term, homonyms) ->
                            val ranking = HomonymRanking(
                                tokenPos = terms[term],
                                contextNouns = lookup.contextNouns,
                                categoryPrefixes = categoryPrefixes
                            )
                            homonyms.sortedWith(ranking.comparator)
                        }
                        .toList()
                        // Sort:
                        // 1. Exact match first
                        // 2. POS Priority (Nouns > Verbs > Adverbs > Grammatical markers)
                        // 3. Longest sub-strings next
                        .sortedWith(
                            compareByDescending<Pair<String, List<DictionaryWithSenses>>> {
                                it.first == word
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
}
