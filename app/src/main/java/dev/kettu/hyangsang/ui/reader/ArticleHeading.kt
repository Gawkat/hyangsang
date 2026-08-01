package dev.kettu.hyangsang.ui.reader

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun ArticleHeading(
    text: String,
    level: Int,
    selectedWord: String?,
    onWordClick: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val style = when (level) {
        1 -> MaterialTheme.typography.headlineLarge
        2 -> MaterialTheme.typography.headlineMedium
        3 -> MaterialTheme.typography.headlineSmall
        else -> MaterialTheme.typography.titleLarge
    }

    ClickableText(
        text = text,
        selectedWord = selectedWord,
        onWordClick = onWordClick,
        style = style.copy(fontWeight = FontWeight.Bold),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    )
}
