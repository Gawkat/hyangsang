package dev.kettu.hyangsang.data.repository

import dev.kettu.hyangsang.data.local.dao.ArticleDao
import dev.kettu.hyangsang.data.local.entity.Article
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

class ArticleRepository(private val articleDao: ArticleDao) {
    fun getAllArticles(): Flow<List<Article>> = articleDao.getAllArticles()

    suspend fun getArticleById(id: Long): Article? = articleDao.getArticleById(id)

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

                // TODO: clean the input?
                //doc = Jsoup.clean(doc.text(), Safelist.basic())

                // TODO: Improve this
                // Basic extraction: Try common article tags or the largest text block
                val articleBody = doc.select("article").first()
                    ?: doc.select("div.article-body").first()
                    ?: doc.select("div.content").first()
                    ?: doc.body()

                // Strip common non-content elements
                articleBody.select("script, style, iframe, footer, nav, .ads").remove()

                val content = articleBody.text()
                val updatedArticle = article.copy(content = content)

                updateArticle(updatedArticle)
                updatedArticle
            } catch (e: Exception) {
                article // Return original on failure
            }
        }
    }
}
