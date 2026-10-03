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
import dev.kettu.hyangsang.parser.ContentBlock
import kotlinx.coroutines.flow.Flow
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

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

    @Transaction
    @Query("SELECT * FROM articles WHERE savedDate IS NOT NULL ORDER BY savedDate DESC")
    fun getSavedArticlesWithFeed(): Flow<List<ArticleWithFeed>>

    @Query("SELECT COUNT(*) FROM articles WHERE feedId = :feedId AND savedDate IS NOT NULL")
    suspend fun countSavedInFeed(feedId: Long): Int

    @Query("SELECT * FROM articles WHERE feedId = :feedId")
    fun getArticlesByFeed(feedId: Long): Flow<List<Article>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertArticle(article: Article): Long

    // Inserts a whole feed's articles in one transaction; existing articles are skipped
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertArticles(articles: List<Article>): List<Long>

    @Update
    suspend fun updateArticle(article: Article)

    @Delete
    suspend fun deleteArticle(article: Article)

    // Targeted updates, so a stale Article copy can't overwrite columns changed elsewhere
    @Query("UPDATE articles SET content = :content, parserVersion = :parserVersion WHERE id = :id")
    suspend fun updateContent(id: Long, content: List<ContentBlock>?, parserVersion: Int)

    // Replaces re-fetched content, clamping the saved position (a content block index) so it
    // still points into the new, possibly shorter, content
    @Query(
        "UPDATE articles SET content = :content, parserVersion = :parserVersion, " +
                "scrollPosition = MAX(0, MIN(scrollPosition, :maxPosition)) WHERE id = :id"
    )
    suspend fun replaceContent(
        id: Long,
        content: List<ContentBlock>,
        maxPosition: Int,
        parserVersion: Int
    )

    @Query("UPDATE articles SET parserVersion = :parserVersion WHERE id = :id")
    suspend fun updateParserVersion(id: Long, parserVersion: Int)

    @Query("UPDATE articles SET savedDate = :savedDate WHERE id = :id")
    suspend fun updateSavedDate(id: Long, savedDate: String?)

    @OptIn(ExperimentalTime::class)
    @Query("UPDATE articles SET scrollPosition = :position, lastReadDate = :timestamp WHERE id = :id")
    suspend fun updateProgress(
        id: Long,
        position: Int,
        timestamp: String = Clock.System.now().toString()
    )
}
