package dev.kettu.hyangsang

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.kettu.hyangsang.data.local.entity.Article
import dev.kettu.hyangsang.data.prefs.UserPreferencesRepository
import dev.kettu.hyangsang.ui.index.SourceIndexScreen
import dev.kettu.hyangsang.ui.reader.ReaderScreen
import dev.kettu.hyangsang.ui.settings.SettingsScreen
import dev.kettu.hyangsang.ui.theme.HyangsangTheme
import dev.kettu.hyangsang.ui.viewmodel.AppViewModelFactory
import dev.kettu.hyangsang.ui.viewmodel.ArticleViewModel
import dev.kettu.hyangsang.ui.viewmodel.DictionaryViewModel
import dev.kettu.hyangsang.ui.viewmodel.VocabularyViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as HyangsangApplication
        val prefsRepository = app.userPreferencesRepository

        setContent {
            val theme by prefsRepository.themeFlow.collectAsState(initial = "System default")
            val fontSize by prefsRepository.fontSizeFlow.collectAsState(initial = "Medium (Default)")

            val darkTheme = when (theme) {
                "Light" -> false
                "Dark" -> true
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }

            val articleViewModel: ArticleViewModel = viewModel(
                factory = AppViewModelFactory(
                    app.articleRepository,
                    app.dictionaryRepository,
                    app.vocabularyRepository
                )
            )

            val dictionaryViewModel: DictionaryViewModel = viewModel(
                factory = AppViewModelFactory(
                    app.articleRepository,
                    app.dictionaryRepository,
                    app.vocabularyRepository
                )
            )

            val vocabularyViewModel: VocabularyViewModel = viewModel(
                factory = AppViewModelFactory(
                    app.articleRepository,
                    app.dictionaryRepository,
                    app.vocabularyRepository
                )
            )

            HyangsangTheme(darkTheme = darkTheme) {
                MainApp(
                    prefsRepository = prefsRepository,
                    articleViewModel = articleViewModel,
                    dictionaryViewModel = dictionaryViewModel,
                    vocabularyViewModel = vocabularyViewModel,
                    currentTheme = theme,
                    currentFontSize = fontSize
                )
            }
        }
    }
}

@Composable
fun MainApp(
    prefsRepository: UserPreferencesRepository,
    articleViewModel: ArticleViewModel,
    dictionaryViewModel: DictionaryViewModel,
    vocabularyViewModel: VocabularyViewModel,
    currentTheme: String,
    currentFontSize: String
) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val articles by articleViewModel.allArticles.collectAsState()

    // Seed data if not done before
    LaunchedEffect(Unit) {
        if (!prefsRepository.isInitialSeedDoneFlow.first()) {
            println("Starting initial seed...")
            articleViewModel.insertArticle(
                Article(
                    title = "첫 번째 기사: 한국의 봄",
                    content = "한국의 봄은 매우 아름답습니다. 벚꽃이 피고 날씨가 따뜻해집니다. 많은 사람들이 공원으로 나들이를 갑니다.",
                    source = "Sample"
                )
            )
            articleViewModel.insertArticle(
                Article(
                    title = "서울의 맛집 가이드",
                    content = "서울에는 맛있는 음식이 정말 많습니다. 특히 명동과 홍대에는 유명한 맛집들이 밀집해 있습니다. 비빔밥과 떡볶이를 꼭 드셔보세요.",
                    source = "Sample"
                )
            )
            prefsRepository.setInitialSeedDone(true)
            println("Initial seed marked as done.")
        } else {
            println("Initial seed already done, skipping.")
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    stringResource(R.string.app_name),
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.headlineSmall
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Home, contentDescription = null) },
                    label = { Text(stringResource(R.string.discover_nav)) },
                    selected = false,
                    onClick = {
                        navController.navigate("index") {
                            popUpTo("index") { inclusive = true }
                        }
                        scope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text(stringResource(R.string.settings_nav)) },
                    selected = false,
                    onClick = {
                        navController.navigate("settings")
                        scope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
            }
        }
    ) {
        NavHost(navController = navController, startDestination = "index") {
            composable("index") {
                SourceIndexScreen(
                    articles = articles,
                    onMenuClick = { scope.launch { drawerState.open() } },
                    onArticleClick = { articleId ->
                        navController.navigate("reader/$articleId")
                    }
                )
            }
            composable("reader/{articleId}") { backStackEntry ->
                val articleIdStr = backStackEntry.arguments?.getString("articleId")
                val articleId = articleIdStr?.toLongOrNull()
                ReaderWithDrawer(
                    articleId = articleId,
                    articleViewModel = articleViewModel,
                    dictionaryViewModel = dictionaryViewModel,
                    vocabularyViewModel = vocabularyViewModel,
                    onMenuClick = { scope.launch { drawerState.open() } },
                    fontSize = currentFontSize
                )
            }
            composable("settings") {
                SettingsScreen(
                    currentTheme = currentTheme,
                    onThemeChange = { scope.launch { prefsRepository.setTheme(it) } },
                    currentFontSize = currentFontSize,
                    onFontSizeChange = { scope.launch { prefsRepository.setFontSize(it) } },
                    onBackClick = { navController.popBackStack() }
                )
            }
        }
    }
}

@Composable
fun ReaderWithDrawer(
    articleId: Long?,
    articleViewModel: ArticleViewModel,
    dictionaryViewModel: DictionaryViewModel,
    vocabularyViewModel: VocabularyViewModel,
    onMenuClick: () -> Unit,
    fontSize: String
) {
    var title by remember { mutableStateOf("Loading...") }
    var content by remember { mutableStateOf("") }

    LaunchedEffect(articleId) {
        if (articleId != null) {
            val article = articleViewModel.getArticleById(articleId)
            if (article != null) {
                title = article.title
                content = article.content
            }
        }
    }

    ReaderScreen(
        title = title,
        content = content,
        onMenuClick = onMenuClick,
        dictionaryViewModel = dictionaryViewModel,
        vocabularyViewModel = vocabularyViewModel,
        fontSize = fontSize
    )
}
