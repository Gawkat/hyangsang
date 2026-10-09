package dev.kettu.hyangsang.parser

import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import org.jsoup.Jsoup
import org.junit.Test

class HaniNewsParserTest {
    private val parser = HaniNewsParser()

    @Test
    fun `extracts body text and images from Hankyoreh article`() {
        val html = readSample("hani_sample.html")

        val result = parser.extractContents(Jsoup.parse(html, "https://www.hani.co.kr/"))

        assertEquals(ContentBlock.Dateline("고경주 기자"), result[0])
        assertEquals(ContentBlock.Heading("지지율 하락세 막았지만 37% ‘제자리걸음’", 4), result[1])

        // The lead image, not the audio player's play button
        val lead = result[2] as ContentBlock.Image
        assertEquals(
            "https://flexible.img.hani.co.kr/flexible/normal/800/534/imgdb/original/2026/1009/20261009501595.jpg",
            lead.url
        )
        assertEquals(800, lead.width)
        assertEquals(534, lead.height)
        assertTrue(lead.caption!!.startsWith("이재명 대통령이 10월6일 청와대에서"))

        val texts = result.filterIsInstance<ContentBlock.Text>()
        assertTrue(texts.first().text.startsWith("이재명 대통령이 지난달 18일 기자회견 이후"))
        assertTrue(texts.none { it.text.contains("광고") || it.text.contains("goh@hani.co.kr") })
        assertTrue(result.none { it is ContentBlock.Heading && it.text.startsWith("지지율 반등 없는") })

        assertEquals(
            listOf(
                ContentBlock.Footer("goh@hani.co.kr"),
                ContentBlock.Footer("© 한겨레신문사 All Rights Reserved. 무단 전재, 재배포, AI 학습 및 활용 금지")
            ),
            result.takeLast(2)
        )
    }

    @Test
    fun `parses headings, signature and footers`() {
        val html = """
            <article id="renewal2023">
              <h3 class="ArticleDetailView_title__9kRU_">제목</h3>
              <div class="ArticleDetailView_articleDetail__IT2fh">
                <div class="ArticleDetailView_reporterList__waOKp"><a href="/arti/JOURNALIST/1">김원철</a>기자</div>
              </div>
              <div class="ArticleDetailView_audioWrap__559ME">
                <button><img src="/_next/static/media/audio_play.svg" width="32" height="32px"/></button>
              </div>
              <h4 class="ArticleDetailView_subtitle__x6jcS"><span>연재 이름</span><br>첫째 부제<br>둘째 부제</h4>
              <div class="article-text">
                <div class="ArticleDetailContent_imageContainer___o_gm">
                  <figure class="ArticleDetailContent_imageArea__EILpj">
                    <div class="ArticleDetailContent_imageWrap__o8GzH"><picture>
                      <source type="image/webp" srcSet="https://flexible.img.hani.co.kr/flexible/normal/1200/800/a.webp"/>
                      <img width="1200" src="https://flexible.img.hani.co.kr/flexible/normal/1200/800/a.jpg" alt="설명"/>
                    </picture>
                    <button title="이미지 크게 보기"><img src="/_next/static/media/zoom.svg" width="24px" height="24px"/></button>
                    </div>
                    <figcaption class="ArticleDetailContent_figcaption__Aq2sU"></figcaption>
                  </figure>
                </div>
                <p class="text">첫 문단은 <b>굵은</b> 글씨를 포함한다.</p>
                <div class="ArticleDetailContent_adWrap__xYVGB"><div class="BaseAd_adWrapper__kTNSx"><span class="sr-only">광고</span></div></div>
                <div class="cont-midtitle type2"><p class="text">중간 제목</p></div>
                <p class="text"> <b>굵은 소제목</b> </p>
                <div class="embeded-area"><div class="video-wrap"><iframe src="https://youtube.com/embed/x"></iframe></div></div>
                <p class="text">마지막 문단.</p>
                <p class="text"> 워싱턴/김원철 특파원 </p>
                <p class="text"> <a href="mailto:wonchul@hani.co.kr">wonchul@hani.co.kr</a></p>
              </div>
            </article>
            <footer><div class="Footer_copyrightBottom__m42f5">© 한겨레신문사 All Rights Reserved.</div></footer>
        """.trimIndent()

        val result = parser.extractContents(Jsoup.parse(html))

        assertEquals(
            listOf(
                ContentBlock.Dateline("워싱턴/김원철 특파원"),
                ContentBlock.Heading("연재 이름\n첫째 부제\n둘째 부제", 4),
                ContentBlock.Image(
                    url = "https://flexible.img.hani.co.kr/flexible/normal/1200/800/a.jpg",
                    width = 1200,
                    height = 800
                ),
                ContentBlock.Text("첫 문단은 굵은 글씨를 포함한다.", listOf(ContentSpan(6, 8, SpanType.BOLD))),
                ContentBlock.Heading("중간 제목", 3),
                ContentBlock.Heading("굵은 소제목", 3),
                ContentBlock.Text("마지막 문단."),
                ContentBlock.Footer("wonchul@hani.co.kr"),
                ContentBlock.Footer("© 한겨레신문사 All Rights Reserved.")
            ),
            result
        )
    }

    @Test
    fun `takes the writer of a column and joint signatures`() {
        val column = """
            <article id="renewal2023"><div class="article-text">
              <p class="text"><strong>김진해 | 한겨레말글연구소 연구위원</strong></p>
              <p class="text">본문.</p>
            </div></article>
        """.trimIndent()
        assertEquals(
            listOf(
                ContentBlock.Dateline("김진해 | 한겨레말글연구소 연구위원"),
                ContentBlock.Text("본문.")
            ),
            parser.extractContents(Jsoup.parse(column))
        )

        val joint = """
            <article id="renewal2023">
              <div class="ArticleDetailView_reporterList__waOKp"><a>최원형</a>, <a>고경주</a>기자</div>
              <div class="article-text">
                <p class="text">본문.</p>
                <p class="text">최원형 기자 <a href="mailto:circle@hani.co.kr">circle@hani.co.kr</a>, 고경주 기자 <a href="mailto:goh@hani.co.kr">goh@hani.co.kr</a></p>
              </div>
            </article>
        """.trimIndent()
        assertEquals(
            listOf(
                ContentBlock.Dateline("최원형 기자, 고경주 기자"),
                ContentBlock.Text("본문."),
                ContentBlock.Footer("circle@hani.co.kr, goh@hani.co.kr")
            ),
            parser.extractContents(Jsoup.parse(joint))
        )
    }

    @Test
    fun `uses the reporter list without a signature and moves sponsored notes to the footer`() {
        val html = """
            <article id="renewal2023">
              <div class="ArticleDetailView_reporterList__waOKp"><a>최원형</a>, <a>고경주</a>기자</div>
              <div class="article-text">
                <p class="text">본문.</p>
                <p class="text">&lt;이 기사는 숭실대학교에서 제공한 정보기사로, 한겨레의 의견과 다를 수 있습니다&gt;</p>
              </div>
            </article>
        """.trimIndent()

        assertEquals(
            listOf(
                ContentBlock.Dateline("최원형, 고경주 기자"),
                ContentBlock.Text("본문."),
                ContentBlock.Footer("<이 기사는 숭실대학교에서 제공한 정보기사로, 한겨레의 의견과 다를 수 있습니다>")
            ),
            parser.extractContents(Jsoup.parse(html))
        )
    }
}
