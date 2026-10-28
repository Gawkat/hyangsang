package dev.kettu.hyangsang.ui.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.Normalizer

class SearchTermsTest {

    private fun nfd(text: String) = Normalizer.normalize(text, Normalizer.Form.NFD)

    @Test
    fun `a blank search matches everything`() {
        assertTrue(SearchTerms("  ").isEmpty)
        assertTrue(SearchTerms("").matches("코스피"))
        assertTrue(SearchTerms("").matches(null))
    }

    @Test
    fun `every word has to appear, in any order and field`() {
        val search = SearchTerms("지수  코스피")
        assertEquals(listOf("지수", "코스피"), search.terms)
        assertTrue(search.matches("코스피지수 하락"))
        assertTrue(search.matches("코스피 반등", "외국인 매수에 지수 상승"))
        assertFalse(search.matches("코스피 반등", null))
    }

    @Test
    fun `latin letters match in any case`() {
        assertTrue(SearchTerms("bbc").matches("BBC News 코리아"))
    }

    @Test
    fun `decomposed hangul matches what is typed`() {
        assertTrue(SearchTerms("메밀꽃").matches(nfd("메밀꽃 필 무렵")))
        assertTrue(SearchTerms(nfd("메밀꽃")).matches("메밀꽃 필 무렵"))
    }

    @Test
    fun `ranges cover every match and merge where they overlap`() {
        assertEquals(listOf(0..2, 7..9), SearchTerms("코스피").matchRanges("코스피와 새 코스피"))
        assertEquals(listOf(0..3), SearchTerms("코스 스피지").matchRanges("코스피지수"))
        assertEquals(emptyList<IntRange>(), SearchTerms("환율").matchRanges("코스피"))
    }

    @Test
    fun `ranges of decomposed text are in its composed form`() {
        assertEquals(listOf(4..4), SearchTerms("필").matchRanges(nfd("메밀꽃 필 무렵")))
    }
}
