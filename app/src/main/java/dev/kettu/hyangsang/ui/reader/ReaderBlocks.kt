package dev.kettu.hyangsang.ui.reader

import android.net.Uri
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import dev.kettu.hyangsang.Constants
import dev.kettu.hyangsang.data.local.dao.DictionaryWithSenses
import dev.kettu.hyangsang.data.prefs.BookParagraphs
import dev.kettu.hyangsang.data.prefs.ReaderSettings
import dev.kettu.hyangsang.parser.ContentBlock
import dev.kettu.hyangsang.ui.settings.openInBrowser
import kotlinx.coroutines.launch

// Upper bound for the lookup sheet, as a fraction of the window height
private const val LOOKUP_SHEET_MAX_FRACTION = 0.55f

// BottomSheetDefaults.DragHandle: 4dp tall with 22dp vertical padding
private val DRAG_HANDLE_HEIGHT = 48.dp

/** How the reader lays out text, from the reader settings. */
data class ReaderLayout(
    val bodyStyle: TextStyle,
    val fontFamily: FontFamily,
    val margin: Dp,
    val paragraphSpacing: Dp,
    // Paragraphs are indented with no space between them, as in print, or spaced apart
    val indented: Boolean = false
) {
    val paragraphStyle: TextStyle =
        if (indented) bodyStyle.copy(textIndent = TextIndent(firstLine = 1.em)) else bodyStyle

    val paragraphGap: Dp = if (indented) 0.dp else paragraphSpacing

    // A blank line, with the gap after the paragraph before it
    val breakHeight: Dp = paragraphSpacing * 2 - paragraphGap
}

/** The layout for articles, or for books, whose paragraphs can be indented. */
@Composable
fun readerLayout(settings: ReaderSettings, isBook: Boolean = false): ReaderLayout = ReaderLayout(
    bodyStyle = readerBodyStyle(settings),
    fontFamily = settings.font.fontFamily(),
    margin = settings.margin.dp.dp,
    paragraphSpacing = (settings.textSize * settings.lineSpacing * 0.5f).dp,
    indented = isBook && settings.bookParagraphs == BookParagraphs.INDENTED
)

/**
 * The word being looked up and whether its sheet is open. Selections point at block indices,
 * so a new [resetKey] (other content) drops them.
 */
@Stable
class WordLookup {
    var selection by mutableStateOf<WordSelection?>(null)
    var word by mutableStateOf("")
    var showSheet by mutableStateOf(false)

    /** Opens the sheet for a word tapped in the text. */
    lateinit var onWordClick: (WordSelection, String) -> Unit
        internal set

    fun close() {
        showSheet = false
        selection = null
    }
}

@Composable
fun rememberWordLookup(
    listState: LazyListState,
    onLookupWord: (word: String, sentence: String) -> Unit,
    resetKey: Any?
): WordLookup {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val windowHeight = LocalWindowInfo.current.containerSize.height.toFloat()
    val currentOnLookupWord by rememberUpdatedState(onLookupWord)
    val currentWindowHeight by rememberUpdatedState(windowHeight)
    return remember(resetKey) {
        WordLookup().apply {
            onWordClick = { wordSelection, word ->
                selection = wordSelection
                this.word = word
                currentOnLookupWord(word, wordSelection.sentence)
                showSheet = true

                // Scroll the tapped line above where the lookup sheet will be, so the sentence
                // being looked up stays readable
                wordSelection.anchorY?.let { anchorY ->
                    val visibleBottom = currentWindowHeight * (1 - LOOKUP_SHEET_MAX_FRACTION) -
                            with(density) { 16.dp.toPx() }
                    if (anchorY > visibleBottom) {
                        scope.launch { listState.animateScrollBy(anchorY - visibleBottom) }
                    }
                }
            }
        }
    }
}

/** Room below the text, enough to scroll the last paragraphs above the lookup sheet. */
@Composable
fun lookupBottomPadding(lookup: WordLookup): Dp {
    val windowHeight = LocalWindowInfo.current.containerSize.height.toFloat()
    return if (lookup.showSheet) {
        with(LocalDensity.current) { (windowHeight * LOOKUP_SHEET_MAX_FRACTION).toDp() }
    } else {
        16.dp
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LookupSheet(
    lookup: WordLookup,
    lookupResult: Map<String, List<DictionaryWithSenses>>,
    isLookingUp: Boolean,
    onClearLookup: () -> Unit,
    onReportLookup: ((shown: DictionaryWithSenses?, picked: DictionaryWithSenses?, note: String) -> Unit)?
) {
    if (!lookup.showSheet) return
    val context = LocalContext.current
    val density = LocalDensity.current
    val lookupSheetMaxPx = LocalWindowInfo.current.containerSize.height * LOOKUP_SHEET_MAX_FRACTION
    // The drag handle and navigation bar padding sit outside the sheet content, so leave room
    // for them to keep the whole sheet within LOOKUP_SHEET_MAX_FRACTION
    val navigationBarBottom = WindowInsets.navigationBars.getBottom(density)
    val lookupContentMaxHeight = with(density) {
        (lookupSheetMaxPx - DRAG_HANDLE_HEIGHT.toPx() - navigationBarBottom).toDp()
    }
    // No scrim and no half-expanded state: the text stays visible, and one back press closes
    // the sheet
    ModalBottomSheet(
        onDismissRequest = {
            lookup.close()
            onClearLookup()
        },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        scrimColor = Color.Transparent
    ) {
        DefinitionOverlay(
            selectedWord = lookup.word,
            lookupResults = lookupResult,
            isLoading = isLookingUp,
            onSearchWeb = { term ->
                openInBrowser(context, Constants.WEB_DICTIONARY_SEARCH_URL + Uri.encode(term))
            },
            modifier = Modifier.heightIn(max = lookupContentMaxHeight),
            onReport = onReportLookup
        )
    }
}

/**
 * The content blocks, as list items. [imageUrl] maps an image block's URL to one that loads,
 * and [onNoteClick] shows a footnote whose marker was tapped.
 */
fun LazyListScope.contentBlocks(
    blocks: List<ContentBlock>,
    lookup: WordLookup,
    layout: ReaderLayout,
    imageUrl: (String) -> String = { it },
    onNoteClick: ((String) -> Unit)? = null
) {
    itemsIndexed(
        items = blocks,
        contentType = { _, block -> block::class }
    ) { index, block ->
        val textId = "block-$index"
        val selectedRange = lookup.selection.rangeIn(textId)
        val onWordClick = lookup.onWordClick
        when (block) {
            is ContentBlock.Text -> {
                ParagraphContent(
                    textBlock = block,
                    textId = textId,
                    selectedRange = selectedRange,
                    style = layout.paragraphStyle,
                    onWordClick = onWordClick,
                    onNoteClick = onNoteClick
                )
                Spacer(modifier = Modifier.height(layout.paragraphGap))
            }

            ContentBlock.Break -> Spacer(modifier = Modifier.height(layout.breakHeight))

            is ContentBlock.Image -> {
                ArticleImage(
                    url = imageUrl(block.url),
                    caption = block.caption,
                    captionSpans = block.captionSpans,
                    fontFamily = layout.fontFamily,
                    width = block.width,
                    height = block.height,
                    textId = textId,
                    selectedRange = selectedRange,
                    onWordClick = onWordClick,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            is ContentBlock.Heading -> {
                ArticleHeading(
                    text = block.text,
                    level = block.level,
                    textId = textId,
                    selectedRange = selectedRange,
                    onWordClick = onWordClick,
                    fontFamily = layout.fontFamily
                )
            }

            is ContentBlock.Dateline -> {
                ClickableText(
                    text = block.text,
                    textId = textId,
                    selectedRange = selectedRange,
                    onWordClick = onWordClick,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontStyle = FontStyle.Italic,
                        fontFamily = layout.fontFamily
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )
            }

            is ContentBlock.Footer -> {
                ClickableText(
                    text = block.text,
                    textId = textId,
                    selectedRange = selectedRange,
                    onWordClick = onWordClick,
                    style = layout.bodyStyle.copy(
                        fontSize = layout.bodyStyle.fontSize * 0.8f,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = layout.paragraphSpacing / 2)
                )
            }

            ContentBlock.Divider -> {
                HorizontalDivider(
                    modifier = Modifier
                        .padding(horizontal = 48.dp)
                        .padding(top = layout.paragraphSpacing / 2, bottom = layout.paragraphSpacing * 1.5f),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
            }
        }
    }
}
