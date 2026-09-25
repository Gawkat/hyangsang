package dev.kettu.hyangsang.data.repository

import dev.kettu.hyangsang.data.local.dao.ArticleDao
import dev.kettu.hyangsang.data.local.dao.RssFeedDao
import dev.kettu.hyangsang.data.local.entity.Article
import dev.kettu.hyangsang.data.local.entity.RssFeed
import dev.kettu.hyangsang.network.RssFeedService
import dev.kettu.hyangsang.parser.RssFeedParser
import dev.kettu.hyangsang.parser.parseToIso8601
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
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
        rssFeedDao.setEnabled(feed.id, !feed.isEnabled)
    }

    // Only fetch for enabled feeds
    @OptIn(ExperimentalTime::class)
    suspend fun refreshEnabledFeeds(forceRefresh: Boolean = false) {
        val enabledFeeds = rssFeedDao.getEnabledFeeds().first()

        enabledFeeds.forEach { feed ->
            // Rate limit on attempts rather than successes, so a broken feed isn't retried constantly
            val lastAttempt = try {
                Instant.parse(feed.lastSyncAttempt ?: feed.lastSynced)
            } catch (_: Exception) {
                Instant.DISTANT_PAST
            }

            // Skip if forceRefresh is false and the last attempt is within the last 5 minutes
            if (!forceRefresh && ((Clock.System.now() - lastAttempt) < DEFAULT_FEED_REFRESH_RATE_LIMIT)) {
                return@forEach
            }

            fetchAndSaveRss(feed)
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
            val attemptTime = Clock.System.now().toString()
            try {
                val response = rssService.getRssFeed(feed.url)
                if (response.isSuccessful) {
                    val xmlString = response.body()?.string() ?: ""
                    val items = RssFeedParser().parse(xmlString)

                    items.forEach { item ->
                        if (item.title.isEmpty() || item.link.isEmpty()) {
                            return@forEach
                        }

                        // Clean title and description from HTML tags and entities
                        val cleanTitle = Parser.unescapeEntities(item.title, false)
                        val cleanDescription = Jsoup.parse(item.description).text()

                        val article = Article(
                            title = cleanTitle,
                            description = cleanDescription,
                            sourceUrl = item.link,
                            feedId = feed.id,
                            pubDate = parseToIso8601(item.pubDate)
                        )
                        articleDao.insertArticle(article)
                    }

                    rssFeedDao.markSyncSucceeded(feed.id, attemptTime)
                } else {
                    rssFeedDao.markSyncFailed(feed.id, attemptTime, "HTTP ${response.code()}")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
                rssFeedDao.markSyncFailed(
                    feed.id,
                    attemptTime,
                    e.message ?: e::class.simpleName ?: "Unknown error"
                )
            }
        }
    }
}
