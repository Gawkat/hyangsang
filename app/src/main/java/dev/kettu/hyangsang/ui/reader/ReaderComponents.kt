package dev.kettu.hyangsang.ui.reader

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.TextUnit
import dev.kettu.hyangsang.parser.ContentBlock
import dev.kettu.hyangsang.parser.ContentSpan
import dev.kettu.hyangsang.parser.SpanType

/**
 * The word the user tapped. [textId] identifies which text on the screen it belongs to, so that
 * only the tapped occurrence is highlighted (not every identical word in the article), and only
 * the affected paragraph has to recompose when the selection changes.
 */
@Immutable
data class WordSelection(
    val textId: String,
    val range: TextRange,
    val token: String
)

/** The selected range if the selection belongs to the text with [textId], otherwise null. */
fun WordSelection?.rangeIn(textId: String): TextRange? =
    if (this != null && this.textId == textId) range else null

private val WordRegex = Regex("\\S+")
private const val LOOKUP_TRIM_CHARS = "!.?,\"'"

/**
 * Korean line breaking: by default Android may break a line between any two Hangul syllables.
 * Phrase-based word breaking (Android 13+) keeps Korean words intact and breaks at spaces,
 * like the previous one-composable-per-word layout did. On older versions this is ignored.
 */
private val KoreanLineBreak = LineBreak(
    strategy = LineBreak.Strategy.HighQuality,
    strictness = LineBreak.Strictness.Normal,
    wordBreak = LineBreak.WordBreak.Phrase
)
private val KoreanLocale = LocaleList("ko-KR")

/** Start/end offsets of every whitespace-separated word, for binary searching tap positions. */
@Immutable
private class WordRanges(val starts: IntArray, val ends: IntArray) {
    /** Index of the word containing [offset] (end-inclusive, so taps on the last glyph count). */
    fun indexAt(offset: Int): Int {
        var lo = 0
        var hi = starts.size - 1
        var candidate = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (starts[mid] <= offset) {
                candidate = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return if (candidate >= 0 && offset <= ends[candidate]) candidate else -1
    }

    companion object {
        fun of(text: String): WordRanges {
            val matches = WordRegex.findAll(text).toList()
            return WordRanges(
                starts = IntArray(matches.size) { matches[it].range.first },
                ends = IntArray(matches.size) { matches[it].range.last + 1 }
            )
        }
    }
}

/**
 * Renders [text] as a single [Text] where every word is tappable.
 *
 * Here the whole paragraph is laid out once, and a single tap handler maps the
 * tap position to a character offset and from there to the word.
 */
@Composable
fun ClickableText(
    text: String,
    textId: String,
    selectedRange: TextRange?,
    onWordClick: (selection: WordSelection, lookupWord: String) -> Unit,
    style: TextStyle,
    modifier: Modifier = Modifier,
    spans: List<ContentSpan> = emptyList(),
) {
    val wordRanges = remember(text) { WordRanges.of(text) }
    val highlightColor = MaterialTheme.colorScheme.primary

    val annotatedText = remember(text, spans, selectedRange, highlightColor) {
        buildWordText(text, spans, selectedRange, highlightColor)
    }

    // Only read from the tap handler, never during composition, so updating it does not
    // trigger recomposition.
    var textLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val currentOnWordClick by rememberUpdatedState(onWordClick)
    val currentTextId by rememberUpdatedState(textId)

    Text(
        text = annotatedText,
        style = style.merge(TextStyle(lineBreak = KoreanLineBreak, localeList = KoreanLocale)),
        onTextLayout = { textLayout = it },
        modifier = modifier.pointerInput(text) {
            detectTapGestures { position ->
                val layout = textLayout ?: return@detectTapGestures

                // Ignore taps below the last line or beside the text on a line
                // (getOffsetForPosition would otherwise snap to the nearest character).
                if (position.y < 0f || position.y > layout.size.height) return@detectTapGestures
                val line = layout.getLineForVerticalPosition(position.y)
                if (position.x < layout.getLineLeft(line) || position.x > layout.getLineRight(line)) {
                    return@detectTapGestures
                }

                val index = wordRanges.indexAt(layout.getOffsetForPosition(position))
                if (index < 0) return@detectTapGestures

                val start = wordRanges.starts[index]
                val end = wordRanges.ends[index]
                val token = text.substring(start, end)
                currentOnWordClick(
                    WordSelection(currentTextId, TextRange(start, end), token),
                    token.trim { it in LOOKUP_TRIM_CHARS }
                )
            }
        }
    )
}

private fun buildWordText(
    text: String,
    spans: List<ContentSpan>,
    selectedRange: TextRange?,
    highlightColor: androidx.compose.ui.graphics.Color
): AnnotatedString = AnnotatedString.Builder(text).apply {
    for (span in spans) {
        val start = span.start.coerceIn(0, text.length)
        val end = span.end.coerceIn(start, text.length)
        if (start == end) continue
        val spanStyle = when (span.type) {
            SpanType.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
            SpanType.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
        }
        addStyle(spanStyle, start, end)
    }
    if (selectedRange != null && selectedRange.end <= text.length) {
        addStyle(
            SpanStyle(color = highlightColor, fontWeight = FontWeight.Bold),
            selectedRange.start,
            selectedRange.end
        )
    }
}.toAnnotatedString()

@Composable
fun ParagraphContent(
    textBlock: ContentBlock.Text,
    textId: String,
    selectedRange: TextRange?,
    baseFontSize: TextUnit,
    onWordClick: (WordSelection, String) -> Unit
) {
    ClickableText(
        text = textBlock.text,
        textId = textId,
        spans = textBlock.spans,
        selectedRange = selectedRange,
        onWordClick = onWordClick,
        style = MaterialTheme.typography.bodyLarge.copy(fontSize = baseFontSize),
        modifier = Modifier.fillMaxWidth()
    )
}
