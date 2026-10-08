package dev.kettu.hyangsang.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import dev.kettu.hyangsang.data.local.entity.RssFeed
import kotlinx.coroutines.flow.Flow

@Dao
interface RssFeedDao {
    @Query("SELECT * FROM rss_feeds ORDER BY title ASC")
    fun getAllFeeds(): Flow<List<RssFeed>>

    @Query("SELECT * FROM rss_feeds WHERE isEnabled = 1 ORDER BY title ASC")
    fun getEnabledFeeds(): Flow<List<RssFeed>>

    @Query("SELECT * FROM rss_feeds WHERE url = :url LIMIT 1")
    suspend fun getFeedByUrl(url: String): RssFeed?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFeed(feed: RssFeed): Long

    // Doesn't touch url or sync status
    @Query("UPDATE rss_feeds SET title = :title, category = :category WHERE id = :id")
    suspend fun updateFeedDetails(id: Long, title: String, category: String)

    @Update
    suspend fun updateFeed(feed: RssFeed)

    @Delete
    suspend fun deleteFeed(feed: RssFeed)

    @Query("UPDATE rss_feeds SET isEnabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("UPDATE rss_feeds SET isEnabled = :enabled WHERE id IN (:ids)")
    suspend fun setEnabledForIds(ids: List<Long>, enabled: Boolean)

    @Transaction
    suspend fun setEnabledStates(enabledIds: List<Long>, disabledIds: List<Long>) {
        setEnabledForIds(enabledIds, true)
        setEnabledForIds(disabledIds, false)
    }

    @Query("UPDATE rss_feeds SET category = :newName WHERE category = :oldName")
    suspend fun renameCategory(oldName: String, newName: String)

    @Query("UPDATE rss_feeds SET lastSynced = :timestamp, lastSyncAttempt = :timestamp, lastSyncError = NULL WHERE id = :id")
    suspend fun markSyncSucceeded(id: Long, timestamp: String)

    @Query("UPDATE rss_feeds SET lastSyncAttempt = :timestamp, lastSyncError = :error WHERE id = :id")
    suspend fun markSyncFailed(id: Long, timestamp: String, error: String)
}
