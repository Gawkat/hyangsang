package dev.kettu.hyangsang.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.kettu.hyangsang.data.local.entity.RssFeed
import dev.kettu.hyangsang.data.repository.RssFeedRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RssFeedViewModel(private val rssFeedRepository: RssFeedRepository) : ViewModel() {
    val feeds: StateFlow<List<RssFeed>> = rssFeedRepository.allFeeds.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun addFeed(title: String, url: String, category: String) {
        viewModelScope.launch {
            rssFeedRepository.insertFeed(RssFeed(title = title, url = url, category = category))
        }
    }

    fun updateFeed(feed: RssFeed) {
        viewModelScope.launch {
            rssFeedRepository.updateFeed(feed)
        }
    }

    fun deleteFeed(feed: RssFeed) {
        viewModelScope.launch {
            rssFeedRepository.deleteFeed(feed)
        }
    }
}
