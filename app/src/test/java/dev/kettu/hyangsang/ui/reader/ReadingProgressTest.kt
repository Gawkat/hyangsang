package dev.kettu.hyangsang.ui.reader

import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.ui.unit.IntSize
import dev.kettu.hyangsang.parser.ContentBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingProgressTest {

    private class Item(override val index: Int, override val offset: Int, override val size: Int) :
        LazyListItemInfo {
        override val key: Any get() = index
    }

    private class Layout(
        override val visibleItemsInfo: List<LazyListItemInfo>,
        override val totalItemsCount: Int,
        override val viewportSize: IntSize
    ) : LazyListLayoutInfo {
        override val viewportStartOffset = 0
        override val viewportEndOffset = viewportSize.height
    }

    private val width = 1000
    private val viewport = IntSize(width, 1000)

    // A header, the blocks and a spacer, laid out scrolled by [scrolled] pixels
    private fun layout(heights: List<Int>, scrolled: Int): Layout {
        var top = -scrolled
        val visible = heights.mapIndexedNotNull { index, height ->
            val item = Item(index, top, height)
            top += height
            item.takeIf { it.offset + height > 0 && it.offset < viewport.height }
        }
        return Layout(visible, heights.size, viewport)
    }

    private fun text(length: Int) = ContentBlock.Text("가".repeat(length))

    // Two pixels a character, and images as tall as the estimate
    private val blocks = List(8) { text(150) } + ContentBlock.Image("https://example.com/a.jpg") + List(8) { text(150) }
    private val heights = listOf(200) + blocks.map { if (it is ContentBlock.Image) 600 else 300 } + 50
    private val maxScroll = heights.sum() - viewport.height

    // Where the bottom of the last block shows, above the spacer
    private val endOfText = maxScroll - heights.last()

    @Test
    fun `nothing is measured before the list is laid out`() {
        val measure = ScrollMeasure(blocks, firstBlockItem = 1)
        assertNull(measure.progress(Layout(emptyList(), heights.size, viewport)))
    }

    @Test
    fun `progress grows steadily from the top to the bottom`() {
        val measure = ScrollMeasure(blocks, firstBlockItem = 1)
        val steps = (0..endOfText step 10).map { measure.progress(layout(heights, it))!! }

        assertEquals(0f, steps.first())
        assertEquals(1f, steps.last())
        val expected = 10f / endOfText
        steps.zipWithNext { a, b -> assertEquals(expected, b - a, 0.0005f) }
        // The spacer after the text doesn't move it back
        assertEquals(1f, measure.progress(layout(heights, maxScroll)))
    }

    @Test
    fun `the end reads as all of it, however wrong the estimates above were`() {
        val measure = ScrollMeasure(blocks, firstBlockItem = 1)
        // Opened at the end, with the text above taller than estimated
        val taller = heights.mapIndexed { index, height -> if (index in 1..10) height * 2 else height }
        assertEquals(1f, measure.progress(layout(taller, taller.sum() - viewport.height)))
    }

    @Test
    fun `an article that fits on screen is all read`() {
        val short = listOf(text(100))
        val measure = ScrollMeasure(short, firstBlockItem = 1)
        assertEquals(1f, measure.progress(layout(listOf(200, 200, 50), 0)))
    }

    @Test
    fun `progress stays within 0 and 1`() {
        assertEquals(1f, scrollProgress(120.0, 100.0))
        assertEquals(0f, scrollProgress(-5.0, 100.0))
        assertTrue(scrollProgress(50.0, 100.0) == 0.5f)
    }
}
