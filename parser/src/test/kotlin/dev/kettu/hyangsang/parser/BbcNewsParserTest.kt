package dev.kettu.hyangsang.parser

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
}
