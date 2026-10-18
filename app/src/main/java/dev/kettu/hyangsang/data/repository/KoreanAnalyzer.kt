package dev.kettu.hyangsang.data.repository

import org.openkoreantext.processor.OpenKoreanTextProcessorJava

/**
 * A token as the lookup uses it, with Open Korean Text's POS names and, for verbs and adjectives,
 * the dictionary form as [stem].
 */
data class KoreanToken(val text: String, val pos: String, val stem: String? = null)

/** Splits text into tokens for dictionary lookups, leaving out spaces and punctuation. */
interface KoreanAnalyzer {
    /**
     * @param sentence the sentence [text] is part of, if known, for analyzers that use the
     *   context
     */
    fun tokenize(text: String, sentence: String? = null): List<KoreanToken>
}

object OktAnalyzer : KoreanAnalyzer {
    override fun tokenize(text: String, sentence: String?): List<KoreanToken> =
        OpenKoreanTextProcessorJava.tokensToJavaKoreanTokenList(
            OpenKoreanTextProcessorJava.tokenize(OpenKoreanTextProcessorJava.normalize(text))
        )
            .filter { it.pos.toString() !in listOf("Space", "Punctuation") }
            .map { KoreanToken(it.text, it.pos.toString(), it.stem) }
}
