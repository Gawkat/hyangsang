package dev.kettu.hyangsang.ui.reader

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.kettu.hyangsang.parser.ContentSpan

@Composable
fun ArticleImage(
    url: String,
    caption: String?,
    textId: String,
    selectedRange: TextRange?,
    onWordClick: (WordSelection, String) -> Unit,
    modifier: Modifier = Modifier,
    captionSpans: List<ContentSpan> = emptyList()
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AsyncImage(
            model = url,
            contentDescription = caption,
            modifier = Modifier.fillMaxWidth(),
            contentScale = ContentScale.FillWidth
        )
        if (!caption.isNullOrBlank()) {
            ClickableText(
                text = caption,
                textId = textId,
                spans = captionSpans,
                selectedRange = selectedRange,
                onWordClick = onWordClick,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.padding(top = 4.dp, start = 16.dp, end = 16.dp)
            )
        }
    }
}
