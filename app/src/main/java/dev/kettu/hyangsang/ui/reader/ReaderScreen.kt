package dev.kettu.hyangsang.ui.reader

import android.content.Intent
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import dev.kettu.hyangsang.R
import dev.kettu.hyangsang.data.local.dao.DictionaryWithSenses
import dev.kettu.hyangsang.data.local.entity.Article
import dev.kettu.hyangsang.data.local.entity.ArticleWithFeed
import dev.kettu.hyangsang.data.local.entity.RssFeed
import dev.kettu.hyangsang.parser.ContentBlock
import dev.kettu.hyangsang.ui.theme.HyangsangTheme
import dev.kettu.hyangsang.ui.viewmodel.DictionaryViewModel
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    articleWithFeed: ArticleWithFeed,
    onBackClick: () -> Unit,
    dictionaryViewModel: DictionaryViewModel,
    modifier: Modifier = Modifier,
    fontSize: String = "Medium (Default)"
) {
    val lookupResult by dictionaryViewModel.lookupResult.collectAsState()

    ReaderContent(
        articleWithFeed = articleWithFeed,
        onBackClick = onBackClick,
        lookupResult = lookupResult,
        onLookupWord = { dictionaryViewModel.lookupWord(it) },
        onClearLookup = { dictionaryViewModel.clearLookup() },
        modifier = modifier,
        fontSize = fontSize
    )
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ReaderContent(
    articleWithFeed: ArticleWithFeed,
    onBackClick: () -> Unit,
    lookupResult: Map<String, List<DictionaryWithSenses>>,
    onLookupWord: (String) -> Unit,
    onClearLookup: () -> Unit,
    modifier: Modifier = Modifier,
    fontSize: String = "Medium (Default)"
) {
    val article = articleWithFeed.article
    // Legacy blocks are split into paragraphs once, up front, so that every paragraph becomes
    // its own lazy item instead of one giant item that is composed all at once.
    val contentBlocks = remember(article.content) { flattenContentBlocks(article.content) }
    val scrollBehavior =
        TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())
    var selection by remember { mutableStateOf<WordSelection?>(null) }
    val sheetState = rememberModalBottomSheetState()
    var showBottomSheet by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val currentOnLookupWord by rememberUpdatedState(onLookupWord)
    val onWordClick: (WordSelection, String) -> Unit = remember {
        { wordSelection, lookupWord ->
            selection = wordSelection
            currentOnLookupWord(lookupWord)
            showBottomSheet = true
        }
    }

    // TODO: use enum or something
    val baseFontSize = remember(fontSize) {
        when (fontSize) {
            "Small" -> 16.sp
            "Medium (Default)" -> 20.sp
            "Large" -> 24.sp
            else -> 20.sp
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        articleWithFeed.feed.category,
                        style = MaterialTheme.typography.titleLarge
                    )
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
                        context.startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                article.sourceUrl.toUri()
                            )
                        )
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = stringResource(R.string.open_in_browser_button) // TODO: add description
                        )
                    }
                    /*
                    IconButton(onClick = { }) {
                        Icon(
                            imageVector = Icons.Filled.BookmarkBorder, // TODO: Indicate if saved or not
                            contentDescription = null // TODO: Change description if saved/not saved
                        )
                    }
                    */
                },
                scrollBehavior = scrollBehavior
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp)
        ) {
            item(key = "header", contentType = "header") {
                ArticleHeader(
                    articleWithFeed = articleWithFeed,
                    selection = selection,
                    onWordClick = onWordClick
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
                            baseFontSize = baseFontSize,
                            onWordClick = onWordClick
                        )
                        Spacer(modifier = Modifier.height(12.dp))
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
                            onWordClick = onWordClick
                        )
                    }

                    is ContentBlock.Dateline -> {
                        ClickableText(
                            text = block.text,
                            textId = textId,
                            selectedRange = selectedRange,
                            onWordClick = onWordClick,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.outline,
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

        if (showBottomSheet) {
            ModalBottomSheet(
                onDismissRequest = {
                    showBottomSheet = false
                    selection = null
                    onClearLookup()
                },
                sheetState = sheetState
            ) {
                DefinitionOverlay(
                    selectedWord = selection?.token ?: "",
                    lookupResults = lookupResult
                )
            }
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
            onLookupWord = {},
            onClearLookup = {}
        )
    }
}
