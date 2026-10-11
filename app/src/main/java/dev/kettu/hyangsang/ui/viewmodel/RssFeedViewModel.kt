package dev.kettu.hyangsang.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.kettu.hyangsang.data.defaults.DefaultFeed
import dev.kettu.hyangsang.data.local.entity.RssFeed
import dev.kettu.hyangsang.data.prefs.UserPreferencesRepository
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

class RssFeedViewModel(
    private val rssFeedRepository: RssFeedRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {
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

    // Built-in feeds added in app updates since the user last looked, that they don't have
    val newDefaultFeeds: StateFlow<List<DefaultFeed>> = rssFeedRepository
        .newDefaultFeeds(userPreferencesRepository.defaultFeedsSeenFlow)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Stops offering the current new built-in feeds. They can still be added from the full list
    fun dismissNewDefaultFeeds() {
        viewModelScope.launch { userPreferencesRepository.markDefaultFeedsSeen() }
    }

    // The new built-in feeds were in the list the user picked from, so they count as seen
    fun addDefaultFeeds(feeds: List<DefaultFeed>) {
        viewModelScope.launch {
            userPreferencesRepository.markDefaultFeedsSeen()
            rssFeedRepository.addDefaultFeeds(feeds)
        }
    }

    /**
     * Adds the feeds picked in the first-run setup and fetches them, shown as a refresh so
     * Discover shows them loading. The setup counts as done once they're stored.
     */
    fun finishSetup(feeds: List<DefaultFeed>) {
        viewModelScope.launch {
            userPreferencesRepository.markDefaultFeedsSeen()
            val added = rssFeedRepository.insertDefaultFeeds(feeds)
            userPreferencesRepository.setFirstRunSetupPending(false)
            _isRefreshing.value = true
            try {
                rssFeedRepository.refreshFeeds(added)
            } finally {
                _isRefreshing.value = false
            }
        }
    }

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
