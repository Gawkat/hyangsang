package dev.kettu.hyangsang.ui.discover

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.kettu.hyangsang.R
import dev.kettu.hyangsang.data.local.entity.ArticleSummary
import dev.kettu.hyangsang.data.local.entity.ArticleSummaryWithFeed
import dev.kettu.hyangsang.data.local.entity.RssFeed
import dev.kettu.hyangsang.ui.theme.HyangsangTheme
import dev.kettu.hyangsang.ui.utils.DateTimeUtils
import java.time.LocalDate
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DiscoverScreen(
    articlesWithFeed: List<ArticleSummaryWithFeed>,
    filterTitle: String,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onArticleClick: (Long) -> Unit,
    onSaveClick: (ArticleSummaryWithFeed) -> Unit,
    onMenuClick: () -> Unit,
    onSettingsClick: () -> Unit,
    showUnreadOnly: Boolean,
    onShowUnreadOnlyChange: (Boolean) -> Unit,
    feeds: List<RssFeed>,
    onFeedStatusClick: () -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState()
) {
    // The list is sorted newest first, so groupBy keeps the days in order
    val articlesByDay = remember(articlesWithFeed) {
        articlesWithFeed.groupBy {
            DateTimeUtils.localDate(it.article.pubDate ?: it.article.addedDate)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        DiscoverSearchBar(
            query = searchQuery,
            onQueryChange = onSearchQueryChange,
            onMenuClick = onMenuClick,
            onSettingsClick = onSettingsClick,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)
        )
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(bottom = 16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item(key = "header") {
                    DiscoverHeader(
                        title = if (searchQuery.isBlank()) {
                            filterTitle
                        } else {
                            pluralStringResource(
                                R.plurals.search_result_count,
                                articlesWithFeed.size,
                                articlesWithFeed.size
                            )
                        },
                        showUnreadOnly = showUnreadOnly,
                        onShowUnreadOnlyChange = onShowUnreadOnlyChange,
                        feeds = feeds.takeIf { searchQuery.isBlank() },
                        onFeedStatusClick = onFeedStatusClick
                    )
                }
                if (articlesWithFeed.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            text = stringResource(
                                when {
                                    searchQuery.isNotBlank() -> R.string.no_search_results
                                    showUnreadOnly -> R.string.no_unread_articles
                                    else -> R.string.no_articles
                                }
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp)
                        )
                    }
                }
                articlesByDay.forEach { (date, articles) ->
                    stickyHeader(key = "day-$date", contentType = "day") {
                        DayHeader(date)
                    }
                    items(
                        articles,
                        key = { it.article.id },
                        contentType = { "article" }
                    ) { articleWithFeed ->
                        ArticleRow(
                            articleWithFeed = articleWithFeed,
                            onClick = { onArticleClick(articleWithFeed.article.id) },
                            onSaveClick = { onSaveClick(articleWithFeed) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DiscoverHeader(
    title: String,
    showUnreadOnly: Boolean,
    onShowUnreadOnlyChange: (Boolean) -> Unit,
    feeds: List<RssFeed>?,
    onFeedStatusClick: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
    ) {
        Text(text = title, style = MaterialTheme.typography.titleLarge)
        FilterChip(
            selected = showUnreadOnly,
            onClick = { onShowUnreadOnlyChange(!showUnreadOnly) },
            label = { Text(stringResource(R.string.unread_only)) },
            leadingIcon = if (showUnreadOnly) {
                { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
            } else {
                null
            }
        )
        if (feeds != null) FeedStatusLine(feeds, onFeedStatusClick)
    }
}

// "Updated 2 min. ago · 1 feed couldn't update", linking to feed management
@Composable
private fun FeedStatusLine(feeds: List<RssFeed>, onClick: () -> Unit) {
    val enabledFeeds = feeds.filter { it.isEnabled }
    val failedCount = enabledFeeds.count { it.lastSyncError != null }
    // ISO-8601 instants from Instant.toString() sort chronologically as strings
    val lastUpdated = enabledFeeds.maxOfOrNull { it.lastSynced }
        ?.takeUnless { it.startsWith("1970-") }
    if (lastUpdated == null && failedCount == 0) return

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
    ) {
        if (lastUpdated != null) {
            Text(
                text = stringResource(
                    R.string.feeds_updated_ago,
                    DateTimeUtils.formatRelativeTime(lastUpdated)
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (failedCount > 0) {
            if (lastUpdated != null) {
                Text(
                    text = "·",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.Filled.Error,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = pluralStringResource(R.plurals.feeds_failed_count, failedCount, failedCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun DayHeader(date: LocalDate?) {
    val today = LocalDate.now()
    val label = when (date) {
        null -> stringResource(R.string.date_unknown)
        today -> stringResource(R.string.today)
        today.minusDays(1) -> stringResource(R.string.yesterday)
        else -> DateTimeUtils.formatDate(date)
    }
    Text(
        text = label,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)
    )
}

/**
 * Search field styled as the screen's top bar. The leading and trailing slots hold the feed drawer
 * and settings buttons, and switch to back and clear while searching.
 */
@Composable
private fun DiscoverSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onMenuClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    var isFocused by remember { mutableStateOf(false) }
    val isSearching = isFocused || query.isNotEmpty()
    val exitSearch = {
        onQueryChange("")
        focusManager.clearFocus()
    }

    BackHandler(enabled = isSearching, onBack = exitSearch)

    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 4.dp)
        ) {
            if (isSearching) {
                IconButton(onClick = exitSearch) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back_button)
                    )
                }
            } else {
                IconButton(onClick = onMenuClick) {
                    Icon(
                        Icons.Filled.Menu,
                        contentDescription = stringResource(R.string.open_feed_drawer)
                    )
                }
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) {
                            Text(
                                text = stringResource(R.string.search_placeholder),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        innerTextField()
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp)
                    .onFocusChanged { isFocused = it.isFocused }
            )
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.clear_search)
                    )
                }
            } else {
                IconButton(onClick = onSettingsClick) {
                    Icon(
                        Icons.Outlined.Settings,
                        contentDescription = stringResource(R.string.settings_title)
                    )
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
                ArticleSummaryWithFeed(
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
                )
            ),
            filterTitle = "All articles",
            searchQuery = "",
            onSearchQueryChange = {},
            isRefreshing = false,
            onRefresh = {},
            onArticleClick = {},
            onSaveClick = {},
            onMenuClick = {},
            onSettingsClick = {},
            showUnreadOnly = false,
            onShowUnreadOnlyChange = {},
            feeds = emptyList(),
            onFeedStatusClick = {}
        )
    }
}
