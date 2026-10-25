package dev.kettu.hyangsang.ui.reader

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.kettu.hyangsang.R
import dev.kettu.hyangsang.data.local.entity.Book
import dev.kettu.hyangsang.data.local.entity.BookChapter
import dev.kettu.hyangsang.data.local.entity.ChapterInfo
import dev.kettu.hyangsang.data.prefs.ReaderSettings
import dev.kettu.hyangsang.data.repository.LookupContext
import dev.kettu.hyangsang.parser.epub.EpubParser
import dev.kettu.hyangsang.ui.components.readingTimeText
import dev.kettu.hyangsang.ui.library.percentText
import dev.kettu.hyangsang.ui.viewmodel.BookReaderViewModel
import dev.kettu.hyangsang.ui.viewmodel.DictionaryViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import java.io.File

// How long the list has to rest before its position is stored
private const val SAVE_POSITION_DELAY_MILLIS = 500L

private const val HEADER_KEY = "header"
private const val END_KEY = "end"

/** Reads a book one chapter at a time, with the same lookups as articles. */
@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
fun BookReaderScreen(
    viewModel: BookReaderViewModel,
    dictionaryViewModel: DictionaryViewModel,
    readerSettings: ReaderSettings,
    onReaderSettingsChange: (ReaderSettings) -> Unit,
    currentTheme: String,
    onThemeChange: (String) -> Unit,
    onMoreTextSettingsClick: () -> Unit,
    onDetailsClick: () -> Unit,
    onBackClick: () -> Unit,
    collectLookupReports: Boolean,
    modifier: Modifier = Modifier
) {
    val book by viewModel.book.collectAsState()
    val chapters by viewModel.chapters.collectAsState()
    val openChapter by viewModel.openChapter.collectAsState()
    val updating by viewModel.updating.collectAsState()
    val lookupResult by dictionaryViewModel.lookupResult.collectAsState()
    val isLookingUp by dictionaryViewModel.isLookingUp.collectAsState()
    val context = LocalContext.current

    val current = openChapter
    val currentBook = book
    if (current == null || currentBook == null) {
        Box(contentAlignment = Alignment.Center, modifier = modifier.fillMaxSize()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                CircularProgressIndicator()
                if (updating) Text(stringResource(R.string.updating_book))
            }
        }
        return
    }
    val chapter = current.chapter

    // A list of its own for each chapter, opened where the reader left it
    val listState = remember(chapter.chapterIndex) {
        LazyListState(firstVisibleItemIndex = if (current.startBlock > 0) current.startBlock + 1 else 0)
    }
    val lookup = rememberWordLookup(
        listState,
        onLookupWord = { word, sentence -> dictionaryViewModel.lookupWord(word, LookupContext(sentence)) },
        resetKey = chapter.chapterIndex
    )
    val layout = readerLayout(readerSettings, isBook = true)
    var showContents by rememberSaveable { mutableStateOf(false) }
    var showTextSheet by rememberSaveable { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var note by rememberSaveable { mutableStateOf<String?>(null) }
    val showTitleInBar by remember(listState) { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
    val imageDir = remember(currentBook.folder) { viewModel.imageDir(currentBook) }
    val isLastChapter = chapter.chapterIndex == currentBook.chapterCount - 1

    // How far into the book the screen is: the chapters before, and the part of this one scrolled
    // through, as the article reader measures it
    val scrollMeasure = remember(chapter) { ScrollMeasure(chapter.content, firstBlockItem = 1) }
    val progress by remember(chapter, currentBook.totalChars) {
        derivedStateOf {
            val inChapter = scrollMeasure.progress(listState.layoutInfo) ?: 0f
            val chars = chapter.charsBefore + inChapter * chapter.charCount
            if (currentBook.totalChars == 0) 0f else chars / currentBook.totalChars
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .debounce(SAVE_POSITION_DELAY_MILLIS)
            .collect { index -> viewModel.onBlockVisible((index - 1).coerceAtLeast(0)) }
    }
    if (isLastChapter) {
        LaunchedEffect(listState) {
            snapshotFlow { listState.layoutInfo.visibleItemsInfo.any { it.key == END_KEY } }
                .filter { it }
                .collect { viewModel.onEndOfBook() }
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        AnimatedVisibility(visible = showTitleInBar, enter = fadeIn(), exit = fadeOut()) {
                            Text(
                                text = chapterTitle(chapter.chapterIndex, chapter.title),
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back_button))
                        }
                    },
                    actions = {
                        IconButton(onClick = { showContents = true }) {
                            Icon(Icons.AutoMirrored.Filled.List, contentDescription = stringResource(R.string.book_contents))
                        }
                        IconButton(onClick = { showTextSheet = true }) {
                            Icon(Icons.Outlined.FormatSize, contentDescription = stringResource(R.string.text_layout_title))
                        }
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more_options))
                            }
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.book_details)) },
                                    leadingIcon = { Icon(Icons.Outlined.Info, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        onDetailsClick()
                                    }
                                )
                            }
                        }
                    }
                )
                LinearProgressIndicator(
                    progress = { progress },
                    drawStopIndicator = {},
                    gapSize = 0.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                )
            }
        },
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentPadding = PaddingValues(
                start = layout.margin,
                end = layout.margin,
                top = 16.dp,
                bottom = lookupBottomPadding(lookup)
            )
        ) {
            item(key = HEADER_KEY, contentType = HEADER_KEY) {
                ChapterHeader(currentBook, chapter, lookup, layout)
            }
            contentBlocks(
                blocks = chapter.content,
                lookup = lookup,
                layout = layout,
                imageUrl = { url -> bookImageUrl(url, imageDir) },
                onNoteClick = { note = it }
            )
            item(key = END_KEY, contentType = END_KEY) {
                ChapterEnd(
                    chapter = chapter,
                    next = chapters.getOrNull(chapter.chapterIndex + 1),
                    previous = chapters.getOrNull(chapter.chapterIndex - 1),
                    onChapterClick = viewModel::openChapter,
                    onBackClick = onBackClick
                )
            }
        }

        LookupSheet(
            lookup = lookup,
            lookupResult = lookupResult,
            isLookingUp = isLookingUp,
            onClearLookup = dictionaryViewModel::clearLookup,
            onReportLookup = if (collectLookupReports) {
                { shown, picked, reportNote ->
                    dictionaryViewModel.reportLookup(shown, picked, reportNote, "book: ${currentBook.title}")
                    Toast.makeText(context, R.string.lookup_report_saved, Toast.LENGTH_SHORT).show()
                }
            } else {
                null
            }
        )

        note?.let { text ->
            NoteSheet(
                text = text,
                style = layout.bodyStyle,
                onWordClick = { selection, word ->
                    note = null
                    // The note's text isn't in the chapter, so nothing there is highlighted
                    lookup.onWordClick(selection.copy(textId = "note", anchorY = null), word)
                },
                onDismiss = { note = null }
            )
        }

        if (showContents) {
            ContentsSheet(
                book = currentBook,
                chapters = chapters,
                currentIndex = chapter.chapterIndex,
                progressInChapter = if (chapter.charCount == 0) 0f else {
                    ((progress * currentBook.totalChars - chapter.charsBefore) / chapter.charCount).coerceIn(0f, 1f)
                },
                onChapterClick = { index ->
                    showContents = false
                    viewModel.openChapter(index)
                },
                onDismiss = { showContents = false }
            )
        }

        if (showTextSheet) {
            ReaderTextSheet(
                settings = readerSettings,
                onSettingsChange = onReaderSettingsChange,
                showBookParagraphs = true,
                currentTheme = currentTheme,
                onThemeChange = onThemeChange,
                onMoreSettingsClick = {
                    showTextSheet = false
                    onMoreTextSettingsClick()
                },
                onDismiss = { showTextSheet = false }
            )
        }
    }
}

// Images of a book are files in its folder
private fun bookImageUrl(url: String, imageDir: File): String =
    if (url.startsWith(EpubParser.IMAGE_PREFIX)) {
        File(imageDir, url.removePrefix(EpubParser.IMAGE_PREFIX)).toURI().toString()
    } else {
        url
    }

@Composable
private fun chapterTitle(index: Int, title: String?): String =
    title?.takeIf { it.isNotBlank() } ?: stringResource(R.string.chapter_number, index + 1)

@Composable
private fun ChapterHeader(book: Book, chapter: BookChapter, lookup: WordLookup, layout: ReaderLayout) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.book_and_chapter, book.title, chapter.chapterIndex + 1).uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        // Tappable like the text, since chapter titles are Korean too
        ClickableText(
            text = chapterTitle(chapter.chapterIndex, chapter.title),
            textId = "title",
            selectedRange = lookup.selection.rangeIn("title"),
            onWordClick = lookup.onWordClick,
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold,
                fontFamily = layout.fontFamily
            ),
            modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)
        )
        if (chapter.wordCount > 0) {
            Text(
                text = stringResource(
                    R.string.reading_time_and_words,
                    readingTimeText(chapter.wordCount),
                    pluralStringResource(R.plurals.word_count, chapter.wordCount, chapter.wordCount)
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = stringResource(
                R.string.chapter_of_chapters_and_percent,
                chapter.chapterIndex + 1,
                book.chapterCount,
                percentText(if (book.totalChars == 0) 0f else chapter.charsBefore.toFloat() / book.totalChars)
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        HorizontalDivider(modifier = Modifier.padding(top = 16.dp, bottom = 20.dp))
    }
}

@Composable
private fun ChapterEnd(
    chapter: BookChapter,
    next: ChapterInfo?,
    previous: ChapterInfo?,
    onChapterClick: (Int) -> Unit,
    onBackClick: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.padding(top = 16.dp, bottom = 32.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HorizontalDivider(modifier = Modifier.weight(1f))
            Text(
                text = stringResource(R.string.end_of_chapter, chapter.chapterIndex + 1),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider(modifier = Modifier.weight(1f))
        }
        if (next != null) {
            Card(
                onClick = { onChapterClick(next.chapterIndex) },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(20.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.next_chapter, next.chapterIndex + 1).uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = chapterTitle(next.chapterIndex, next.title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                }
            }
        } else {
            Text(
                text = stringResource(R.string.end_of_book),
                style = MaterialTheme.typography.titleMedium
            )
            Button(onClick = onBackClick) { Text(stringResource(R.string.back_to_library)) }
        }
        if (previous != null) {
            TextButton(onClick = { onChapterClick(previous.chapterIndex) }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.previous_chapter, chapterTitle(previous.chapterIndex, previous.title)))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteSheet(
    text: String,
    style: TextStyle,
    onWordClick: (WordSelection, String) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 24.dp)
        ) {
            Text(stringResource(R.string.note_title), style = MaterialTheme.typography.titleMedium)
            ClickableText(
                text = text,
                textId = "note",
                selectedRange = null,
                onWordClick = onWordClick,
                style = style,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * The book's chapters, the ones before [currentIndex] marked as read. Also opened from the Library,
 * where a book not started has none current, and a finished one has all of them read.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContentsSheet(
    book: Book,
    chapters: List<ChapterInfo>,
    currentIndex: Int,
    progressInChapter: Float,
    onChapterClick: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 12.dp)) {
            Text(stringResource(R.string.book_contents), style = MaterialTheme.typography.headlineSmall)
            Text(
                text = stringResource(R.string.book_and_progress, book.title, percentText(book.progress)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        val contentsState = remember { LazyListState(firstVisibleItemIndex = (currentIndex - 2).coerceAtLeast(0)) }
        LazyColumn(state = contentsState, contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp)) {
            items(chapters, key = { it.chapterIndex }) { info ->
                val isCurrent = info.chapterIndex == currentIndex
                val isRead = info.chapterIndex < currentIndex
                Surface(
                    onClick = { onChapterClick(info.chapterIndex) },
                    shape = RoundedCornerShape(28.dp),
                    color = if (isCurrent) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
                    ) {
                        val color = if (isRead) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                        Text(
                            text = (info.chapterIndex + 1).toString(),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(24.dp)
                        )
                        Text(
                            text = chapterTitle(info.chapterIndex, info.title),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                            color = color,
                            modifier = Modifier.weight(1f)
                        )
                        when {
                            isRead -> Icon(
                                Icons.Filled.Check,
                                contentDescription = stringResource(R.string.chapter_read),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            isCurrent -> Text(
                                text = stringResource(R.string.reading_chapter, percentText(progressInChapter)),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }
        }
    }
}
