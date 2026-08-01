package dev.kettu.hyangsang.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.kettu.hyangsang.parser.ContentBlock
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

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
    indices = [
        Index(value = ["feedId"]),
        Index(value = ["pubDate"]),
        Index(value = ["addedDate"]),
        Index(value = ["sourceUrl"], unique = true)
    ]
)
data class Article @OptIn(ExperimentalTime::class) constructor(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val feedId: Long,
    val title: String,
    val description: String,
    val content: List<ContentBlock>? = null,
    val sourceUrl: String,
    val addedDate: String = Clock.System.now().toString(),
    val pubDate: String?,
    val lastReadDate: String? = null,
    val scrollPosition: Int = 0
)