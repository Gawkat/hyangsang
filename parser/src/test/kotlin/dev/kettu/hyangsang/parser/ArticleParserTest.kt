package dev.kettu.hyangsang.parser

import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import org.jsoup.Jsoup
import org.junit.Test

class ArticleParserTest {
    private val articleParser = ArticleParser()

    @Test
    fun `should use BbcNewsParser for BBC URLs`() {
        val url = "https://feeds.bbci.co.uk/korean/rss.xml"
        val doc = Jsoup.parse("<html><body><main><p>BBC Contents</p></main></body></html>")

        val result = articleParser.parse(url, doc)
        assertTrue(result.any { it is ContentBlock.Text && it.text == "BBC Contents" })
    }

    @Test
    fun `should use YonhapNewsParser for Yonhap URLs`() {
        val url = "https://www.yna.co.kr/rss/entertainment.xml"
        val doc = Jsoup.parse("<html><body><article><p>Yonhap Contents</p></article></body></html>")

        val result = articleParser.parse(url, doc)
        assertTrue(result.any { it is ContentBlock.Text && it.text == "Yonhap Contents" })
    }

    @Test
    fun `should use EasyLawParser for easylaw URLs`() {
        val url = "https://www.easylaw.go.kr/CSP/CnpClsMain.laf?popMenu=ov&csmSeq=1"
        val doc = Jsoup.parse("<html><body><div id=\"ovDiv\"><div class='plv3'>EasyLaw Contents</div></div></body></html>")

        val result = articleParser.parse(url, doc)
        assertEquals(listOf(ContentBlock.Text("EasyLaw Contents")), result)
    }

    @Test
    fun `articleUrl leaves links from other sites as they are`() {
        val url = "http://www.yna.co.kr/view/AKR1"
        assertEquals(url, ArticleParser.articleUrl(url))
    }

    @Test
    fun `articleUrl drops easylaw links without an article`() {
        assertEquals(null, ArticleParser.articleUrl("https://www.easylaw.go.kr/CSP/EasyLawInfoR.laf?easySeq=1"))
    }

    @Test
    fun `should use NdSoftNewsParser for pages on the ND Soft CMS`() {
        val url = "https://www.mediatoday.co.kr/news/articleView.html?idxno=1"
        val doc = Jsoup.parse(
            """
            <html><body>
                <nav><p>Navigation</p></nav>
                <article id="article-view-content-div"><p>ND Soft Contents</p></article>
                <article class="article-copy">Copyright</article>
            </body></html>
            """.trimIndent()
        )

        val result = articleParser.parse(url, doc)
        assertEquals(
            listOf(ContentBlock.Text("ND Soft Contents"), ContentBlock.Footer("Copyright")),
            result
        )
    }

    @Test
    fun `should use GenericContentsParser for unknown URLs`() {
        val url = "https://example.com/article"
        val doc =
            Jsoup.parse("<html><body><article><p>Hello <b>World</b></p></article></body></html>")

        val result = articleParser.parse(url, doc)
        val textBlock = result.first() as ContentBlock.Text
        assertEquals("Hello World", textBlock.text)
        assertEquals(1, textBlock.spans.size)
        assertEquals(SpanType.BOLD, textBlock.spans.first().type)
        assertEquals(6, textBlock.spans.first().start)
        assertEquals(11, textBlock.spans.first().end)
    }

    @Test
    fun `should parse images and headings`() {
        val url = "https://example.com/article"
        val html = """
            <html>
            <body>
                <article>
                    <h1>Main Title</h1>
                    <p>Paragraph 1</p>
                    <figure>
                        <img src="https://example.com/img.jpg" alt="Alt Text">
                        <figcaption>Image <b>Caption</b></figcaption>
                    </figure>
                </article>
            </body>
            </html>
        """.trimIndent()
        val doc = Jsoup.parse(html)
        doc.setBaseUri("https://example.com")

        val result = articleParser.parse(url, doc)

        assertEquals(3, result.size)
        assertTrue(result[0] is ContentBlock.Heading)
        assertEquals("Main Title", (result[0] as ContentBlock.Heading).text)

        assertTrue(result[1] is ContentBlock.Text)
        assertEquals("Paragraph 1", (result[1] as ContentBlock.Text).text)

        assertTrue(result[2] is ContentBlock.Image)
        val imageBlock = result[2] as ContentBlock.Image
        assertEquals("https://example.com/img.jpg", imageBlock.url)
        assertEquals("Image Caption", imageBlock.caption)
        assertEquals(1, imageBlock.captionSpans.size)
        assertEquals(SpanType.BOLD, imageBlock.captionSpans.first().type)
        assertEquals(6, imageBlock.captionSpans.first().start)
        assertEquals(13, imageBlock.captionSpans.first().end)
    }

    @Test
    fun `should parse image dimensions and skip tracking pixels`() {
        val url = "https://example.com/article"
        val html = """
            <html>
            <body>
                <article>
                    <figure>
                        <img src="https://example.com/sized.jpg" width="640px" height="360">
                    </figure>
                    <img src="https://example.com/unsized.jpg" width="100%">
                    <img src="https://example.com/pixel.gif" width="1" height="1">
                </article>
            </body>
            </html>
        """.trimIndent()
        val doc = Jsoup.parse(html)
        doc.setBaseUri("https://example.com")

        val result = articleParser.parse(url, doc)

        assertEquals(2, result.size)
        val sized = result[0] as ContentBlock.Image
        assertEquals(640, sized.width)
        assertEquals(360, sized.height)

        val unsized = result[1] as ContentBlock.Image
        assertEquals("https://example.com/unsized.jpg", unsized.url)
        assertEquals(null, unsized.width)
        assertEquals(null, unsized.height)
    }

    @Test
    fun `should extract Yonhap dateline`() {
        val html = """
            <html>
            <body>
                <div class="story-news article">
                    <p>(파리=연합뉴스) 송진원 특파원 = 프랑스 파리에서...</p>
                </div>
            </body>
            </html>
        """.trimIndent()
        val doc = Jsoup.parse(html)

        val parser = YonhapNewsParser()
        val result = parser.extractContents(doc)

        assertEquals(2, result.size)
        assertTrue(result[0] is ContentBlock.Dateline)
        assertEquals("(파리=연합뉴스) 송진원 특파원", (result[0] as ContentBlock.Dateline).text)

        assertTrue(result[1] is ContentBlock.Text)
        assertEquals("프랑스 파리에서...", (result[1] as ContentBlock.Text).text)
    }

    @Test
    fun `should extract BBC byline`() {
        val html = """
            <html>
            <body>
                <main>
                    <section data-testid="byline">
                        <strong id="article-byline">기사 관련 정보</strong>
                        <ul>
                            <li>
                                <span>기자, </span><span>소피아 페헤이라 산투스</span>
                            </li>
                            <li>
                                <div><span>게재 시간 </span><time datetime="2026-08-01">8시간 전</time></div>
                            </li>
                            <li>
                                <div data-testid="read-time"><span>읽는 시간: 4 분</span></div>
                            </li>
                        </ul>
                    </section>
                    <p>Main content start.</p>
                </main>
            </body>
            </html>
        """.trimIndent()
        val doc = Jsoup.parse(html)
        val parser = BbcNewsParser()
        val result = parser.extractContents(doc)

        assertEquals(2, result.size)
        assertTrue(result[0] is ContentBlock.Dateline)
        assertEquals("기자, 소피아 페헤이라 산투스", (result[0] as ContentBlock.Dateline).text)

        assertTrue(result[1] is ContentBlock.Text)
        assertEquals("Main content start.", (result[1] as ContentBlock.Text).text)
    }
}
