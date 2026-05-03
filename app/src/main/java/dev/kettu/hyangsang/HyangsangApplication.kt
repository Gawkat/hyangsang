package dev.kettu.hyangsang

import android.app.Application
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
            rssFeedService
        )
    }

    val userPreferencesRepository: UserPreferencesRepository by lazy {
        UserPreferencesRepository(applicationContext)
    }
}
