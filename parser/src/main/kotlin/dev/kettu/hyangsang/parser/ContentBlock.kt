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
        val captionSpans: List<ContentSpan> = emptyList(),
        // Intrinsic size from the HTML attributes, used to reserve layout space before loading
        val width: Int? = null,
        val height: Int? = null
    ) : ContentBlock()

    @Serializable
    data class Heading(
        val text: String,
        val level: Int
    ) : ContentBlock()

    @Serializable
    data class Dateline(
        val text: String
    ) : ContentBlock()

    // Trailing contact and copyright notes, shown de-emphasised after the article body
    @Serializable
    data class Footer(
        val text: String
    ) : ContentBlock()

    // A break between sections, from <hr>, shown as a line
    @Serializable
    data object Divider : ContentBlock()
}

@Serializable
data class ContentSpan(
    val start: Int,
    val end: Int,
    val type: SpanType,
    // For a NOTE span, the text of the note it marks
    val note: String? = null
)

@Serializable
enum class SpanType {
    BOLD,
    ITALIC,

    /** A footnote marker, such as [1], whose note shows when it's tapped */
    NOTE
}
