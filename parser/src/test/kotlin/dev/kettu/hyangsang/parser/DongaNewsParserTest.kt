package dev.kettu.hyangsang.parser

import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import org.jsoup.Jsoup
import org.junit.Test

class DongaNewsParserTest {
    private val parser = DongaNewsParser()

    @Test
    fun `extracts an article`() {
        val html = readSample("donga_sample.html")

        val result = parser.extractContents(
            Jsoup.parse(html, "https://www.donga.com/news/It/article/all/20261009/134810590/2")
        )

        assertEquals(ContentBlock.Dateline("허진석 기자"), result[0])
        val subtitle = result[1] as ContentBlock.Heading
        assertEquals(4, subtitle.level)
        assertEquals(4, subtitle.text.lines().size)
        assertTrue(subtitle.text.startsWith("표 속 숫자 이해하고 예측하는 AI 만든 넘즈에이아이\n"))
        assertTrue((result[2] as ContentBlock.Image).url.startsWith("https://dimg.donga.com/wps/NEWS/IMAGE/"))
        assertTrue((result[3] as ContentBlock.Text).text.startsWith("고객 1만 명에 쿠폰은 500장 뿐이다."))
        assertTrue(result.contains(ContentBlock.Heading("대기업 데이터사이언스팀의 고민", 3)))
        assertEquals(4, result.count { it is ContentBlock.Heading && it.level == 3 })

        val texts = result.filterIsInstance<ContentBlock.Text>()
        assertTrue(texts.none { "googletag" in it.text || "크게보기" in it.text })
        assertEquals(
            listOf(
                ContentBlock.Footer("jameshur@donga.com"),
                ContentBlock.Footer("© dongA.com All rights reserved. 무단 전재, 재배포 및 AI학습 이용 금지")
            ),
            result.takeLast(2)
        )
    }

    @Test
    fun `extracts a wire story`() {
        val html = readSample("donga_wire_sample.html")

        val result = parser.extractContents(
            Jsoup.parse(html, "https://www.donga.com/news/Sports/article/all/20261010/134817367/1")
        )

        assertEquals(ContentBlock.Dateline("서울=뉴시스"), result[0])
        assertEquals(ContentBlock.Heading("“가장 놀라운 건 가족 같은 구단이라는 점”", 4), result[1])
        assertEquals("ⓒ뉴시스", (result[2] as ContentBlock.Image).caption)
        assertTrue((result.last() as ContentBlock.Text).text.startsWith("한편 9~10월 A매치를 마치고"))
    }

    @Test
    fun `splits the body into paragraphs and headings`() {
        val html = """
            <html><body><div class="main_view">
              <section class="news_view">
                <h2 class='sub_tit'>첫째 줄<br /> 둘째 줄</h2>
                <figure class="img_cont articlePhotoC"><div class="in_cont">
                  <img src='https://dimg.donga.com/a.jpg' alt='사진 설명'/>
                  <div class='btn_img'><button><span class='is_blind'>크게보기</span></button></div>
                </div><figcaption>사진 설명</figcaption></figure><div class='view_adK'><script>ad()</script></div>
                <div class='view_m_adK'><div class='a1' data-src='https://ads'></div></div>첫 문단이다. <br><br>둘째 <b>문단</b>이다.<br>같은 문단의 다음 줄이다.<br><br><div class='view_ad06 ad box bg'><div class='a1'></div></div><b>● 소제목<br></b><br>셋째 문단이다.<br><br><b>짧지만 문장인 굵은 줄이다.</b><br><br><!--BYLINE--><!--//BYLINE-->
              </section>
              <div class='byline'>홍길동 기자 hong@donga.com<br>부산=김철수 기자 kim@donga.com</div><div class='caution_text'>© dongA.com All rights reserved.</div>
            </div></body></html>
        """.trimIndent()

        val result = parser.extractContents(Jsoup.parse(html, "https://www.donga.com/news/x"))

        assertEquals(
            listOf(
                ContentBlock.Dateline("홍길동 기자, 부산=김철수 기자"),
                ContentBlock.Heading("첫째 줄\n둘째 줄", 4),
                ContentBlock.Image(url = "https://dimg.donga.com/a.jpg", caption = "사진 설명"),
                ContentBlock.Text("첫 문단이다."),
                ContentBlock.Text(
                    "둘째 문단이다.\n같은 문단의 다음 줄이다.",
                    listOf(ContentSpan(3, 5, SpanType.BOLD))
                ),
                ContentBlock.Heading("소제목", 3),
                ContentBlock.Text("셋째 문단이다."),
                ContentBlock.Text("짧지만 문장인 굵은 줄이다.", listOf(ContentSpan(0, 15, SpanType.BOLD))),
                ContentBlock.Footer("hong@donga.com, kim@donga.com"),
                ContentBlock.Footer("© dongA.com All rights reserved.")
            ),
            result
        )
    }

    @Test
    fun `uses the wire credit as the dateline`() {
        val html = """
            <section class="news_view">본문이다.<br><br>(워싱턴=뉴스1)</section>
        """.trimIndent()

        val result = parser.extractContents(Jsoup.parse(html))

        assertEquals(
            listOf(ContentBlock.Dateline("워싱턴=뉴스1"), ContentBlock.Text("본문이다.")),
            result
        )
    }
}
