package dev.kettu.hyangsang.data.importing

import org.junit.Assert.assertEquals
import org.junit.Test

class SharedTextTest {
    @Test
    fun readsALinkOnItsOwn() {
        assertEquals(
            SharedText.Link("https://www.yna.co.kr/view/AKR1", "기사 제목"),
            SharedText.classify(" https://www.yna.co.kr/view/AKR1\n", subject = "기사 제목")
        )
    }

    @Test
    fun readsATitleFollowedByALink() {
        assertEquals(
            SharedText.Link("https://n.news.naver.com/article/1", null),
            SharedText.classify("[네이버 뉴스] 기사 제목\nhttps://n.news.naver.com/article/1")
        )
    }

    @Test
    fun readsLongerTextEndingInALinkAsText() {
        val text = "가".repeat(200) + " https://example.com"
        assertEquals(SharedText.Text(text, null), SharedText.classify(text))
    }

    @Test
    fun readsTextWithoutALinkAsText() {
        assertEquals(
            SharedText.Text("첫 문장.\n둘째 문장.", "제목"),
            SharedText.classify("첫 문장.\n둘째 문장.\n", subject = " 제목 ")
        )
        assertEquals(SharedText.Text("ftp://example.com", null), SharedText.classify("ftp://example.com"))
    }
}
