package dev.kettu.hyangsang.parser

import kotlinx.serialization.Serializable

@Serializable
sealed class ContentBlock {
    @Serializable
    data class Text(
        val text: String,
        val spans: List<ContentSpan> = emptyList()
    ) : ContentBlock()

    @Serializable
    data class Image(
        val url: String,
        val caption: String? = null,
        val captionSpans: List<ContentSpan> = emptyList()
    ) : ContentBlock()

    @Serializable
    data class Heading(
        val text: String,
        val level: Int
    ) : ContentBlock()

    @Serializable
    data class Legacy(
        val text: String
    ) : ContentBlock()

    @Serializable
    data class Dateline(
        val text: String
    ) : ContentBlock()
}

@Serializable
data class ContentSpan(
    val start: Int,
    val end: Int,
    val type: SpanType
)

@Serializable
enum class SpanType {
    BOLD,
    ITALIC
}
