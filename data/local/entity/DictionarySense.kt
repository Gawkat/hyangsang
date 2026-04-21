package dev.kettu.hyangsang.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "dictionary_senses",
    foreignKeys = [
        ForeignKey(
            entity = DictionaryEntry::class,
            parentColumns = ["id"],
            childColumns = ["entryId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("entryId")]
)
data class DictionarySense(
    @PrimaryKey(autoGenerate = true)
    val senseId: Long = 0,
    val entryId: String,
    val definitionKo: String,
    val definitionEn: String?,
    val translationEn: String? // The English 'lemma' from Equivalent
)
