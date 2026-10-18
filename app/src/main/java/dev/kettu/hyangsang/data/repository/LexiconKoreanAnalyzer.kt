package dev.kettu.hyangsang.data.repository

import dev.kettu.hyangsang.analyzer.LookupAnalyzer
import dev.kettu.hyangsang.analyzer.Tag

/**
 * [KoreanAnalyzer] using the dictionary-driven [LookupAnalyzer], which is to replace Open Korean
 * Text. Lookups don't use it yet. Its best analysis of each word is turned into tokens like Open Korean
 * Text's: a predicate with its endings is one token with the dictionary form as stem, and the
 * endings follow it as tokens of their own with the dictionary's form (-면서) as stem.
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

    private fun core(word: String) = word.trim { !it.isLetterOrDigit() }

    private fun tokens(word: String, previous: String?, next: String?): List<KoreanToken> {
        val morphemes = analyzer.analyze(word, previous, next).firstOrNull()?.morphemes ?: return emptyList()
        val tokens = mutableListOf<KoreanToken>()
        var i = 0
        while (i < morphemes.size) {
            val m = morphemes[i]
            when {
                m.tag.isNominal || m.tag == Tag.PREFIX -> tokens += KoreanToken(m.form, "Noun")
                m.tag == Tag.SUFFIX -> tokens += KoreanToken(m.form, "Suffix")
                m.tag == Tag.COPULA -> Unit
                m.tag.isPredicate -> {
                    var last = i
                    while (morphemes.getOrNull(last + 1)?.tag == Tag.ENDING) last++
                    val surface = word.substring(m.start.coerceAtMost(word.length), morphemes[last].end.coerceAtMost(word.length))
                    tokens += when (m.tag) {
                        Tag.PREDICATE_SUFFIX -> KoreanToken(surface, "Suffix", m.form + "다")
                        Tag.ADJECTIVE, Tag.AUX_ADJECTIVE -> KoreanToken(surface, "Adjective", m.headword)
                        else -> KoreanToken(surface, "Verb", m.headword)
                    }
                }
                m.tag == Tag.PARTICLE -> tokens += KoreanToken(m.form, "Josa")
                m.tag == Tag.ENDING -> tokens += KoreanToken(m.form, "Eomi", m.headword)
                m.tag == Tag.ADVERB -> tokens += KoreanToken(m.form, "Adverb")
                m.tag == Tag.DETERMINER -> tokens += KoreanToken(m.form, "Determiner")
                m.tag == Tag.INTERJECTION -> tokens += KoreanToken(m.form, "Exclamation")
                m.tag == Tag.NUMBER -> tokens += KoreanToken(m.form, "Number")
                m.tag == Tag.FOREIGN -> tokens += KoreanToken(m.form, "Alpha")
                else -> tokens += KoreanToken(m.form, "Foreign")
            }
            i++
        }
        return tokens
    }

    private companion object {
        val WHITESPACE = Regex("\\s+")
    }
}
