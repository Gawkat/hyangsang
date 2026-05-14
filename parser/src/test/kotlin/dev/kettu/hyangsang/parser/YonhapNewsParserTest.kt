package dev.kettu.hyangsang.parser

import junit.framework.TestCase.assertEquals
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

        assertEquals(expected, result)
    }
}