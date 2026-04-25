package dev.kettu.hyangsang.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "articles")
data class Article(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val content: String,
    val sourceUrl: String? = null,
    val addedDate: Long = System.currentTimeMillis(),
    val lastReadDate: Long? = null,
    val scrollPosition: Int = 0
)
