package dev.kettu.hyangsang.tools

import com.google.gson.Gson
import com.google.gson.TypeAdapter
import com.google.gson.TypeAdapterFactory
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import java.lang.reflect.ParameterizedType

class EnsureListTypeAdapterFactory : TypeAdapterFactory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> create(gson: Gson, type: TypeToken<T>): TypeAdapter<T>? {
        // We only care about fields that are Lists
        if (!MutableList::class.java.isAssignableFrom(type.rawType)) {
            return null
        }

        val elementType = (type.type as ParameterizedType).actualTypeArguments[0]
        val elementAdapter = gson.getAdapter(TypeToken.get(elementType)) as TypeAdapter<Any?>

        return object : TypeAdapter<T>() {
            override fun write(out: JsonWriter, value: T?) {
                if (value == null) {
                    out.nullValue()
                    return
                }

                out.beginArray()
                for (item in value as List<*>) {
                    elementAdapter.write(out, item)
                }
                out.endArray()
            }

            override fun read(reader: JsonReader): T? {
                val list = mutableListOf<Any?>()

                when (reader.peek()) {
                    JsonToken.BEGIN_ARRAY -> {
                        reader.beginArray()
                        while (reader.hasNext()) {
                            list.add(elementAdapter.read(reader))
                        }
                        reader.endArray()
                    }

                    JsonToken.NULL -> {
                        reader.nextNull()
                        return null
                    }

                    else -> {
                        // If it's a single value instead of an array, wrap it in a list
                        list.add(elementAdapter.read(reader))
                    }
                }
                return list as T
            }
        }.nullSafe()
    }
}