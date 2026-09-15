package dev.kettu.hyangsang.data.repository

import dev.kettu.hyangsang.data.local.dao.DictionaryDao
import dev.kettu.hyangsang.data.local.dao.DictionaryWithSenses
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.openkoreantext.processor.OpenKoreanTextProcessorJava

class DictionaryRepository(
    private val dictionaryDao: DictionaryDao
) {
    fun getAllSearchTerms(word: String): List<String> {
        val normalized = OpenKoreanTextProcessorJava.normalize(word)
        val tokens = OpenKoreanTextProcessorJava.tokenize(normalized)
        val javaTokens = OpenKoreanTextProcessorJava.tokensToJavaKoreanTokenList(tokens)

        val stems = javaTokens
            .filter { it.pos.toString() !in listOf("Space", "Punctuation") }
            .flatMap { token ->
                val list = mutableListOf<String>()
                val stem = if (!token.stem.isNullOrEmpty()) token.stem else token.text
                list.add(stem)

                // Handle compound nouns by generating all sub-strings (Length >= 2)
                // This lets the dictionary decide which parts are "real" words.
                if (token.pos.toString() == "Noun" && stem.length >= 3) {
                    for (i in stem.indices) {
                        for (j in i + 2..stem.length) {
                            val sub = stem.substring(i, j)
                            if (sub.length < stem.length) list.add(sub)
                        }
                    }
                }

                // Regex to catch numbers followed by Hangul (e.g., "2009년", "12일")
                // This ensures "년" or "일" are added as separate search terms
                val numericSuffixMatch = Regex("^\\d+([ㄱ-ㅎㅏ-ㅣ가-힣]+)$").find(token.text)
                numericSuffixMatch?.let {
                    list.add(it.groupValues[1])
                }

                list
            }

        // Combine original word with stems, remove duplicates,
        // and filter out pure numbers (e.g. "2009") to keep the UI chips clean
        return (listOf(word) + stems)
            .distinct()
            .filter { term -> term.any { !it.isDigit() } || term == word }
    }

    fun getDefinitionsForWord(word: String): Flow<Map<String, List<DictionaryWithSenses>>> {
        val terms = getAllSearchTerms(word)

        return dictionaryDao.getEntriesForTerms(terms).map { entries ->
            entries.groupBy { it.entry.word }
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
