package dev.kettu.hyangsang.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.PostAdd
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.kettu.hyangsang.R
import dev.kettu.hyangsang.data.local.entity.ArticleSummary
import dev.kettu.hyangsang.data.local.entity.ArticleSummaryWithFeed
import dev.kettu.hyangsang.ui.discover.ArticleRow
import kotlinx.coroutines.launch

/** The Articles tab of the Library: saved and imported articles. */
@Composable
fun SavedArticles(
    savedArticles: List<ArticleSummaryWithFeed>,
    importedOnly: Boolean,
    onImportedOnlyChange: (Boolean) -> Unit,
    onArticleClick: (Long) -> Unit,
    onUnsave: (Long) -> Unit,
    onUndoUnsave: (articleId: Long, savedDate: String) -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val removedMessage = stringResource(R.string.removed_from_saved)
    val undoLabel = stringResource(R.string.undo)

    Column(modifier = modifier) {
        FilterChip(
            selected = importedOnly,
            onClick = { onImportedOnlyChange(!importedOnly) },
            label = { Text(stringResource(R.string.imported_filter)) },
            leadingIcon = if (importedOnly) {
                { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
            } else {
                null
            },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        SavedList(
            savedArticles = savedArticles,
            importedOnly = importedOnly,
            onArticleClick = onArticleClick,
            onSaveClick = { article ->
                val savedDate = article.savedDate
                onUnsave(article.id)
                scope.launch {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    val result = snackbarHostState.showSnackbar(
                        message = removedMessage,
                        actionLabel = undoLabel,
                        duration = SnackbarDuration.Long
                    )
                    if (result == SnackbarResult.ActionPerformed && savedDate != null) {
                        onUndoUnsave(article.id, savedDate)
                    }
                }
            }
        )
    }
}

@Composable
private fun SavedList(
    savedArticles: List<ArticleSummaryWithFeed>,
    importedOnly: Boolean,
    onArticleClick: (Long) -> Unit,
    onSaveClick: (ArticleSummary) -> Unit
) {
    if (savedArticles.isEmpty()) {
        SavedEmptyState(
            importedOnly = importedOnly,
            modifier = Modifier.fillMaxSize()
        )
    } else {
        LazyColumn(
            contentPadding = PaddingValues(bottom = 16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(savedArticles, key = { it.article.id }) { articleWithFeed ->
                val article = articleWithFeed.article
                ArticleRow(
                    articleWithFeed = articleWithFeed,
                    onClick = { onArticleClick(article.id) },
                    onSaveClick = { onSaveClick(article) },
                    showSavedTime = true,
                    modifier = Modifier.animateItem()
                )
            }
        }
    }
}

@Composable
private fun SavedEmptyState(importedOnly: Boolean, modifier: Modifier = Modifier) {
    Box(contentAlignment = Alignment.Center, modifier = modifier.padding(32.dp)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = if (importedOnly) Icons.Outlined.PostAdd else Icons.Outlined.BookmarkBorder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = stringResource(
                    if (importedOnly) R.string.imported_empty_title else R.string.saved_empty_title
                ),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(
                    if (importedOnly) R.string.imported_empty_body else R.string.saved_empty_body
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
