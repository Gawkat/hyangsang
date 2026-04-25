package dev.kettu.hyangsang.data.repository

import dev.kettu.hyangsang.data.local.dao.ArticleDao
import dev.kettu.hyangsang.data.local.dao.RssFeedDao
import dev.kettu.hyangsang.data.local.entity.Article
import dev.kettu.hyangsang.data.local.entity.RssFeed
import dev.kettu.hyangsang.data.local.entity.RssItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.forEach
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.net.URL

class RssFeedRepository(
    private val rssFeedDao: RssFeedDao,
    private val articleDao: ArticleDao
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
    suspend fun refreshEnabledFeeds() {
        val enabledFeeds = rssFeedDao.getEnabledFeeds() // You'll need this in DAO
        enabledFeeds.collect { feedList ->
            feedList.forEach { feed ->
                fetchAndSaveRss(feed.url)
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

    suspend fun fetchAndSaveRss(feedUrl: String) {
        withContext(Dispatchers.IO) {
            try {
                // 1. Fetch the XML (Simplified example)
                val xmlContent = URL(feedUrl).readText()

                // 2. Parse the XML
                // You can use a library or a simple regex/XmlPullParser
                val items = parseRssXml(xmlContent)

                // 3. Convert RssItem to Article and Save
                items.forEach { item ->
                    val article = Article(
                        title = item.title,
                        content = item.description, // TODO: Clean HTML tags if necessary
                        sourceUrl = item.link
                    )
                    articleDao.insertArticle(article)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun parseRssXml(xml: String): List<RssItem> {
        // Implementation using XmlPullParser or a library
        return emptyList()
    }
}
