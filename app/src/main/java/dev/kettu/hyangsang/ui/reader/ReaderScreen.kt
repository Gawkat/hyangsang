package dev.kettu.hyangsang.ui.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.kettu.hyangsang.data.local.entity.Article
import dev.kettu.hyangsang.data.local.entity.ArticleWithFeed
import dev.kettu.hyangsang.data.local.entity.RssFeed
import dev.kettu.hyangsang.ui.theme.HyangsangTheme
import dev.kettu.hyangsang.ui.viewmodel.DictionaryViewModel
import dev.kettu.hyangsang.ui.viewmodel.VocabularyViewModel

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    articleWithFeed: ArticleWithFeed,
    onMenuClick: () -> Unit,
    dictionaryViewModel: DictionaryViewModel,
    vocabularyViewModel: VocabularyViewModel,
    modifier: Modifier = Modifier,
    fontSize: String = "Medium (Default)"
) {
    val stemmedWord by dictionaryViewModel.stemmedWord.collectAsState()
    val wordDefinitions by dictionaryViewModel.wordDefinitions.collectAsState()

    ReaderContent(
        articleWithFeed = articleWithFeed,
        onMenuClick = onMenuClick,
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
    onMenuClick: () -> Unit,
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
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    var selectedWord by remember { mutableStateOf<String?>(null) }
    val sheetState = rememberModalBottomSheetState()
    var showBottomSheet by remember { mutableStateOf(false) }

    val baseFontSize = when (fontSize) {
        "Small" -> 16.sp
        "Medium (Default)" -> 20.sp
        "Large" -> 24.sp
        else -> 20.sp
    }

    // Basic tokenization: split by spaces and keep them to preserve layout
    val tokens = remember(content) { content.split(Regex("(?<=\\s)|(?=\\s)")) }

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
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                )
            )
        },
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                ArticleHeader(articleWithFeed = articleWithFeed)
                FlowRow {
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
                                    selectedWord = token
                                    onLookupWord(wordToLookup)
                                    showBottomSheet = true
                                }
                            )
                        }
                    }
                }
            }
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
    fontSize: androidx.compose.ui.unit.TextUnit,
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
                    content = "실시간 전략 게임의 전설, 스타크래프트 2의 캠페인과 멀티플레이어 매력을 심층 분석합니다. 테란, 저그, 프로토스 세 종족의 운명이 걸린 거대한 전쟁 속으로 뛰어들어 보세요.",
                    pubDate = "2024-03-20"
                ),
                feed = RssFeed(
                    id = 1,
                    title = "게임 소식",
                    url = "https://example.com/rss",
                    category = "게임"
                )
            ),
            onMenuClick = {},
            stemmedWord = null,
            wordDefinitions = emptyList(),
            onLookupWord = {},
            onClearLookup = {}
        )
    }
}
