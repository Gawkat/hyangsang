package dev.kettu.hyangsang.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.DynamicFeed
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.RssFeed
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SportsSoccer
import androidx.compose.material.icons.outlined.TheaterComedy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.kettu.hyangsang.R
import dev.kettu.hyangsang.data.local.entity.RssFeed

/**
 * Feed and category picker for Discover. Categories with a single feed act as that feed;
 * categories with several feeds can be expanded to pick one.
 *
 * @param unreadCounts unread articles per feed id, or null when counts are hidden
 */
@Composable
fun FeedDrawerContent(
    feeds: List<RssFeed>,
    selectedCategory: String?,
    selectedFeedId: Long?,
    unreadCounts: Map<Long, Int>?,
    onSelectAll: () -> Unit,
    onSelectCategory: (String) -> Unit,
    onSelectFeed: (Long) -> Unit,
    onManageFeedsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val feedsByCategory = remember(feeds) {
        feeds.filter { it.isEnabled }.groupBy { it.category }.toSortedMap()
    }
    // Start with the selected feed's category open, so the selection is visible
    var expanded by rememberSaveable {
        mutableStateOf(
            feeds.firstOrNull { it.id == selectedFeedId }?.category?.let { listOf(it) }
                ?: emptyList()
        )
    }

    ModalDrawerSheet(modifier = modifier) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp)
        ) {
            Column(modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 16.dp)) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.app_name_romanized),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            NavigationDrawerItem(
                label = { Text(stringResource(R.string.all_articles)) },
                icon = { Icon(Icons.Outlined.DynamicFeed, contentDescription = null) },
                selected = selectedCategory == null && selectedFeedId == null,
                onClick = onSelectAll,
                badge = unreadCounts?.let { counts -> { CountBadge(counts.values.sum()) } }
            )

            Text(
                text = stringResource(R.string.categories_label),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
            )

            feedsByCategory.forEach { (category, categoryFeeds) ->
                val isExpandable = categoryFeeds.size > 1
                val isExpanded = category in expanded
                val categoryCount = unreadCounts?.let { counts ->
                    categoryFeeds.sumOf { counts[it.id] ?: 0 }
                }

                NavigationDrawerItem(
                    label = {
                        LabelWithError(
                            text = category,
                            hasError = categoryFeeds.any { it.lastSyncError != null }
                        )
                    },
                    icon = { Icon(categoryIcon(category), contentDescription = null) },
                    selected = selectedCategory == category,
                    onClick = { onSelectCategory(category) },
                    badge = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (categoryCount != null) CountBadge(categoryCount)
                            if (isExpandable) {
                                IconButton(
                                    onClick = {
                                        expanded = if (isExpanded) {
                                            expanded - category
                                        } else {
                                            expanded + category
                                        }
                                    },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isExpanded) {
                                            Icons.Outlined.ExpandLess
                                        } else {
                                            Icons.Outlined.ExpandMore
                                        },
                                        contentDescription = stringResource(
                                            if (isExpanded) R.string.collapse_category else R.string.expand_category,
                                            category
                                        )
                                    )
                                }
                            }
                        }
                    }
                )

                if (isExpandable && isExpanded) {
                    categoryFeeds.forEach { feed ->
                        NavigationDrawerItem(
                            label = {
                                LabelWithError(
                                    text = feed.title,
                                    hasError = feed.lastSyncError != null
                                )
                            },
                            icon = { FeedAvatar(feed.title) },
                            selected = selectedFeedId == feed.id,
                            onClick = { onSelectFeed(feed.id) },
                            badge = unreadCounts?.let { counts ->
                                { CountBadge(counts[feed.id] ?: 0) }
                            },
                            modifier = Modifier.padding(start = 16.dp)
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

            NavigationDrawerItem(
                label = { Text(stringResource(R.string.manage_feeds)) },
                icon = { Icon(Icons.Outlined.RssFeed, contentDescription = null) },
                selected = false,
                onClick = onManageFeedsClick
            )
            NavigationDrawerItem(
                label = { Text(stringResource(R.string.settings_title)) },
                icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                selected = false,
                onClick = onSettingsClick
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun LabelWithError(text: String, hasError: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        if (hasError) {
            Icon(
                imageVector = Icons.Filled.Error,
                contentDescription = stringResource(R.string.feed_update_failed),
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun CountBadge(count: Int) {
    Text(text = count.toString(), style = MaterialTheme.typography.labelMedium)
}

// Stand-in for a favicon
@Composable
private fun FeedAvatar(title: String) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(24.dp)
            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
    ) {
        Text(
            text = title.take(1),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

// Categories are free text, so only the built-in ones get their own icon
private fun categoryIcon(category: String): ImageVector = when (category) {
    "News" -> Icons.Outlined.Newspaper
    "Culture" -> Icons.Outlined.TheaterComedy
    "Economy" -> Icons.AutoMirrored.Outlined.TrendingUp
    "Entertainment" -> Icons.Outlined.Movie
    "North Korea" -> Icons.Outlined.Public
    "Politics" -> Icons.Outlined.AccountBalance
    "Sports" -> Icons.Outlined.SportsSoccer
    else -> Icons.AutoMirrored.Outlined.Label
}
