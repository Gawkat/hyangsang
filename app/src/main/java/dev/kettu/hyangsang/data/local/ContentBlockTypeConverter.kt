package dev.kettu.hyangsang.data.local

import androidx.room.TypeConverter
import dev.kettu.hyangsang.parser.ContentBlock
import kotlinx.serialization.json.Json

class ContentBlockTypeConverter {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromContentBlocks(blocks: List<ContentBlock>?): String? {
        return blocks?.let { json.encodeToString(it) }
    }

    @TypeConverter
    fun toContentBlocks(value: String?): List<ContentBlock>? {
        if (value == null) return null
        return json.decodeFromString<List<ContentBlock>>(value)
    }
}
