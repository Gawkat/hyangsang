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
            .map { if (!it.stem.isNullOrEmpty()) it.stem else it.text }

        // Combine original word with its constituent stems, removing duplicates
        return (listOf(word) + stems).distinct()
    }

    fun getDefinitionsForWord(word: String): Flow<Map<String, List<DictionaryWithSenses>>> {
        val terms = getAllSearchTerms(word)

        return dictionaryDao.getEntriesForTerms(terms).map { entries ->
            entries.groupBy { it.entry.word }
        }
    }
}
