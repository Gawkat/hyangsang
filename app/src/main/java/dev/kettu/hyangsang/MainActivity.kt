package dev.kettu.hyangsang

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.StringRes
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.google.android.gms.oss.licenses.v2.OssLicensesMenuActivity
import dev.kettu.hyangsang.data.defaults.DefaultCategory
import dev.kettu.hyangsang.data.prefs.ReaderSettings
import dev.kettu.hyangsang.data.prefs.UserPreferencesRepository
import dev.kettu.hyangsang.ui.discover.DiscoverScreen
import dev.kettu.hyangsang.ui.feeds.FeedsScreen
import dev.kettu.hyangsang.ui.navigation.FeedDrawerContent
import dev.kettu.hyangsang.ui.reader.ReaderScreen
import dev.kettu.hyangsang.ui.saved.SavedScreen
import dev.kettu.hyangsang.ui.settings.SettingsScreen
import dev.kettu.hyangsang.ui.settings.TextLayoutScreen
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
            val readerSettings by prefsRepository.readerSettingsFlow.collectAsState(initial = ReaderSettings())
            val showUnreadCounts by prefsRepository.showUnreadCountsFlow.collectAsState(initial = false)

            val darkTheme = when (theme) {
                "Light" -> false
                "Dark" -> true
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }

            // System bar icons must follow the app theme, not the system one
            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) {
                        darkTheme
                    },
                    navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { darkTheme }
                )
                onDispose {}
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
                    readerSettings = readerSettings,
                    showUnreadCounts = showUnreadCounts
                )
            }
        }
    }

    private companion object {
        // Same scrims enableEdgeToEdge() uses by default for 3-button navigation
        val LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        val DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}

private object Routes {
    const val DISCOVER = "discover"
    const val SAVED = "saved"
    const val FEEDS = "manage_feeds"
    const val SETTINGS = "settings"
    const val TEXT_LAYOUT = "text_layout"
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
    readerSettings: ReaderSettings,
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
        NavItem(
            Routes.DISCOVER,
            Icons.Outlined.Newspaper,
            Icons.Filled.Newspaper,
            R.string.discover_nav
        ),
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

    // Built-in categories are renamed when the app language changes, so the selection follows
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    LaunchedEffect(filterCriteria.selectedCategory, configuration) {
        val selected = filterCriteria.selectedCategory ?: return@LaunchedEffect
        val renamed =
            DefaultCategory.byLabel(context)[selected]?.let { context.getString(it.label) }
        if (renamed != null && renamed != selected) articleViewModel.setCategory(renamed)
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
                        showUnreadOnly = filterCriteria.showUnreadOnly,
                        onShowUnreadOnlyChange = { articleViewModel.setShowUnreadOnly(it) },
                        feeds = feeds,
                        onFeedStatusClick = { navController.navigate(Routes.FEEDS) },
                        listState = discoverListState
                    )
                }
                composable(Routes.SAVED) {
                    SavedScreen(
                        savedArticles = savedArticles,
                        onArticleClick = { articleId ->
                            navController.navigate(Routes.reader(articleId))
                        },
                        onUnsave = { articleId -> articleViewModel.setSaved(articleId, false) },
                        onUndoUnsave = { articleId, savedDate ->
                            articleViewModel.restoreSavedDate(articleId, savedDate)
                        }
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
                    ReaderRoute(
                        articleId = articleId,
                        articleViewModel = articleViewModel,
                        dictionaryViewModel = dictionaryViewModel,
                        onBackClick = { navController.popBackStack() },
                        readerSettings = readerSettings,
                        onReaderSettingsChange = {
                            scope.launch { prefsRepository.setReaderSettings(it) }
                        },
                        currentTheme = currentTheme,
                        onThemeChange = { scope.launch { prefsRepository.setTheme(it) } },
                        onMoreTextSettingsClick = { navController.navigate(Routes.TEXT_LAYOUT) }
                    )
                }
                composable(Routes.TEXT_LAYOUT) {
                    TextLayoutScreen(
                        settings = readerSettings,
                        onSettingsChange = { scope.launch { prefsRepository.setReaderSettings(it) } },
                        onBackClick = { navController.popBackStack() }
                    )
                }
                composable(Routes.SETTINGS) {
                    val colorScheme = MaterialTheme.colorScheme
                    val typography = MaterialTheme.typography
                    SettingsScreen(
                        currentTheme = currentTheme,
                        onThemeChange = { scope.launch { prefsRepository.setTheme(it) } },
                        currentPalette = currentPalette,
                        onPaletteChange = { scope.launch { prefsRepository.setPalette(it.key) } },
                        readerSettings = readerSettings,
                        onTextLayoutClick = { navController.navigate(Routes.TEXT_LAYOUT) },
                        showUnreadCounts = showUnreadCounts,
                        onShowUnreadCountsChange = {
                            scope.launch { prefsRepository.setShowUnreadCounts(it) }
                        },
                        feeds = feeds,
                        onManageFeedsClick = { navController.navigate(Routes.FEEDS) },
                        onOssLicensesClick = {
                            OssLicensesMenuActivity.setTheme(colorScheme, colorScheme, typography)
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
fun ReaderRoute(
    articleId: Long?,
    articleViewModel: ArticleViewModel,
    dictionaryViewModel: DictionaryViewModel,
    onBackClick: () -> Unit,
    readerSettings: ReaderSettings,
    onReaderSettingsChange: (ReaderSettings) -> Unit,
    currentTheme: String,
    onThemeChange: (String) -> Unit,
    onMoreTextSettingsClick: () -> Unit
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
            val article = state.articleWithFeed.article
            ReaderScreen(
                state.articleWithFeed,
                onBackClick = onBackClick,
                dictionaryViewModel = dictionaryViewModel,
                readerSettings = readerSettings,
                onReaderSettingsChange = onReaderSettingsChange,
                currentTheme = currentTheme,
                onThemeChange = onThemeChange,
                onSaveToggle = { saved -> articleViewModel.setSaved(article.id, saved) },
                onMoreTextSettingsClick = onMoreTextSettingsClick
            )
        }
    }
}
