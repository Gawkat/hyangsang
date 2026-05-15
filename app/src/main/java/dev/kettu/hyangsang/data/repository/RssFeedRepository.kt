package dev.kettu.hyangsang.data.repository

import dev.kettu.hyangsang.data.local.dao.ArticleDao
import dev.kettu.hyangsang.data.local.dao.RssFeedDao
import dev.kettu.hyangsang.data.local.entity.Article
import dev.kettu.hyangsang.data.local.entity.RssFeed
import dev.kettu.hyangsang.network.RssFeedService
import dev.kettu.hyangsang.parser.RssFeedParser
import dev.kettu.hyangsang.parser.parseToIso8601
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

private val DEFAULT_FEED_REFRESH_RATE_LIMIT = 5.minutes

class RssFeedRepository(
    private val rssFeedDao: RssFeedDao,
    private val articleDao: ArticleDao,
    private val rssService: RssFeedService
) {
    val allFeeds: Flow<List<RssFeed>> = rssFeedDao.getAllFeeds()

    // Get all feeds grouped by category for the "Manage Feeds" screen
    val allFeedsByCategory: Flow<Map<String, List<RssFeed>>> =
        rssFeedDao.getAllFeeds().map { feeds ->
            feeds.groupBy { it.category }
        }

    // Toggle feed status
    suspend fun toggleFeed(feed: RssFeed) {
        rssFeedDao.updateFeed(feed.copy(isEnabled = !feed.isEnabled))
    }

    // Only fetch for enabled feeds
    @OptIn(ExperimentalTime::class)
    suspend fun refreshEnabledFeeds(forceRefresh: Boolean = false) {
        val enabledFeeds = rssFeedDao.getEnabledFeeds()
        enabledFeeds.collect { feedList ->
            feedList.forEach { feed ->
                // Skip if forceRefresh is false and lastSynced is within the last 5 minutes
                val lastSynced = Instant.parse(feed.lastSynced)
                if (!forceRefresh && ((Clock.System.now() - lastSynced) < DEFAULT_FEED_REFRESH_RATE_LIMIT)) {
                    return@forEach
                }

                fetchAndSaveRss(feed)
            }
        }
    }

    suspend fun insertFeed(feed: RssFeed) {
        rssFeedDao.insertFeed(feed)
    }

    suspend fun updateFeed(feed: RssFeed) {
        rssFeedDao.updateFeed(feed)
    }

    suspend fun deleteFeed(feed: RssFeed) {
        rssFeedDao.deleteFeed(feed)
    }

    @OptIn(ExperimentalTime::class)
    suspend fun fetchAndSaveRss(feed: RssFeed) {
        withContext(Dispatchers.IO) {
            try {
                val response = rssService.getRssFeed(feed.url)
                if (response.isSuccessful) {
                    val xmlString = response.body()?.string() ?: ""
                    val items = RssFeedParser().parse(xmlString)

                    items.forEach { item ->
                        if (item.title.isEmpty() || item.link.isEmpty()) {
                            return@forEach
                        }

                        val article = Article(
                            title = item.title,
                            description = item.description,
                            sourceUrl = item.link,
                            feedId = feed.id,
                            pubDate = parseToIso8601(item.pubDate)
                        )
                        articleDao.insertArticle(article)
                    }

                    val syncedFeed = feed.copy(lastSynced = Clock.System.now().toString())
                    rssFeedDao.updateFeed(syncedFeed)
                }
            } catch (e: Exception) {
                // TODO: Probably note that this feed failed to sync
                e.printStackTrace()
            }
        }
    }
}
