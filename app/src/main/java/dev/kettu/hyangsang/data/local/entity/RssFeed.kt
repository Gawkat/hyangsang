package dev.kettu.hyangsang.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@Entity(
    tableName = "rss_feeds",
    indices = [Index(value = ["url"], unique = true)]
)
data class RssFeed @OptIn(ExperimentalTime::class) constructor(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    val category: String,
    val isEnabled: Boolean = true,
    val lastSynced: String = Instant.fromEpochMilliseconds(0).toString()
)

data class RssItem(
    val title: String,
    val link: String,
    val pubDate: String?,
    val description: String
)