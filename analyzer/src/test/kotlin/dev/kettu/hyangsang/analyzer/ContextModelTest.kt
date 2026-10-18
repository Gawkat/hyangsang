package dev.kettu.hyangsang.analyzer

import org.junit.Assert.assertEquals
import org.junit.Test

class ContextModelTest {
    @Test
    fun `the bundled counts are loaded`() {
        // 사는 is far more often a form of 살다 than of 사다
        val lexicon = Lexicon(
            listOf("살다", "사다").map { DictionaryRow(it, "Verb", "Beginner", "Word") } +
                DictionaryRow("-는", "Ending", null, "Word")
        )
        val analyzer = LookupAnalyzer(lexicon, ContextModel.bundled())
        assertEquals("살다", analyzer.analyze("사는").first().content?.headword)
    }
}
