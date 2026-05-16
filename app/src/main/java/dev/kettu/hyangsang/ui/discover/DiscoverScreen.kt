package dev.kettu.hyangsang.ui.discover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.kettu.hyangsang.R
import dev.kettu.hyangsang.data.local.entity.Article
import dev.kettu.hyangsang.data.local.entity.ArticleWithFeed
import dev.kettu.hyangsang.data.local.entity.RssFeed
import dev.kettu.hyangsang.ui.theme.HyangsangTheme
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(
    articlesWithFeed: List<ArticleWithFeed>,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onArticleClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.app_name)) }
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        modifier = modifier
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(articlesWithFeed) { articleWithFeed ->
                    ArticleCard(
                        articleWithFeed = articleWithFeed,
                        onClick = { onArticleClick(articleWithFeed.article.id) })
                }
            }
        }
    }
}

@OptIn(ExperimentalTime::class)
@Preview(showBackground = true)
@Composable
fun DiscoverScreenPreview() {
    HyangsangTheme {
        DiscoverScreen(
            articlesWithFeed = listOf(
                ArticleWithFeed(
                    article = Article(
                        id = 1,
                        feedId = 1,
                        title = "스타크래프트 2: 자유의 날개 다시 보기",
                        description = "실시간 전략 게임의 전설, 스타크래프트 2의 캠페인과 멀티플레이어 매력을 심층 분석합니다.",
                        pubDate = Clock.System.now().toString(),
                        sourceUrl = "https://www.source.url/article"
                    ),
                    feed = RssFeed(
                        0,
                        "Feed Title",
                        "Feed URL",
                        "Feed Category"
                    )
                )
            ),
            isRefreshing = false,
            onRefresh = {},
            onArticleClick = {}
        )
    }
}
