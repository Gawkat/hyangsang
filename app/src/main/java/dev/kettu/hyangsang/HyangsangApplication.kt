package dev.kettu.hyangsang

import android.app.Application
import android.app.UiModeManager
import android.os.Build
import dev.kettu.hyangsang.data.defaults.DefaultData
import dev.kettu.hyangsang.data.local.HyangsangDatabase
import dev.kettu.hyangsang.data.prefs.UserPreferencesRepository
import dev.kettu.hyangsang.data.repository.ArticleRepository
import dev.kettu.hyangsang.data.repository.DictionaryRepository
import dev.kettu.hyangsang.data.repository.RssFeedRepository
import dev.kettu.hyangsang.data.repository.VocabularyRepository
import dev.kettu.hyangsang.network.RssFeedService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.openkoreantext.processor.OpenKoreanTextProcessorJava
import retrofit2.Retrofit

class HyangsangApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: HyangsangDatabase by lazy { HyangsangDatabase.getDatabase(this) }

    private val retrofit by lazy {
        val logging = okhttp3.logging.HttpLoggingInterceptor().apply {
            level = okhttp3.logging.HttpLoggingInterceptor.Level.BASIC
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()

        Retrofit.Builder()
            // Placeholder URL
            .baseUrl("https://www.kettu.dev/")
            .client(client)
            .build()
    }

    val rssFeedService: RssFeedService by lazy {
        retrofit.create(RssFeedService::class.java)
    }

    override fun onCreate() {
        super.onCreate()

        applicationScope.launch {
            // Fetch latest articles from feeds
            rssFeedRepository.refreshEnabledFeeds()

            // Initialize Open Korean Text resources
            OpenKoreanTextProcessorJava.loadResources()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            applicationScope.launch {
                userPreferencesRepository.themeFlow.distinctUntilChanged().collect { theme ->
                    applyApplicationNightMode(theme)
                }
            }
        }
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

    val articleRepository: ArticleRepository by lazy {
        ArticleRepository(database.articleDao())
    }

    val dictionaryRepository: DictionaryRepository by lazy {
        DictionaryRepository(
            database.dictionaryDao()
        )
    }

    val vocabularyRepository: VocabularyRepository by lazy {
        VocabularyRepository(database.vocabularyDao())
    }

    val rssFeedRepository: RssFeedRepository by lazy {
        RssFeedRepository(
            database.rssFeedDao(),
            database.articleDao(),
            rssFeedService,
            defaultFeeds = { DefaultData.resolveFeeds(this) }
        )
    }

    val userPreferencesRepository: UserPreferencesRepository by lazy {
        UserPreferencesRepository(applicationContext)
    }
}
