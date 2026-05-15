package dev.kettu.hyangsang.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import dev.kettu.hyangsang.data.local.entity.Article
import dev.kettu.hyangsang.data.local.entity.ArticleWithFeed
import kotlinx.coroutines.flow.Flow

@Dao
interface ArticleDao {
    @Query("SELECT * FROM articles ORDER BY COALESCE(pubDate, addedDate) DESC")
    fun getAllArticles(): Flow<List<Article>>

    @Query("SELECT * FROM articles WHERE id = :id")
    suspend fun getArticleById(id: Long): Article?

    @Transaction
    @Query("SELECT * FROM articles WHERE id = :id")
    suspend fun getArticleWithFeedById(id: Long): ArticleWithFeed?

    @Transaction
    @Query(
        """
        SELECT * FROM articles 
        ORDER BY COALESCE(pubDate, addedDate) DESC
    """
    )
    fun getAllArticlesWithFeed(): Flow<List<ArticleWithFeed>>

    @Query("SELECT * FROM articles WHERE feedId = :feedId")
    fun getArticlesByFeed(feedId: Long): Flow<List<Article>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertArticle(article: Article): Long

    @Update
    suspend fun updateArticle(article: Article)

    @Delete
    suspend fun deleteArticle(article: Article)

    @Query("UPDATE articles SET scrollPosition = :position, lastReadDate = :timestamp WHERE id = :id")
    suspend fun updateProgress(
        id: Long,
        position: Int,
        timestamp: Long = System.currentTimeMillis()
    )
}
