package dev.kettu.hyangsang

import android.content.Context
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import dev.kettu.hyangsang.analyzer.ContextModel
import dev.kettu.hyangsang.analyzer.DictionaryRow
import dev.kettu.hyangsang.analyzer.Lexicon
import dev.kettu.hyangsang.analyzer.LookupAnalyzer
import dev.kettu.hyangsang.data.defaults.DefaultData
import dev.kettu.hyangsang.data.local.BooksDatabase
import dev.kettu.hyangsang.data.local.DictionaryDatabase
import dev.kettu.hyangsang.data.local.HyangsangDatabase
import dev.kettu.hyangsang.data.prefs.UserPreferencesRepository
import dev.kettu.hyangsang.data.repository.ArticleRepository
import dev.kettu.hyangsang.data.repository.BookRepository
import dev.kettu.hyangsang.data.repository.DictionaryRepository
import dev.kettu.hyangsang.data.repository.KoreanAnalyzer
import dev.kettu.hyangsang.data.repository.LookupReportRepository
import dev.kettu.hyangsang.data.repository.RssFeedRepository
import dev.kettu.hyangsang.data.repository.VocabularyRepository
import dev.kettu.hyangsang.network.RssFeedService
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.io.File

/**
 * Creates the app's shared dependencies, each on first use. The application holds the one
 * instance, which activities, workers and ViewModel factories reach through it.
 */
class AppContainer(private val context: Context) {
    val database: HyangsangDatabase by lazy { HyangsangDatabase.getDatabase(context) }
    val dictionaryDatabase: DictionaryDatabase by lazy { DictionaryDatabase.getDatabase(context) }
    val booksDatabase: BooksDatabase by lazy { BooksDatabase.getDatabase(context) }

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

    val articleRepository: ArticleRepository by lazy {
        ArticleRepository(database.articleDao())
    }

    /**
     * The analyzer word lookups use, built from the installed dictionary on first use. That takes
     * about a second, and reads the dictionary, which Room doesn't allow on the main thread.
     */
    val koreanAnalyzer: Lazy<KoreanAnalyzer> = lazy {
        val rows = dictionaryDatabase.dictionaryDao().getLexiconRows().map {
            DictionaryRow(it.word, it.partOfSpeech, it.vocabularyLevel, it.lexicalUnit)
        }
        KoreanAnalyzer(LookupAnalyzer(Lexicon(rows), ContextModel.bundled()))
    }

    val dictionaryRepository: DictionaryRepository by lazy {
        DictionaryRepository(dictionaryDatabase.dictionaryDao(), koreanAnalyzer)
    }

    val bookRepository: BookRepository by lazy {
        BookRepository(booksDatabase.bookDao(), File(context.filesDir, "books"))
    }

    val lookupReportRepository: LookupReportRepository by lazy {
        LookupReportRepository(File(context.filesDir, "lookup-reports.tsv"))
    }

    val vocabularyRepository:VocabularyRepository by lazy {
        VocabularyRepository(database.vocabularyDao())
    }

    val rssFeedRepository: RssFeedRepository by lazy {
        RssFeedRepository(
            database.rssFeedDao(),
            database.articleDao(),
            rssFeedService,
            resolveDefaultFeeds = { DefaultData.resolveFeeds(context, it) },
            localizeFeeds = { DefaultData.localizeFeeds(context, it) }
        )
    }

    val userPreferencesRepository: UserPreferencesRepository by lazy {
        UserPreferencesRepository(context)
    }
}

/** The container, for the `initializer` blocks of ViewModel factories. */
val CreationExtras.appContainer: AppContainer
    get() = (this[APPLICATION_KEY] as HyangsangApplication).container
