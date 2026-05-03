package dev.kettu.hyangsang.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.kettu.hyangsang.data.local.entity.Article
import dev.kettu.hyangsang.data.repository.ArticleRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class ArticleUiState {
    object Loading : ArticleUiState()
    data class Success(val article: Article) : ArticleUiState()
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

    fun insertArticle(article: Article) {
        viewModelScope.launch {
            articleRepository.insertArticle(article)
        }
    }

    suspend fun getArticleById(id: Long): Article? = articleRepository.getArticleById(id)

    fun loadArticle(articleId: Long) {
        viewModelScope.launch {
            _currentArticleState.value = ArticleUiState.Loading
            val article = articleRepository.getArticleById(articleId)

            if (article != null) {
                if (article.content.isNullOrBlank()) {
                    val updated = articleRepository.fetchAndSaveArticleContent(article)
                    _currentArticleState.value = ArticleUiState.Success(updated)
                } else {
                    _currentArticleState.value = ArticleUiState.Success(article)
                }
            } else {
                _currentArticleState.value = ArticleUiState.Error("Article not found")
            }
        }
    }
}
