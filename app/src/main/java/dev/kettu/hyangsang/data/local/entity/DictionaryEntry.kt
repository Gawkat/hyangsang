package dev.kettu.hyangsang.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "dictionary_entries",
    // Lookups query by word, which would otherwise scan the whole table
    indices = [Index("word")]
)
data class DictionaryEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val originalId: String, // The "val" attribute in the root of the JSON
    val word: String, // Lemma -> feat(writtenForm)
    val origin: String?, // For Hanja (e.g., "家")
    val homonymNumber: Int = 0, // feat(homonym_number)
    val partOfSpeech: String?, // feat(partOfSpeech) -> Translated to English
    val vocabularyLevel: String?, // feat(vocabularyLevel) -> Translated to English
    val semanticCategory: String?, // feat(semanticCategory)
    val lexicalUnit: String?, // feat(lexicalUnit) (Word, Idiom, Proverb)
    val pronunciation: String?, // WordForm -> feat(pronunciation)
    val audioUrl: String? // WordForm -> feat(sound)
)
