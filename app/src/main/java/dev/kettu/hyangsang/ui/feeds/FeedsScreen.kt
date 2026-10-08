package dev.kettu.hyangsang.ui.feeds

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.kettu.hyangsang.R
import dev.kettu.hyangsang.data.local.entity.RssFeed
import dev.kettu.hyangsang.data.repository.FeedCheckError
import dev.kettu.hyangsang.data.repository.FeedCheckResult
import dev.kettu.hyangsang.ui.components.FeedAvatar
import dev.kettu.hyangsang.ui.components.rememberCategoryIcons
import dev.kettu.hyangsang.ui.utils.DateTimeUtils
import dev.kettu.hyangsang.ui.viewmodel.RssFeedViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedsScreen(
    viewModel: RssFeedViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val feeds by viewModel.feeds.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val refreshingFeedIds by viewModel.refreshingFeedIds.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val categoryIcon = rememberCategoryIcons()

    var showMenu by remember { mutableStateOf(false) }
    var showAddSheet by rememberSaveable { mutableStateOf(false) }
    var editingFeedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var removingFeedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var renamingCategory by rememberSaveable { mutableStateOf<String?>(null) }

    // Same order as the drawer: categories by name, feeds by title
    val feedsByCategory = remember(feeds) { feeds.groupBy { it.category }.toSortedMap() }
    val categories = feedsByCategory.keys.toList()

    // Categories that are all off start collapsed. The set is taken once, when the feeds first
    // load, so turning a category off doesn't hide its feeds under the user's finger
    var collapsed by rememberSaveable { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(feedsByCategory) {
        if (collapsed == null && feedsByCategory.isNotEmpty()) {
            collapsed = feedsByCategory.filterValues { group -> group.none { it.isEnabled } }.keys.toList()
        }
    }
    val collapsedCategories = collapsed.orEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feeds_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back_button)
                        )
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                Icons.Filled.MoreVert,
                                contentDescription = stringResource(R.string.more_options)
                            )
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.refresh_all_feeds)) },
                                leadingIcon = { Icon(Icons.Outlined.Refresh, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    viewModel.refreshFeeds()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.restore_default_feeds)) },
                                leadingIcon = { Icon(Icons.Outlined.Restore, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    scope.launch {
                                        val restored = viewModel.restoreDefaultFeeds()
                                        snackbarHostState.showSnackbar(
                                            if (restored == 0) {
                                                context.getString(R.string.restore_nothing)
                                            } else {
                                                context.resources.getQuantityString(
                                                    R.plurals.restored_feeds,
                                                    restored,
                                                    restored
                                                )
                                            }
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddSheet = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.add_feed)) }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            if (isRefreshing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            LazyColumn(
                // Keep the last row clear of the floating button
                contentPadding = PaddingValues(bottom = 88.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item(key = "helper") {
                    Text(
                        text = stringResource(R.string.feeds_helper),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                feedsByCategory.forEach { (category, categoryFeeds) ->
                    val isExpanded = category !in collapsedCategories
                    item(key = "category:$category") {
                        CategoryHeader(
                            category = category,
                            icon = categoryIcon(category),
                            feeds = categoryFeeds,
                            isExpanded = isExpanded,
                            onToggleExpanded = {
                                collapsed = if (isExpanded) {
                                    collapsedCategories + category
                                } else {
                                    collapsedCategories - category
                                }
                            },
                            onSetEnabled = { enabled ->
                                viewModel.setFeedsEnabled(categoryFeeds, enabled)
                                // Turning off a category with only some feeds on loses which
                                // ones were on, so offer to bring them back
                                val isMixed = categoryFeeds.any { it.isEnabled } &&
                                    categoryFeeds.any { !it.isEnabled }
                                if (!enabled && isMixed) {
                                    scope.launch {
                                        snackbarHostState.currentSnackbarData?.dismiss()
                                        val result = snackbarHostState.showSnackbar(
                                            message = context.getString(R.string.category_turned_off, category),
                                            actionLabel = context.getString(R.string.undo),
                                            duration = SnackbarDuration.Long
                                        )
                                        if (result == SnackbarResult.ActionPerformed) {
                                            viewModel.restoreEnabledStates(categoryFeeds)
                                        }
                                    }
                                }
                            },
                            onRefresh = {
                                viewModel.refreshFeeds(
                                    categoryFeeds.filter { it.isEnabled && it.id !in refreshingFeedIds }
                                )
                            },
                            onRename = { renamingCategory = category },
                            modifier = Modifier.animateItem()
                        )
                    }
                    if (isExpanded) {
                        items(categoryFeeds, key = { it.id }) { feed ->
                            FeedRow(
                                feed = feed,
                                isRefreshing = feed.id in refreshingFeedIds,
                                onClick = { editingFeedId = feed.id },
                                onToggle = { viewModel.toggleFeed(feed) },
                                onRetry = { viewModel.refreshFeeds(listOf(feed)) },
                                modifier = Modifier.animateItem()
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddSheet) {
        AddFeedSheet(
            categories = categories,
            onCheck = viewModel::checkFeed,
            onAdd = { url, title, category ->
                viewModel.addFeed(url, title, category)
                // Show the new feed, even if its category was collapsed
                collapsed = collapsedCategories - category
                showAddSheet = false
            },
            onDismiss = { showAddSheet = false }
        )
    }

    feeds.firstOrNull { it.id == editingFeedId }?.let { feed ->
        EditFeedSheet(
            feed = feed,
            categories = categories,
            isRefreshing = feed.id in refreshingFeedIds,
            onRefresh = { viewModel.refreshFeed(feed) },
            onSave = { title, category ->
                viewModel.updateFeedDetails(feed, title, category)
                collapsed = collapsedCategories - category
                editingFeedId = null
            },
            onRemove = {
                editingFeedId = null
                removingFeedId = feed.id
            },
            onDismiss = { editingFeedId = null }
        )
    }

    feeds.firstOrNull { it.id == removingFeedId }?.let { feed ->
        RemoveFeedDialog(
            feed = feed,
            countSavedArticles = { viewModel.countSavedArticles(feed) },
            onConfirm = {
                viewModel.deleteFeed(feed)
                removingFeedId = null
            },
            onDismiss = { removingFeedId = null }
        )
    }

    renamingCategory?.takeIf { it in feedsByCategory }?.let { category ->
        RenameCategoryDialog(
            category = category,
            onConfirm = { newName ->
                viewModel.renameCategory(category, newName)
                // Keep the category open or closed under its new name
                if (category in collapsedCategories) {
                    collapsed = collapsedCategories - category + newName
                }
                renamingCategory = null
            },
            onDismiss = { renamingCategory = null }
        )
    }
}

// "Sports · 1 of 2 on", with a switch that turns every feed in it on or off
@Composable
private fun CategoryHeader(
    category: String,
    icon: ImageVector,
    feeds: List<RssFeed>,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    onSetEnabled: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onRename: () -> Unit,
    modifier: Modifier = Modifier
) {
    val enabledCount = feeds.count { it.isEnabled }
    val hasError = feeds.any { it.isEnabled && it.lastSyncError != null }
    var showMenu by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        HorizontalDivider()
        ListItem(
            headlineContent = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = category,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Icon(
                        imageVector = if (isExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            },
            supportingContent = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (hasError) {
                        Icon(
                            Icons.Filled.Error,
                            contentDescription = stringResource(R.string.feed_update_failed),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        text = if (enabledCount == 0) {
                            stringResource(R.string.feed_status_off)
                        } else {
                            stringResource(R.string.feeds_enabled_summary, enabledCount, feeds.size)
                        },
                        maxLines = 1
                    )
                }
            },
            leadingContent = {
                // As wide as the feed avatars, so category and feed names line up
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(40.dp)) {
                    Icon(icon, contentDescription = null)
                }
            },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                Icons.Filled.MoreVert,
                                contentDescription = stringResource(R.string.category_options, category)
                            )
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.refresh_category)) },
                                leadingIcon = { Icon(Icons.Outlined.Refresh, contentDescription = null) },
                                enabled = enabledCount > 0,
                                onClick = {
                                    showMenu = false
                                    onRefresh()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.rename_category)) },
                                leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    onRename()
                                }
                            )
                        }
                    }
                    Switch(
                        checked = enabledCount > 0,
                        onCheckedChange = onSetEnabled,
                        modifier = Modifier.semantics { contentDescription = category }
                    )
                }
            },
            modifier = Modifier.clickable(
                onClickLabel = stringResource(
                    if (isExpanded) R.string.collapse_category else R.string.expand_category,
                    category
                ),
                onClick = onToggleExpanded
            )
        )
    }
}

@Composable
private fun FeedRow(
    feed: RssFeed,
    isRefreshing: Boolean,
    onClick: () -> Unit,
    onToggle: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasError = feed.isEnabled && feed.lastSyncError != null
    // The category header already names the category, so "Yonhap News - Sports" is shown as
    // "Yonhap News". Titles without the category are shown as they are
    val categorySuffix = stringResource(R.string.default_feed_title, "", feed.category)
    val title = feed.title.removeSuffix(categorySuffix).ifBlank { feed.title }

    ListItem(
        headlineContent = {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = { FeedStatus(feed, isRefreshing) },
        leadingContent = {
            FeedAvatar(
                title = feed.title,
                size = 40.dp,
                textStyle = MaterialTheme.typography.titleSmall
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (hasError) {
                    IconButton(onClick = onRetry, enabled = !isRefreshing) {
                        Icon(
                            Icons.Outlined.Refresh,
                            contentDescription = stringResource(R.string.refresh_feed, feed.title)
                        )
                    }
                }
                Switch(
                    checked = feed.isEnabled,
                    onCheckedChange = { onToggle() },
                    modifier = Modifier.semantics { contentDescription = feed.title }
                )
            }
        },
        modifier = modifier.clickable(onClick = onClick)
    )
}

// "Updated 14 min. ago", with failures in the error colour
@Composable
private fun FeedStatus(feed: RssFeed, isRefreshing: Boolean) {
    val hasError = !isRefreshing && feed.isEnabled && feed.lastSyncError != null
    val status = when {
        isRefreshing -> stringResource(R.string.feed_status_updating)
        !feed.isEnabled -> stringResource(R.string.feed_status_off)
        hasError -> stringResource(
            R.string.feed_status_failed,
            DateTimeUtils.formatRelativeTime(feed.lastSyncAttempt)
        )

        feed.lastSynced.startsWith("1970-") -> stringResource(R.string.feed_status_never)
        else -> stringResource(
            R.string.feed_status_updated,
            DateTimeUtils.formatRelativeTime(feed.lastSynced)
        )
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (isRefreshing) {
            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
        }
        if (hasError) {
            Icon(
                Icons.Filled.Error,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(16.dp)
            )
        }
        Text(
            text = status,
            color = if (hasError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private sealed interface CheckState {
    data object Idle : CheckState
    data object Checking : CheckState
    data class Done(val result: FeedCheckResult) : CheckState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddFeedSheet(
    categories: List<String>,
    onCheck: suspend (String) -> FeedCheckResult,
    onAdd: (url: String, title: String, category: String) -> Unit,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var url by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("") }
    var checkState by remember { mutableStateOf<CheckState>(CheckState.Idle) }
    val urlFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { urlFocusRequester.requestFocus() }

    val validResult = (checkState as? CheckState.Done)?.result as? FeedCheckResult.Valid
    val invalidResult = (checkState as? CheckState.Done)?.result as? FeedCheckResult.Invalid
    val runCheck = {
        if (url.isNotBlank() && checkState != CheckState.Checking) {
            scope.launch {
                checkState = CheckState.Checking
                val result = onCheck(url)
                if (result is FeedCheckResult.Valid && name.isBlank()) {
                    name = result.title.orEmpty()
                }
                checkState = CheckState.Done(result)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .imePadding()
                .verticalScroll(rememberScrollState())
        ) {
            Text(stringResource(R.string.add_feed), style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(
                value = url,
                onValueChange = {
                    // URLs never contain whitespace, and pasted ones often carry a trailing newline
                    url = it.filterNot(Char::isWhitespace)
                    checkState = CheckState.Idle
                },
                label = { Text(stringResource(R.string.feed_url_label)) },
                singleLine = true,
                isError = invalidResult != null,
                supportingText = {
                    when {
                        checkState == CheckState.Checking -> Text(stringResource(R.string.feed_checking))
                        validResult != null -> Text(
                            pluralStringResource(
                                R.plurals.feed_found,
                                validResult.articleCount,
                                validResult.articleCount
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )

                        invalidResult != null -> Text(checkErrorMessage(invalidResult))
                    }
                },
                trailingIcon = {
                    when {
                        checkState == CheckState.Checking -> CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp)
                        )

                        validResult != null -> Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Go
                ),
                keyboardActions = KeyboardActions(onGo = { runCheck() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(urlFocusRequester)
            )

            if (validResult == null) {
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(android.R.string.cancel))
                    }
                    Button(
                        onClick = { runCheck() },
                        enabled = url.isNotBlank() && checkState != CheckState.Checking
                    ) {
                        Text(stringResource(R.string.check_feed))
                    }
                }
            } else {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.feed_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                CategoryField(value = category, onValueChange = { category = it }, categories = categories)
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(android.R.string.cancel))
                    }
                    Button(
                        onClick = { onAdd(validResult.url, name.trim(), category.trim()) },
                        enabled = name.isNotBlank() && category.isNotBlank()
                    ) {
                        Text(stringResource(R.string.add_feed))
                    }
                }
            }
        }
    }
}

@Composable
private fun checkErrorMessage(result: FeedCheckResult.Invalid): String = when (result.error) {
    FeedCheckError.UNREACHABLE -> stringResource(R.string.feed_check_unreachable)
    FeedCheckError.HTTP_ERROR -> stringResource(R.string.feed_check_http, result.httpCode ?: 0)
    FeedCheckError.NOT_A_FEED -> stringResource(R.string.feed_check_not_feed)
    FeedCheckError.ALREADY_ADDED -> stringResource(R.string.feed_check_duplicate)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditFeedSheet(
    feed: RssFeed,
    categories: List<String>,
    isRefreshing: Boolean,
    onRefresh: suspend () -> Int?,
    onSave: (title: String, category: String) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var name by rememberSaveable(feed.id) { mutableStateOf(feed.title) }
    var category by rememberSaveable(feed.id) { mutableStateOf(feed.category) }
    // New articles from a refresh started in this sheet; failures show as the last error instead
    var newArticles by remember(feed.id) { mutableStateOf<Int?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .imePadding()
                .verticalScroll(rememberScrollState())
        ) {
            Text(stringResource(R.string.edit_feed), style = MaterialTheme.typography.headlineSmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = feed.url,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val refreshedCount = newArticles
                    when {
                        isRefreshing -> Text(
                            text = stringResource(R.string.feed_status_updating),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Show the raw error here, since the list only says that the update failed
                        feed.isEnabled && feed.lastSyncError != null -> Text(
                            text = stringResource(R.string.feed_last_error, feed.lastSyncError),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )

                        refreshedCount != null -> Text(
                            text = if (refreshedCount == 0) {
                                stringResource(R.string.feed_refreshed_none)
                            } else {
                                pluralStringResource(R.plurals.feed_refreshed, refreshedCount, refreshedCount)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                // Articles of disabled feeds aren't shown anywhere, so there's nothing to refresh
                if (feed.isEnabled) {
                    TextButton(
                        onClick = {
                            newArticles = null
                            scope.launch { newArticles = onRefresh() }
                        },
                        enabled = !isRefreshing
                    ) {
                        if (isRefreshing) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(ButtonDefaults.IconSize)
                            )
                        } else {
                            Icon(
                                Icons.Outlined.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(ButtonDefaults.IconSize)
                            )
                        }
                        Spacer(modifier = Modifier.size(ButtonDefaults.IconSpacing))
                        Text(stringResource(R.string.refresh))
                    }
                }
            }
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.feed_name_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            CategoryField(value = category, onValueChange = { category = it }, categories = categories)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                TextButton(
                    onClick = onRemove,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.remove_feed))
                }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) {
                    Text(stringResource(android.R.string.cancel))
                }
                Button(
                    onClick = { onSave(name.trim(), category.trim()) },
                    enabled = name.isNotBlank() && category.isNotBlank()
                ) {
                    Text(stringResource(R.string.save))
                }
            }
        }
    }
}

// A free-text field, with the existing categories as one-tap suggestions
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryField(
    value: String,
    onValueChange: (String) -> Unit,
    categories: List<String>
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(stringResource(R.string.category_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            categories.forEach { category ->
                FilterChip(
                    selected = value.trim() == category,
                    onClick = { onValueChange(category) },
                    label = { Text(category) }
                )
            }
        }
    }
}

// Renames the category on every feed in it. Giving it the name of another category merges them
@Composable
private fun RenameCategoryDialog(
    category: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by rememberSaveable(category) { mutableStateOf(category) }
    val newName = name.trim()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rename_category)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.feed_name_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(newName) },
                enabled = newName.isNotEmpty() && newName != category
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}

// Removing a feed cascades to its articles, saved ones included, so say so before confirming
@Composable
private fun RemoveFeedDialog(
    feed: RssFeed,
    countSavedArticles: suspend () -> Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    var savedCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(feed.id) { savedCount = countSavedArticles() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.remove_feed_title, feed.title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.remove_feed_body))
                if (savedCount > 0) {
                    Text(
                        text = pluralStringResource(R.plurals.remove_feed_saved, savedCount, savedCount),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text(stringResource(R.string.remove))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}
