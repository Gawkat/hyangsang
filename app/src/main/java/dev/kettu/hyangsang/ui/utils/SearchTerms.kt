package dev.kettu.hyangsang.ui.utils

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import java.text.Normalizer

/**
 * What the user searched for: words that must all appear, in any order and in any of the fields
 * searched. Korean spacing varies, so "코스피 지수" also finds "코스피지수".
 *
 * Text is compared in NFC, since some EPUB tools store Hangul decomposed (NFD), which looks the
 * same but doesn't match what's typed.
 */
class SearchTerms(query: String) {
    val terms: List<String> = nfc(query).split(WHITESPACE).filter { it.isNotEmpty() }

    val isEmpty: Boolean get() = terms.isEmpty()

    // Blank fields are fine; an empty search matches everything
    fun matches(vararg fields: String?): Boolean {
        val normalized = fields.mapNotNull { it?.let(::nfc) }
        return terms.all { term -> normalized.any { it.contains(term, ignoreCase = true) } }
    }

    /** Where the terms appear in [text], once it's in NFC, merged where they overlap. */
    fun matchRanges(text: String): List<IntRange> {
        val normalized = nfc(text)
        val ranges = terms.flatMap { term ->
            generateSequence(normalized.indexOf(term, ignoreCase = true)) { start ->
                normalized.indexOf(term, start + term.length, ignoreCase = true)
            }.takeWhile { it >= 0 }.map { it until it + term.length }.toList()
        }.sortedBy { it.first }

        val merged = mutableListOf<IntRange>()
        for (range in ranges) {
            val last = merged.lastOrNull()
            if (last != null && range.first <= last.last + 1) {
                merged[merged.lastIndex] = last.first..maxOf(last.last, range.last)
            } else {
                merged += range
            }
        }
        return merged
    }

    /** [text] in NFC, with [style] on the terms. */
    fun highlight(text: String, style: SpanStyle): AnnotatedString {
        val normalized = nfc(text)
        return buildAnnotatedString {
            var position = 0
            for (range in matchRanges(normalized)) {
                append(normalized, position, range.first)
                withStyle(style) { append(normalized, range.first, range.last + 1) }
                position = range.last + 1
            }
            append(normalized, position, normalized.length)
        }
    }

    private companion object {
        val WHITESPACE = Regex("\\s+")

        fun nfc(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFC)
    }
}
