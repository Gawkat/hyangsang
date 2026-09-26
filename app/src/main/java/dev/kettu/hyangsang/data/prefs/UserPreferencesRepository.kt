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
        val SHOW_UNREAD_COUNTS = booleanPreferencesKey("show_unread_counts")
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
            margin = enumOrDefault(preferences[Keys.READER_MARGIN], ReaderMargin.NORMAL)
        )
    }.distinctUntilChanged()

    // Off by default: large feeds produce far more articles than anyone reads
    val showUnreadCountsFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[Keys.SHOW_UNREAD_COUNTS] ?: false
    }

    suspend fun setShowUnreadCounts(show: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_UNREAD_COUNTS] = show }
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
