package dev.kettu.hyangsang.parser

import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import org.jsoup.Jsoup
import org.junit.Test

class SbsNewsParserTest {
    private val parser = SbsNewsParser()

    @Test
    fun `extracts body text and images from SBS News article`() {
        val html = javaClass.classLoader.getResource("sbs_sample.html")?.readText() ?: return

        val result = parser.extractContents(Jsoup.parse(html))

        assertEquals(ContentBlock.Dateline("편광현 기자"), result.first())
        assertTrue(result.last() is ContentBlock.Footer)

        // Lead image, captioned by the "▲" line at the top of the body
        val lead = result[1] as ContentBlock.Image
        assertEquals("https://img.sbs.co.kr/newimg/news/20260927/202226243_1280.jpg", lead.url)
        assertEquals(
            "27일 일본 아이치현 도요하시 시민구장에서 열린 2026 아이치·나고야 아시안게임 결승 한국과 일본의 경기. " +
                "3대1로 금메달을 차지한 한국대표팀 선수들이 셀카를 찍고 있다.",
            lead.caption
        )

        val texts = result.filterIsInstance<ContentBlock.Text>()
        assertEquals("추석 연휴 마지막 날에도 대한민국 국가대표 선수들의 금빛 낭보는 계속됐습니다.", texts.first().text)
        assertTrue(texts.none { it.text.contains("▲") || it.text.contains("영문 기사") })

        // Lazy-loaded body images use data-src and take their caption from alt
        val images = result.filterIsInstance<ContentBlock.Image>()
        assertTrue(images.none { it.url.contains("thumb_v3") })
        val equestrian = images.first { it.url.endsWith("202226244_1280.jpg") }
        assertEquals(1280, equestrian.width)
        assertEquals(720, equestrian.height)
        assertTrue(equestrian.caption!!.startsWith("남동헌이 27일 일본 도쿄 승마공원에서"))

        // The text directly after an image starts its own paragraph
        val afterImage = result[result.indexOf(equestrian) + 1] as ContentBlock.Text
        assertTrue(afterImage.text.startsWith("남동헌은 승마 마장마술 개인전 결승에서"))
    }

    @Test
    fun `splits paragraphs on double br and keeps single br as line break`() {
        val html = """
            <div class="w_article_mainimg"><div class="article_image">
              <img class="mainimg" src="https://img.sbs.co.kr/main.jpg" alt="헤드라인">
            </div></div>
            <div class="main_text">
             <div class="text_area" itemprop="articleBody">
              첫 문단입니다.
              <br>
              <br> <strong>굵은</strong> 글씨와 일반 글씨.
              <br> 둘째 줄.
              <br>
              <div class="w_artcle_conimg"><div class="article_image">
                <img alt="캡션 없음" data-captionyn="N" src="https://img.sbs.co.kr/news/pc/thumb_v3.png"
                  data-src="//img.sbs.co.kr/a.jpg" v_width="800" v_height="600">
                <button class="b_expand"><em class="i_large">이미지 확대하기</em></button>
              </div></div>
              <div style="text-align:center">
               <span style="color:#808080"><strong>▲ 본문 사진 설명</strong></span>
              </div>
              <br> 마지막 문단.
              <div class="w_artcle_conimg"><div class="article_image">
                <img alt="사진 설명" data-captionyn="Y" src="https://img.sbs.co.kr/b.jpg">
              </div></div><p class="img_desc">사진 설명</p>이어지는 글.
             </div>
            </div>
            <div class="main_text xlang-wrap"><a class="xlang-btn" href="#"><span>영문 기사 보기</span></a></div>
            <div class="copyrightsbs">Copyright Ⓒ SBS. All rights reserved.</div>
        """.trimIndent()

        val result = parser.extractContents(Jsoup.parse(html))

        assertEquals(
            listOf(
                ContentBlock.Image(url = "https://img.sbs.co.kr/main.jpg"),
                ContentBlock.Text("첫 문단입니다."),
                ContentBlock.Text(
                    "굵은 글씨와 일반 글씨.\n둘째 줄.",
                    listOf(ContentSpan(0, 2, SpanType.BOLD))
                ),
                ContentBlock.Image(
                    url = "https://img.sbs.co.kr/a.jpg",
                    caption = "본문 사진 설명",
                    width = 800,
                    height = 600
                ),
                ContentBlock.Text("마지막 문단."),
                ContentBlock.Image(url = "https://img.sbs.co.kr/b.jpg", caption = "사진 설명"),
                ContentBlock.Text("이어지는 글."),
                ContentBlock.Footer("Copyright Ⓒ SBS. All rights reserved.")
            ),
            result
        )
    }
}
