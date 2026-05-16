package dev.kettu.hyangsang.ui.viewmodel

import android.content.Context
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.kettu.hyangsang.R
import dev.kettu.hyangsang.data.local.entity.Article
import dev.kettu.hyangsang.data.local.entity.ArticleWithFeed
import dev.kettu.hyangsang.data.repository.ArticleRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class ArticleUiState {
    object Loading : ArticleUiState()
    data class Success(val articleWithFeed: ArticleWithFeed) : ArticleUiState()
    data class Error(val message: String) : ArticleUiState()
}

class ArticleViewModel(private val articleRepository: ArticleRepository) : ViewModel() {
    private val _currentArticleState = MutableStateFlow<ArticleUiState>(ArticleUiState.Loading)
    val currentArticleState: StateFlow<ArticleUiState> = _currentArticleState.asStateFlow()

    val allArticles: StateFlow<List<Article>> = articleRepository.getAllArticles()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val allArticlesWithFeed: StateFlow<List<ArticleWithFeed>> =
        articleRepository.getAllArticlesWithFeed()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    fun insertArticle(article: Article) {
        viewModelScope.launch {
            articleRepository.insertArticle(article)
        }
    }

    suspend fun getArticleById(id: Long): Article? = articleRepository.getArticleById(id)

    fun loadArticle(articleId: Long) {
        viewModelScope.launch {
            _currentArticleState.value = ArticleUiState.Loading
            var articleWithFeed = articleRepository.getArticleWithFeedById(articleId)

            if (articleWithFeed == null) {
                _currentArticleState.value = ArticleUiState.Error("Article not found")
                return@launch
            }

            if (articleWithFeed.article.content.isNullOrBlank()) {
                val updatedArticle =
                    articleRepository.fetchAndSaveArticleContent(articleWithFeed.article)
                articleWithFeed = articleWithFeed.copy(article = updatedArticle)
            }

            articleRepository.updateProgress(
                id = articleId,
                position = articleWithFeed.article.scrollPosition
            )

            _currentArticleState.value = ArticleUiState.Success(articleWithFeed)
        }
    }
}
