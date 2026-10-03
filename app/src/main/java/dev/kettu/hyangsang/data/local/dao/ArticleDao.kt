package dev.kettu.hyangsang.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.MapColumn
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import dev.kettu.hyangsang.data.local.entity.Article
import dev.kettu.hyangsang.data.local.entity.ArticleSummaryWithFeed
import dev.kettu.hyangsang.data.local.entity.ArticleWithFeed
import dev.kettu.hyangsang.parser.ContentBlock
import kotlinx.coroutines.flow.Flow
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@Dao
interface ArticleDao {
    @Query("SELECT * FROM articles WHERE id = :id")
    suspend fun getArticleById(id: Long): Article?

    @Transaction
    @Query("SELECT * FROM articles WHERE id = :id")
    suspend fun getArticleWithFeedById(id: Long): ArticleWithFeed?

    // A null category or feed matches all; a blank query matches all and otherwise is a LIKE
    // pattern, escaped with a backslash
    @Transaction
    @Query(
        """
        SELECT a.id, a.feedId, a.title, a.description, a.addedDate, a.pubDate, a.lastReadDate,
            a.savedDate
        FROM articles a
        INNER JOIN rss_feeds f ON f.id = a.feedId
        WHERE (:category IS NULL OR f.category = :category)
            AND (:feedId IS NULL OR a.feedId = :feedId)
            AND (:unreadOnly = 0 OR a.lastReadDate IS NULL)
            AND (:query = '' OR a.title LIKE '%' || :query || '%' ESCAPE '\'
                OR a.description LIKE '%' || :query || '%' ESCAPE '\')
        ORDER BY COALESCE(a.pubDate, a.addedDate) DESC
    """
    )
    fun getArticleSummaries(
        category: String?,
        feedId: Long?,
        query: String,
        unreadOnly: Boolean
    ): Flow<List<ArticleSummaryWithFeed>>

    @Transaction
    @Query(
        """
        SELECT id, feedId, title, description, addedDate, pubDate, lastReadDate, savedDate
        FROM articles WHERE savedDate IS NOT NULL ORDER BY savedDate DESC
    """
    )
    fun getSavedArticleSummaries(): Flow<List<ArticleSummaryWithFeed>>

    // Feeds without unread articles are left out
    @Query("SELECT feedId, COUNT(*) AS unread FROM articles WHERE lastReadDate IS NULL GROUP BY feedId")
    fun getUnreadCounts(): Flow<Map<@MapColumn("feedId") Long, @MapColumn("unread") Int>>

    @Query("SELECT COUNT(*) FROM articles WHERE feedId = :feedId AND savedDate IS NOT NULL")
    suspend fun countSavedInFeed(feedId: Long): Int

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

    // Stores downloaded content, clamping the saved position (a content block index) so it
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

    // Drops the downloaded text of unsaved articles not opened since the cutoff; it's downloaded
    // again on the next open. Content fetched without being opened falls back to addedDate
    @Query(
        """
        UPDATE articles SET content = NULL, parserVersion = 0
        WHERE savedDate IS NULL AND content IS NOT NULL
            AND COALESCE(lastReadDate, addedDate) < :cutoff
    """
    )
    suspend fun clearContentNotOpenedSince(cutoff: String): Int

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
