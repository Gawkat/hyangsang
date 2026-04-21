package dev.kettu.hyangsang.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "dictionary_examples",
    foreignKeys = [
        ForeignKey(
            entity = DictionarySense::class,
            parentColumns = ["senseId"],
            childColumns = ["senseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("senseId")]
)
data class DictionaryExample(
    @PrimaryKey(autoGenerate = true)
    val exampleId: Long = 0,
    val senseId: Long,
    val example: String,
    val type: String? // "구" (Phrase), "문장" (Sentence), "대화" (Dialogue) -> Can be translated
)
