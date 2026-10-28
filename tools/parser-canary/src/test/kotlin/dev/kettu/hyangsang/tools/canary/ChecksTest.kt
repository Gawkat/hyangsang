package dev.kettu.hyangsang.tools.canary

import dev.kettu.hyangsang.parser.ContentBlock
import dev.kettu.hyangsang.parser.ContentSpan
import dev.kettu.hyangsang.parser.SpanType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChecksTest {
    private val paragraph = "정부가 내년 예산안을 발표하면서 지방 교통망 확충에 가장 많은 돈을 쓰겠다고 밝혔다. " +
        "전문가들은 이번 예산이 지역 균형 발전에 도움이 될 것이라고 평가했다."

    private val article = listOf(
        ContentBlock.Dateline("(서울=연합뉴스) 홍길동 기자"),
        ContentBlock.Text(paragraph),
        ContentBlock.Footer("저작권자(c) 연합뉴스, 무단 전재-재배포 금지")
    )

    @Test
    fun `a well parsed article has no problems`() {
        assertEquals(emptyList<String>(), contentProblems(article, "내년 예산안 발표"))
    }

    @Test
    fun `a lost body is a problem`() {
        val problems = contentProblems(listOf(ContentBlock.Dateline("홍길동 기자")), "제목")
        assertTrue(problems.single().startsWith("only 0 Hangul letters"))
    }

    @Test
    fun `a short body is fine when the lead is found in it`() {
        val blocks = listOf(ContentBlock.Text("케이크를 주제로 한 활동지예요."))
        assertEquals(emptyList<String>(), contentProblems(blocks, null, leadFound = true))
        assertEquals(1, contentProblems(blocks, null, leadFound = false).size)
    }

    @Test
    fun `markup and copyright notes in the body are problems`() {
        val blocks = article + listOf(
            ContentBlock.Text("기사 본문 &nbsp; 사이에 <span class=\"ad\"> 남은 태그"),
            ContentBlock.Text("ⓒ 한겨레신문사 무단 전재 금지")
        )
        val problems = contentProblems(blocks, null)
        assertTrue(problems.any { it.startsWith("markup or script left") })
        assertTrue(problems.any { it.startsWith("copyright note in the body") })
    }

    @Test
    fun `repeated paragraphs and a repeated title are problems`() {
        val blocks = listOf(
            ContentBlock.Heading("내년 예산안 발표", 1),
            ContentBlock.Text(paragraph),
            ContentBlock.Text(paragraph)
        )
        val problems = contentProblems(blocks, "내년 예산안 발표")
        assertTrue(problems.any { it.startsWith("repeated paragraph") })
        assertTrue(problems.any { it.startsWith("the title is repeated") })
    }

    @Test
    fun `paragraphs the page itself repeats are fine`() {
        val blocks = listOf(ContentBlock.Text(paragraph), ContentBlock.Text(paragraph))
        val problems = contentProblems(blocks, null, pageText = "유한회사 $paragraph 주식회사 $paragraph")
        assertEquals(emptyList<String>(), problems)
    }

    @Test
    fun `spans out of bounds and relative image URLs are problems`() {
        val blocks = listOf(
            ContentBlock.Text(paragraph, listOf(ContentSpan(0, paragraph.length + 1, SpanType.BOLD))),
            ContentBlock.Image("/images/photo.jpg")
        )
        val problems = contentProblems(blocks, null)
        assertTrue(problems.any { it.startsWith("span out of bounds") })
        assertTrue(problems.any { it.startsWith("image without an absolute URL") })
    }

    @Test
    fun `the lead is found by any of its sentences, ignoring spacing and punctuation`() {
        // A digest whose description joins its section headings
        val lead = "(서울=연합뉴스) 홍길동 기자 = 정부가 내년 예산안을 발표하면서… 다른 기사의 제목도 함께 실렸다."
        assertEquals(true, leadFound(article, listOf(lead)))
        assertEquals(false, leadFound(article, listOf("이 문장은 기사 본문 어디에도 나오지 않는 요약문이다.")))
    }

    @Test
    fun `there's no lead to check without a long enough sentence`() {
        assertEquals(null, leadFound(article, listOf("", "최근 업데이트")))
    }

    @Test
    fun `quoted headings and the WordPress footer aren't taken for sentences`() {
        // A newsletter whose description lists its section headings
        val lead = "조희대 국정조사. “지뢰는 방어용 무기.” “미군을 믿을 수 있나.” 다르게 읽기...\n" +
            "The post 앤트로픽이 돈을 벌기 시작했다는 신호: 슬로우레터 10월28일. appeared first on 슬로우뉴스.."
        assertEquals(null, leadFound(article, listOf(lead)))
    }
}
