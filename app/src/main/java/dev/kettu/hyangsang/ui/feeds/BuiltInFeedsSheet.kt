package dev.kettu.hyangsang.ui.feeds

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.triStateToggleable
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
    val resources = LocalResources.current
    var picked by rememberSaveable { mutableStateOf(emptyList<String>()) }
    // By source name, like the feeds screen, with each source's feeds in their built-in order
    val sources = remember(feeds, resources) {
        feeds.groupBy { it.source }.toList().sortedBy { (source, _) -> resources.getString(source.label) }
    }
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
        LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
            sources.forEach { (source, sourceFeeds) ->
                val pickable = sourceFeeds.filter { it.url !in addedUrls }.map { it.url }
                item(key = source.name) {
                    SourceRow(
                        source = source,
                        state = when {
                            pickable.isEmpty() || pickable.all { it in picked } -> ToggleableState.On
                            pickable.any { it in picked } -> ToggleableState.Indeterminate
                            else -> ToggleableState.Off
                        },
                        enabled = pickable.isNotEmpty(),
                        onToggle = {
                            picked = if (pickable.all { it in picked }) {
                                picked - pickable.toSet()
                            } else {
                                (picked + pickable).distinct()
                            }
                        }
                    )
                }
                items(sourceFeeds, key = { it.url }) { feed ->
                    FeedRow(
                        feed = feed,
                        isAdded = feed.url in addedUrls,
                        isNew = feed.url in newUrls,
                        isPicked = feed.url in picked,
                        onToggle = {
                            picked = if (feed.url in picked) picked - feed.url else picked + feed.url
                        }
                    )
                }
            }
        }
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

// The source's name and description, with a checkbox that picks all of its feeds
@Composable
private fun SourceRow(
    source: DefaultSource,
    state: ToggleableState,
    enabled: Boolean,
    onToggle: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(stringResource(source.label), style = MaterialTheme.typography.titleMedium)
        },
        supportingContent = { Text(stringResource(source.about)) },
        leadingContent = { TriStateCheckbox(state = state, onClick = null, enabled = enabled) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.triStateToggleable(
            state = state,
            enabled = enabled,
            role = Role.Checkbox,
            onClick = onToggle
        )
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
