package dev.kettu.hyangsang.parser

import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import org.jsoup.Jsoup
import org.junit.Test

class NdSoftNewsParserTest {
    private val parser = NdSoftNewsParser()

    @Test
    fun `extracts body text and images from 어린이동아 article`() {
        val html = readSample("kids_donga_sample.html")

        val result = parser.extractContents(Jsoup.parse(html, "https://kids.donga.com/news/"))

        assertEquals(ContentBlock.Dateline("황민주 기자"), result[0])
        val lead = result[1] as ContentBlock.Image
        assertEquals("https://cdn.kids.donga.com/news/photo/202610/170004_267984_327.jpg", lead.url)
        assertEquals(600, lead.width)
        assertEquals(400, lead.height)
        assertTrue(lead.caption!!.startsWith("34년 만에 노벨 물리학상을"))

        val texts = result.filterIsInstance<ContentBlock.Text>()
        assertTrue(texts.first().text.startsWith("올해 노벨 물리학상 수상자가 발표됐어요."))
        assertTrue(texts.last().text.endsWith("또 하나의 출발점이 된 셈이에요."))
        assertTrue(texts.none { it.text.contains("adsbygoogle") || it.text.contains("Util.") })

        assertEquals(
            listOf(
                ContentBlock.Footer("hmj2760@donga.com"),
                ContentBlock.Footer("Copyright © 어린이동아 All rights reserved. 무단 전재, 재배포 및 AI학습 이용 금지")
            ),
            result.takeLast(2)
        )
    }

    @Test
    fun `parses subheading, headings, reporter box and copyright`() {
        val html = """
            <header class="article-view-header">
              <div class="info-group"><ul class="infomation">
                <li><i class="icon-user-o"><span class="show-for-sr">기자명</span></i> 김은별 미국 통신원 (sisa@sisajournal.com)</li>
              </ul></div>
            </header>
            <article id="article-view-content-div" class="article-veiw-body view-page" itemprop="articleBody">
              <H2 class="subheading">첫째 부제<br /> 둘째 부제</H2>
              <div style="text-align:center">
                <figure class="photo-layout image"><div class="IMGFLOATING">
                  <img alt="설명" height="400" src="/news/photo/202610/1_2_3.jpg" width="600" /></div>
                  <figcaption>▲사진 설명</figcaption>
                </figure>
              </div>
              <p>첫 문단은 <strong>굵은</strong> 글씨를 포함한다.</p>
              <div id="AD123" class="ad-template"><div class="col"><p class="ad_title">ADVERTISEMENT</p></div></div>
              <p><strong>굵은 소제목</strong></p>
              <div style="border-top:1px solid #000;font-weight:700">
                <p style="margin-bottom:0">상자 소제목</p>
              </div>
              <p><strong>굵게 인용한 문장이다.</strong></p>
              <p>마지막 문단.</p>
              <p>&nbsp;</p>
              <script>Util.copyExec(".urlBtns");</script>
            </article>
            <article class="writer">
              <strong class="name"><a href="/news/articleList.html">김은별</a>미국 통신원<br></strong>
              <a href="mailto:sisa@sisajournal.com" class="email">sisa@sisajournal.com</a>
            </article>
            <article class="article-copy">저작권자 &copy; 시사저널 무단전재 및 재배포 금지</article>
        """.trimIndent()

        val result = parser.extractContents(Jsoup.parse(html, "https://www.sisajournal.com/news/"))

        assertEquals(
            listOf(
                ContentBlock.Dateline("김은별 미국 통신원"),
                ContentBlock.Heading("첫째 부제\n둘째 부제", 4),
                ContentBlock.Image(
                    url = "https://www.sisajournal.com/news/photo/202610/1_2_3.jpg",
                    caption = "사진 설명",
                    width = 600,
                    height = 400
                ),
                ContentBlock.Text("첫 문단은 굵은 글씨를 포함한다.", listOf(ContentSpan(6, 8, SpanType.BOLD))),
                ContentBlock.Heading("굵은 소제목", 3),
                ContentBlock.Heading("상자 소제목", 3),
                ContentBlock.Text("굵게 인용한 문장이다.", listOf(ContentSpan(0, 12, SpanType.BOLD))),
                ContentBlock.Text("마지막 문단."),
                ContentBlock.Footer("sisa@sisajournal.com"),
                ContentBlock.Footer("저작권자 © 시사저널 무단전재 및 재배포 금지")
            ),
            result
        )
    }

    @Test
    fun `moves the reporter line at the end of the body to the dateline and footer`() {
        val withEmail = """
            <article id="article-view-content-div">
              <p>본문.</p>
              <p style="text-align: justify;">김재호 기자 kimyital@kyosu.net</p>
            </article>
            <article class="article-copyright">저작권자 &copy; 교수신문</article>
        """.trimIndent()
        assertEquals(
            listOf(
                ContentBlock.Dateline("김재호 기자"),
                ContentBlock.Text("본문."),
                ContentBlock.Footer("kimyital@kyosu.net"),
                ContentBlock.Footer("저작권자 © 교수신문")
            ),
            parser.extractContents(Jsoup.parse(withEmail))
        )

        val withSlash = """
            <div class="info-group"><ul class="breadcrumbs"><li class="info-name" aria-label="기자명"> 정종엽 기자 </li></ul></div>
            <article id="article-view-content-div">
              <p>본문.</p>
              <p>/정종엽 기자</p>
            </article>
        """.trimIndent()
        assertEquals(
            listOf(ContentBlock.Dateline("정종엽 기자"), ContentBlock.Text("본문.")),
            parser.extractContents(Jsoup.parse(withSlash))
        )

        // A short last paragraph that isn't a reporter line stays
        val bookInfo = """
            <article id="article-view-content-div">
              <p>본문.</p>
              <p>고희정 글, 김선배 그림, 1만5000원</p>
            </article>
        """.trimIndent()
        assertEquals(
            listOf(ContentBlock.Text("본문."), ContentBlock.Text("고희정 글, 김선배 그림, 1만5000원")),
            parser.extractContents(Jsoup.parse(bookInfo))
        )
    }

    @Test
    fun `drops share buttons and reporter box inside the body`() {
        // 제주일보's skin puts them inside the body container
        val html = """
            <article id="article-view-content-div" itemprop="articleBody">
              <div class="article-head-sub">부제</div>
              <p>본문.</p>
              <div class="text-center"><ul class="info-options2 no-bullet">
                <li><button type="button" title="기사공유하기"><span class="show-for-sr">기사공유하기</span></button></li>
              </ul></div>
              <div class="view-copyright">저작권자 &copy; 제주일보</div>
              <div class="view-editors">
                <img src="/news/photo/member/roots.jpg" alt="좌동철 기자" />
                <strong class="names">좌동철 기자</strong>
                <span class="email"><img src="/image/user-email-icon.png"><a href="mailto:roots@jejunews.com" class="user-email">roots@jejunews.com</a></span>
              </div>
            </article>
        """.trimIndent()

        assertEquals(
            listOf(
                ContentBlock.Dateline("좌동철 기자"),
                ContentBlock.Heading("부제", 4),
                ContentBlock.Text("본문."),
                ContentBlock.Footer("roots@jejunews.com"),
                ContentBlock.Footer("저작권자 © 제주일보")
            ),
            parser.extractContents(Jsoup.parse(html, "https://www.jejunews.com/news/"))
        )
    }
}
