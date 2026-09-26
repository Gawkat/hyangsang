package dev.kettu.hyangsang.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.kettu.hyangsang.data.local.entity.Article
import dev.kettu.hyangsang.data.local.entity.ArticleWithFeed
import dev.kettu.hyangsang.data.repository.ArticleRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// selectedCategory and selectedFeedId are mutually exclusive; both null means all articles
data class ArticleFilterCriteria(
    val selectedCategory: String? = null,
    val selectedFeedId: Long? = null,
    val searchQuery: String = "",
    val showUnreadOnly: Boolean = false
)

sealed class ArticleUiState {
    object Loading : ArticleUiState()
    data class Success(val articleWithFeed: ArticleWithFeed) : ArticleUiState()
    data class Error(val message: String) : ArticleUiState()
}

class ArticleViewModel(private val articleRepository: ArticleRepository) : ViewModel() {
    private val _currentArticleState = MutableStateFlow<ArticleUiState>(ArticleUiState.Loading)
    val currentArticleState: StateFlow<ArticleUiState> = _currentArticleState.asStateFlow()

    private val _filterCriteria = MutableStateFlow(ArticleFilterCriteria())
    val filterCriteria: StateFlow<ArticleFilterCriteria> = _filterCriteria.asStateFlow()

    val allArticles: StateFlow<List<Article>> = articleRepository.getAllArticles()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val allArticlesWithFeed: StateFlow<List<ArticleWithFeed>> =
        combine(
            articleRepository.getAllArticlesWithFeed(),
            _filterCriteria
        ) { articles, criteria ->
            articles.filter { articleWithFeed ->
                val matchesCategory = criteria.selectedCategory == null ||
                        articleWithFeed.feed.category == criteria.selectedCategory
                val matchesFeed = criteria.selectedFeedId == null ||
                        articleWithFeed.feed.id == criteria.selectedFeedId
                val matchesSearch = criteria.searchQuery.isBlank() ||
                        articleWithFeed.article.title.contains(
                            criteria.searchQuery,
                            ignoreCase = true
                        ) ||
                        articleWithFeed.article.description.contains(
                            criteria.searchQuery,
                            ignoreCase = true
                        )
                val matchesUnread =
                    !criteria.showUnreadOnly || articleWithFeed.article.lastReadDate == null

                matchesCategory && matchesFeed && matchesSearch && matchesUnread
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val savedArticlesWithFeed: StateFlow<List<ArticleWithFeed>> =
        articleRepository.getSavedArticlesWithFeed().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Unread article count per feed id
    val unreadCounts: StateFlow<Map<Long, Int>> = articleRepository.getAllArticles()
        .map { articles ->
            articles.filter { it.lastReadDate == null }
                .groupingBy { it.feedId }
                .eachCount()
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap()
        )

    fun updateFilter(criteria: ArticleFilterCriteria) {
        _filterCriteria.value = criteria
    }

    fun setCategory(category: String?) {
        _filterCriteria.value =
            _filterCriteria.value.copy(selectedCategory = category, selectedFeedId = null)
    }

    fun setFeed(feedId: Long?) {
        _filterCriteria.value =
            _filterCriteria.value.copy(selectedCategory = null, selectedFeedId = feedId)
    }

    fun restoreSavedDate(articleId: Long, savedDate: String) {
        viewModelScope.launch {
            articleRepository.restoreSavedDate(articleId, savedDate)
        }
    }

    fun setSaved(articleId: Long, saved: Boolean) {
        viewModelScope.launch {
            articleRepository.setSaved(articleId, saved)
        }
    }

    fun setSearchQuery(query: String) {
        _filterCriteria.value = _filterCriteria.value.copy(searchQuery = query)
    }

    fun setShowUnreadOnly(showUnread: Boolean) {
        _filterCriteria.value = _filterCriteria.value.copy(showUnreadOnly = showUnread)
    }

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

            if (articleWithFeed.article.content.isNullOrEmpty()) {
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
