package dev.kettu.hyangsang.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.kettu.hyangsang.R
import dev.kettu.hyangsang.data.local.entity.ArticleSummaryWithFeed
import dev.kettu.hyangsang.data.local.entity.Book
import dev.kettu.hyangsang.data.local.entity.BookWithChapter
import dev.kettu.hyangsang.data.prefs.LibraryTab
import dev.kettu.hyangsang.ui.viewmodel.BookSort
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    selectedTab: LibraryTab,
    onTabSelect: (LibraryTab) -> Unit,
    savedArticles: List<ArticleSummaryWithFeed>,
    importedOnly: Boolean,
    onImportedOnlyChange: (Boolean) -> Unit,
    onAddTextClick: () -> Unit,
    onArticleClick: (Long) -> Unit,
    onUnsave: (Long) -> Unit,
    onUndoUnsave: (articleId: Long, savedDate: String) -> Unit,
    books: List<BookWithChapter>,
    bookSort: BookSort,
    onBookSortChange: (BookSort) -> Unit,
    coverFile: (Book) -> File?,
    onBookClick: (Long) -> Unit,
    onAddBookClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.library_nav)) },
                actions = {
                    when (selectedTab) {
                        LibraryTab.ARTICLES -> IconButton(onClick = onAddTextClick) {
                            Icon(
                                Icons.Filled.Add,
                                contentDescription = stringResource(R.string.add_text_title)
                            )
                        }

                        // The empty Books tab has a button of its own
                        LibraryTab.BOOKS -> if (books.isNotEmpty()) {
                            IconButton(onClick = onAddBookClick) {
                                Icon(
                                    Icons.Filled.Add,
                                    contentDescription = stringResource(R.string.add_epub_file)
                                )
                            }
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        modifier = modifier
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            PrimaryTabRow(selectedTabIndex = selectedTab.ordinal) {
                LibraryTab.entries.forEach { tab ->
                    Tab(
                        selected = tab == selectedTab,
                        onClick = { onTabSelect(tab) },
                        selectedContentColor = MaterialTheme.colorScheme.primary,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        text = {
                            Text(
                                stringResource(
                                    when (tab) {
                                        LibraryTab.ARTICLES -> R.string.library_articles_tab
                                        LibraryTab.BOOKS -> R.string.library_books_tab
                                    }
                                )
                            )
                        }
                    )
                }
            }
            when (selectedTab) {
                LibraryTab.ARTICLES -> SavedArticles(
                    savedArticles = savedArticles,
                    importedOnly = importedOnly,
                    onImportedOnlyChange = onImportedOnlyChange,
                    onArticleClick = onArticleClick,
                    onUnsave = onUnsave,
                    onUndoUnsave = onUndoUnsave,
                    snackbarHostState = snackbarHostState
                )

                LibraryTab.BOOKS -> BooksTab(
                    books = books,
                    sort = bookSort,
                    onSortChange = onBookSortChange,
                    coverFile = coverFile,
                    onBookClick = onBookClick,
                    onBookOptionsClick = onBookClick,
                    onAddClick = onAddBookClick,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
