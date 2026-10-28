package dev.kettu.hyangsang.data.repository

import dev.kettu.hyangsang.data.local.dao.ArticleDao
import dev.kettu.hyangsang.data.local.entity.Article
import dev.kettu.hyangsang.data.local.entity.ArticleSummaryWithFeed
import dev.kettu.hyangsang.data.local.entity.ArticleWithFeed
import dev.kettu.hyangsang.data.prefs.ArticleRetention
import dev.kettu.hyangsang.data.prefs.ContentRetention
import dev.kettu.hyangsang.network.USER_AGENT
import dev.kettu.hyangsang.parser.ArticleParser
import dev.kettu.hyangsang.parser.ContentBlock
import dev.kettu.hyangsang.parser.PlainText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.jsoup.HttpStatusException
import org.jsoup.Jsoup
import java.io.IOException
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.ExperimentalTime

class ArticleRepository(private val articleDao: ArticleDao) {
    // A blank search matches all articles
    fun getArticleSummaries(
        category: String?,
        feedId: Long?,
        searchQuery: String,
        unreadOnly: Boolean
    ): Flow<List<ArticleSummaryWithFeed>> = articleDao.getArticleSummaries(
        category = category,
        feedId = feedId,
        query = if (searchQuery.isBlank()) "" else escapeLike(searchQuery),
        unreadOnly = unreadOnly
    )

    fun getSavedArticleSummaries(importedOnly: Boolean): Flow<List<ArticleSummaryWithFeed>> =
        articleDao.getSavedArticleSummaries(importedOnly)

    // Unread article count per feed id; feeds without unread articles are left out
    fun getUnreadCounts(): Flow<Map<Long, Int>> = articleDao.getUnreadCounts()

    suspend fun getArticleById(id: Long): Article? = articleDao.getArticleById(id)

    suspend fun getArticleWithFeedById(id: Long): ArticleWithFeed? =
        articleDao.getArticleWithFeedById(id)

    suspend fun insertArticle(article: Article): Long = articleDao.insertArticle(article)

    suspend fun updateArticle(article: Article) = articleDao.updateArticle(article)

    suspend fun deleteArticle(article: Article) = articleDao.deleteArticle(article)

    suspend fun markOpened(id: Long) = articleDao.markOpened(id)

    /** Stores where the reader is: a content block, [progress] of the way through the article. */
    suspend fun savePosition(id: Long, blockIndex: Int, progress: Float) =
        articleDao.updatePosition(id, blockIndex, progress)

    @OptIn(ExperimentalTime::class)
    suspend fun setSaved(id: Long, saved: Boolean) =
        articleDao.updateSavedDate(id, if (saved) Clock.System.now().toString() else null)

    // Used to undo an unsave without moving the article in the saved list
    suspend fun restoreSavedDate(id: Long, savedDate: String) =
        articleDao.updateSavedDate(id, savedDate)

    // Imports a shared link as a saved article, downloaded like a feed's when it's first opened.
    // A link that's already stored, from a feed or shared before, is saved and reused instead
    @OptIn(ExperimentalTime::class)
    suspend fun importLink(url: String, title: String?): Long {
        val now = Clock.System.now().toString()
        articleDao.getArticleIdBySourceUrl(url)?.let { id ->
            articleDao.saveIfUnsaved(id, now)
            return id
        }
        return articleDao.insertArticle(
            Article(
                feedId = null,
                title = title.orEmpty(),
                description = "",
                sourceUrl = url,
                addedDate = now,
                pubDate = null,
                savedDate = now
            )
        )
    }

    // Imports pasted or shared text as a saved article. Without a title, the start of the text
    // stands in for one
    @OptIn(ExperimentalTime::class)
    suspend fun importText(title: String, text: String): Long {
        val content = PlainText.blocks(text)
        val firstParagraph = (content.firstOrNull() as? ContentBlock.Text)?.text.orEmpty()
        val now = Clock.System.now().toString()
        return articleDao.insertArticle(
            Article(
                feedId = null,
                title = title.trim().ifEmpty { firstParagraph.take(IMPORTED_TITLE_LENGTH) },
                description = firstParagraph.take(IMPORTED_DESCRIPTION_LENGTH),
                content = content,
                sourceUrl = null,
                addedDate = now,
                pubDate = null,
                savedDate = now,
                parserVersion = ArticleParser.VERSION
            )
        )
    }

    // Downloads the article the first time it's opened. A failure says why, so the reader can
    // offer what might help
    suspend fun fetchAndSaveArticleContent(article: Article): ContentDownload {
        if (!article.content.isNullOrEmpty()) return ContentDownload.Done(article)

        return try {
            val (content, pageTitle) = fetchContent(article) ?: return ContentDownload.Done(article)
            // A shared link is stored before its page is downloaded, so its title comes from the page
            val title = article.title.ifBlank { pageTitle.orEmpty() }
            if (title != article.title) articleDao.updateTitle(article.id, title)
            // The content may have been cleared and is now downloaded again, so a stored position
            // has to be clamped to the new content like a refresh does
            articleDao.replaceContent(
                article.id,
                content,
                maxPosition = content.lastIndex,
                parserVersion = ArticleParser.VERSION
            )
            ContentDownload.Done(
                article.copy(
                    title = title,
                    content = content,
                    parserVersion = ArticleParser.VERSION,
                    blockIndex = article.blockIndex.coerceIn(0, maxOf(0, content.lastIndex))
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ContentDownload.Failed(DownloadFailure.of(e))
        }
    }

    // Downloads and parses the article again, after a parser fix or for a publisher's edit.
    // The stored content is only replaced when the new parse looks complete
    suspend fun refreshArticleContent(article: Article): ContentRefresh {
        val content = try {
            fetchContent(article)?.content ?: return ContentRefresh.Unchanged
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return ContentRefresh.Failed
        }

        if (content == article.content) {
            articleDao.updateParserVersion(article.id, ArticleParser.VERSION)
            return ContentRefresh.Unchanged
        }
        // Not marked as parsed, so a rejected parse is tried again on the next open
        if (!isUsableRefresh(article.content.orEmpty(), content)) return ContentRefresh.Failed

        articleDao.replaceContent(
            article.id,
            content,
            maxPosition = content.lastIndex,
            parserVersion = ArticleParser.VERSION
        )
        return ContentRefresh.Updated
    }

    // Removes old unsaved articles, then the downloaded text of ones not opened for a while.
    // Saved articles are never touched
    suspend fun pruneStorage(articleRetention: ArticleRetention, contentRetention: ContentRetention) {
        articleDao.deleteUnsavedImports()
        articleRetention.days?.let { articleDao.deleteUnsavedOlderThan(cutoff(it)) }
        contentRetention.days?.let { articleDao.clearContentNotOpenedSince(cutoff(it)) }
    }

    @OptIn(ExperimentalTime::class)
    private fun cutoff(days: Int): String = (Clock.System.now() - days.days).toString()

    // Stored content from an older parser, which may have missed what a fix now extracts.
    // Pasted text has no page to parse again
    fun needsReparse(article: Article): Boolean = article.sourceUrl != null &&
            !article.content.isNullOrEmpty() && article.parserVersion < ArticleParser.VERSION

    // Null for pasted text, which has no page to download
    private suspend fun fetchContent(article: Article): FetchedPage? {
        val url = article.sourceUrl ?: return null
        return withContext(Dispatchers.IO) {
            val document = Jsoup.connect(url).userAgent(USER_AGENT).get()
            val parser = ArticleParser()
            if (parser.isErrorPage(url, document)) throw ErrorPageException(url)
            val pageTitle = ArticleParser.pageTitle(document)
            FetchedPage(
                parser.parse(url, document, article.title.ifBlank { pageTitle }),
                pageTitle
            )
        }
    }

    private data class FetchedPage(val content: List<ContentBlock>, val title: String?)
}

// A site's error page for a missing article, served with a success status as if it were the
// article. It's handled like a 404
internal class ErrorPageException(url: String) : IOException("Error page served for $url")

// Makes the search text match literally inside a LIKE pattern that uses ESCAPE '\'
internal fun escapeLike(text: String): String =
    text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

enum class ContentRefresh { Updated, Unchanged, Failed }

sealed interface ContentDownload {
    data class Done(val article: Article) : ContentDownload
    data class Failed(val reason: DownloadFailure) : ContentDownload
}

enum class DownloadFailure {
    // The site refused the download, often bot protection that a browser gets past
    BLOCKED,

    // The page doesn't exist, so trying again won't help
    NOT_FOUND,

    // No connection, a timeout or a server error, which may pass
    OTHER;

    companion object {
        fun of(e: Exception): DownloadFailure =
            if (e is ErrorPageException) NOT_FOUND
            else when ((e as? HttpStatusException)?.statusCode) {
                401, 403, 451 -> BLOCKED
                404, 410 -> NOT_FOUND
                else -> OTHER
            }
    }
}

// How much of an imported text's first paragraph stands in for a missing title, and shows in lists
private const val IMPORTED_TITLE_LENGTH = 40
private const val IMPORTED_DESCRIPTION_LENGTH = 200

// A refresh shorter than this fraction of the stored text is more likely an error, consent or
// paywall page than the article itself
private const val MIN_REFRESH_TEXT_FRACTION = 0.3

// Rejects a new parse that would replace readable content with an empty or much shorter one
internal fun isUsableRefresh(old: List<ContentBlock>, new: List<ContentBlock>): Boolean {
    val newLength = textLength(new)
    if (newLength == 0) return false
    return newLength >= textLength(old) * MIN_REFRESH_TEXT_FRACTION
}

private fun textLength(blocks: List<ContentBlock>): Int = blocks.sumOf { block ->
    when (block) {
        is ContentBlock.Text -> block.text.trim().length
        is ContentBlock.Heading -> block.text.trim().length
        else -> 0
    }
}
