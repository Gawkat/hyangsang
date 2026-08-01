package dev.kettu.hyangsang.ui.reader

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.kettu.hyangsang.data.local.entity.Article
import dev.kettu.hyangsang.data.local.entity.ArticleWithFeed
import dev.kettu.hyangsang.data.local.entity.RssFeed
import dev.kettu.hyangsang.parser.ContentBlock
import dev.kettu.hyangsang.ui.utils.DateTimeUtils
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private const val wordsPerMinute = 200

@Composable
fun ArticleHeader(
    articleWithFeed: ArticleWithFeed,
    selectedWord: String?,
    onWordClick: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(bottom = 24.dp)) {
        ClickableText(
            text = articleWithFeed.feed.title,
            spans = emptyList(),
            selectedWord = selectedWord,
            onWordClick = onWordClick,
            style = MaterialTheme.typography.labelMedium.copy(
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        )

        ClickableText(
            text = articleWithFeed.article.title,
            spans = emptyList(),
            selectedWord = selectedWord,
            onWordClick = onWordClick,
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold
            ),
            modifier = Modifier.padding(vertical = 8.dp)
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            if (articleWithFeed.article.pubDate != null) {
                Text(
                    text = DateTimeUtils.formatFullDate(articleWithFeed.article.pubDate),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }

        val wordCount = articleWithFeed.article.content?.sumOf { block ->
            when (block) {
                is ContentBlock.Text -> block.text.split(Regex("\\s+"))
                    .filter { it.isNotBlank() }.size

                is ContentBlock.Legacy -> block.text.split(Regex("\\s+"))
                    .filter { it.isNotBlank() }.size

                is ContentBlock.Dateline -> block.text.split(Regex("\\s+"))
                    .filter { it.isNotBlank() }.size

                else -> 0
            }
        } ?: 0
        val readingTime = maxOf(1, wordCount / wordsPerMinute)

        val durationLabel = if (readingTime <= 1) "minute" else "minutes"

        Text(
            text = "~$readingTime $durationLabel ($wordCount words)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )

        HorizontalDivider(modifier = Modifier.padding(top = 16.dp))
    }
}

@OptIn(ExperimentalTime::class)
@Preview(showBackground = true)
@Composable
fun ArticleHeaderPreview() {
    MaterialTheme {
        ArticleHeader(
            articleWithFeed = ArticleWithFeed(
                Article(
                    0,
                    0,
                    "Article Title",
                    "Article Description",
                    listOf(ContentBlock.Text("Article Content")),
                    "Source URL",
                    Clock.System.now().toString(),
                    Clock.System.now().toString()
                ),
                RssFeed(
                    0,
                    "Feed Title",
                    "Feed URL",
                    "Feed Category"
                )
            ),
            selectedWord = null,
            onWordClick = { _, _ -> }
        )
    }
}
