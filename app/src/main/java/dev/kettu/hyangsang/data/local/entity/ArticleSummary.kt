package dev.kettu.hyangsang.data.local.entity

import androidx.room.Embedded
import androidx.room.Relation

// An article without its content, for lists. Loading the content of every stored article each
// time the list changes would grow with the whole index
data class ArticleSummary(
    val id: Long,
    val feedId: Long,
    val title: String,
    val description: String,
    val addedDate: String,
    val pubDate: String?,
    val lastReadDate: String?,
    val savedDate: String?
)

data class ArticleSummaryWithFeed(
    @Embedded val article: ArticleSummary,
    @Relation(
        parentColumn = "feedId",
        entityColumn = "id"
    )
    val feed: RssFeed
)
