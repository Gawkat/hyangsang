package dev.kettu.hyangsang

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.remember
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.google.android.gms.oss.licenses.v2.OssLicensesMenuActivity
import dev.kettu.hyangsang.data.prefs.UserPreferencesRepository
import dev.kettu.hyangsang.ui.discover.DiscoverScreen
import dev.kettu.hyangsang.ui.feeds.FeedsScreen
import dev.kettu.hyangsang.ui.menu.MenuScreen
import dev.kettu.hyangsang.ui.reader.ReaderScreen
import dev.kettu.hyangsang.ui.settings.SettingsScreen
import dev.kettu.hyangsang.ui.theme.HyangsangTheme
import dev.kettu.hyangsang.ui.theme.ThemePalette
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
            val paletteKey by prefsRepository.paletteFlow.collectAsState(initial = "hyangsang")
            val palette = ThemePalette.fromKey(paletteKey)
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

            HyangsangTheme(darkTheme = darkTheme, palette = palette) {
                MainApp(
                    prefsRepository = prefsRepository,
                    articleViewModel = articleViewModel,
                    dictionaryViewModel = dictionaryViewModel,
                    vocabularyViewModel = vocabularyViewModel,
                    rssFeedViewModel = feedViewModel,
                    currentTheme = theme,
                    currentPalette = palette,
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
    currentPalette: ThemePalette,
    currentFontSize: String
) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val discoverListState = rememberLazyListState()
    val articlesWithFeed by articleViewModel.allArticlesWithFeed.collectAsState()
    val filterCriteria by articleViewModel.filterCriteria.collectAsState()
    val feeds by rssFeedViewModel.feeds.collectAsState()
    val categories = remember(feeds) {
        feeds.map { it.category }.distinct().sorted()
    }
    val isRefreshing by rssFeedViewModel.isRefreshing.collectAsState()

    val navItems = listOf(
        Triple("discover", Icons.Default.Home, R.string.discover_nav),
        Triple("manage_feeds", Icons.Default.RssFeed, R.string.feeds_nav),
        Triple("menu", Icons.Default.Menu, R.string.menu_nav)
    )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Scaffold(
        bottomBar = {
            if (currentDestination?.route in navItems.map { it.first }) {
                NavigationBar {
                    navItems.forEach { (route, icon, labelRes) ->
                        NavigationBarItem(
                            icon = { Icon(icon, contentDescription = stringResource(labelRes)) },
                            label = { Text(stringResource(labelRes)) },
                            selected = currentDestination?.hierarchy?.any { it.route == route } == true,
                            onClick = {
                                if (currentDestination?.hierarchy?.any { it.route == route } == true) {
                                    // Re-tap logic
                                    if (route == "discover") {
                                        scope.launch {
                                            discoverListState.animateScrollToItem(0)
                                        }
                                    }
                                } else {
                                    navController.navigate(route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = navItems.first().first,
            modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            composable("discover") {
                DiscoverScreen(
                    articlesWithFeed = articlesWithFeed,
                    categories = categories,
                    selectedCategory = filterCriteria.selectedCategory,
                    onCategorySelected = { articleViewModel.setCategory(it) },
                    isRefreshing = isRefreshing,
                    onRefresh = { rssFeedViewModel.refreshFeeds() },
                    onArticleClick = { articleId ->
                        navController.navigate("reader/$articleId")
                    },
                    listState = discoverListState
                )
            }
            composable("manage_feeds") {
                FeedsScreen(
                    viewModel = rssFeedViewModel
                )
            }
            composable("menu") {
                MenuScreen(
                    onSettingsClick = { navController.navigate("settings") },
                    onOssLicensesClick = {
                        //TODO: Set theme to match app
                        navController.context.startActivity(
                            Intent(
                                navController.context,
                                OssLicensesMenuActivity::class.java
                            )
                        )
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
                    onBackClick = { navController.popBackStack() },
                    fontSize = currentFontSize
                )
            }
            composable("settings") {
                SettingsScreen(
                    currentTheme = currentTheme,
                    onThemeChange = { scope.launch { prefsRepository.setTheme(it) } },
                    currentPalette = currentPalette,
                    onPaletteChange = { scope.launch { prefsRepository.setPalette(it.key) } },
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
    onBackClick: () -> Unit,
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
            Text(stringResource(R.string.article_not_found_label))
        }

        is ArticleUiState.Success -> {
            ReaderScreen(
                state.articleWithFeed,
                onBackClick = onBackClick,
                dictionaryViewModel = dictionaryViewModel,
                fontSize = fontSize
            )
        }
    }
}
