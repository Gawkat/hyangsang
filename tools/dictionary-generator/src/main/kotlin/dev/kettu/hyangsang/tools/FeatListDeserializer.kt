package dev.kettu.hyangsang.tools

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import java.lang.reflect.Type

class FeatListDeserializer : JsonDeserializer<List<Feat>> {
    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext
    ): List<Feat> {
        return when {
            // If it's an array [{}, {}], parse it as a list
            json.isJsonArray -> {
                json.asJsonArray.map { context.deserialize<Feat>(it, Feat::class.java) }
            }
            // If it's a single object {}, wrap it in a list
            json.isJsonObject -> {
                listOf(context.deserialize<Feat>(json, Feat::class.java))
            }
            // If it's null or something else, return empty list
            else -> emptyList()
        }
    }
}