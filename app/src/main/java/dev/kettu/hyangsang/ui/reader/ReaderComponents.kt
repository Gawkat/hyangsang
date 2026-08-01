package dev.kettu.hyangsang.ui.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import dev.kettu.hyangsang.parser.ContentBlock
import dev.kettu.hyangsang.parser.ContentSpan
import dev.kettu.hyangsang.parser.SpanType

@Composable
fun ClickableWord(
    word: String,
    isSelected: Boolean,
    style: TextStyle,
    onClick: () -> Unit,
) {
    Text(
        text = word,
        style = style.copy(
            color = if (isSelected) MaterialTheme.colorScheme.primary else style.color,
            fontWeight = if (isSelected) FontWeight.Bold else style.fontWeight
        ),
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 1.dp)
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ClickableText(
    text: String,
    modifier: Modifier = Modifier,
    spans: List<ContentSpan> = emptyList(),
    selectedWord: String?,
    onWordClick: (String, String) -> Unit,
    style: TextStyle
) {
    val tokens = remember(text) { text.split(Regex("(?<=\\s)|(?=\\s)")) }
    var currentOffset = 0

    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.Start
    ) {
        tokens.forEach { token ->
            val tokenStart = currentOffset
            val tokenEnd = currentOffset + token.length

            if (token.isBlank()) {
                Text(text = token, style = style)
            } else {
                // Determine if this token is bold/italic
                val isBold =
                    spans.any { (it.type == SpanType.BOLD) && (it.start < tokenEnd) && (it.end > tokenStart) }
                val isItalic =
                    spans.any { (it.type == SpanType.ITALIC) && (it.start < tokenEnd) && (it.end > tokenStart) }

                val tokenStyle = style.copy(
                    fontWeight = if (isBold) FontWeight.Bold else style.fontWeight,
                    fontStyle = if (isItalic) FontStyle.Italic else style.fontStyle
                )

                ClickableWord(
                    word = token,
                    isSelected = selectedWord == token,
                    style = tokenStyle
                ) {
                    val wordToLookup = token.trim { it in "!.?,\"'" }
                    onWordClick(wordToLookup, token)
                }
            }
            currentOffset = tokenEnd
        }
    }
}

@Composable
fun ParagraphContent(
    textBlock: ContentBlock.Text,
    selectedWord: String?,
    baseFontSize: TextUnit,
    onWordClick: (String, String) -> Unit
) {
    ClickableText(
        text = textBlock.text,
        spans = textBlock.spans,
        selectedWord = selectedWord,
        onWordClick = onWordClick,
        style = MaterialTheme.typography.bodyLarge.copy(fontSize = baseFontSize),
        modifier = Modifier.fillMaxWidth()
    )
}
