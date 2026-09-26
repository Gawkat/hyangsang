package dev.kettu.hyangsang.ui.reader

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun ArticleHeading(
    text: String,
    level: Int,
    textId: String,
    selectedRange: TextRange?,
    onWordClick: (WordSelection, String) -> Unit,
    modifier: Modifier = Modifier,
    fontFamily: FontFamily = FontFamily.Default
) {
    val style = when (level) {
        1 -> MaterialTheme.typography.headlineLarge
        2 -> MaterialTheme.typography.headlineMedium
        3 -> MaterialTheme.typography.headlineSmall
        else -> MaterialTheme.typography.titleLarge
    }

    ClickableText(
        text = text,
        textId = textId,
        selectedRange = selectedRange,
        onWordClick = onWordClick,
        style = style.copy(fontWeight = FontWeight.Bold, fontFamily = fontFamily),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    )
}
