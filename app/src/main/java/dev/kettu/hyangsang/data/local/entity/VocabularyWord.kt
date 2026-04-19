package dev.kettu.hyangsang.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vocabulary")
data class VocabularyWord(
    @PrimaryKey
    val word: String, // The base form/stem of the word
    val status: Int = 0, // 0: New, 1: Learning, 2: Known, 3: Ignored
    val timesEncountered: Int = 0,
    val firstEncounteredDate: Long = System.currentTimeMillis(),
    val lastEncounteredDate: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false
)
