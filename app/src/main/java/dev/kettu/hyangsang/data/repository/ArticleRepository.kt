package dev.kettu.hyangsang.data.repository

import dev.kettu.hyangsang.data.local.dao.ArticleDao
import dev.kettu.hyangsang.data.local.entity.Article
import dev.kettu.hyangsang.data.local.entity.ArticleWithFeed
import dev.kettu.hyangsang.parser.ArticleParser
import dev.kettu.hyangsang.parser.ContentBlock
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

    suspend fun fetchAndSaveArticleContent(
        article: Article,
        forceRefresh: Boolean = false
    ): Article {
        if (!forceRefresh && !article.content.isNullOrEmpty()) {
            // Check if it's legacy content
            val isLegacy = article.content.any { it is ContentBlock.Legacy }
            if (!isLegacy) return article
        }
        val url = article.sourceUrl

        return withContext(Dispatchers.IO) {
            try {
                val doc = Jsoup.connect(url).get()

                val content = ArticleParser().parse(url, doc)
                articleDao.updateContent(article.id, content)
                article.copy(content = content)
            } catch (_: Exception) {
                article // Return original on failure
            }
        }
    }
}
