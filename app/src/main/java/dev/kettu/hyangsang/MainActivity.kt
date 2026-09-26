package dev.kettu.hyangsang

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.annotation.StringRes
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
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
import dev.kettu.hyangsang.ui.navigation.FeedDrawerContent
import dev.kettu.hyangsang.ui.saved.SavedScreen
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
            val showUnreadCounts by prefsRepository.showUnreadCountsFlow.collectAsState(initial = false)

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
                    currentFontSize = fontSize,
                    showUnreadCounts = showUnreadCounts
                )
            }
        }
    }
}

private object Routes {
    const val DISCOVER = "discover"
    const val SAVED = "saved"
    const val FEEDS = "manage_feeds"
    const val SETTINGS = "settings"
    const val READER = "reader/{articleId}"

    fun reader(articleId: Long) = "reader/$articleId"
}

private data class NavItem(
    val route: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
    @param:StringRes val labelRes: Int
)

@Composable
fun MainApp(
    prefsRepository: UserPreferencesRepository,
    articleViewModel: ArticleViewModel,
    dictionaryViewModel: DictionaryViewModel,
    vocabularyViewModel: VocabularyViewModel,
    rssFeedViewModel: RssFeedViewModel,
    currentTheme: String,
    currentPalette: ThemePalette,
    currentFontSize: String,
    showUnreadCounts: Boolean
) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val discoverListState = rememberLazyListState()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val articlesWithFeed by articleViewModel.allArticlesWithFeed.collectAsState()
    val savedArticles by articleViewModel.savedArticlesWithFeed.collectAsState()
    val unreadCounts by articleViewModel.unreadCounts.collectAsState()
    val filterCriteria by articleViewModel.filterCriteria.collectAsState()
    val feeds by rssFeedViewModel.feeds.collectAsState()
    val isRefreshing by rssFeedViewModel.isRefreshing.collectAsState()

    val navItems = listOf(
        NavItem(Routes.DISCOVER, Icons.Outlined.Newspaper, Icons.Filled.Newspaper, R.string.discover_nav),
        NavItem(Routes.SAVED, Icons.Outlined.Bookmarks, Icons.Filled.Bookmarks, R.string.saved_nav)
    )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val isOnDiscover = currentDestination?.route == Routes.DISCOVER

    val allArticlesTitle = stringResource(R.string.all_articles)
    val filterTitle = when {
        filterCriteria.selectedFeedId != null ->
            feeds.firstOrNull { it.id == filterCriteria.selectedFeedId }?.title ?: allArticlesTitle

        else -> filterCriteria.selectedCategory ?: allArticlesTitle
    }

    // Show the top of the list whenever the drawer selection changes
    LaunchedEffect(filterCriteria.selectedCategory, filterCriteria.selectedFeedId) {
        discoverListState.scrollToItem(0)
    }

    val closeDrawerThen: (() -> Unit) -> Unit = { action ->
        scope.launch { drawerState.close() }
        action()
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        // The drawer belongs to Discover; elsewhere it can only be closed
        gesturesEnabled = isOnDiscover || drawerState.isOpen,
        drawerContent = {
            FeedDrawerContent(
                feeds = feeds,
                selectedCategory = filterCriteria.selectedCategory,
                selectedFeedId = filterCriteria.selectedFeedId,
                unreadCounts = unreadCounts.takeIf { showUnreadCounts },
                onSelectAll = { closeDrawerThen { articleViewModel.setCategory(null) } },
                onSelectCategory = { category ->
                    closeDrawerThen { articleViewModel.setCategory(category) }
                },
                onSelectFeed = { feedId -> closeDrawerThen { articleViewModel.setFeed(feedId) } },
                onManageFeedsClick = { closeDrawerThen { navController.navigate(Routes.FEEDS) } },
                onSettingsClick = { closeDrawerThen { navController.navigate(Routes.SETTINGS) } }
            )
        }
    ) {
        BackHandler(enabled = drawerState.isOpen) {
            scope.launch { drawerState.close() }
        }

        Scaffold(
            bottomBar = {
                if (currentDestination?.route in navItems.map { it.route }) {
                    NavigationBar {
                        navItems.forEach { item ->
                            val selected =
                                currentDestination?.hierarchy?.any { it.route == item.route } == true
                            NavigationBarItem(
                                icon = {
                                    Icon(
                                        if (selected) item.selectedIcon else item.icon,
                                        contentDescription = null
                                    )
                                },
                                label = { Text(stringResource(item.labelRes)) },
                                selected = selected,
                                onClick = {
                                    if (selected) {
                                        // Re-tap scrolls Discover back to the top
                                        if (item.route == Routes.DISCOVER) {
                                            scope.launch {
                                                discoverListState.animateScrollToItem(0)
                                            }
                                        }
                                    } else {
                                        navController.navigate(item.route) {
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
            },
            contentWindowInsets = WindowInsets(0, 0, 0, 0)
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = Routes.DISCOVER,
                modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding())
            ) {
                composable(Routes.DISCOVER) {
                    DiscoverScreen(
                        articlesWithFeed = articlesWithFeed,
                        filterTitle = filterTitle,
                        searchQuery = filterCriteria.searchQuery,
                        onSearchQueryChange = { articleViewModel.setSearchQuery(it) },
                        isRefreshing = isRefreshing,
                        onRefresh = { rssFeedViewModel.refreshFeeds() },
                        onArticleClick = { articleId ->
                            navController.navigate(Routes.reader(articleId))
                        },
                        onSaveClick = { articleWithFeed ->
                            articleViewModel.setSaved(
                                articleWithFeed.article.id,
                                articleWithFeed.article.savedDate == null
                            )
                        },
                        onMenuClick = { scope.launch { drawerState.open() } },
                        onSettingsClick = { navController.navigate(Routes.SETTINGS) },
                        listState = discoverListState
                    )
                }
                composable(Routes.SAVED) {
                    SavedScreen(
                        savedArticles = savedArticles,
                        onArticleClick = { articleId ->
                            navController.navigate(Routes.reader(articleId))
                        },
                        onUnsave = { articleId -> articleViewModel.setSaved(articleId, false) }
                    )
                }
                composable(Routes.FEEDS) {
                    FeedsScreen(
                        viewModel = rssFeedViewModel,
                        onBackClick = { navController.popBackStack() }
                    )
                }
                composable(Routes.READER) { backStackEntry ->
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
                composable(Routes.SETTINGS) {
                    val context = LocalContext.current
                    SettingsScreen(
                        currentTheme = currentTheme,
                        onThemeChange = { scope.launch { prefsRepository.setTheme(it) } },
                        currentPalette = currentPalette,
                        onPaletteChange = { scope.launch { prefsRepository.setPalette(it.key) } },
                        currentFontSize = currentFontSize,
                        onFontSizeChange = { scope.launch { prefsRepository.setFontSize(it) } },
                        showUnreadCounts = showUnreadCounts,
                        onShowUnreadCountsChange = {
                            scope.launch { prefsRepository.setShowUnreadCounts(it) }
                        },
                        feeds = feeds,
                        onManageFeedsClick = { navController.navigate(Routes.FEEDS) },
                        onOssLicensesClick = {
                            //TODO: Set theme to match app
                            context.startActivity(
                                Intent(context, OssLicensesMenuActivity::class.java)
                            )
                        },
                        onBackClick = { navController.popBackStack() }
                    )
                }
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
