package dev.kettu.hyangsang.ui.reader

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInBrowser
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import dev.kettu.hyangsang.R
import dev.kettu.hyangsang.data.local.entity.Article
import dev.kettu.hyangsang.data.local.entity.ArticleWithFeed
import dev.kettu.hyangsang.data.local.entity.RssFeed
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
    val stemmedWord by dictionaryViewModel.stemmedWord.collectAsState()
    val wordDefinitions by dictionaryViewModel.wordDefinitions.collectAsState()

    ReaderContent(
        articleWithFeed = articleWithFeed,
        onBackClick = onBackClick,
        stemmedWord = stemmedWord,
        wordDefinitions = wordDefinitions,
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
    stemmedWord: String?,
    wordDefinitions: List<dev.kettu.hyangsang.data.local.dao.DictionaryWithSenses>,
    onLookupWord: (String) -> Unit,
    onClearLookup: () -> Unit,
    modifier: Modifier = Modifier,
    fontSize: String = "Medium (Default)"
) {
    val article = articleWithFeed.article
    val content = article.content ?: ""
    val scrollBehavior =
        TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())
    var selectedWord by remember { mutableStateOf<String?>(null) }
    val sheetState = rememberModalBottomSheetState()
    var showBottomSheet by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // TODO: use enum or something
    val baseFontSize = remember(fontSize) {
        when (fontSize) {
            "Small" -> 16.sp
            "Medium (Default)" -> 20.sp
            "Large" -> 24.sp
            else -> 20.sp
        }
    }

    val paragraphs = remember(content) {
        content.split("\n")
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
                            imageVector = Icons.Filled.OpenInBrowser,
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
            item {
                ArticleHeader(articleWithFeed = articleWithFeed)
            }
            items(paragraphs) { paragraph ->
                if (paragraph.isNotBlank()) {
                    ParagraphContent(
                        paragraph = paragraph,
                        selectedWord = selectedWord,
                        baseFontSize = baseFontSize,
                        onWordClick = { word, token ->
                            selectedWord = token
                            onLookupWord(word)
                            showBottomSheet = true
                        }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                } else {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
            item { Spacer(modifier = Modifier.height(32.dp)) }
        }

        if (showBottomSheet) {
            ModalBottomSheet(
                onDismissRequest = {
                    showBottomSheet = false
                    selectedWord = null
                    onClearLookup()
                },
                sheetState = sheetState
            ) {
                DefinitionOverlay(
                    word = selectedWord ?: "",
                    stem = stemmedWord ?: "",
                    definitions = wordDefinitions
                )
            }
        }
    }
}

@Composable
fun ClickableWord(
    word: String,
    isSelected: Boolean,
    fontSize: TextUnit,
    onClick: () -> Unit
) {
    Text(
        text = word,
        fontSize = fontSize,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 1.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
    )
}

@Composable
fun ParagraphContent(
    paragraph: String,
    selectedWord: String?,
    baseFontSize: TextUnit,
    onWordClick: (String, String) -> Unit
) {
    val tokens = remember(paragraph) { paragraph.split(Regex("(?<=\\s)|(?=\\s)")) }

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        tokens.forEach { token ->
            if (token.isBlank()) {
                Text(text = token, fontSize = baseFontSize)
            } else {
                ClickableWord(
                    word = token,
                    isSelected = selectedWord == token,
                    fontSize = baseFontSize,
                    onClick = {
                        val wordToLookup = token.trim { it in "!.?,\"'" }
                        onWordClick(wordToLookup, token)
                    }
                )
            }
        }
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
                    content = "스타크래프트 II의 이야기는 오리지널 스타크래프트 출시작의 이야기를 이어가며, 스타크래프트의 종족과 스타크래프트 시리즈의 등장인물 목록을 다룬다.\n" +
                            "\n" +
                            "자유의 날개는 스타크래프트: 브루드 워 사건으로부터 4년 후를 배경으로 하며, 짐 레이너의 반란군과 황제 아크튜러스 멩스크가 이끄는 테란 자치령 간의 갈등에 초점을 맞춘다. 저그는 자주 위협으로 다시 나타나지만, 레이너는 결국 저그 모행성에서 무력화된 그들의 여왕 사라 케리건을 회수한다.\n" +
                            "\n" +
                            "군단의 심장에서는 자치령이 레이너와 케리건을 공격하고, 이야기는 주로 멩스크의 병력과 새로 나타난 프로토스-저그 혼종에 맞서는 케리건의 활약을 따라간다.\n" +
                            "\n" +
                            "공허의 유산에서는 프로토스가 주인공이며, 제라툴과 아르타니스가 이끄는 프로토스-저그 혼종의 창조자인 악한 존재 아몬에 맞서 싸운다. 공허의 유산이 끝난 후 짧은 에필로그에서 세 진영 모두 공허 속에서 아몬에 맞서기 위해 합류한다.\n" +
                            "\n" +
                            "노바 비밀 작전은 아몬의 최종 패배 후 어느 시점을 배경으로 하며, 유령 요원 노바가 재편성된 테란 자치령을 위협하는 음모를 밝혀내는 과정을 따라간다.",
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
            stemmedWord = null,
            wordDefinitions = emptyList(),
            onLookupWord = {},
            onClearLookup = {}
        )
    }
}
