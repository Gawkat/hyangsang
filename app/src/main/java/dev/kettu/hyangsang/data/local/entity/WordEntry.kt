package dev.kettu.hyangsang.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "dictionary")
data class WordEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val word: String,
    val definition: String,
    val translation: String? = null,
    val pronunciation: String? = null,
    val pos: String? = null, // Part of Speech
    val synonyms: String? = null, // Comma-separated or serialized
    val examples: String? = null  // Serialized list of examples
)
