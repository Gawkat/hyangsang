package dev.kettu.hyangsang.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.kettu.hyangsang.data.defaults.DefaultData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val THEME = stringPreferencesKey("theme")
        val PALETTE = stringPreferencesKey("palette")

        // Replaced by READER_TEXT_SIZE; only read to carry over the old choice
        val LEGACY_FONT_SIZE = stringPreferencesKey("font_size")
        val READER_TEXT_SIZE = intPreferencesKey("reader_text_size")
        val READER_FONT = stringPreferencesKey("reader_font")
        val READER_FONT_WEIGHT = stringPreferencesKey("reader_font_weight")
        val READER_LINE_SPACING = floatPreferencesKey("reader_line_spacing")
        val READER_MARGIN = stringPreferencesKey("reader_margin")
        val READER_BOOK_PARAGRAPHS = stringPreferencesKey("reader_book_paragraphs")
        val READING_SPEED = stringPreferencesKey("reading_speed")
        val SHOW_UNREAD_COUNTS = booleanPreferencesKey("show_unread_counts")
        val CONTENT_RETENTION = stringPreferencesKey("content_retention")
        val FEED_SYNC_INTERVAL = stringPreferencesKey("feed_sync_interval")
        val ARTICLE_RETENTION = stringPreferencesKey("article_retention")
        val FEED_GROUPING = stringPreferencesKey("feed_grouping")
        val DEFAULT_FEEDS_SEEN = intPreferencesKey("default_feeds_seen")
        val FIRST_RUN_SETUP_PENDING = booleanPreferencesKey("first_run_setup_pending")
        val COLLECT_LOOKUP_REPORTS = booleanPreferencesKey("collect_lookup_reports")
        val LIBRARY_TAB = stringPreferencesKey("library_tab")
    }

    val themeFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[Keys.THEME] ?: "System default"
    }

    // Stored as ThemePalette.key
    val paletteFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[Keys.PALETTE] ?: "hyangsang"
    }

    val readerSettingsFlow: Flow<ReaderSettings> = context.dataStore.data.map { preferences ->
        ReaderSettings(
            textSize = preferences[Keys.READER_TEXT_SIZE]
                ?: legacyTextSize(preferences[Keys.LEGACY_FONT_SIZE]),
            font = enumOrDefault(preferences[Keys.READER_FONT], ReaderFont.SANS),
            fontWeight = enumOrDefault(preferences[Keys.READER_FONT_WEIGHT], ReaderFontWeight.REGULAR),
            lineSpacing = preferences[Keys.READER_LINE_SPACING] ?: ReaderSettings.DEFAULT_LINE_SPACING,
            margin = enumOrDefault(preferences[Keys.READER_MARGIN], ReaderMargin.NORMAL),
            bookParagraphs = enumOrDefault(preferences[Keys.READER_BOOK_PARAGRAPHS], BookParagraphs.INDENTED)
        )
    }.distinctUntilChanged()

    val readingSpeedFlow: Flow<ReadingSpeed> = context.dataStore.data.map { preferences ->
        enumOrDefault(preferences[Keys.READING_SPEED], ReadingSpeed.DEFAULT)
    }.distinctUntilChanged()

    suspend fun setReadingSpeed(speed: ReadingSpeed) {
        context.dataStore.edit { it[Keys.READING_SPEED] = speed.name }
    }

    // Off by default: large feeds produce far more articles than anyone reads
    val showUnreadCountsFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[Keys.SHOW_UNREAD_COUNTS] ?: false
    }

    suspend fun setShowUnreadCounts(show: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_UNREAD_COUNTS] = show }
    }

    // Adds a button to the lookup sheet for saving wrong lookups, to improve the dictionary ranking
    val collectLookupReportsFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[Keys.COLLECT_LOOKUP_REPORTS] ?: false
    }.distinctUntilChanged()

    suspend fun setCollectLookupReports(collect: Boolean) {
        context.dataStore.edit { it[Keys.COLLECT_LOOKUP_REPORTS] = collect }
    }

    val feedSyncIntervalFlow: Flow<FeedSyncInterval> = context.dataStore.data.map { preferences ->
        enumOrDefault(preferences[Keys.FEED_SYNC_INTERVAL], FeedSyncInterval.DEFAULT)
    }.distinctUntilChanged()

    suspend fun setFeedSyncInterval(interval: FeedSyncInterval) {
        context.dataStore.edit { it[Keys.FEED_SYNC_INTERVAL] = interval.name }
    }

    val contentRetentionFlow: Flow<ContentRetention> = context.dataStore.data.map { preferences ->
        enumOrDefault(preferences[Keys.CONTENT_RETENTION], ContentRetention.DEFAULT)
    }.distinctUntilChanged()

    suspend fun setContentRetention(retention: ContentRetention) {
        context.dataStore.edit { it[Keys.CONTENT_RETENTION] = retention.name }
    }

    val articleRetentionFlow: Flow<ArticleRetention> = context.dataStore.data.map { preferences ->
        enumOrDefault(preferences[Keys.ARTICLE_RETENTION], ArticleRetention.DEFAULT)
    }.distinctUntilChanged()

    suspend fun setArticleRetention(retention: ArticleRetention) {
        context.dataStore.edit { it[Keys.ARTICLE_RETENTION] = retention.name }
    }

    val feedGroupingFlow: Flow<FeedGrouping> = context.dataStore.data.map { preferences ->
        enumOrDefault(preferences[Keys.FEED_GROUPING], FeedGrouping.CATEGORY)
    }.distinctUntilChanged()

    suspend fun setFeedGrouping(grouping: FeedGrouping) {
        context.dataStore.edit { it[Keys.FEED_GROUPING] = grouping.name }
    }

    val libraryTabFlow: Flow<LibraryTab> = context.dataStore.data.map { preferences ->
        enumOrDefault(preferences[Keys.LIBRARY_TAB], LibraryTab.ARTICLES)
    }.distinctUntilChanged()

    suspend fun setLibraryTab(tab: LibraryTab) {
        context.dataStore.edit { it[Keys.LIBRARY_TAB] = tab.name }
    }

    // The DefaultData.VERSION whose built-in feeds the user has been offered. Unset for users who
    // installed the app before it was stored
    val defaultFeedsSeenFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[Keys.DEFAULT_FEEDS_SEEN] ?: DefaultData.BASELINE_VERSION
    }.distinctUntilChanged()

    suspend fun markDefaultFeedsSeen() = setDefaultFeedsSeen(DefaultData.VERSION)

    // Null clears it, as for users from before it was stored. Only set directly for testing
    suspend fun setDefaultFeedsSeen(version: Int?) {
        context.dataStore.edit {
            if (version == null) it.remove(Keys.DEFAULT_FEEDS_SEEN) else it[Keys.DEFAULT_FEEDS_SEEN] = version
        }
    }

    // Set when the database is created, until the user has picked their feeds. Never set for users
    // who installed the app before the setup existed
    val firstRunSetupPendingFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[Keys.FIRST_RUN_SETUP_PENDING] ?: false
    }.distinctUntilChanged()

    suspend fun setFirstRunSetupPending(pending: Boolean) {
        context.dataStore.edit { it[Keys.FIRST_RUN_SETUP_PENDING] = pending }
    }

    suspend fun setTheme(theme: String) {
        context.dataStore.edit { it[Keys.THEME] = theme }
    }

    suspend fun setPalette(palette: String) {
        context.dataStore.edit { it[Keys.PALETTE] = palette }
    }

    suspend fun setReaderSettings(settings: ReaderSettings) {
        context.dataStore.edit {
            it[Keys.READER_TEXT_SIZE] = settings.textSize
            it[Keys.READER_FONT] = settings.font.name
            it[Keys.READER_FONT_WEIGHT] = settings.fontWeight.name
            it[Keys.READER_LINE_SPACING] = settings.lineSpacing
            it[Keys.READER_MARGIN] = settings.margin.name
            it[Keys.READER_BOOK_PARAGRAPHS] = settings.bookParagraphs.name
        }
    }

    // Sizes the old Small/Medium/Large setting used
    private fun legacyTextSize(fontSize: String?): Int = when (fontSize) {
        "Small" -> 16
        "Large" -> 24
        else -> ReaderSettings.DEFAULT_TEXT_SIZE
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(name: String?, default: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: default
}
