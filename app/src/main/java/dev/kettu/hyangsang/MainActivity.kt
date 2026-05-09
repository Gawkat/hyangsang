package dev.kettu.hyangsang

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.kettu.hyangsang.data.prefs.UserPreferencesRepository
import dev.kettu.hyangsang.ui.discover.DiscoverScreen
import dev.kettu.hyangsang.ui.feeds.FeedsScreen
import dev.kettu.hyangsang.ui.reader.ReaderScreen
import dev.kettu.hyangsang.ui.settings.SettingsScreen
import dev.kettu.hyangsang.ui.theme.HyangsangTheme
import dev.kettu.hyangsang.ui.viewmodel.AppViewModelFactory
import dev.kettu.hyangsang.ui.viewmodel.ArticleUiState
import dev.kettu.hyangsang.ui.viewmodel.ArticleViewModel
import dev.kettu.hyangsang.ui.viewmodel.DictionaryViewModel
import dev.kettu.hyangsang.ui.viewmodel.RssFeedViewModel
import dev.kettu.hyangsang.ui.viewmodel.VocabularyViewModel
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
                    app.vocabularyRepository,
                    app.rssFeedRepository
                )
            )

            val dictionaryViewModel: DictionaryViewModel = viewModel(
                factory = AppViewModelFactory(
                    app.articleRepository,
                    app.dictionaryRepository,
                    app.vocabularyRepository,
                    app.rssFeedRepository
                )
            )

            val vocabularyViewModel: VocabularyViewModel = viewModel(
                factory = AppViewModelFactory(
                    app.articleRepository,
                    app.dictionaryRepository,
                    app.vocabularyRepository,
                    app.rssFeedRepository
                )
            )

            val feedViewModel: RssFeedViewModel = viewModel(
                factory = AppViewModelFactory(
                    app.articleRepository,
                    app.dictionaryRepository,
                    app.vocabularyRepository,
                    app.rssFeedRepository
                )
            )

            HyangsangTheme(darkTheme = darkTheme) {
                MainApp(
                    prefsRepository = prefsRepository,
                    articleViewModel = articleViewModel,
                    dictionaryViewModel = dictionaryViewModel,
                    vocabularyViewModel = vocabularyViewModel,
                    rssFeedViewModel = feedViewModel,
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
    rssFeedViewModel: RssFeedViewModel,
    currentTheme: String,
    currentFontSize: String
) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val articlesWithFeed by articleViewModel.allArticlesWithFeed.collectAsState()

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
                        navController.navigate("discover") {
                            popUpTo("discover") { inclusive = true }
                        }
                        scope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.RssFeed, contentDescription = null) },
                    label = { Text(stringResource(R.string.feeds_nav)) },
                    selected = false,
                    onClick = {
                        navController.navigate("manage_feeds")
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
        NavHost(navController = navController, startDestination = "discover") {
            composable("discover") {
                DiscoverScreen(
                    articlesWithFeed = articlesWithFeed,
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
            composable("manage_feeds") {
                FeedsScreen(
                    onMenuClick = { scope.launch { drawerState.open() } },
                    viewModel = rssFeedViewModel
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
    val uiState by articleViewModel.currentArticleState.collectAsState()

    LaunchedEffect(articleId) {
        if (articleId != null) {
            articleViewModel.loadArticle(articleId)
        }
    }

    when (val state = uiState) {
        is ArticleUiState.Loading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        is ArticleUiState.Error -> {
            Text("Error: ${state.message}")
        }

        is ArticleUiState.Success -> {
            ReaderScreen(
                state.articleWithFeed,
                onMenuClick = onMenuClick,
                dictionaryViewModel = dictionaryViewModel,
                vocabularyViewModel = vocabularyViewModel,
                fontSize = fontSize
            )
        }
    }
}
