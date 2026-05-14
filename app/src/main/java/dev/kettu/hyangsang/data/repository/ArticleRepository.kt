package dev.kettu.hyangsang.data.repository

import dev.kettu.hyangsang.data.local.dao.ArticleDao
import dev.kettu.hyangsang.data.local.entity.Article
import dev.kettu.hyangsang.data.local.entity.ArticleWithFeed
import dev.kettu.hyangsang.parser.ArticleParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

class ArticleRepository(private val articleDao: ArticleDao) {
    fun getAllArticles(): Flow<List<Article>> = articleDao.getAllArticles()

    fun getAllArticlesWithFeed(): Flow<List<ArticleWithFeed>> = articleDao.getAllArticlesWithFeed()

    suspend fun getArticleById(id: Long): Article? = articleDao.getArticleById(id)

    suspend fun getArticleWithFeedById(id: Long): ArticleWithFeed? = articleDao.getArticleWithFeedById(id)

    suspend fun insertArticle(article: Article): Long = articleDao.insertArticle(article)

    suspend fun updateArticle(article: Article) = articleDao.updateArticle(article)

    suspend fun deleteArticle(article: Article) = articleDao.deleteArticle(article)

    suspend fun updateProgress(id: Long, position: Int) = articleDao.updateProgress(id, position)

    suspend fun fetchAndSaveArticleContent(article: Article): Article {
        if (!article.content.isNullOrBlank()) return article
        val url = article.sourceUrl ?: return article

        return withContext(Dispatchers.IO) {
            try {
                val doc = Jsoup.connect(url).get()

                val content = ArticleParser().parse(url, doc)
                val updatedArticle = article.copy(content = content)

                updateArticle(updatedArticle)
                updatedArticle
            } catch (_: Exception) {
                article // Return original on failure
            }
        }
    }
}
