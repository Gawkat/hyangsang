package dev.kettu.hyangsang.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "rss_feeds",
    indices = [Index(value = ["url"], unique = true)]
)
data class RssFeed(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    val category: String,
    val isEnabled: Boolean = true,
    val lastSynced: Long = 0L
)

data class RssItem(
    val title: String,
    val link: String,
    val pubDate: String?,
    val description: String
)