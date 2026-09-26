package dev.kettu.hyangsang.data.prefs

enum class ReaderFont { SANS, SERIF }

enum class ReaderFontWeight { REGULAR, MEDIUM, BOLD }

// Horizontal padding around the article text, in dp
enum class ReaderMargin(val dp: Int) { NARROW(12), NORMAL(16), WIDE(28) }

data class ReaderSettings(
    val textSize: Int = DEFAULT_TEXT_SIZE, // sp
    val font: ReaderFont = ReaderFont.SANS,
    val fontWeight: ReaderFontWeight = ReaderFontWeight.REGULAR,
    val lineSpacing: Float = DEFAULT_LINE_SPACING, // multiple of the text size
    val margin: ReaderMargin = ReaderMargin.NORMAL
) {
    companion object {
        const val DEFAULT_TEXT_SIZE = 20
        const val MIN_TEXT_SIZE = 14
        const val MAX_TEXT_SIZE = 30
        const val DEFAULT_LINE_SPACING = 1.6f
        const val MIN_LINE_SPACING = 1.2f
        const val MAX_LINE_SPACING = 2.2f
    }
}
