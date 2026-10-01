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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

private val DEFAULT_FEED_REFRESH_RATE_LIMIT = 5.minutes
private const val MAX_CONCURRENT_FEED_FETCHES = 6

enum class FeedCheckError { UNREACHABLE, HTTP_ERROR, NOT_A_FEED, ALREADY_ADDED }

sealed interface FeedCheckResult {
    data class Valid(
        val url: String,
        val title: String?,
        val articleCount: Int
    ) : FeedCheckResult

    data class Invalid(val error: FeedCheckError, val httpCode: Int? = null) : FeedCheckResult
}

class RssFeedRepository(
    private val rssFeedDao: RssFeedDao,
    private val articleDao: ArticleDao,
    private val rssService: RssFeedService,
    // Resolved on each call, so restored feeds get names in the current app language
    private val defaultFeeds: () -> List<RssFeed>,
    // Returns the given feeds whose built-in names need translating into the app language
    private val localizeFeeds: (List<RssFeed>) -> List<RssFeed> = { emptyList() }
) {
    val allFeeds: Flow<List<RssFeed>> = rssFeedDao.getAllFeeds()

    // Get all feeds grouped by category for the "Manage Feeds" screen
    val allFeedsByCategory: Flow<Map<String, List<RssFeed>>> =
        rssFeedDao.getAllFeeds().map { feeds ->
            feeds.groupBy { it.category }
        }

    // Adds https:// when no scheme is given, since most people paste or type bare domains
    fun normalizeFeedUrl(input: String): String {
        val trimmed = input.trim()
        return if (trimmed.contains("://")) trimmed else "https://$trimmed"
    }

    // Fetches and parses the feed without saving anything, so the user can confirm it works
    suspend fun checkFeed(input: String): FeedCheckResult = withContext(Dispatchers.IO) {
        val url = normalizeFeedUrl(input)
        if (rssFeedDao.getFeedByUrl(url) != null) {
            return@withContext FeedCheckResult.Invalid(FeedCheckError.ALREADY_ADDED)
        }
        try {
            val response = rssService.getRssFeed(url)
            if (!response.isSuccessful) {
                return@withContext FeedCheckResult.Invalid(
                    FeedCheckError.HTTP_ERROR,
                    response.code()
                )
            }
            val xmlString = response.body()?.string() ?: ""
            val items = try {
                RssFeedParser().parse(xmlString)
            } catch (_: Exception) {
                return@withContext FeedCheckResult.Invalid(FeedCheckError.NOT_A_FEED)
            }
            val title = Jsoup.parse(xmlString, "", Parser.xmlParser())
                .selectFirst("channel > title")
                ?.text()
                ?.let { Parser.unescapeEntities(it, false).trim() }
                ?.takeIf { it.isNotEmpty() }
            FeedCheckResult.Valid(url = url, title = title, articleCount = items.size)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            FeedCheckResult.Invalid(FeedCheckError.UNREACHABLE)
        }
    }

    // Inserts the feed and fetches it right away, so it has articles and a sync status
    suspend fun addFeed(url: String, title: String, category: String) {
        val feed = RssFeed(title = title, url = url, category = category)
        val id = rssFeedDao.insertFeed(feed)
        if (id != -1L) {
            fetchAndSaveRss(feed.copy(id = id))
        }
    }

    suspend fun updateFeedDetails(feed: RssFeed, title: String, category: String) {
        rssFeedDao.updateFeedDetails(feed.id, title, category)
    }

    // Built-in category names and default titles are stored in the language they were added
    // in, so translate them after the app language changes
    suspend fun localizeDefaultNames() {
        localizeFeeds(rssFeedDao.getAllFeeds().first()).forEach { feed ->
            rssFeedDao.updateFeedDetails(feed.id, feed.title, feed.category)
        }
    }

    suspend fun countSavedArticles(feed: RssFeed): Int = articleDao.countSavedInFeed(feed.id)

    // Re-adds built-in feeds the user removed; existing ones are left as they are
    suspend fun restoreDefaultFeeds(): Int {
        val restored = defaultFeeds().mapNotNull { feed ->
            val id = rssFeedDao.insertFeed(feed)
            if (id != -1L) feed.copy(id = id) else null
        }
        fetchConcurrently(restored)
        return restored.size
    }

    // Toggle feed status
    suspend fun toggleFeed(feed: RssFeed) {
        rssFeedDao.setEnabled(feed.id, !feed.isEnabled)
    }

    // Only fetch for enabled feeds
    @OptIn(ExperimentalTime::class)
    suspend fun refreshEnabledFeeds(forceRefresh: Boolean = false) {
        val enabledFeeds = rssFeedDao.getEnabledFeeds().first()
        val now = Clock.System.now()

        val dueFeeds = enabledFeeds.filter { feed ->
            // Rate limit on attempts rather than successes, so a broken feed isn't retried constantly
            val lastAttempt = try {
                Instant.parse(feed.lastSyncAttempt ?: feed.lastSynced)
            } catch (_: Exception) {
                Instant.DISTANT_PAST
            }

            // Skip if forceRefresh is false and the last attempt is within the last 5 minutes
            forceRefresh || (now - lastAttempt) >= DEFAULT_FEED_REFRESH_RATE_LIMIT
        }

        fetchConcurrently(dueFeeds)
    }

    // Fetches feeds in parallel; each fetch records its own failure, so one bad feed doesn't
    // cancel the rest. The cap bounds how many feed bodies are downloaded and parsed at once
    private suspend fun fetchConcurrently(feeds: List<RssFeed>) {
        val semaphore = Semaphore(MAX_CONCURRENT_FEED_FETCHES)
        coroutineScope {
            feeds.map { feed ->
                async { semaphore.withPermit { fetchAndSaveRss(feed) } }
            }.awaitAll()
        }
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

                    val articles = items
                        .filter { it.title.isNotEmpty() && it.link.isNotEmpty() }
                        .map { item ->
                            // Clean title and description from HTML tags and entities
                            Article(
                                title = Parser.unescapeEntities(item.title, false),
                                description = Jsoup.parse(item.description).text(),
                                sourceUrl = item.link,
                                feedId = feed.id,
                                pubDate = parseToIso8601(item.pubDate)
                            )
                        }
                    articleDao.insertArticles(articles)

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
