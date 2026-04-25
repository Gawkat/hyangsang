package dev.kettu.hyangsang.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import dev.kettu.hyangsang.data.local.entity.RssFeed
import kotlinx.coroutines.flow.Flow

@Dao
interface RssFeedDao {
    @Query("SELECT * FROM rss_feeds ORDER BY title ASC")
    fun getAllFeeds(): Flow<List<RssFeed>>

    @Query("SELECT * FROM rss_feeds WHERE isEnabled = 1 ORDER BY title ASC")
    fun getEnabledFeeds(): Flow<List<RssFeed>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFeed(feed: RssFeed): Long

    @Update
    suspend fun updateFeed(feed: RssFeed)

    @Delete
    suspend fun deleteFeed(feed: RssFeed)
}
