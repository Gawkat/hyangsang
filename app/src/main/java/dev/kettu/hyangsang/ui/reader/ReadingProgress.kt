package dev.kettu.hyangsang.ui.reader

import androidx.compose.foundation.lazy.LazyListLayoutInfo
import dev.kettu.hyangsang.data.repository.charCount
import dev.kettu.hyangsang.parser.ContentBlock

// The height of an image not shown yet, against the width of the list: a typical news photo
private const val IMAGE_ASPECT = 0.6f

/**
 * Measures how far a list of content blocks is scrolled, like a scrollbar. A lazy list only lays out
 * what's on screen, so the other items are as tall as they were when they last showed, or estimated:
 * text by the height per character of the text measured so far, and images as a typical photo.
 * Item [firstBlockItem] is the first of [blocks]; the items before them are measured as they show.
 */
class ScrollMeasure(blocks: List<ContentBlock>, private val firstBlockItem: Int) {
    private val chars = blocks.map { it.charCount() }
    private val images = blocks.map { it is ContentBlock.Image }
    private val measured = HashMap<Int, Int>()

    /** From 0 at the top to 1 at the bottom, or null before the list is laid out. */
    fun progress(info: LazyListLayoutInfo): Float? {
        val visible = info.visibleItemsInfo
        if (visible.isEmpty()) return null
        visible.forEach { measured[it.index] = it.size }

        var textHeight = 0f
        var textChars = 0
        measured.forEach { (item, size) ->
            val itemChars = chars.getOrElse(item - firstBlockItem) { 0 }
            if (itemChars > 0) {
                textHeight += size
                textChars += itemChars
            }
        }
        val heightPerChar = if (textChars == 0) 0f else textHeight / textChars

        fun height(item: Int): Float = measured[item]?.toFloat() ?: run {
            val block = item - firstBlockItem
            if (images.getOrElse(block) { false }) {
                info.viewportSize.width * IMAGE_ASPECT
            } else {
                chars.getOrElse(block) { 0 } * heightPerChar
            }
        }

        val first = visible.first()
        val scrolled = (0 until first.index).sumOf { height(it).toDouble() } - first.offset
        // Read to the end once the bottom of the last block shows, so the items after it and the
        // padding, which grows while the lookup sheet is open, are left out
        val content = (0 until firstBlockItem + chars.size).sumOf { height(it).toDouble() }
        val range = content + info.beforeContentPadding - info.viewportSize.height
        return scrollProgress(scrolled, range)
    }
}

/** How far [scrolled] is through [range]. A list that fits on screen is all read. */
fun scrollProgress(scrolled: Double, range: Double): Float =
    if (range <= 0.0) 1f else (scrolled / range).toFloat().coerceIn(0f, 1f)
