package dev.kettu.hyangsang.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.kettu.hyangsang.data.local.entity.RssFeed
import dev.kettu.hyangsang.data.repository.FeedCheckResult
import dev.kettu.hyangsang.data.repository.RssFeedRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RssFeedViewModel(private val rssFeedRepository: RssFeedRepository) : ViewModel() {
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

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
