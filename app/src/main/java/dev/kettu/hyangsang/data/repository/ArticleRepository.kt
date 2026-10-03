package dev.kettu.hyangsang.data.repository

import dev.kettu.hyangsang.data.local.dao.ArticleDao
import dev.kettu.hyangsang.data.local.entity.Article
import dev.kettu.hyangsang.data.local.entity.ArticleWithFeed
import dev.kettu.hyangsang.parser.ArticleParser
import dev.kettu.hyangsang.parser.ContentBlock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class ArticleRepository(private val articleDao: ArticleDao) {
    fun getAllArticles(): Flow<List<Article>> = articleDao.getAllArticles()

    fun getAllArticlesWithFeed(): Flow<List<ArticleWithFeed>> = articleDao.getAllArticlesWithFeed()

    fun getSavedArticlesWithFeed(): Flow<List<ArticleWithFeed>> =
        articleDao.getSavedArticlesWithFeed()

    suspend fun getArticleById(id: Long): Article? = articleDao.getArticleById(id)

    suspend fun getArticleWithFeedById(id: Long): ArticleWithFeed? =
        articleDao.getArticleWithFeedById(id)

    suspend fun insertArticle(article: Article): Long = articleDao.insertArticle(article)

    suspend fun updateArticle(article: Article) = articleDao.updateArticle(article)

    suspend fun deleteArticle(article: Article) = articleDao.deleteArticle(article)

    suspend fun updateProgress(id: Long, position: Int) = articleDao.updateProgress(id, position)

    @OptIn(ExperimentalTime::class)
    suspend fun setSaved(id: Long, saved: Boolean) =
        articleDao.updateSavedDate(id, if (saved) Clock.System.now().toString() else null)

    // Used to undo an unsave without moving the article in the saved list
    suspend fun restoreSavedDate(id: Long, savedDate: String) =
        articleDao.updateSavedDate(id, savedDate)

    suspend fun fetchAndSaveArticleContent(article: Article): Article {
        if (!article.content.isNullOrEmpty()) return article

        return try {
            val content = fetchContent(article)
            articleDao.updateContent(article.id, content, ArticleParser.VERSION)
            article.copy(content = content, parserVersion = ArticleParser.VERSION)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            article // Return original on failure
        }
    }

    // Downloads and parses the article again, after a parser fix or for a publisher's edit.
    // The stored content is only replaced when the new parse looks complete
    suspend fun refreshArticleContent(article: Article): ContentRefresh {
        val content = try {
            fetchContent(article)
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

    // Stored content from an older parser, which may have missed what a fix now extracts
    fun needsReparse(article: Article): Boolean =
        !article.content.isNullOrEmpty() && article.parserVersion < ArticleParser.VERSION

    private suspend fun fetchContent(article: Article): List<ContentBlock> =
        withContext(Dispatchers.IO) {
            val url = article.sourceUrl
            ArticleParser().parse(url, Jsoup.connect(url).get(), article.title)
        }
}

enum class ContentRefresh { Updated, Unchanged, Failed }

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
