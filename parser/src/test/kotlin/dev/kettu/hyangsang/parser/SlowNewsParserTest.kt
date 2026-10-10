package dev.kettu.hyangsang.parser

import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import org.jsoup.Jsoup
import org.junit.Test

class SlowNewsParserTest {
    private val parser = SlowNewsParser()

    @Test
    fun `extracts an article`() {
        val html = readSample("slownews_sample.html")

        val result = parser.extractContents(Jsoup.parse(html, "https://slownews.kr/167638"))

        assertEquals(ContentBlock.Dateline("참여연대 사법감시센터"), result[0])
        val subtitle = result[1] as ContentBlock.Heading
        assertEquals(4, subtitle.level)
        assertTrue(subtitle.text.startsWith("[집시법 특집 판결비평②]"))
        assertTrue((result[2] as ContentBlock.Text).text.startsWith("2016년과 2024년 헌정질서를"))
        assertEquals(ContentBlock.Heading("‘집회’ 정의조차 없는 집회시위법", 3), result[3])

        val images = result.filterIsInstance<ContentBlock.Image>()
        assertEquals(4, images.size)
        assertTrue(images.all { it.url.startsWith("https://slownews.kr/wp-content/uploads/") })
        assertEquals("게티이미지.", images.first().caption)

        // The box about the series ends the article
        assertTrue(result.contains(ContentBlock.Heading("👨‍⚖️ 광장에 나온 판결: 325번째 이야기", 4)))
        assertTrue((result.last() as ContentBlock.Text).text.contains("참여연대 사법감시센터는 최근 판결 중"))
    }

    @Test
    fun `extracts a 슬로우레터`() {
        val html = readSample("slownews_letter_sample.html")

        val result = parser.extractContents(Jsoup.parse(html, "https://slownews.kr/167649"))

        assertEquals(ContentBlock.Dateline("이정환"), result[0])
        assertEquals(ContentBlock.Heading("농지조사 후퇴.", 4), result[2])
        assertTrue(result.contains(ContentBlock.Heading("쟁점과 현안.", 3)))
        assertTrue(result.contains(ContentBlock.Heading("삼성전자 3분기 영업이익 107조 원.", 4)))

        val texts = result.filterIsInstance<ContentBlock.Text>()
        assertTrue(texts.none { "구독을 추천" in it.text || "국정감사 리포트." in it.text })
        assertTrue(texts.last().text.startsWith("“서두를 시간이 없다”는 모순된 문장을"))
    }

    @Test
    fun `reads the body, author, subtitle and images`() {
        val html = """
            <html><body>
              <div class="entry-hero"><div class="entry-meta"><a href="/author/a" rel="author">김도연</a></div></div>
              <article class="single-entry"><div class="entry-content single-content"><div id="pavo_social_sharing"><div id="pavo_contents">
                <div class="wp-block-rank-math-toc-block" id="rank-math-toc">
                  <h2>[슬로우폴리시] 요약.</h2>
                  <nav><ul><li><a href="#a">이게 왜 중요한가.</a></li></ul></nav>
                </div>
                <figure class="wp-block-image"><img width="1000" height="563" src="data:image/png;base64,iVBOR" data-src="https://slownews.kr/wp-content/uploads/a.png"><noscript><img src="https://slownews.kr/wp-content/uploads/a.png"></noscript><figcaption>게티이미지. </figcaption></figure>
                <h3 class="wp-block-heading" id="a">이게 왜 중요한가.</h3>
                <ul class="wp-block-list"><li>택시가 가장 <a href="https://example.com">백발</a>이다.</li></ul>
                <p><a href="https://slownews.net/report.html">국정감사 리포트.</a></p>
                <div class="wp-block-group"><h2>슬로우레터를 구독하세요.</h2><div class="wp-block-buttons"><a href="/category/slowletter">목록 보기.</a></div></div>
              </div>
              <div class="social_share"><a class="pvss facebook_share"><img src="https://slownews.kr/wp-content/plugins/pavo-social-sharing/facebook.svg"></a></div>
              </div></div></article>
            </body></html>
        """.trimIndent()

        val result = parser.extractContents(Jsoup.parse(html, "https://slownews.kr/1"))

        assertEquals(
            listOf(
                ContentBlock.Dateline("김도연"),
                ContentBlock.Heading("[슬로우폴리시] 요약.", 4),
                ContentBlock.Image(
                    url = "https://slownews.kr/wp-content/uploads/a.png",
                    caption = "게티이미지.",
                    width = 1000,
                    height = 563
                ),
                ContentBlock.Heading("이게 왜 중요한가.", 3),
                ContentBlock.Text("택시가 가장 백발이다.")
            ),
            result
        )
    }

    @Test
    fun `makes the largest heading level 3`() {
        val html = """
            <article class="single-entry"><div class="entry-content">
              <h1>쟁점과 현안.</h1><h3>삼성전자 3분기 영업이익 107조 원.</h3><p>하루 1.2조 원을 번다.</p>
            </div></article>
        """.trimIndent()

        val result = parser.extractContents(Jsoup.parse(html))

        assertEquals(
            listOf(
                ContentBlock.Heading("쟁점과 현안.", 3),
                ContentBlock.Heading("삼성전자 3분기 영업이익 107조 원.", 4),
                ContentBlock.Text("하루 1.2조 원을 번다.")
            ),
            result
        )
    }
}
