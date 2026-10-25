package dev.kettu.hyangsang.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.RemoveDone
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.kettu.hyangsang.R
import dev.kettu.hyangsang.data.local.entity.Book
import dev.kettu.hyangsang.data.local.entity.BookWithChapter
import dev.kettu.hyangsang.data.local.entity.ChapterInfo
import dev.kettu.hyangsang.ui.reader.ContentsSheet
import dev.kettu.hyangsang.ui.viewmodel.BookSort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.io.File

/** The Books tab of the Library: the book being read, then all books in a grid. */
@Composable
fun BooksTab(
    books: List<BookWithChapter>,
    sort: BookSort,
    onSortChange: (BookSort) -> Unit,
    coverFile: (Book) -> File?,
    onBookClick: (Long) -> Unit,
    onBookDetailsClick: (Long) -> Unit,
    onBookReadChange: (Book, Boolean) -> Unit,
    // Gets the book as it was before, to put it back where it was
    onUndoBookReadChange: (Book) -> Unit,
    snackbarHostState: SnackbarHostState,
    chapters: (Long) -> Flow<List<ChapterInfo>>,
    onChapterClick: (bookId: Long, chapter: Int) -> Unit,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val markedReadMessage = stringResource(R.string.marked_book_read)
    val markedUnreadMessage = stringResource(R.string.marked_book_unread)
    val undoLabel = stringResource(R.string.undo)

    if (books.isEmpty()) {
        BooksEmptyState(onAddClick = onAddClick, modifier = modifier)
        return
    }
    val current = books.filter { it.book.isStarted }.maxByOrNull { it.book.lastReadDate ?: 0 }
    var contentsBookId by rememberSaveable { mutableStateOf<Long?>(null) }

    books.firstOrNull { it.book.id == contentsBookId }?.book?.let { book ->
        val bookChapters by remember(book.id) { chapters(book.id) }.collectAsState(initial = emptyList())
        BookContentsSheet(
            book = book,
            chapters = bookChapters,
            onChapterClick = { index ->
                contentsBookId = null
                onChapterClick(book.id, index)
            },
            onDismiss = { contentsBookId = null }
        )
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = modifier
    ) {
        if (current != null) {
            item(key = "current", span = { GridItemSpan(maxLineSpan) }) {
                ContinueReadingCard(
                    book = current,
                    coverFile = coverFile(current.book),
                    onClick = { onBookClick(current.book.id) }
                )
            }
        }
        item(key = "header", span = { GridItemSpan(maxLineSpan) }) {
            BooksHeader(count = books.size, sort = sort, onSortChange = onSortChange)
        }
        items(books, key = { it.book.id }) { item ->
            BookGridItem(
                book = item.book,
                coverFile = coverFile(item.book),
                onClick = { onBookClick(item.book.id) },
                onContentsClick = { contentsBookId = item.book.id },
                onReadChange = { read ->
                    val before = item.book
                    onBookReadChange(before, read)
                    scope.launch {
                        snackbarHostState.currentSnackbarData?.dismiss()
                        val result = snackbarHostState.showSnackbar(
                            message = if (read) markedReadMessage else markedUnreadMessage,
                            actionLabel = undoLabel,
                            duration = SnackbarDuration.Long
                        )
                        if (result == SnackbarResult.ActionPerformed) onUndoBookReadChange(before)
                    }
                },
                onDetailsClick = { onBookDetailsClick(item.book.id) },
                modifier = Modifier.animateItem()
            )
        }
    }
}

@Composable
private fun ContinueReadingCard(book: BookWithChapter, coverFile: File?, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            BookCover(
                title = book.book.title,
                author = book.book.author,
                coverFile = coverFile,
                titleSize = 13.sp,
                modifier = Modifier.width(84.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.continue_reading).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = book.book.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                book.book.author?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    text = chapterText(book.book.chapterIndex, book.chapterTitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp)
                )
                Spacer(modifier = Modifier.weight(1f))
                ProgressRow(book.book.progress)
            }
        }
    }
}

@Composable
private fun ProgressRow(progress: Float) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LinearProgressIndicator(
            progress = { progress },
            drawStopIndicator = {},
            modifier = Modifier.weight(1f)
        )
        Text(
            text = percentText(progress),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun BooksHeader(count: Int, sort: BookSort, onSortChange: (BookSort) -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.all_books_count, count),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Box {
            FilterChip(
                selected = false,
                onClick = { menuOpen = true },
                label = { Text(stringResource(sort.labelRes())) },
                trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) }
            )
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                BookSort.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(stringResource(option.labelRes())) },
                        onClick = {
                            menuOpen = false
                            onSortChange(option)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun BookGridItem(
    book: Book,
    coverFile: File?,
    onClick: () -> Unit,
    onContentsClick: () -> Unit,
    onReadChange: (Boolean) -> Unit,
    onDetailsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuOpen by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = modifier) {
        BookCover(
            title = book.title,
            author = book.author,
            coverFile = coverFile,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
        )
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f).clickable(onClick = onClick)) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                book.author?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Box {
                IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.book_options),
                        modifier = Modifier.size(20.dp)
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.book_contents)) },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onContentsClick()
                        }
                    )
                    // A book that was opened at all has progress to undo
                    val opened = book.lastReadDate != null
                    DropdownMenuItem(
                        text = { Text(stringResource(if (opened) R.string.mark_book_unread else R.string.mark_book_read)) },
                        leadingIcon = {
                            Icon(if (opened) Icons.Outlined.RemoveDone else Icons.Outlined.Done, contentDescription = null)
                        },
                        onClick = {
                            menuOpen = false
                            onReadChange(!opened)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.book_details)) },
                        leadingIcon = { Icon(Icons.Outlined.Info, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onDetailsClick()
                        }
                    )
                }
            }
        }
        when {
            book.isStarted -> ProgressRow(book.progress)
            book.isFinished -> StatusText(stringResource(R.string.book_finished))
            book.lastReadDate == null -> StatusText(stringResource(R.string.book_new))
        }
    }
}

// The reader's contents, with the chapter the book was left at as the current one
@Composable
private fun BookContentsSheet(
    book: Book,
    chapters: List<ChapterInfo>,
    onChapterClick: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val currentIndex = when {
        book.isFinished -> book.chapterCount
        book.isStarted -> book.chapterIndex
        else -> -1
    }
    val chapter = chapters.getOrNull(currentIndex)
    ContentsSheet(
        book = book,
        chapters = chapters,
        currentIndex = currentIndex,
        progressInChapter = if (chapter == null || chapter.charCount == 0) 0f else {
            ((book.progressChars - chapter.charsBefore).toFloat() / chapter.charCount).coerceIn(0f, 1f)
        },
        onChapterClick = onChapterClick,
        onDismiss = onDismiss
    )
}

@Composable
private fun StatusText(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun BooksEmptyState(onAddClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(contentAlignment = Alignment.Center, modifier = modifier.padding(32.dp)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.MenuBook,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = stringResource(R.string.books_empty_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(R.string.books_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(R.string.books_empty_drm),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Button(onClick = onAddClick) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.add_epub_file))
            }
        }
    }
}

private fun BookSort.labelRes(): Int = when (this) {
    BookSort.RECENTLY_READ -> R.string.sort_recently_read
    BookSort.TITLE -> R.string.sort_title
    BookSort.RECENTLY_ADDED -> R.string.sort_recently_added
}
