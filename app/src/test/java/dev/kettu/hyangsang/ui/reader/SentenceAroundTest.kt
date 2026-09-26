package dev.kettu.hyangsang.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class SentenceAroundTest {

    private fun sentenceOf(text: String, word: String): String {
        val start = text.indexOf(word)
        return sentenceAround(text, start, start + word.length)
    }

    @Test
    fun `finds the sentence in the middle of a paragraph`() {
        val text = "첫 문장이다. 경기 침체가 이어졌다. 마지막 문장이다."
        assertEquals("경기 침체가 이어졌다.", sentenceOf(text, "침체가"))
    }

    @Test
    fun `a word ending the sentence does not pull in the next one`() {
        val text = "경기 침체가 이어졌다. 다음 문장이다."
        assertEquals("경기 침체가 이어졌다.", sentenceOf(text, "이어졌다."))
    }

    @Test
    fun `decimal points do not end sentences`() {
        val text = "성장률은 3.5%로 떨어졌다. 다음 문장이다."
        assertEquals("성장률은 3.5%로 떨어졌다.", sentenceOf(text, "떨어졌다."))
    }

    @Test
    fun `a period before a closing quote ends the sentence`() {
        val text = "그는 \"사과한다.\" 라고 말했다"
        assertEquals("그는 \"사과한다.", sentenceOf(text, "그는"))
    }

    @Test
    fun `text without terminators is one sentence`() {
        assertEquals("경기 침체 우려", sentenceOf("경기 침체 우려", "침체"))
    }
}
