package dev.kettu.hyangsang.parser

import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import org.jsoup.Jsoup
import org.junit.Test

class YonhapNewsParserTest {
    private val parser = YonhapNewsParser()

    @Test
    fun `extracts text from Yonhap News article`() {
        val html = javaClass.classLoader.getResource("yonhap_sample.htm")?.readText()
        val expected =
            javaClass.classLoader.getResource("yonhap_sample_expected_output.txt")?.readText()
                ?.replace(Regex("\\r\\n?"), "\n")

        if (html == null || expected == null) {
            return
        }

        val doc = Jsoup.parse(html)
        val result = parser.extractContents(doc)

        // Find if the dateline was extracted
        val hasDateline = result.any { it is ContentBlock.Dateline }
        assertTrue("Yonhap article should have a dateline", hasDateline)

        // Check for main content
        val flattenedText = result.filterIsInstance<ContentBlock.Text>()
            .joinToString("\n\n") { it.text }

        assertTrue(
            "Result should contain core content",
            flattenedText.contains("도널드 트럼프 미국 대통령과 시진핑 중국 국가주석의 14일 베이징 회담")
        )
    }

    @Test
    fun `separates email and copyright notice into footer blocks`() {
        val html = """
            <div class="story-news article">
              <p>(서울=연합뉴스) 홍길동 기자 = 본문입니다.</p>
              <p> nari@yna.co.kr<br/></p>
              <p class="txt-copyright adrs">
                <em class="txt03"><a href="#">제보는 카카오톡 okjebo</a></em>
                <em class="txt01">&lt;저작권자(c) 연합뉴스,</em>
                <em class="txt02">무단 전재-재배포, AI 학습 및 활용 금지&gt;</em>
                <span class="date">
                  <span class="txt01" aria-hidden="true">2026/05/14 23:24 송고</span>
                  <span class="ir-txt01">2026년05월14일 23시24분 송고</span>
                </span>
              </p>
            </div>
        """.trimIndent()

        val result = parser.extractContents(Jsoup.parse(html))

        assertEquals(
            listOf(
                ContentBlock.Dateline("(서울=연합뉴스) 홍길동 기자"),
                ContentBlock.Text("본문입니다."),
                ContentBlock.Footer("nari@yna.co.kr"),
                ContentBlock.Footer(
                    "제보는 카카오톡 okjebo <저작권자(c) 연합뉴스, 무단 전재-재배포, AI 학습 및 활용 금지> 2026/05/14 23:24 송고"
                )
            ),
            result
        )
    }
}
