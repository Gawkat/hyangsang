package dev.kettu.hyangsang.parser

import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import org.jsoup.Jsoup
import org.junit.Test

class BbcNewsParserTest {
    private val parser = BbcNewsParser()

    @Test
    fun `extracts text from BBC News article`() {
        val html = javaClass.classLoader.getResource("bbc_sample.html")?.readText()
        val expected =
            javaClass.classLoader.getResource("bbc_sample_expected_output.txt")?.readText()
                ?.replace(Regex("\\r\\n?"), "\n")

        if (html == null || expected == null) {
            return
        }

        val doc = Jsoup.parse(html)
        val result = parser.extractContents(doc)

        val flattenedResult = result.filterIsInstance<ContentBlock.Text>()
            .joinToString("\n\n") { it.text }

        // Check if the expected core content is present
        assertTrue(
            "Result should contain expected text",
            flattenedResult.contains(expected.substring(0, 100))
        )
    }

    @Test
    fun `removes heading that matches the article title`() {
        val doc = Jsoup.parse(
            """
            <main>
                <h1>기사 제목</h1>
                <p>본문</p>
                <h2>소제목</h2>
            </main>
            """.trimIndent()
        )

        val result = parser.extractContents(doc, "기사 제목")

        assertEquals(
            listOf(ContentBlock.Text("본문"), ContentBlock.Heading("소제목", 2)),
            result
        )
    }

    @Test
    fun `keeps heading that differs from the article title`() {
        val doc = Jsoup.parse("<main><h1>기사 제목입니다</h1><p>본문</p></main>")

        val result = parser.extractContents(doc, "기사 제목")

        assertEquals(
            listOf(ContentBlock.Heading("기사 제목입니다", 1), ContentBlock.Text("본문")),
            result
        )
    }
}
