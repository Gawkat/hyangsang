package dev.kettu.hyangsang.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.kettu.hyangsang.R
import dev.kettu.hyangsang.data.local.entity.ArticleSummaryWithFeed
import dev.kettu.hyangsang.data.local.entity.Book
import dev.kettu.hyangsang.data.local.entity.BookWithChapter
import dev.kettu.hyangsang.data.local.entity.ChapterInfo
import dev.kettu.hyangsang.data.prefs.LibraryTab
import dev.kettu.hyangsang.ui.components.SearchField
import dev.kettu.hyangsang.ui.utils.SearchTerms
import dev.kettu.hyangsang.ui.viewmodel.BookSort
import kotlinx.coroutines.flow.Flow
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
    onBookDetailsClick: (Long) -> Unit,
    onBookReadChange: (Book, Boolean) -> Unit,
    onUndoBookReadChange: (Book) -> Unit,
    bookChapters: (Long) -> Flow<List<ChapterInfo>>,
    onBookChapterClick: (bookId: Long, chapter: Int) -> Unit,
    onAddBookClick: () -> Unit,
    // Null when not searching. Searching keeps the query when the tab changes
    searchQuery: String?,
    onSearchQueryChange: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val search = remember(searchQuery) { SearchTerms(searchQuery.orEmpty()) }
    val shownArticles = remember(savedArticles, search) {
        if (search.isEmpty) savedArticles else savedArticles.filter { search.matches(it) }
    }
    val shownBooks = remember(books, search) {
        if (search.isEmpty) books else books.filter { search.matches(it) }
    }
    val searching = !search.isEmpty
    // Set by the search button, so coming back to a search doesn't bring up the keyboard
    var focusSearch by remember { mutableStateOf(false) }

    BackHandler(enabled = searchQuery != null) { onSearchQueryChange(null) }

    Scaffold(
        topBar = {
            if (searchQuery != null) {
                LibrarySearchBar(
                    query = searchQuery,
                    onQueryChange = onSearchQueryChange,
                    placeholder = stringResource(
                        when (selectedTab) {
                            LibraryTab.ARTICLES -> R.string.search_placeholder
                            LibraryTab.BOOKS -> R.string.search_books_placeholder
                        }
                    ),
                    focus = focusSearch,
                    onFocused = { focusSearch = false }
                )
            } else TopAppBar(
                title = { Text(stringResource(R.string.library_nav)) },
                actions = {
                    // An empty tab has nothing to search
                    val tabEmpty = when (selectedTab) {
                        LibraryTab.ARTICLES -> savedArticles.isEmpty()
                        LibraryTab.BOOKS -> books.isEmpty()
                    }
                    if (!tabEmpty) {
                        IconButton(onClick = {
                            focusSearch = true
                            onSearchQueryChange("")
                        }) {
                            Icon(
                                Icons.Filled.Search,
                                contentDescription = stringResource(R.string.search_button)
                            )
                        }
                    }
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
                    savedArticles = shownArticles,
                    search = search.takeIf { searching },
                    otherTabMatches = if (searching) shownBooks.size else 0,
                    onShowOtherTab = { onTabSelect(LibraryTab.BOOKS) },
                    importedOnly = importedOnly,
                    onImportedOnlyChange = onImportedOnlyChange,
                    onArticleClick = onArticleClick,
                    onUnsave = onUnsave,
                    onUndoUnsave = onUndoUnsave,
                    snackbarHostState = snackbarHostState
                )

                LibraryTab.BOOKS -> BooksTab(
                    books = shownBooks,
                    search = search.takeIf { searching },
                    otherTabMatches = if (searching) shownArticles.size else 0,
                    onShowOtherTab = { onTabSelect(LibraryTab.ARTICLES) },
                    sort = bookSort,
                    onSortChange = onBookSortChange,
                    coverFile = coverFile,
                    onBookClick = onBookClick,
                    onBookDetailsClick = onBookDetailsClick,
                    onBookReadChange = onBookReadChange,
                    onUndoBookReadChange = onUndoBookReadChange,
                    snackbarHostState = snackbarHostState,
                    chapters = bookChapters,
                    onChapterClick = onBookChapterClick,
                    onAddClick = onAddBookClick,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

/**
 * The top bar while searching: back, the search field and a button that clears it. [focus] puts
 * the cursor in the field, and [onFocused] is called once it's there.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibrarySearchBar(
    query: String,
    onQueryChange: (String?) -> Unit,
    placeholder: String,
    focus: Boolean,
    onFocused: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(focus) {
        if (focus) {
            focusRequester.requestFocus()
            onFocused()
        }
    }
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = { onQueryChange(null) }) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back_button)
                )
            }
        },
        title = {
            SearchField(
                query = query,
                onQueryChange = onQueryChange,
                placeholder = placeholder,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
            )
        },
        actions = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.clear_search))
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    )
}

/** The count of results, read out by TalkBack as it changes. */
@Composable
internal fun SearchResultCount(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite }
    )
}

/** Shown when nothing in a tab matches, with a way to the other tab if something there does. */
@Composable
internal fun NoSearchResults(
    message: String,
    otherTabLabel: String?,
    onOtherTabClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(contentAlignment = Alignment.Center, modifier = modifier.padding(32.dp)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.SearchOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
            if (otherTabLabel != null) {
                TextButton(onClick = onOtherTabClick) { Text(otherTabLabel) }
            }
        }
    }
}
