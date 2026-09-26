package dev.kettu.hyangsang.ui.reader

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.toUpperCase
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.kettu.hyangsang.R
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
    selection: WordSelection?,
    onWordClick: (WordSelection, String) -> Unit,
    modifier: Modifier = Modifier,
    fontFamily: FontFamily = FontFamily.Default
) {
    Column(modifier = modifier.padding(bottom = 24.dp)) {
        ClickableText(
            text = articleWithFeed.feed.title.toUpperCase(Locale.current),
            textId = "feed-title",
            selectedRange = selection.rangeIn("feed-title"),
            onWordClick = onWordClick,
            style = MaterialTheme.typography.labelMedium.copy(
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        )

        ClickableText(
            text = articleWithFeed.article.title,
            textId = "article-title",
            selectedRange = selection.rangeIn("article-title"),
            onWordClick = onWordClick,
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                fontFamily = fontFamily
            ),
            modifier = Modifier.padding(vertical = 8.dp)
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            if (articleWithFeed.article.pubDate != null) {
                Text(
                    text = DateTimeUtils.formatDateTime(articleWithFeed.article.pubDate),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        val content = articleWithFeed.article.content
        val wordCount = remember(content) { countWords(content) }
        val readingTime = maxOf(1, wordCount / wordsPerMinute)

        Text(
            text = stringResource(
                R.string.reading_time_and_words,
                pluralStringResource(R.plurals.reading_time, readingTime, readingTime),
                pluralStringResource(R.plurals.word_count, wordCount, wordCount)
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        HorizontalDivider(modifier = Modifier.padding(top = 16.dp))
    }
}

private val WhitespaceRegex = Regex("\\s+")

private fun countWords(content: List<ContentBlock>?): Int =
    content?.sumOf { block ->
        val text = when (block) {
            is ContentBlock.Text -> block.text
            is ContentBlock.Dateline -> block.text
            else -> return@sumOf 0
        }
        text.split(WhitespaceRegex).count { it.isNotBlank() }
    } ?: 0

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
            selection = null,
            onWordClick = { _, _ -> }
        )
    }
}
