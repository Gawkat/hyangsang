package dev.kettu.hyangsang.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class WordRangesTest {

    private fun wordsIn(text: String): List<String> {
        val ranges = WordRanges.of(text)
        return ranges.starts.indices.map { text.substring(ranges.starts[it], ranges.ends[it]) }
    }

    @Test
    fun `words are split at spaces`() {
        assertEquals(listOf("경기", "침체가", "이어졌다."), wordsIn("경기  침체가\n이어졌다."))
    }

    @Test
    fun `punctuation without spaces splits words`() {
        assertEquals(listOf("포프모빌", "콘서트장"), wordsIn("포프모빌…콘서트장"))
        assertEquals(listOf("한", "미", "정상회담"), wordsIn("한·미 정상회담"))
        assertEquals(listOf("다음", "주", "9월", "28일", "10월", "2일", "에는"), wordsIn("다음 주(9월 28일∼10월 2일)에는"))
        assertEquals(listOf("문화가", "있는", "날", "을"), wordsIn("'문화가 있는 날'을"))
    }

    @Test
    fun `numbers keep their decimal points and commas`() {
        assertEquals(listOf("3.5%", "1,000명"), wordsIn("3.5% 1,000명"))
    }

    @Test
    fun `a tap on the word's last glyph finds the word`() {
        val ranges = WordRanges.of("포프모빌…콘서트장")
        assertEquals(0, ranges.indexAt(3))
        assertEquals(1, ranges.indexAt(5))
    }
}
