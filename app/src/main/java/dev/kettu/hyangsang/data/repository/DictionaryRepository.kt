package dev.kettu.hyangsang.data.repository

import dev.kettu.hyangsang.data.local.dao.DictionaryDao
import dev.kettu.hyangsang.data.local.dao.DictionaryWithSenses
import kotlinx.coroutines.flow.Flow
import org.openkoreantext.processor.KoreanTokenJava
import org.openkoreantext.processor.OpenKoreanTextProcessorJava

class DictionaryRepository(
    private val dictionaryDao: DictionaryDao
) {
    fun getStem(word: String): String {
        val normalized = OpenKoreanTextProcessorJava.normalize(word)
        val tokens = OpenKoreanTextProcessorJava.tokenize(normalized)
        val javaTokens: List<KoreanTokenJava> =
            OpenKoreanTextProcessorJava.tokensToJavaKoreanTokenList(tokens)

        // Find the first token that is not space or punctuation and use its stem
        return javaTokens.firstOrNull {
            val pos = it.pos.toString()
            pos != "Space" && pos != "Punctuation"
        }?.let {
            if (it.stem != null && it.stem.isNotEmpty()) it.stem else it.text
        } ?: word
    }

    fun getOfflineEntriesByWord(word: String): Flow<List<DictionaryWithSenses>> {
        val stem = getStem(word)
        return dictionaryDao.getFullEntriesByWord(stem)
    }
}
