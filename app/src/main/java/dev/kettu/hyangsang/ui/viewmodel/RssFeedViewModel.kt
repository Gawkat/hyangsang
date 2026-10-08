package dev.kettu.hyangsang.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.kettu.hyangsang.data.local.entity.RssFeed
import dev.kettu.hyangsang.data.repository.FeedCheckResult
import dev.kettu.hyangsang.data.repository.RssFeedRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RssFeedViewModel(private val rssFeedRepository: RssFeedRepository) : ViewModel() {
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    // Feeds being refreshed on their own or by category, rather than by a refresh of all feeds
    private val _refreshingFeedIds = MutableStateFlow<Set<Long>>(emptySet())
    val refreshingFeedIds: StateFlow<Set<Long>> = _refreshingFeedIds.asStateFlow()

    val feeds: StateFlow<List<RssFeed>> = rssFeedRepository.allFeeds.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    suspend fun checkFeed(url: String): FeedCheckResult = rssFeedRepository.checkFeed(url)

    fun addFeed(url: String, title: String, category: String) {
        viewModelScope.launch {
            rssFeedRepository.addFeed(url = url, title = title, category = category)
        }
    }

    fun updateFeedDetails(feed: RssFeed, title: String, category: String) {
        viewModelScope.launch {
            rssFeedRepository.updateFeedDetails(feed, title, category)
        }
    }

    fun toggleFeed(feed: RssFeed) {
        viewModelScope.launch {
            rssFeedRepository.toggleFeed(feed)
        }
    }

    fun setFeedsEnabled(feeds: List<RssFeed>, enabled: Boolean) {
        viewModelScope.launch {
            rssFeedRepository.setFeedsEnabled(feeds, enabled)
        }
    }

    // Sets each feed back to the state it has in [feeds]
    fun restoreEnabledStates(feeds: List<RssFeed>) {
        viewModelScope.launch {
            rssFeedRepository.restoreEnabledStates(feeds)
        }
    }

    fun renameCategory(oldName: String, newName: String) {
        viewModelScope.launch {
            rssFeedRepository.renameCategory(oldName, newName)
        }
    }

    fun refreshFeeds(feeds: List<RssFeed>) {
        viewModelScope.launch { refreshTracked(feeds) }
    }

    // Returns the number of new articles, or null when the refresh failed. The refresh runs in
    // the view model's scope, so it finishes even if the caller stops waiting for it
    suspend fun refreshFeed(feed: RssFeed): Int? =
        viewModelScope.async { refreshTracked(listOf(feed)).firstOrNull() }.await()

    private suspend fun refreshTracked(feeds: List<RssFeed>): List<Int?> {
        val ids = feeds.map { it.id }.toSet()
        _refreshingFeedIds.update { it + ids }
        try {
            return rssFeedRepository.refreshFeeds(feeds)
        } finally {
            _refreshingFeedIds.update { it - ids }
        }
    }

    suspend fun countSavedArticles(feed: RssFeed): Int = rssFeedRepository.countSavedArticles(feed)

    // Returns how many feeds were re-added
    suspend fun restoreDefaultFeeds(): Int = rssFeedRepository.restoreDefaultFeeds()

    fun deleteFeed(feed: RssFeed) {
        viewModelScope.launch {
            rssFeedRepository.deleteFeed(feed)
        }
    }

    fun refreshFeeds() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                rssFeedRepository.refreshEnabledFeeds(forceRefresh = true)
            } catch (_: Exception) {
                // TODO: log error?
            } finally {
                _isRefreshing.value = false
            }
        }
    }
}
