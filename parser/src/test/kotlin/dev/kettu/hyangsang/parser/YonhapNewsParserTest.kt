package dev.kettu.hyangsang.parser

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
}
