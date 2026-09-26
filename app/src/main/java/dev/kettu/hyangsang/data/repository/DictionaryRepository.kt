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
    fun getAllSearchTerms(word: String): List<String> = getSearchTermsWithPos(word).keys.toList()

    /**
     * Search terms for [word], each with the Open Korean Text POS of the token it came from, or
     * null when unknown (such as the whole word when it spans several tokens).
     */
    private fun getSearchTermsWithPos(word: String): Map<String, String?> {
        val javaTokens = tokenize(word)
        val terms = LinkedHashMap<String, String?>()
        terms[word] = javaTokens.singleOrNull()?.pos?.toString()

        javaTokens.forEach { token ->
            val pos = token.pos.toString()
            val stem = if (!token.stem.isNullOrEmpty()) token.stem else token.text
            terms.putIfAbsent(stem, pos)

            // Handle compound nouns by generating all sub-strings (Length >= 2)
            // This lets the dictionary decide which parts are "real" words.
            if (pos == "Noun" && stem.length >= 3) {
                for (i in stem.indices) {
                    for (j in i + 2..stem.length) {
                        val sub = stem.substring(i, j)
                        if (sub.length < stem.length) terms.putIfAbsent(sub, "Noun")
                    }
                }
            }

            // Regex to catch numbers followed by Hangul (e.g., "2009년", "12일")
            // This ensures "년" or "일" are added as separate search terms
            val numericSuffixMatch = Regex("^\\d+([ㄱ-ㅎㅏ-ㅣ가-힣]+)$").find(token.text)
            numericSuffixMatch?.let {
                terms.putIfAbsent(it.groupValues[1], null)
            }
        }

        // Filter out pure numbers (e.g. "2009") to keep the UI chips clean
        return terms.filterKeys { term -> term.any { !it.isDigit() } || term == word }
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

    private class Lookup(val terms: Map<String, String?>, val contextNouns: Set<String>)

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getDefinitionsForWord(
        word: String,
        context: LookupContext = LookupContext()
    ): Flow<Map<String, List<DictionaryWithSenses>>> {
        val categoryPrefixes = HomonymRanking.categoryPrefixesFor(context.feedCategory)
        // Tokenizing with Open Korean Text is CPU-heavy (and the first call loads its
        // dictionaries), so it must not run on the main thread.
        return flow {
            val terms = getSearchTermsWithPos(word)
            emit(Lookup(terms, contextNouns(context.sentence, terms.keys)))
        }
            .flowOn(Dispatchers.Default)
            .flatMapLatest { lookup ->
                dictionaryDao.getEntriesForTerms(lookup.terms.keys.toList()).map { entries ->
                    entries.groupBy { it.entry.word }
                        .mapValues { (term, homonyms) ->
                            val ranking = HomonymRanking(
                                tokenPos = lookup.terms[term],
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
