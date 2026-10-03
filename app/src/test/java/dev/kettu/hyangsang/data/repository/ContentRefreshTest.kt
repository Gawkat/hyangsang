package dev.kettu.hyangsang.data.repository

import dev.kettu.hyangsang.parser.ContentBlock
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentRefreshTest {

    private fun text(length: Int) = ContentBlock.Text("가".repeat(length))

    private val stored = listOf(text(400), text(400))

    @Test
    fun `an edited article of similar length replaces the stored one`() {
        assertTrue(isUsableRefresh(stored, listOf(text(400), text(300))))
    }

    @Test
    fun `a parse without text is rejected`() {
        assertFalse(isUsableRefresh(stored, emptyList()))
        assertFalse(isUsableRefresh(stored, listOf(ContentBlock.Image("https://example.com/a.jpg"))))
        assertFalse(isUsableRefresh(stored, listOf(ContentBlock.Text("   "))))
    }

    @Test
    fun `a much shorter parse is rejected as a likely error page`() {
        assertFalse(isUsableRefresh(stored, listOf(text(100))))
    }

    @Test
    fun `a parse with text fills an article that had none`() {
        assertTrue(isUsableRefresh(emptyList(), listOf(text(10))))
    }

    @Test
    fun `a longer parse fixing missing content is accepted`() {
        assertTrue(isUsableRefresh(listOf(text(50)), listOf(text(50), text(800))))
    }
}
