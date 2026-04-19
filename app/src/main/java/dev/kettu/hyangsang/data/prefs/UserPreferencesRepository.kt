package dev.kettu.hyangsang.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val THEME = stringPreferencesKey("theme")
        val FONT_SIZE = stringPreferencesKey("font_size")
        val IS_INITIAL_SEED_DONE = booleanPreferencesKey("is_initial_seed_done")
    }

    val themeFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[Keys.THEME] ?: "System default"
    }

    val fontSizeFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[Keys.FONT_SIZE] ?: "Medium (Default)"
    }

    val isInitialSeedDoneFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[Keys.IS_INITIAL_SEED_DONE] ?: false
    }

    suspend fun setTheme(theme: String) {
        println("Setting theme to $theme")
        context.dataStore.edit { it[Keys.THEME] = theme }
    }

    suspend fun setFontSize(fontSize: String) {
        println("Setting font size to $fontSize")
        context.dataStore.edit { it[Keys.FONT_SIZE] = fontSize }
    }

    suspend fun setInitialSeedDone(done: Boolean) {
        println("Setting initial seed done to $done")
        context.dataStore.edit { it[Keys.IS_INITIAL_SEED_DONE] = done }
    }
}
