package dev.kettu.hyangsang.ui.reader

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.kettu.hyangsang.Constants
import dev.kettu.hyangsang.R
import dev.kettu.hyangsang.data.local.dao.DictionaryWithSenses
import dev.kettu.hyangsang.data.local.entity.Article
import dev.kettu.hyangsang.data.local.entity.ArticleWithFeed
import dev.kettu.hyangsang.data.local.entity.RssFeed
import dev.kettu.hyangsang.data.prefs.ReaderSettings
import dev.kettu.hyangsang.parser.ContentBlock
import dev.kettu.hyangsang.ui.settings.openInBrowser
import dev.kettu.hyangsang.ui.theme.HyangsangTheme
import dev.kettu.hyangsang.ui.viewmodel.DictionaryViewModel
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

// Upper bound for the lookup sheet, as a fraction of the window height
private const val LOOKUP_SHEET_MAX_FRACTION = 0.55f

// BottomSheetDefaults.DragHandle: 4dp tall with 22dp vertical padding
private val DRAG_HANDLE_HEIGHT = 48.dp

@Composable
fun ReaderScreen(
    articleWithFeed: ArticleWithFeed,
    onBackClick: () -> Unit,
    dictionaryViewModel: DictionaryViewModel,
    readerSettings: ReaderSettings,
    onReaderSettingsChange: (ReaderSettings) -> Unit,
    currentTheme: String,
    onThemeChange: (String) -> Unit,
    onSaveToggle: (saved: Boolean) -> Unit,
    onMoreTextSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val lookupResult by dictionaryViewModel.lookupResult.collectAsState()
    val isLookingUp by dictionaryViewModel.isLookingUp.collectAsState()

    ReaderContent(
        articleWithFeed = articleWithFeed,
        onBackClick = onBackClick,
        lookupResult = lookupResult,
        isLookingUp = isLookingUp,
        onLookupWord = { dictionaryViewModel.lookupWord(it) },
        onClearLookup = { dictionaryViewModel.clearLookup() },
        readerSettings = readerSettings,
        onReaderSettingsChange = onReaderSettingsChange,
        currentTheme = currentTheme,
        onThemeChange = onThemeChange,
        onSaveToggle = onSaveToggle,
        onMoreTextSettingsClick = onMoreTextSettingsClick,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderContent(
    articleWithFeed: ArticleWithFeed,
    onBackClick: () -> Unit,
    lookupResult: Map<String, List<DictionaryWithSenses>>,
    isLookingUp: Boolean,
    onLookupWord: (String) -> Unit,
    onClearLookup: () -> Unit,
    readerSettings: ReaderSettings,
    onReaderSettingsChange: (ReaderSettings) -> Unit,
    currentTheme: String,
    onThemeChange: (String) -> Unit,
    onSaveToggle: (saved: Boolean) -> Unit,
    onMoreTextSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val article = articleWithFeed.article
    // Legacy blocks are split into paragraphs once, up front, so that every paragraph becomes
    // its own lazy item instead of one giant item that is composed all at once.
    val contentBlocks = remember(article.content) { flattenContentBlocks(article.content) }
    val scrollBehavior =
        TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val density = LocalDensity.current
    val windowHeight = LocalWindowInfo.current.containerSize.height.toFloat()
    val lookupSheetMaxPx = windowHeight * LOOKUP_SHEET_MAX_FRACTION
    val lookupSheetMaxHeight = with(density) { lookupSheetMaxPx.toDp() }
    // The drag handle and navigation bar padding sit outside the sheet content, so leave room
    // for them to keep the whole sheet within LOOKUP_SHEET_MAX_FRACTION
    val navigationBarBottom = WindowInsets.navigationBars.getBottom(density)
    val lookupContentMaxHeight = with(density) {
        (lookupSheetMaxPx - DRAG_HANDLE_HEIGHT.toPx() - navigationBarBottom).toDp()
    }

    var selection by remember { mutableStateOf<WordSelection?>(null) }
    var lookupWord by remember { mutableStateOf("") }
    var showLookupSheet by remember { mutableStateOf(false) }
    var showTextSheet by rememberSaveable { mutableStateOf(false) }
    // The article snapshot doesn't update, so the saved state is tracked locally
    var isSaved by rememberSaveable(article.id) { mutableStateOf(article.savedDate != null) }
    val showTitleInBar by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }

    val currentOnLookupWord by rememberUpdatedState(onLookupWord)
    val currentWindowHeight by rememberUpdatedState(windowHeight)
    val onWordClick: (WordSelection, String) -> Unit = remember {
        { wordSelection, word ->
            selection = wordSelection
            lookupWord = word
            currentOnLookupWord(word)
            showLookupSheet = true

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

    val bodyStyle = readerBodyStyle(readerSettings)
    val fontFamily = readerSettings.font.fontFamily()
    val margin = readerSettings.margin.dp.dp
    val paragraphSpacing = (readerSettings.textSize * readerSettings.lineSpacing * 0.5f).dp

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    AnimatedVisibility(visible = showTitleInBar, enter = fadeIn(), exit = fadeOut()) {
                        Text(
                            text = article.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back_button)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        isSaved = !isSaved
                        onSaveToggle(isSaved)
                    }) {
                        Icon(
                            imageVector = if (isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                            contentDescription = stringResource(
                                if (isSaved) R.string.unsave_article else R.string.save_article
                            ),
                            tint = if (isSaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { showTextSheet = true }) {
                        Icon(
                            Icons.Outlined.FormatSize,
                            contentDescription = stringResource(R.string.text_layout_title)
                        )
                    }
                    IconButton(onClick = { openInBrowser(context, article.sourceUrl) }) {
                        Icon(
                            Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = stringResource(R.string.open_in_browser_button)
                        )
                    }
                },
                scrollBehavior = scrollBehavior
            )
        },
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentPadding = PaddingValues(
                start = margin,
                end = margin,
                top = 16.dp,
                // Room to scroll the last paragraphs above the lookup sheet
                bottom = if (showLookupSheet) lookupSheetMaxHeight else 16.dp
            )
        ) {
            item(key = "header", contentType = "header") {
                ArticleHeader(
                    articleWithFeed = articleWithFeed,
                    selection = selection,
                    onWordClick = onWordClick,
                    fontFamily = fontFamily
                )
            }
            itemsIndexed(
                items = contentBlocks,
                contentType = { _, block -> block::class }
            ) { index, block ->
                val textId = "block-$index"
                val selectedRange = selection.rangeIn(textId)
                when (block) {
                    is ContentBlock.Text -> {
                        ParagraphContent(
                            textBlock = block,
                            textId = textId,
                            selectedRange = selectedRange,
                            style = bodyStyle,
                            onWordClick = onWordClick
                        )
                        Spacer(modifier = Modifier.height(paragraphSpacing))
                    }

                    // Already flattened into Text blocks by flattenContentBlocks()
                    is ContentBlock.Legacy -> Unit

                    is ContentBlock.Image -> {
                        ArticleImage(
                            url = block.url,
                            caption = block.caption,
                            captionSpans = block.captionSpans,
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
                            fontFamily = fontFamily
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
                                fontStyle = FontStyle.Italic
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                        )
                    }
                }
            }
            item(key = "bottom-spacer") { Spacer(modifier = Modifier.height(32.dp)) }
        }

        if (showLookupSheet) {
            // No scrim and no half-expanded state: the article stays visible, and one back
            // press closes the sheet
            ModalBottomSheet(
                onDismissRequest = {
                    showLookupSheet = false
                    selection = null
                    onClearLookup()
                },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                scrimColor = Color.Transparent
            ) {
                DefinitionOverlay(
                    selectedWord = lookupWord,
                    lookupResults = lookupResult,
                    isLoading = isLookingUp,
                    onSearchWeb = { term ->
                        openInBrowser(context, Constants.WEB_DICTIONARY_SEARCH_URL + Uri.encode(term))
                    },
                    modifier = Modifier.heightIn(max = lookupContentMaxHeight)
                )
            }
        }

        if (showTextSheet) {
            ReaderTextSheet(
                settings = readerSettings,
                onSettingsChange = onReaderSettingsChange,
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

private fun flattenContentBlocks(blocks: List<ContentBlock>?): List<ContentBlock> =
    blocks.orEmpty().flatMap { block ->
        if (block is ContentBlock.Legacy) {
            block.text.split("\n").filter { it.isNotBlank() }.map { ContentBlock.Text(it) }
        } else {
            listOf(block)
        }
    }

@OptIn(ExperimentalTime::class)
@Preview(showBackground = true)
@Composable
fun ReaderScreenPreview() {
    HyangsangTheme {
        ReaderContent(
            articleWithFeed = ArticleWithFeed(
                article = Article(
                    id = 1,
                    feedId = 1,
                    title = "스타크래프트 2: 자유의 날개 다시 보기",
                    description = "실시간 전략 게임의 전설, 스타크래프트 2의 캠페인과 멀티플레이어 매력을 심층 분석합니다.",
                    content = listOf(
                        ContentBlock.Text(
                            "스타크래프트 II의 이야기는 오리지널 스타크래프트 출시작의 이야기를 이어가며, 스타크래프트의 종족과 스타크래프트 시리즈의 등장인물 목록을 다룬다."
                        ),
                        ContentBlock.Text(
                            "자유의 날개는 스타크래프트: 브루드 워 사건으로부터 4년 후를 배경으로 하며, 짐 레이너의 반란군과 황제 아크튜러스 멩스크가 이끄는 테란 자치령 간의 갈등에 초점을 맞춘다. 저그는 자주 위협으로 다시 나타나지만, 레이너는 결국 저그 모행성에서 무력화된 그들의 여왕 사라 케리건을 회수한다."
                        )
                    ),
                    pubDate = Clock.System.now().toString(),
                    sourceUrl = "https://www.source.url/article"
                ),
                feed = RssFeed(
                    id = 1,
                    title = "게임 소식",
                    url = "https://example.com/rss",
                    category = "게임"
                )
            ),
            onBackClick = {},
            lookupResult = emptyMap(),
            isLookingUp = false,
            onLookupWord = {},
            onClearLookup = {},
            readerSettings = ReaderSettings(),
            onReaderSettingsChange = {},
            currentTheme = "System default",
            onThemeChange = {},
            onSaveToggle = {},
            onMoreTextSettingsClick = {}
        )
    }
}
