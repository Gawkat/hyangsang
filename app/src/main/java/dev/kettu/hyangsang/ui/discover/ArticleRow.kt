package dev.kettu.hyangsang.ui.discover

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.kettu.hyangsang.R
import dev.kettu.hyangsang.data.local.entity.ArticleSummary
import dev.kettu.hyangsang.data.local.entity.ArticleSummaryWithFeed
import dev.kettu.hyangsang.data.local.entity.RssFeed
import dev.kettu.hyangsang.ui.theme.HyangsangTheme
import dev.kettu.hyangsang.ui.utils.DateTimeUtils
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * A flat article list row. Read articles drop to a regular weight and muted colour,
 * so unread ones stand out without needing a badge.
 *
 * @param showSavedTime show when the article was saved instead of when it was published
 */
@Composable
fun ArticleRow(
    articleWithFeed: ArticleSummaryWithFeed,
    onClick: () -> Unit,
    onSaveClick: () -> Unit,
    modifier: Modifier = Modifier,
    showSavedTime: Boolean = false
) {
    val article = articleWithFeed.article
    val isRead = article.lastReadDate != null
    val isSaved = article.savedDate != null
    val time = if (showSavedTime && article.savedDate != null) {
        stringResource(R.string.saved_ago, DateTimeUtils.formatRelativeTime(article.savedDate))
    } else {
        DateTimeUtils.formatRelativeTime(article.pubDate ?: article.addedDate)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "${articleWithFeed.feed.title} · $time",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = article.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (isRead) FontWeight.Normal else FontWeight.Bold,
                    color = if (isRead) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                if (article.description.isNotBlank()) {
                    Text(
                        text = article.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(onClick = onSaveClick, modifier = Modifier.padding(top = 4.dp)) {
                Icon(
                    imageVector = if (isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                    contentDescription = stringResource(
                        if (isSaved) R.string.unsave_article else R.string.save_article
                    ),
                    tint = if (isSaved) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

@OptIn(ExperimentalTime::class)
@Preview(showBackground = true)
@Composable
fun ArticleRowPreview() {
    HyangsangTheme {
        ArticleRow(
            articleWithFeed = ArticleSummaryWithFeed(
                article = ArticleSummary(
                    id = 1,
                    feedId = 1,
                    title = "스타크래프트 2: 자유의 날개 다시 보기",
                    description = "실시간 전략 게임의 전설, 스타크래프트 2의 캠페인과 멀티플레이어 매력을 심층 분석합니다.",
                    pubDate = Clock.System.now().toString(),
                    addedDate = Clock.System.now().toString(),
                    lastReadDate = null,
                    savedDate = null
                ),
                feed = RssFeed(
                    0,
                    "Feed Title",
                    "Feed URL",
                    "Feed Category"
                )
            ),
            onClick = {},
            onSaveClick = {}
        )
    }
}
