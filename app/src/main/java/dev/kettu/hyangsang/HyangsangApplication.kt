package dev.kettu.hyangsang

import android.app.Application
import dev.kettu.hyangsang.data.local.HyangsangDatabase
import dev.kettu.hyangsang.data.prefs.UserPreferencesRepository
import dev.kettu.hyangsang.data.repository.ArticleRepository
import dev.kettu.hyangsang.data.repository.DictionaryRepository
import dev.kettu.hyangsang.data.repository.VocabularyRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.openkoreantext.processor.OpenKoreanTextProcessorJava

class HyangsangApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: HyangsangDatabase by lazy { HyangsangDatabase.getDatabase(this) }

    override fun onCreate() {
        super.onCreate()
        
        // Initialize Open Korean Text resources in the background
        applicationScope.launch {
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

    val userPreferencesRepository: UserPreferencesRepository by lazy {
        UserPreferencesRepository(applicationContext)
    }
}
