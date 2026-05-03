package dev.kettu.hyangsang.data.local.entity

import androidx.room.Embedded
import androidx.room.Relation

data class ArticleWithFeed(
    @Embedded val article: Article,
    @Relation(
        parentColumn = "feedId",
        entityColumn = "id"
    )
    val feed: RssFeed
)