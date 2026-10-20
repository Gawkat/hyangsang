package dev.kettu.hyangsang.parser

/** Turns text the user pasted or shared into content blocks, one paragraph per line. */
object PlainText {
    // Longer texts are left to a format with chapters, so one article stays small enough to load
    const val MAX_LENGTH = 100_000

    // A first line longer than this is more likely a paragraph than a title
    private const val MAX_TITLE_LENGTH = 80

    fun blocks(text: String): List<ContentBlock> = paragraphs(text).map { ContentBlock.Text(it) }

    /**
     * Splits off a first line that looks like a title, returning the title, or null if there
     * isn't one, and the rest of the text.
     */
    fun splitTitle(text: String): Pair<String?, String> {
        val lines = paragraphs(text)
        val first = lines.firstOrNull()
        if (first == null || lines.size < 2 || first.length > MAX_TITLE_LENGTH) {
            return null to text.trim()
        }
        return first to lines.drop(1).joinToString("\n")
    }

    private fun paragraphs(text: String): List<String> =
        text.lines().map { it.trim() }.filter { it.isNotEmpty() }
}
