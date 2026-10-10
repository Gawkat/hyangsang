package dev.kettu.hyangsang.parser

import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import org.jsoup.Jsoup
import org.junit.Test

class EasyLawParserTest {
    private val parser = EasyLawParser()

    private val disclaimer = "생활법령정보는 법적 효력을 갖는 유권해석(결정, 판단)의 근거가 되지 않고, " +
        "각종 신고, 불복 청구 등의 증거자료로서의 효력은 없습니다."

    @Test
    fun `extracts a chapter of a law guide`() {
        val html = readSample("easylaw_sample.html")
        val title = "(생활법령) 소비자 안전정보 > 소비자 안전정보 > 소비자 안전정보 알아보기 > 소비자의 권리, 책무 및 보호"

        val result = parser.extractContents(Jsoup.parse(html, "https://www.easylaw.go.kr/CSP/"), title)

        assertEquals(
            listOf(
                ContentBlock.Heading("소비자에게는 소비자로서 누릴 권리와 책임이 있습니다.", 3),
                ContentBlock.Heading("소비자의 개념", 4)
            ),
            result.take(2)
        )
        val texts = result.filterIsInstance<ContentBlock.Text>()
        assertTrue(texts.first().text.startsWith("“소비자”란 사업자가 제공하는 물품"))
        assertTrue(texts.any { it.text == "√ 「소비자기본법」 제49조에 따른 수거·파기 등의 권고" })
        // From the table at the end of the chapter
        assertTrue(texts.any { it.text.startsWith("이 사안에 대하여 공정거래위원회는") })
        assertTrue(texts.none { "인쇄체크" in it.text || "홈페이지 개선의견" in it.text })
        assertTrue(result.filterIsInstance<ContentBlock.Image>().isEmpty())

        assertEquals(
            listOf(
                ContentBlock.Footer("이 정보는 2026년 9월 15일 기준으로 작성된 것입니다."),
                ContentBlock.Footer(disclaimer)
            ),
            result.takeLast(2)
        )
    }

    @Test
    fun `extracts a question and its answer`() {
        val html = readSample("easylaw_qna_sample.html")

        val result = parser.extractContents(Jsoup.parse(html, "https://www.easylaw.go.kr/CSP/"))

        assertEquals(
            listOf(
                ContentBlock.Heading(
                    "인공지능 분야의 전문인력을 양성하거나 해외 전문인력을 확보하기 위해 정보에서 지원하는 사업은 무엇인가요?",
                    3
                ),
                ContentBlock.Text(
                    "정부 정책 및 지원사업을 통해 국내외 인공지능 전문인력의 역량 강화, 고용 촉진, " +
                        "글로벌 교류 및 유치를 위한 다각적인 지원을 받을 수 있습니다."
                ),
                ContentBlock.Heading("국내 전문인력 양성 지원", 4),
                ContentBlock.Text("☞ 전문인력의 직무역량 강화 및 경력개발을 위한 교육훈련 프로그램 개발·활용")
            ),
            result.take(4)
        )
        assertTrue(result.none { it is ContentBlock.Text && "카카오톡" in it.text })
        assertEquals(ContentBlock.Footer(disclaimer), result.last())
    }

    @Test
    fun `parses the depths, boxes and tables of a guide`() {
        val html = """
            <div id="ovDiv"><div class="ovDivbox">
              <div class='cnpClsTitle'><img src='https://www.easylaw.go.kr/CSP/images/icon_1.gif' alt='' />&nbsp;<b>다른 제목</b></div>
              <div class='spaceDiv1'><a name='1.1'></a></div>
              <div class='plv1a'><label class='labelnone'><input type='hidden' />인쇄체크</label><img src='https://www.easylaw.go.kr/CSP/images/icon_arrow01.gif' alt='' /> <strong>절 제목</strong>&nbsp;<a href="#copyAddress"><img src="/common/images/etc/btn_copy_address2.gif" alt="주소복사" /></a><a href="#addBookmark"><img src="/common/images/etc/btn_bookmark2.gif" alt="즐겨찾기에추가" /></a></div>
              <div class='plv2a'><img src='https://www.easylaw.go.kr/CSP/images/icon_arrow03.gif' alt='' /> 항목 제목</div>
              <div class='plv3a'><img src='https://www.easylaw.go.kr/CSP/images/icon_arrow02.gif' alt='' /> 본문은 <a href="http://www.law.go.kr/">「법」 제2조</a>를 따릅니다.</div>
              <div class='plv4a'><a href="#" onclick="openBtrCard('1', '2');return false;"><img src="https://www.easylaw.go.kr/CSP/images/icon/icon_btr.gif" alt="규제"/></a>목록 항목</div>
              <div class='tplv2d'><strong>Q. </strong><strong>상자 질문</strong></div>
              <div class='tplv6'><table><tbody>
                <tr><td><p><strong>표 제목</strong></p></td></tr>
                <tr><td>표 내용</td></tr>
              </tbody></table></div>
            </div></div>
            <div class="info_box">
              <strong>이 정보는 <b>2026년 9월 15일 </b> 기준으로 작성된 것입니다.</strong>
              <ul><li>법적 효력은 없습니다.</li><li>국민신문고에 문의하시기 바랍니다.</li></ul>
            </div>
        """.trimIndent()

        val result = parser.extractContents(
            Jsoup.parse(html, "https://www.easylaw.go.kr/CSP/"),
            "(생활법령) 공탁 > 공탁의 신청"
        )

        assertEquals(
            listOf(
                ContentBlock.Heading("다른 제목", 3),
                ContentBlock.Heading("절 제목", 3),
                ContentBlock.Heading("항목 제목", 4),
                ContentBlock.Text("본문은 「법」 제2조를 따릅니다."),
                ContentBlock.Text("목록 항목"),
                ContentBlock.Text(
                    "Q. 상자 질문",
                    listOf(ContentSpan(0, 3, SpanType.BOLD), ContentSpan(3, 8, SpanType.BOLD))
                ),
                ContentBlock.Text("표 제목", listOf(ContentSpan(0, 4, SpanType.BOLD))),
                ContentBlock.Text("표 내용"),
                ContentBlock.Footer("이 정보는 2026년 9월 15일 기준으로 작성된 것입니다."),
                ContentBlock.Footer("법적 효력은 없습니다.")
            ),
            result
        )
    }

    @Test
    fun `drops the chapter title the feed title ends with`() {
        val html = """
            <div id="ovDiv"><div class="ovDivbox">
              <div class='cnpClsTitle'><b>공탁 신청절차</b></div>
              <div class='plv3'>본문.</div>
            </div></div>
        """.trimIndent()

        val result = parser.extractContents(Jsoup.parse(html), "(생활법령) 공탁 > 공탁의 신청 > 공탁 신청절차")

        assertEquals(listOf(ContentBlock.Text("본문.")), result)
    }

    @Test
    fun `articleUrl keeps pages with text and upgrades http`() {
        assertEquals(
            "https://www.easylaw.go.kr/CSP/CnpClsMain.laf?popMenu=ov&csmSeq=1651&ccfNo=1&cciNo=1&cnpClsNo=1",
            EasyLawParser.articleUrl(
                "https://www.easylaw.go.kr/CSP/CnpClsMain.laf?popMenu=ov&csmSeq=1651&ccfNo=1&cciNo=1&cnpClsNo=1"
            )
        )
        assertEquals(
            "https://www.easylaw.go.kr/CSP/OnhunqueansInfoRetrieve.laf?onhunqnaAstSeq=94&onhunqueSeq=6656",
            EasyLawParser.articleUrl(
                "http://www.easylaw.go.kr/CSP/OnhunqueansInfoRetrieve.laf?onhunqnaAstSeq=94&onhunqueSeq=6656"
            )
        )
    }

    @Test
    fun `articleUrl drops cases, card news, webtoons, videos and news`() {
        listOf(
            "https://www.easylaw.go.kr/CSP/SolomonRetrieveLst.laf?topMenu=serviceUl6",
            "https://www.easylaw.go.kr/CSP/EasyLawInfoR.laf?easySeq=3692",
            "https://www.easylaw.go.kr/CSP/ComicsRetrieve01.laf?webtoonNo=159&topMenu=serviceUl4",
            "https://www.easylaw.go.kr/CSP/UccRetrieve01.laf?uccNo=38&topMenu=serviceUl4",
            "https://www.easylaw.go.kr/CSP/NewsRetrieve01.laf?topMenu=openUl3&ntcSeq=1782",
            "https://www.easylaw.go.kr/CSP/NewsLetterRetrieve01.laf?topMenu=openUl4&ntcSeq=1779",
            "https://www.easylaw.go.kr/CSP/NtcRetrieve01.laf?topMenu=openUl1&ntcSeq=1783"
        ).forEach { assertNull(it, EasyLawParser.articleUrl(it)) }
    }
}
