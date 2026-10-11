package dev.kettu.hyangsang.ui.feeds

import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import dev.kettu.hyangsang.R
import dev.kettu.hyangsang.data.defaults.DefaultFeed
import dev.kettu.hyangsang.data.defaults.DefaultSource

/**
 * Built-in [feeds] grouped by source, with a description of each source, to pick feeds to add.
 * Nothing is picked to begin with. Feeds in [addedUrls] are checked and can't be picked,
 * and feeds in [newUrls] are marked as new.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuiltInFeedsSheet(
    title: String,
    description: String,
    feeds: List<DefaultFeed>,
    addedUrls: Set<String>,
    newUrls: Set<String>,
    onAdd: (List<DefaultFeed>) -> Unit,
    onDismiss: () -> Unit
) {
    var picked by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val pickedFeeds = feeds.filter { it.url in picked }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 8.dp)
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        BuiltInFeedsList(
            feeds = feeds,
            addedUrls = addedUrls,
            newUrls = newUrls,
            picked = picked,
            onPickedChange = { picked = it },
            initiallyExpanded = true,
            modifier = Modifier.weight(1f, fill = false)
        )
        HorizontalDivider()
        Row(
            horizontalArrangement = Arrangement.End,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
            Button(onClick = { onAdd(pickedFeeds) }, enabled = pickedFeeds.isNotEmpty()) {
                Text(
                    if (pickedFeeds.isEmpty()) {
                        stringResource(R.string.add_feeds)
                    } else {
                        pluralStringResource(R.plurals.add_feeds_count, pickedFeeds.size, pickedFeeds.size)
                    }
                )
            }
        }
    }
}

/**
 * Built-in [feeds] grouped by source, each with its description and a checkbox for each feed.
 * [picked] holds the URLs of the picked feeds. Feeds in [addedUrls] are checked and can't be
 * picked, and feeds in [newUrls] are marked as new. Sources can be collapsed to their name and
 * the start of their description, and all start out open if [initiallyExpanded].
 */
@Composable
fun BuiltInFeedsList(
    feeds: List<DefaultFeed>,
    addedUrls: Set<String>,
    newUrls: Set<String>,
    picked: List<String>,
    onPickedChange: (List<String>) -> Unit,
    initiallyExpanded: Boolean,
    modifier: Modifier = Modifier
) {
    val resources = LocalResources.current
    // By source name, like the feeds screen, with each source's feeds in their built-in order
    val sources = remember(feeds, resources) {
        feeds.groupBy { it.source }.toList().sortedBy { (source, _) -> resources.getString(source.label) }
    }
    var expanded by rememberSaveable {
        mutableStateOf(if (initiallyExpanded) sources.map { (source, _) -> source.name } else emptyList())
    }

    LazyColumn(modifier = modifier) {
        sources.forEach { (source, sourceFeeds) ->
            val pickable = sourceFeeds.filter { it.url !in addedUrls }.map { it.url }
            val isExpanded = source.name in expanded
            item(key = source.name) {
                SourceRow(
                    source = source,
                    state = when {
                        pickable.isEmpty() || pickable.all { it in picked } -> ToggleableState.On
                        pickable.any { it in picked } -> ToggleableState.Indeterminate
                        else -> ToggleableState.Off
                    },
                    enabled = pickable.isNotEmpty(),
                    checkedCount = sourceFeeds.count { it.url in addedUrls || it.url in picked },
                    feedCount = sourceFeeds.size,
                    isExpanded = isExpanded,
                    onToggle = {
                        onPickedChange(
                            if (pickable.all { it in picked }) {
                                picked - pickable.toSet()
                            } else {
                                (picked + pickable).distinct()
                            }
                        )
                    },
                    onExpandToggle = {
                        expanded = if (isExpanded) expanded - source.name else expanded + source.name
                    }
                )
            }
            if (isExpanded) {
                items(sourceFeeds, key = { it.url }) { feed ->
                    FeedRow(
                        feed = feed,
                        isAdded = feed.url in addedUrls,
                        isNew = feed.url in newUrls,
                        isPicked = feed.url in picked,
                        onToggle = {
                            onPickedChange(if (feed.url in picked) picked - feed.url else picked + feed.url)
                        }
                    )
                }
            }
        }
    }
}

// The source's name and description, with a checkbox that picks all of its feeds. Tapping the
// row opens or closes the source's feeds, and its whole description
@Composable
private fun SourceRow(
    source: DefaultSource,
    state: ToggleableState,
    enabled: Boolean,
    checkedCount: Int,
    feedCount: Int,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onExpandToggle: () -> Unit
) {
    val name = stringResource(source.label)
    ListItem(
        headlineContent = { Text(name, style = MaterialTheme.typography.titleMedium) },
        supportingContent = {
            Text(
                text = stringResource(source.about),
                maxLines = if (isExpanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingContent = {
            TriStateCheckbox(
                state = state,
                onClick = onToggle,
                enabled = enabled,
                modifier = Modifier.semantics { contentDescription = name }
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.built_in_feeds_checked_count, checkedCount, feedCount),
                    style = MaterialTheme.typography.labelMedium
                )
                Icon(
                    if (isExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = stringResource(
                        if (isExpanded) R.string.collapse_category else R.string.expand_category,
                        name
                    )
                )
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onExpandToggle)
    )
}

// A feed by its section, or by its category for sources with a single feed
@Composable
private fun FeedRow(
    feed: DefaultFeed,
    isAdded: Boolean,
    isNew: Boolean,
    isPicked: Boolean,
    onToggle: () -> Unit
) {
    ListItem(
        headlineContent = { Text(stringResource(feed.section ?: feed.category.label)) },
        // Added feeds are shown by their checked, disabled checkbox
        supportingContent = if (isNew && !isAdded) {
            { Text(stringResource(R.string.built_in_feed_new), color = MaterialTheme.colorScheme.primary) }
        } else {
            null
        },
        leadingContent = { Checkbox(checked = isAdded || isPicked, onCheckedChange = null, enabled = !isAdded) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        // Indented under the source's checkbox
        modifier = Modifier
            .toggleable(
                value = isAdded || isPicked,
                enabled = !isAdded,
                role = Role.Checkbox,
                onValueChange = { onToggle() }
            )
            .padding(start = 40.dp)
    )
}
