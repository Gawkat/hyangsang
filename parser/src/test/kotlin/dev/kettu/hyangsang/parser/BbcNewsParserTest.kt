package dev.kettu.hyangsang.parser

import junit.framework.TestCase.assertEquals
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

        assertEquals(expected, result)
    }
}