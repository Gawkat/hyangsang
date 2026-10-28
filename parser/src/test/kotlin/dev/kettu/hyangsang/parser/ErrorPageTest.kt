package dev.kettu.hyangsang.parser

import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import org.jsoup.Jsoup
import org.junit.Test

class ErrorPageTest {
    private val articleParser = ArticleParser()

    // Parses [html] as if the request for [url] ended up at [location]
    private fun isErrorPage(url: String, html: String, location: String = url): Boolean =
        articleParser.isErrorPage(url, Jsoup.parse(html, location))

    @Test
    fun `Yonhap's not found page is an error page`() {
        val url = "https://www.yna.co.kr/view/AKR20251020000100001"
        val html = """
            <html><head><title> | 연합뉴스</title></head><body>
            <nav><a href="/politics">정치</a><a href="/economy">경제</a></nav>
            <div class="container591 article-deleted"><article id="articleWrap" class="article-wrap01">
                <section class="box-type090 box-deleted">
                    <h1 class="title-type017"><span class="tit01">원하시는 페이지를 찾을 수<br>없습니다.</span></h1>
                    <p class="txt01">페이지가 존재하지 않거나,<br>현재 사용할 수 없는 페이지입니다.</p>
                </section>
            </article></div>
            </body></html>
        """.trimIndent()

        assertTrue(isErrorPage(url, html))
    }

    @Test
    fun `SBS's page for a missing article is an error page`() {
        val url = "https://news.sbs.co.kr/news/endPage.do?news_id=N1999999999"
        val html = """
            <html><head><title>기사 내용이 없습니다.</title>
            <meta name="robots" content="noindex nofollow">
            <link rel="canonical" href="https://news.sbs.co.kr/news/endPage.do?news_id=null">
            </head><body><div class="w_news_list"><a href="/news/1">다른 기사</a></div></body></html>
        """.trimIndent()

        assertTrue(isErrorPage(url, html))
    }

    @Test
    fun `an SBS article isn't an error page`() {
        val url = "https://news.sbs.co.kr/news/endPage.do?news_id=N1008819907"
        val html = """
            <html><head><meta name="robots" content="max-image-preview:large"></head><body>
            <div class="main_text"><div class="text_area">본문</div></div>
            </body></html>
        """.trimIndent()

        assertFalse(isErrorPage(url, html))
    }

    @Test
    fun `EasyLaw pages that redirect away from the article are error pages`() {
        val guide = "https://www.easylaw.go.kr/CSP/CnpClsMain.laf?popMenu=ov&csmSeq=999999&ccfNo=3"
        val answer = "https://www.easylaw.go.kr/CSP/OnhunqueansInfoRetrieve.laf?onhunqnaAstSeq=1&onhunqueSeq=9"
        val html = "<html><body><p>찾기쉬운 생활법령정보</p></body></html>"

        assertTrue(isErrorPage(guide, html, "https://www.easylaw.go.kr/CSP/Main.laf"))
        assertTrue(isErrorPage(answer, html, "https://www.easylaw.go.kr/CSP/OnhunqueansLstRetrieve.laf"))
        assertFalse(isErrorPage(guide, "<div id='ovDiv'><div class='plv3'>본문</div></div>"))
    }

    @Test
    fun `a page that only alerts and goes back is an error page`() {
        val url = "https://www.kidshankook.kr/news/articleView.html?idxno=9999999"
        val html = "<script>alert('존재하지 않는 링크 입니다.\\n링크를 확인해 주세요.'); history.back(); </script>"

        assertTrue(isErrorPage(url, html))
    }

    @Test
    fun `a page with text isn't an error page for a script that goes back`() {
        val url = "https://example.com/article/1"
        val html = "<p>본문</p><button onclick='history.back()'>뒤로</button><script>history.back()</script>"

        assertFalse(isErrorPage(url, html))
    }

    @Test
    fun `a redirect to the home page is an error page`() {
        assertTrue(isErrorPage("https://example.com/article/1", "<p>홈</p>", "https://example.com/"))
        assertFalse(isErrorPage("https://example.com/", "<p>홈</p>", "https://example.com/"))
    }

    @Test
    fun `a page giving a parent of its path as its address is an error page`() {
        val url = "https://example.com/view/AKR1"

        assertTrue(isErrorPage(url, "<link rel='canonical' href='https://example.com/view/'><p>메뉴</p>"))
        assertTrue(isErrorPage(url, "<meta property='og:url' content='https://www.example.com'><p>메뉴</p>"))
    }

    @Test
    fun `a page giving its own or another address isn't an error page`() {
        val url = "https://www.example.com/view/AKR1/"

        assertFalse(isErrorPage(url, "<link rel='canonical' href='https://example.com/view/AKR1'><p>본문</p>"))
        assertFalse(isErrorPage(url, "<link rel='canonical' href='https://example.com/view/AKR10'><p>본문</p>"))
        assertFalse(isErrorPage(url, "<link rel='canonical' href='https://example.com/view?id=1'><p>본문</p>"))
        // A copy of the article on another site
        assertFalse(isErrorPage(url, "<link rel='canonical' href='https://other.example/'><p>본문</p>"))
    }

    @Test
    fun `the saved samples aren't error pages`() {
        listOf(
            "bbc_sample.html", "yonhap_sample.htm", "sbs_sample.html", "hani_sample.html",
            "easylaw_sample.html", "easylaw_qna_sample.html", "kids_donga_sample.html",
            "slownews_sample.html", "slownews_letter_sample.html", "donga_sample.html",
            "donga_wire_sample.html"
        ).forEach { name ->
            val html = readSample(name)
            // The samples don't keep their URL, but most give it as their address
            val address = Jsoup.parse(html).let {
                it.selectFirst("meta[property=og:url]")?.attr("content")
                    ?: it.selectFirst("link[rel=canonical]")?.attr("href")
            }
            if (address.isNullOrEmpty()) return@forEach
            assertFalse(name, isErrorPage(address, html))
        }
    }
}
