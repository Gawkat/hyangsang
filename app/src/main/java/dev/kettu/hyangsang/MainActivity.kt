package dev.kettu.hyangsang

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalLibrary
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.outlined.LocalLibrary
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.IntentCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.kettu.hyangsang.data.defaults.DefaultCategory
import dev.kettu.hyangsang.data.importing.SharedText
import dev.kettu.hyangsang.data.prefs.ArticleRetention
import dev.kettu.hyangsang.data.prefs.ContentRetention
import dev.kettu.hyangsang.data.prefs.FeedGrouping
import dev.kettu.hyangsang.data.prefs.FeedSyncInterval
import dev.kettu.hyangsang.data.prefs.LibraryTab
import dev.kettu.hyangsang.data.prefs.ReaderSettings
import dev.kettu.hyangsang.data.prefs.UserPreferencesRepository
import dev.kettu.hyangsang.data.repository.LookupReportRepository
import dev.kettu.hyangsang.parser.PlainText
import dev.kettu.hyangsang.ui.discover.DiscoverScreen
import dev.kettu.hyangsang.ui.feeds.FeedsScreen
import dev.kettu.hyangsang.ui.importing.AddTextScreen
import dev.kettu.hyangsang.ui.library.BookDetailsScreen
import dev.kettu.hyangsang.ui.library.ImportBookSheet
import dev.kettu.hyangsang.ui.library.LibraryScreen
import dev.kettu.hyangsang.ui.library.displayName
import dev.kettu.hyangsang.ui.navigation.FeedDrawerContent
import dev.kettu.hyangsang.ui.reader.BookReaderScreen
import dev.kettu.hyangsang.ui.reader.ReaderScreen
import dev.kettu.hyangsang.ui.settings.LicensesScreen
import dev.kettu.hyangsang.ui.settings.SettingsScreen
import dev.kettu.hyangsang.ui.settings.TextLayoutScreen
import dev.kettu.hyangsang.ui.setup.SetupScreen
import dev.kettu.hyangsang.ui.theme.HyangsangTheme
import dev.kettu.hyangsang.ui.theme.ThemePalette
import dev.kettu.hyangsang.ui.viewmodel.ArticleUiState
import dev.kettu.hyangsang.ui.viewmodel.ArticleViewModel
import dev.kettu.hyangsang.ui.viewmodel.BookReaderViewModel
import dev.kettu.hyangsang.ui.viewmodel.BooksViewModel
import dev.kettu.hyangsang.ui.viewmodel.DictionaryViewModel
import dev.kettu.hyangsang.ui.viewmodel.RssFeedViewModel
import dev.kettu.hyangsang.ui.viewmodel.VocabularyViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    // A link or text shared from another app, until the main screens have opened it
    private val sharedText = MutableStateFlow<SharedText?>(null)

    // An EPUB file opened or shared from another app, until the Library has started importing it
    private val sharedBook = MutableStateFlow<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A recreated activity has already handled the share that started it
        if (savedInstanceState == null) readShare(intent)
        enableEdgeToEdge()
        val app = application as HyangsangApplication
        app.loadReaderResources()
        val prefsRepository = app.container.userPreferencesRepository

        setContent {
            val theme by prefsRepository.themeFlow.collectAsState(initial = "System default")
            val paletteKey by prefsRepository.paletteFlow.collectAsState(initial = "hyangsang")
            val palette = ThemePalette.fromKey(paletteKey)
            val readerSettings by prefsRepository.readerSettingsFlow.collectAsState(initial = ReaderSettings())
            val showUnreadCounts by prefsRepository.showUnreadCountsFlow.collectAsState(initial = false)
            val feedSyncInterval by prefsRepository.feedSyncIntervalFlow
                .collectAsState(initial = FeedSyncInterval.DEFAULT)
            val articleRetention by prefsRepository.articleRetentionFlow
                .collectAsState(initial = ArticleRetention.DEFAULT)
            val contentRetention by prefsRepository.contentRetentionFlow
                .collectAsState(initial = ContentRetention.DEFAULT)
            val feedGrouping by prefsRepository.feedGroupingFlow
                .collectAsState(initial = FeedGrouping.CATEGORY)

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

            val articleViewModel: ArticleViewModel = viewModel(factory = ArticleViewModel.Factory)

            val dictionaryViewModel: DictionaryViewModel = viewModel(
                factory = DictionaryViewModel.Factory
            )

            val vocabularyViewModel: VocabularyViewModel = viewModel(
                factory = VocabularyViewModel.Factory
            )

            val feedViewModel: RssFeedViewModel = viewModel(factory = RssFeedViewModel.Factory)

            val booksViewModel: BooksViewModel = viewModel(factory = BooksViewModel.Factory)

            // Null until the database is open. Once shown, the setup stays until the user leaves
            // it, although it stops being pending as soon as the feeds are added
            val setupPending by remember { app.firstRunSetupPending }.collectAsState(initial = null)
            var inSetup by rememberSaveable { mutableStateOf(false) }
            LaunchedEffect(setupPending) {
                if (setupPending == true) inSetup = true
            }

            HyangsangTheme(darkTheme = darkTheme, palette = palette) {
                when {
                    inSetup || setupPending == true -> SetupScreen(
                        dictionaryViewModel = dictionaryViewModel,
                        onAddFeeds = feedViewModel::finishSetup,
                        onFinish = { inSetup = false }
                    )

                    setupPending == null -> Surface(modifier = Modifier.fillMaxSize()) {}

                    else -> MainApp(
                        sharedText = sharedText.collectAsState().value,
                        onSharedTextHandled = { sharedText.value = null },
                        sharedBook = sharedBook.collectAsState().value,
                        onSharedBookHandled = { sharedBook.value = null },
                        booksViewModel = booksViewModel,
                        prefsRepository = prefsRepository,
                        lookupReportRepository = app.container.lookupReportRepository,
                        articleViewModel = articleViewModel,
                        dictionaryViewModel = dictionaryViewModel,
                        vocabularyViewModel = vocabularyViewModel,
                        rssFeedViewModel = feedViewModel,
                        currentTheme = theme,
                        currentPalette = palette,
                        readerSettings = readerSettings,
                        showUnreadCounts = showUnreadCounts,
                        feedSyncInterval = feedSyncInterval,
                        articleRetention = articleRetention,
                        contentRetention = contentRetention,
                        feedGrouping = feedGrouping
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readShare(intent)
    }

    private fun readShare(intent: Intent) {
        readBook(intent)?.let {
            sharedBook.value = it
            return
        }
        if (intent.action != Intent.ACTION_SEND) return
        val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
        if (text.isNullOrBlank()) return
        sharedText.value = SharedText.classify(text, intent.getStringExtra(Intent.EXTRA_SUBJECT))
    }

    // An EPUB file opened with the app, or shared with it
    private fun readBook(intent: Intent): Uri? {
        if (intent.type != EPUB_TYPE) return null
        return when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            else -> null
        }
    }

    private companion object {
        const val EPUB_TYPE = "application/epub+zip"

        // Same scrims enableEdgeToEdge() uses by default for 3-button navigation
        val LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        val DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}

private object Routes {
    const val DISCOVER = "discover"
    const val LIBRARY = "library"
    const val FEEDS = "manage_feeds"
    const val SETTINGS = "settings"
    const val TEXT_LAYOUT = "text_layout"
    const val LICENSES = "licenses"
    const val ADD_TEXT = "add_text"
    const val READER = "reader/{articleId}"
    const val BOOK = "book/{bookId}"
    const val BOOK_READER = "book-reader/{bookId}"

    fun bookReader(bookId: Long) = "book-reader/$bookId"

    fun book(bookId: Long) = "book/$bookId"

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
    sharedText: SharedText?,
    onSharedTextHandled: () -> Unit,
    sharedBook: Uri?,
    onSharedBookHandled: () -> Unit,
    booksViewModel: BooksViewModel,
    prefsRepository: UserPreferencesRepository,
    lookupReportRepository: LookupReportRepository,
    articleViewModel: ArticleViewModel,
    dictionaryViewModel: DictionaryViewModel,
    vocabularyViewModel: VocabularyViewModel,
    rssFeedViewModel: RssFeedViewModel,
    currentTheme: String,
    currentPalette: ThemePalette,
    readerSettings: ReaderSettings,
    showUnreadCounts: Boolean,
    feedSyncInterval: FeedSyncInterval,
    articleRetention: ArticleRetention,
    contentRetention: ContentRetention,
    feedGrouping: FeedGrouping
) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val collectLookupReports by prefsRepository.collectLookupReportsFlow.collectAsState(initial = false)
    val discoverListState = rememberLazyListState()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val articlesWithFeed by articleViewModel.allArticlesWithFeed.collectAsState()
    val savedArticles by articleViewModel.savedArticlesWithFeed.collectAsState()
    val savedImportedOnly by articleViewModel.savedImportedOnly.collectAsState()
    val libraryTab by prefsRepository.libraryTabFlow.collectAsState(initial = LibraryTab.ARTICLES)
    val books by booksViewModel.books.collectAsState()
    val bookSort by booksViewModel.sort.collectAsState()
    val bookImportState by booksViewModel.importState.collectAsState()
    val context = LocalContext.current
    val importBook: (Uri) -> Unit = { uri ->
        val resolver = context.contentResolver
        booksViewModel.startImport(
            open = { resolver.openInputStream(uri) },
            fileName = { resolver.displayName(uri) }
        )
    }
    // Some apps don't know the EPUB type, so other files are offered too, and the parser decides
    val pickBook = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(importBook)
    }
    // What the Add text screen starts with: empty from the Library, or text shared from another app
    var addTextDraft by remember { mutableStateOf(SharedText.Text("", null)) }
    val unreadCounts by articleViewModel.unreadCounts.collectAsState()
    val filterCriteria by articleViewModel.filterCriteria.collectAsState()
    val feeds by rssFeedViewModel.feeds.collectAsState()
    val newDefaultFeeds by rssFeedViewModel.newDefaultFeeds.collectAsState()
    val isRefreshing by rssFeedViewModel.isRefreshing.collectAsState()

    val navItems = listOf(
        NavItem(
            Routes.DISCOVER,
            Icons.Outlined.Newspaper,
            Icons.Filled.Newspaper,
            R.string.discover_nav
        ),
        NavItem(
            Routes.LIBRARY,
            Icons.Outlined.LocalLibrary,
            Icons.Filled.LocalLibrary,
            R.string.library_nav
        )
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

    // A shared link opens in the reader; shared text can be checked before it's added
    LaunchedEffect(sharedText) {
        when (val shared = sharedText ?: return@LaunchedEffect) {
            is SharedText.Link -> navController.navigate(
                Routes.reader(articleViewModel.importLink(shared.url, shared.title))
            )

            is SharedText.Text -> {
                addTextDraft = shared
                navController.navigate(Routes.ADD_TEXT) {
                    // A second share replaces a draft that wasn't added
                    popUpTo(Routes.ADD_TEXT) { inclusive = true }
                }
            }
        }
        onSharedTextHandled()
    }

    // An opened or shared book is imported from the Books tab of the Library
    LaunchedEffect(sharedBook) {
        val uri = sharedBook ?: return@LaunchedEffect
        prefsRepository.setLibraryTab(LibraryTab.BOOKS)
        navController.navigate(Routes.LIBRARY) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
        }
        importBook(uri)
        onSharedBookHandled()
    }

    val untitledBook = stringResource(R.string.untitled_book)
    bookImportState?.let { state ->
        ImportBookSheet(
            state = state,
            coverFile = booksViewModel::coverFile,
            onImport = {
                booksViewModel.confirmImport(untitledBook) { bookId ->
                    navController.navigate(Routes.bookReader(bookId))
                }
            },
            onOpenExisting = { bookId ->
                booksViewModel.cancelImport()
                navController.navigate(Routes.book(bookId))
            },
            onDismiss = booksViewModel::cancelImport
        )
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
                hasNewDefaultFeeds = newDefaultFeeds.isNotEmpty(),
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
                composable(Routes.DISCOVER) { entry ->
                    DiscoverScreen(
                        articlesWithFeed = articlesWithFeed,
                        filterTitle = filterTitle,
                        searchQuery = filterCriteria.searchQuery,
                        onSearchQueryChange = { articleViewModel.setSearchQuery(it) },
                        isRefreshing = isRefreshing,
                        onRefresh = { rssFeedViewModel.refreshFeeds() },
                        onArticleClick = { articleId ->
                            entry.ifResumed { navController.navigate(Routes.reader(articleId)) }
                        },
                        onSaveClick = { articleWithFeed ->
                            articleViewModel.setSaved(
                                articleWithFeed.article.id,
                                articleWithFeed.article.savedDate == null
                            )
                        },
                        onMenuClick = { scope.launch { drawerState.open() } },
                        onSettingsClick = {
                            entry.ifResumed { navController.navigate(Routes.SETTINGS) }
                        },
                        showUnreadOnly = filterCriteria.showUnreadOnly,
                        onShowUnreadOnlyChange = { articleViewModel.setShowUnreadOnly(it) },
                        feeds = feeds,
                        onFeedStatusClick = {
                            entry.ifResumed { navController.navigate(Routes.FEEDS) }
                        },
                        listState = discoverListState
                    )
                }
                composable(Routes.LIBRARY) { entry ->
                    LibraryScreen(
                        selectedTab = libraryTab,
                        onTabSelect = { scope.launch { prefsRepository.setLibraryTab(it) } },
                        savedArticles = savedArticles,
                        importedOnly = savedImportedOnly,
                        onImportedOnlyChange = articleViewModel::setSavedImportedOnly,
                        onAddTextClick = {
                            entry.ifResumed {
                                addTextDraft = SharedText.Text("", null)
                                navController.navigate(Routes.ADD_TEXT)
                            }
                        },
                        onArticleClick = { articleId ->
                            entry.ifResumed { navController.navigate(Routes.reader(articleId)) }
                        },
                        onUnsave = { articleId -> articleViewModel.setSaved(articleId, false) },
                        onUndoUnsave = { articleId, savedDate ->
                            articleViewModel.restoreSavedDate(articleId, savedDate)
                        },
                        books = books,
                        bookSort = bookSort,
                        onBookSortChange = booksViewModel::setSort,
                        coverFile = booksViewModel::coverFile,
                        onBookClick = { bookId ->
                            entry.ifResumed { navController.navigate(Routes.bookReader(bookId)) }
                        },
                        onBookOptionsClick = { bookId ->
                            entry.ifResumed { navController.navigate(Routes.book(bookId)) }
                        },
                        onAddBookClick = {
                            pickBook.launch(arrayOf("application/epub+zip", "application/octet-stream"))
                        }
                    )
                }
                composable(Routes.BOOK_READER) { entry ->
                    val bookId = entry.arguments?.getString("bookId")?.toLongOrNull() ?: return@composable
                    BookReaderScreen(
                        viewModel = viewModel(key = "book-$bookId", factory = BookReaderViewModel.factory(bookId)),
                        dictionaryViewModel = dictionaryViewModel,
                        readerSettings = readerSettings,
                        onReaderSettingsChange = { scope.launch { prefsRepository.setReaderSettings(it) } },
                        currentTheme = currentTheme,
                        onThemeChange = { scope.launch { prefsRepository.setTheme(it) } },
                        onMoreTextSettingsClick = {
                            entry.ifResumed { navController.navigate(Routes.TEXT_LAYOUT) }
                        },
                        onDetailsClick = { entry.ifResumed { navController.navigate(Routes.book(bookId)) } },
                        onBackClick = { entry.ifResumed { navController.popBackStack() } },
                        collectLookupReports = collectLookupReports
                    )
                }
                composable(Routes.BOOK) { entry ->
                    val bookId = entry.arguments?.getString("bookId")?.toLongOrNull() ?: return@composable
                    val book by remember(bookId) { booksViewModel.getBook(bookId) }.collectAsState(initial = null)
                    book?.let { current ->
                        BookDetailsScreen(
                            book = current,
                            coverFile = booksViewModel.coverFile(current),
                            onTitleAndAuthorChange = { title, author ->
                                booksViewModel.updateTitleAndAuthor(current.id, title, author)
                            },
                            onReadClick = {
                                entry.ifResumed {
                                    navController.navigate(Routes.bookReader(current.id)) {
                                        // From the reader's menu, go back to it rather than open another
                                        popUpTo(Routes.BOOK_READER) { inclusive = true }
                                    }
                                }
                            },
                            onDelete = {
                                entry.ifResumed {
                                    booksViewModel.delete(current)
                                    navController.popBackStack()
                                }
                            },
                            onBackClick = { entry.ifResumed { navController.popBackStack() } }
                        )
                    }
                }
                composable(Routes.FEEDS) { entry ->
                    FeedsScreen(
                        viewModel = rssFeedViewModel,
                        grouping = feedGrouping,
                        onGroupingChange = { scope.launch { prefsRepository.setFeedGrouping(it) } },
                        onBackClick = { entry.ifResumed { navController.popBackStack() } }
                    )
                }
                composable(Routes.READER) { entry ->
                    val articleIdStr = entry.arguments?.getString("articleId")
                    val articleId = articleIdStr?.toLongOrNull()
                    ReaderRoute(
                        articleId = articleId,
                        articleViewModel = articleViewModel,
                        dictionaryViewModel = dictionaryViewModel,
                        onBackClick = { entry.ifResumed { navController.popBackStack() } },
                        readerSettings = readerSettings,
                        onReaderSettingsChange = {
                            scope.launch { prefsRepository.setReaderSettings(it) }
                        },
                        currentTheme = currentTheme,
                        onThemeChange = { scope.launch { prefsRepository.setTheme(it) } },
                        onMoreTextSettingsClick = {
                            entry.ifResumed { navController.navigate(Routes.TEXT_LAYOUT) }
                        },
                        collectLookupReports = collectLookupReports
                    )
                }
                composable(Routes.TEXT_LAYOUT) { entry ->
                    TextLayoutScreen(
                        settings = readerSettings,
                        onSettingsChange = { scope.launch { prefsRepository.setReaderSettings(it) } },
                        onBackClick = { entry.ifResumed { navController.popBackStack() } }
                    )
                }
                composable(Routes.ADD_TEXT) { entry ->
                    // Without a title from the share, a short first line becomes one
                    val (initialTitle, initialText) = remember(addTextDraft) {
                        addTextDraft.title?.let { it to addTextDraft.text }
                            ?: PlainText.splitTitle(addTextDraft.text)
                    }
                    AddTextScreen(
                        initialTitle = initialTitle.orEmpty(),
                        initialText = initialText,
                        onAdd = { title, text ->
                            entry.ifResumed {
                                scope.launch {
                                    // A link on its own is downloaded rather than kept as text
                                    val link = (SharedText.classify(text) as? SharedText.Link)
                                        ?.takeIf { it.url == text.trim() }
                                    val articleId = if (link != null) {
                                        articleViewModel.importLink(link.url, title.ifBlank { null })
                                    } else {
                                        articleViewModel.importText(title, text)
                                    }
                                    navController.navigate(Routes.reader(articleId)) {
                                        popUpTo(Routes.ADD_TEXT) { inclusive = true }
                                    }
                                }
                            }
                        },
                        onBackClick = { entry.ifResumed { navController.popBackStack() } }
                    )
                }
                composable(Routes.LICENSES) { entry ->
                    LicensesScreen(
                        onBackClick = { entry.ifResumed { navController.popBackStack() } }
                    )
                }
                composable(Routes.SETTINGS) { entry ->
                    val context = LocalContext.current
                    val lookupReportCount by lookupReportRepository.reportCount.collectAsState(initial = 0)
                    SettingsScreen(
                        currentTheme = currentTheme,
                        onThemeChange = { scope.launch { prefsRepository.setTheme(it) } },
                        currentPalette = currentPalette,
                        onPaletteChange = { scope.launch { prefsRepository.setPalette(it.key) } },
                        readerSettings = readerSettings,
                        onTextLayoutClick = {
                            entry.ifResumed { navController.navigate(Routes.TEXT_LAYOUT) }
                        },
                        showUnreadCounts = showUnreadCounts,
                        onShowUnreadCountsChange = {
                            scope.launch { prefsRepository.setShowUnreadCounts(it) }
                        },
                        feedSyncInterval = feedSyncInterval,
                        onFeedSyncIntervalChange = {
                            scope.launch { prefsRepository.setFeedSyncInterval(it) }
                        },
                        articleRetention = articleRetention,
                        onArticleRetentionChange = {
                            scope.launch { prefsRepository.setArticleRetention(it) }
                        },
                        contentRetention = contentRetention,
                        onContentRetentionChange = {
                            scope.launch { prefsRepository.setContentRetention(it) }
                        },
                        collectLookupReports = collectLookupReports,
                        onCollectLookupReportsChange = {
                            scope.launch { prefsRepository.setCollectLookupReports(it) }
                        },
                        lookupReportCount = lookupReportCount,
                        onShareLookupReports = {
                            scope.launch {
                                val reports = lookupReportRepository.export() ?: return@launch
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.lookup_reports_share_subject))
                                    putExtra(Intent.EXTRA_TEXT, reports)
                                }
                                context.startActivity(Intent.createChooser(send, null))
                            }
                        },
                        onDeleteLookupReports = { scope.launch { lookupReportRepository.clear() } },
                        feeds = feeds,
                        hasNewDefaultFeeds = newDefaultFeeds.isNotEmpty(),
                        onManageFeedsClick = {
                            entry.ifResumed { navController.navigate(Routes.FEEDS) }
                        },
                        onOssLicensesClick = {
                            entry.ifResumed { navController.navigate(Routes.LICENSES) }
                        },
                        onBackClick = { entry.ifResumed { navController.popBackStack() } }
                    )
                }
            }
        }
    }
}

// A screen still animating in or out isn't resumed; dropping its clicks stops a double tap from
// navigating twice, e.g. popping the start destination after the first back already popped Settings
private inline fun NavBackStackEntry.ifResumed(action: () -> Unit) {
    if (lifecycle.currentState == Lifecycle.State.RESUMED) action()
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
    onMoreTextSettingsClick: () -> Unit,
    collectLookupReports: Boolean
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
                onMoreTextSettingsClick = onMoreTextSettingsClick,
                isRefreshing = state.isRefreshing,
                refreshResult = state.refreshResult,
                onRefreshClick = articleViewModel::refreshArticle,
                onRefreshResultShown = articleViewModel::onRefreshResultShown,
                downloadFailure = state.downloadFailure,
                onRetryDownloadClick = articleViewModel::retryDownload,
                collectLookupReports = collectLookupReports
            )
        }
    }
}
