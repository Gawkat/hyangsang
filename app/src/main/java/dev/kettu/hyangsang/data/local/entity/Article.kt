package dev.kettu.hyangsang.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "articles",
    foreignKeys = [
        ForeignKey(
            entity = RssFeed::class,
            parentColumns = ["id"],
            childColumns = ["feedId"],
            onDelete = ForeignKey.CASCADE // If a feed is deleted, delete its articles
        )
    ],
    indices = [Index(value = ["feedId"])]
)
data class Article(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val feedId: Long,
    val title: String,
    val description: String,
    val content: String? = null,
    val sourceUrl: String? = null,
    val addedDate: Long = System.currentTimeMillis(),
    val pubDate: String?,
    val lastReadDate: Long? = null,
    val scrollPosition: Int = 0
)