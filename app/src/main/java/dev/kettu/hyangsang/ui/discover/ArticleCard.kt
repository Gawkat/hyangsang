package dev.kettu.hyangsang.ui.discover

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.kettu.hyangsang.data.local.entity.Article
import dev.kettu.hyangsang.data.local.entity.ArticleWithFeed
import dev.kettu.hyangsang.data.local.entity.RssFeed
import dev.kettu.hyangsang.ui.theme.HyangsangTheme
import dev.kettu.hyangsang.ui.utils.DateTimeUtils
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@Composable
fun ArticleCard(
    articleWithFeed: ArticleWithFeed,
    onClick: () -> Unit
) {
    val article = articleWithFeed.article
    val feed = articleWithFeed.feed

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = feed.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Normal
                )
                Text(
                    text = DateTimeUtils.formatRelativeTime(article.pubDate ?: article.addedDate),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.outline,
                    fontWeight = FontWeight.Normal
                )
            }
            Text(
                text = article.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = article.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalTime::class)
@Preview(showBackground = true)
@Composable
fun ArticleCardPreview() {
    HyangsangTheme {
        ArticleCard(
            articleWithFeed = ArticleWithFeed(
                article = Article(
                    id = 1,
                    feedId = 1,
                    title = "스타크래프트 2: 자유의 날개 다시 보기",
                    description = "실시간 전략 게임의 전설, 스타크래프트 2의 캠페인과 멀티플레이어 매력을 심층 분석합니다.",
                    pubDate = Clock.System.now().toString()
                ),
                feed = RssFeed(
                    0,
                    "Feed Title",
                    "Feed URL",
                    "Feed Category"
                )
            ),
            onClick = {}
        )
    }
}