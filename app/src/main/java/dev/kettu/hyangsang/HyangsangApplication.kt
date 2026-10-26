package dev.kettu.hyangsang

import android.app.Application
import android.app.UiModeManager
import android.content.res.Configuration
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import dev.kettu.hyangsang.network.withUserAgent
import dev.kettu.hyangsang.sync.FeedSyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.util.concurrent.atomic.AtomicBoolean

class HyangsangApplication : Application(), ImageLoaderFactory {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val container = AppContainer(this)

    // Article images come from the same sites as the articles, so they're requested the same way
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient { OkHttpClient.Builder().withUserAgent().build() }
        .build()

    override fun onCreate() {
        super.onCreate()

        applicationScope.launch {
            // The language may have changed while the app wasn't running
            container.rssFeedRepository.localizeDefaultNames()

            // Fetch latest articles from feeds
            container.rssFeedRepository.refreshEnabledFeeds()
        }

        // Imports the user never finished leave their files behind
        applicationScope.launch {
            container.bookRepository.cleanUp()
        }

        applicationScope.launch {
            container.userPreferencesRepository.feedSyncIntervalFlow.collect { interval ->
                FeedSyncWorker.schedule(this@HyangsangApplication, interval)
            }
        }

        // Runs at startup and again when either setting changes
        applicationScope.launch {
            combine(
                container.userPreferencesRepository.articleRetentionFlow,
                container.userPreferencesRepository.contentRetentionFlow,
                ::Pair
            ).collect { (articleRetention, contentRetention) ->
                container.articleRepository.pruneStorage(articleRetention, contentRetention)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            applicationScope.launch {
                container.userPreferencesRepository.themeFlow
                    .distinctUntilChanged()
                    .collect { theme ->
                    applyApplicationNightMode(theme)
                }
            }
        }
    }

    /**
     * Whether the user still has to pick their feeds. Opening the database first is what marks
     * a new install as pending, so the answer isn't known before then.
     */
    val firstRunSetupPending: Flow<Boolean> = flow {
        withContext(Dispatchers.IO) { container.database.openHelper.writableDatabase }
        emitAll(container.userPreferencesRepository.firstRunSetupPendingFlow)
    }

    private val readerResourcesLoaded = AtomicBoolean(false)

    // Loads what word lookups need ahead of the first one. Called when the UI starts rather than
    // in onCreate, so a background feed sync doesn't load them. Only the first call loads
    fun loadReaderResources() {
        if (!readerResourcesLoaded.compareAndSet(false, true)) return

        // Building the analyzer opens the dictionary, which copies the bundled one on first
        // launch or after an update. Both would otherwise delay the first lookup.
        applicationScope.launch {
            container.koreanAnalyzer.value
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Also runs for other changes, such as rotation, when there's nothing to rename
        applicationScope.launch { container.rssFeedRepository.localizeDefaultNames() }
    }

    /**
     * Stores the theme setting as the per-app night mode, which the system uses when drawing
     * the launch splash screen before the app starts. Without it, the splash follows the
     * system theme and flashes light when the app is set to Dark (and vice versa).
     */
    private fun applyApplicationNightMode(theme: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val mode = when (theme) {
            "Light" -> UiModeManager.MODE_NIGHT_NO
            "Dark" -> UiModeManager.MODE_NIGHT_YES
            else -> UiModeManager.MODE_NIGHT_AUTO
        }
        getSystemService(UiModeManager::class.java).setApplicationNightMode(mode)
    }
}
