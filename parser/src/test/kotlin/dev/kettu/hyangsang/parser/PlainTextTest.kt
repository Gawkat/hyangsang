package dev.kettu.hyangsang.parser

import org.junit.Assert.assertEquals
import org.junit.Test

class PlainTextTest {
    @Test
    fun makesAParagraphOfEachLine() {
        assertEquals(
            listOf(ContentBlock.Text("첫 문단."), ContentBlock.Text("둘째 문단.")),
            PlainText.blocks("  첫 문단.\r\n\r\n \n둘째 문단.  \n")
        )
    }

    @Test
    fun splitsOffAShortFirstLineAsTitle() {
        assertEquals("제목" to "첫 문단.\n둘째 문단.", PlainText.splitTitle("제목\n\n첫 문단.\n둘째 문단."))
    }

    @Test
    fun keepsTextWithoutATitle() {
        val paragraph = "가".repeat(81)
        assertEquals(null to "$paragraph\n다음", PlainText.splitTitle("$paragraph\n다음\n"))
        assertEquals(null to "한 줄", PlainText.splitTitle(" 한 줄 "))
    }
}
