package dev.kettu.hyangsang.ui.discover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(
    articlesWithFeed: List<ArticleWithFeed>,
    onMenuClick: () -> Unit,
    onArticleClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(
                            Icons.Default.Menu,
                            contentDescription = stringResource(R.string.menu_button)
                        )
                    }
                }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
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
                        pubDate = "2024-03-20"
                    ),
                    feed = RssFeed(
                        0,
                        "Feed Title",
                        "Feed URL",
                        "Feed Category"
                    )
                )
            ),
            onMenuClick = {},
            onArticleClick = {})
    }
}
