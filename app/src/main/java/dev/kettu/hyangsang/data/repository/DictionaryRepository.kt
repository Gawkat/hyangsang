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

        // TODO: figure out how to handle compound words better (e.g. "한국전력")

        val stems = javaTokens
            .filter { it.pos.toString() !in listOf("Space", "Punctuation") }
            .flatMap { token ->
                val list = mutableListOf<String>()
                val stem = if (!token.stem.isNullOrEmpty()) token.stem else token.text
                list.add(stem)

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
        }
    }
}
