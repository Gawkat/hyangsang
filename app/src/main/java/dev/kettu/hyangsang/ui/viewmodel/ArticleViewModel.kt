package dev.kettu.hyangsang.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.kettu.hyangsang.appContainer
import dev.kettu.hyangsang.data.local.entity.Article
import dev.kettu.hyangsang.data.local.entity.ArticleSummaryWithFeed
import dev.kettu.hyangsang.data.local.entity.ArticleWithFeed
import dev.kettu.hyangsang.data.repository.ArticleRepository
import dev.kettu.hyangsang.data.repository.ContentDownload
import dev.kettu.hyangsang.data.repository.ContentRefresh
import dev.kettu.hyangsang.data.repository.DownloadFailure
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
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
    data class Success(
        val articleWithFeed: ArticleWithFeed,
        val isRefreshing: Boolean = false,
        // Outcome of the last reload, until the reader has shown it
        val refreshResult: ContentRefresh? = null,
        // Why the article has no content, if downloading it failed
        val downloadFailure: DownloadFailure? = null
    ) : ArticleUiState()
    data class Error(val message: String) : ArticleUiState()
}

class ArticleViewModel(private val articleRepository: ArticleRepository) : ViewModel() {
    private val _currentArticleState = MutableStateFlow<ArticleUiState>(ArticleUiState.Loading)
    val currentArticleState: StateFlow<ArticleUiState> = _currentArticleState.asStateFlow()
    private var refreshJob: Job? = null

    private val _filterCriteria = MutableStateFlow(ArticleFilterCriteria())
    val filterCriteria: StateFlow<ArticleFilterCriteria> = _filterCriteria.asStateFlow()

    // Filtered by the database, so changing the filter runs a new query
    @OptIn(ExperimentalCoroutinesApi::class)
    val allArticlesWithFeed: StateFlow<List<ArticleSummaryWithFeed>> = _filterCriteria
        .flatMapLatest { criteria ->
            articleRepository.getArticleSummaries(
                category = criteria.selectedCategory,
                feedId = criteria.selectedFeedId,
                searchQuery = criteria.searchQuery,
                unreadOnly = criteria.showUnreadOnly
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _savedImportedOnly = MutableStateFlow(false)
    val savedImportedOnly: StateFlow<Boolean> = _savedImportedOnly.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val savedArticlesWithFeed: StateFlow<List<ArticleSummaryWithFeed>> = _savedImportedOnly
        .flatMapLatest { articleRepository.getSavedArticleSummaries(importedOnly = it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Unread article count per feed id
    val unreadCounts: StateFlow<Map<Long, Int>> = articleRepository.getUnreadCounts()
        .stateIn(
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

    fun setSavedImportedOnly(importedOnly: Boolean) {
        _savedImportedOnly.value = importedOnly
    }

    // Both return the id of the article to open
    suspend fun importLink(url: String, title: String?): Long =
        articleRepository.importLink(url, title)

    suspend fun importText(title: String, text: String): Long =
        articleRepository.importText(title, text)

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

            var downloadFailure: DownloadFailure? = null
            if (articleWithFeed.article.content.isNullOrEmpty()) {
                when (val download =
                    articleRepository.fetchAndSaveArticleContent(articleWithFeed.article)) {
                    is ContentDownload.Done ->
                        articleWithFeed = articleWithFeed.copy(article = download.article)

                    is ContentDownload.Failed -> downloadFailure = download.reason
                }
            }

            articleRepository.updateProgress(
                id = articleId,
                position = articleWithFeed.article.scrollPosition
            )

            _currentArticleState.value =
                ArticleUiState.Success(articleWithFeed, downloadFailure = downloadFailure)

            // Shows the stored content right away and swaps in the new parse if it differs
            if (articleRepository.needsReparse(articleWithFeed.article)) {
                startRefresh(articleId, reportResult = false)
            }
        }
    }

    // Downloads an article again after the first download failed
    fun retryDownload() {
        val current = _currentArticleState.value as? ArticleUiState.Success ?: return
        if (current.isRefreshing) return
        val articleId = current.articleWithFeed.article.id
        updateSuccess(articleId) { it.copy(isRefreshing = true) }

        refreshJob = viewModelScope.launch {
            val download =
                articleRepository.fetchAndSaveArticleContent(current.articleWithFeed.article)
            updateSuccess(articleId) {
                when (download) {
                    is ContentDownload.Done -> it.copy(
                        articleWithFeed = it.articleWithFeed.copy(article = download.article),
                        isRefreshing = false,
                        downloadFailure = null
                    )

                    is ContentDownload.Failed ->
                        it.copy(isRefreshing = false, downloadFailure = download.reason)
                }
            }
        }
    }

    fun refreshArticle() {
        val current = _currentArticleState.value as? ArticleUiState.Success ?: return
        if (current.isRefreshing) return
        // A background re-parse may still be running; this one replaces it and reports back
        refreshJob?.cancel()
        startRefresh(current.articleWithFeed.article.id, reportResult = true)
    }

    // Only a manual reload shows progress and its result
    private fun startRefresh(articleId: Long, reportResult: Boolean) {
        if (reportResult) {
            updateSuccess(articleId) { it.copy(isRefreshing = true, refreshResult = null) }
        }

        refreshJob = viewModelScope.launch {
            // Start from the stored article, so a refresh compares against what's saved
            val stored = articleRepository.getArticleWithFeedById(articleId)
            val result = stored?.let { articleRepository.refreshArticleContent(it.article) }
                ?: ContentRefresh.Failed
            val updated = if (result == ContentRefresh.Updated) {
                articleRepository.getArticleWithFeedById(articleId)
            } else {
                null
            }
            updateSuccess(articleId) {
                val withContent = if (updated != null) {
                    it.copy(articleWithFeed = updated, downloadFailure = null)
                } else {
                    it
                }
                if (reportResult) {
                    withContent.copy(isRefreshing = false, refreshResult = result)
                } else {
                    withContent
                }
            }
        }
    }

    fun onRefreshResultShown() {
        _currentArticleState.update {
            if (it is ArticleUiState.Success) it.copy(refreshResult = null) else it
        }
    }

    // Leaves the state alone if another article was opened in the meantime
    private fun updateSuccess(
        articleId: Long,
        transform: (ArticleUiState.Success) -> ArticleUiState.Success
    ) {
        _currentArticleState.update {
            if (it is ArticleUiState.Success && it.articleWithFeed.article.id == articleId) {
                transform(it)
            } else {
                it
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                ArticleViewModel(appContainer.articleRepository)
            }
        }
    }
}
