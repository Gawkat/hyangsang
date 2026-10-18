package dev.kettu.hyangsang.data.repository

import dev.kettu.hyangsang.analyzer.Tag
import org.openkoreantext.processor.OpenKoreanTextProcessorJava

/**
 * A token as the lookup uses it, with the analyzer's [tag] and, for verbs, adjectives and
 * endings, the dictionary form as [stem].
 */
data class KoreanToken(val text: String, val tag: Tag, val stem: String? = null)

/** Splits text into tokens for dictionary lookups, leaving out spaces and punctuation. */
interface KoreanAnalyzer {
    /**
     * @param sentence the sentence [text] is part of, if known, for analyzers that use the
     *   context
     */
    fun tokenize(text: String, sentence: String? = null): List<KoreanToken>

    /**
     * The words in [sentence] that tell what it's about, for matching against dictionary entries:
     * its nouns, and the stems of its verbs and adjectives (만들 for 만들었다, 도착 for 도착했다).
     */
    fun contextWords(sentence: String): List<String>
}

/**
 * Open Korean Text, which lookups used before [LexiconKoreanAnalyzer]. Only kept for comparing
 * the two in LookupEvaluationTest, until it's removed from the build.
 */
object OktAnalyzer : KoreanAnalyzer {
    override fun tokenize(text: String, sentence: String?): List<KoreanToken> =
        OpenKoreanTextProcessorJava.tokensToJavaKoreanTokenList(
            OpenKoreanTextProcessorJava.tokenize(OpenKoreanTextProcessorJava.normalize(text))
        )
            .filter { it.pos.toString() !in listOf("Space", "Punctuation") }
            .map { KoreanToken(it.text, TAGS[it.pos.toString()] ?: Tag.FOREIGN, it.stem) }

    override fun contextWords(sentence: String): List<String> =
        tokenize(sentence).filter { it.tag == Tag.NOUN }.map { it.text }

    private val TAGS = mapOf(
        "Noun" to Tag.NOUN,
        "ProperNoun" to Tag.NOUN,
        "Verb" to Tag.VERB,
        "Adjective" to Tag.ADJECTIVE,
        "Adverb" to Tag.ADVERB,
        "Determiner" to Tag.DETERMINER,
        "Modifier" to Tag.DETERMINER,
        "Exclamation" to Tag.INTERJECTION,
        "Josa" to Tag.PARTICLE,
        "Eomi" to Tag.ENDING,
        "PreEomi" to Tag.ENDING,
        "Suffix" to Tag.SUFFIX,
        "VerbPrefix" to Tag.PREFIX,
        "Number" to Tag.NUMBER
    )
}
