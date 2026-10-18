package dev.kettu.hyangsang.data.repository

import dev.kettu.hyangsang.analyzer.LookupAnalyzer
import dev.kettu.hyangsang.analyzer.Tag

/**
 * [KoreanAnalyzer] using the dictionary-driven [LookupAnalyzer]. Its best analysis of each word
 * is turned into tokens: a predicate with its endings is one token with the dictionary form as
 * stem, and the endings follow it as tokens of their own with the dictionary's form (-면서) as
 * stem. Other morphemes are a token each.
 *
 * With a sentence, the words around each word are taken from it, for the analyzer's context.
 */
class LexiconKoreanAnalyzer(private val analyzer: LookupAnalyzer) : KoreanAnalyzer {
    override fun tokenize(text: String, sentence: String?): List<KoreanToken> {
        val words = text.split(WHITESPACE).filter { it.isNotEmpty() }
        val sentenceWords = sentence?.split(WHITESPACE)?.filter { it.isNotEmpty() }
        return words.flatMapIndexed { index, word ->
            val (previous, next) = if (words.size == 1 && sentenceWords != null) {
                val i = sentenceWords.indexOfFirst { core(it) == core(word) }
                (if (i > 0) sentenceWords[i - 1] else null) to sentenceWords.getOrNull(i + 1)?.takeIf { i >= 0 }
            } else {
                words.getOrNull(index - 1) to words.getOrNull(index + 1)
            }
            tokens(word, previous, next)
        }
    }

    // Each word on its own, which is enough for these, and much less work than with the context
    override fun contextWords(sentence: String): List<String> =
        sentence.split(WHITESPACE).filter { it.isNotEmpty() }.flatMap { word ->
            analyzer.analyze(word, limit = 1).firstOrNull()?.morphemes.orEmpty().mapNotNull { m ->
                when {
                    m.tag == Tag.NOUN || m.tag == Tag.UNKNOWN -> m.form
                    // The noun of 하다 and 되다 verbs, as their stems contract (도착했다)
                    m.tag.isPredicate && m.tag.isContent && m.form.length > 2 && m.form.last() in "하되" ->
                        m.form.dropLast(1)
                    // One-syllable stems are part of too many other words
                    m.tag.isPredicate && m.tag.isContent && m.form.length > 1 -> m.form
                    else -> null
                }
            }
        }

    private fun core(word: String) = word.trim { !it.isLetterOrDigit() }

    private fun tokens(word: String, previous: String?, next: String?): List<KoreanToken> {
        val morphemes = analyzer.analyze(word, previous, next).firstOrNull()?.morphemes ?: return emptyList()
        val tokens = mutableListOf<KoreanToken>()
        var i = 0
        while (i < morphemes.size) {
            val m = morphemes[i]
            when {
                m.tag == Tag.COPULA -> Unit
                m.tag.isPredicate -> {
                    var last = i
                    while (morphemes.getOrNull(last + 1)?.tag == Tag.ENDING) last++
                    val surface = word.substring(m.start.coerceAtMost(word.length), morphemes[last].end.coerceAtMost(word.length))
                    tokens += KoreanToken(surface, m.tag, m.headword.removePrefix("-"))
                }
                m.tag == Tag.ENDING -> tokens += KoreanToken(m.form, m.tag, m.headword)
                else -> tokens += KoreanToken(m.form, m.tag)
            }
            i++
        }
        return tokens
    }

    private companion object {
        val WHITESPACE = Regex("\\s+")
    }
}
