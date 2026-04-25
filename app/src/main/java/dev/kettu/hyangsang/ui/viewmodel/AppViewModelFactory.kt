package dev.kettu.hyangsang.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dev.kettu.hyangsang.data.repository.ArticleRepository
import dev.kettu.hyangsang.data.repository.DictionaryRepository
import dev.kettu.hyangsang.data.repository.RssFeedRepository
import dev.kettu.hyangsang.data.repository.VocabularyRepository

@Suppress("UNCHECKED_CAST")
class AppViewModelFactory(
    private val articleRepository: ArticleRepository,
    private val dictionaryRepository: DictionaryRepository,
    private val vocabularyRepository: VocabularyRepository,
    private val rssFeedRepository: RssFeedRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(ArticleViewModel::class.java) -> {
                ArticleViewModel(articleRepository) as T
            }

            modelClass.isAssignableFrom(DictionaryViewModel::class.java) -> {
                DictionaryViewModel(dictionaryRepository) as T
            }

            modelClass.isAssignableFrom(VocabularyViewModel::class.java) -> {
                VocabularyViewModel(vocabularyRepository) as T
            }

            modelClass.isAssignableFrom(RssFeedViewModel::class.java) -> {
                RssFeedViewModel(rssFeedRepository) as T
            }

            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
