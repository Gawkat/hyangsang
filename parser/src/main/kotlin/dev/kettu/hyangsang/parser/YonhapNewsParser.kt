package dev.kettu.hyangsang.parser

import org.jsoup.nodes.Document

class YonhapNewsParser : ContentsParser {
    companion object {
        // Regex to match Yonhap dateline: (Location=연합뉴스) Reporter Name =
        private val datelineRegex = Regex("""^\(([^)]+연합뉴스)\)\s*(.*?)\s*=\s*""")
        private val emailRegex = Regex("""[\w.+-]+@[\w-]+(\.[\w-]+)+""")
        private val emailSeparatorRegex = Regex("""[\s,/]+""")

        private const val BODY = "div[class=story-news article]"

        // The "원하시는 페이지를 찾을 수 없습니다." box, which takes the article's place
        private const val NOT_FOUND = ".article-deleted, .box-deleted"
    }

    override fun recognizes(document: Document): Boolean = document.selectFirst(BODY) != null

    override fun isErrorPage(url: String, document: Document): Boolean =
        document.selectFirst(NOT_FOUND) != null

    override fun extractContents(document: Document, title: String?): List<ContentBlock> {
        document.select("aside, script, meta, .writer-zone01, .related-zone, #newsWriterCarousel01")
            .remove()

        val main = document.select(BODY).firstOrNull() ?: document.body()

        // Pull the copyright notice out before parsing so it doesn't become body text.
        // .ir-txt01 is a screen-reader copy of the visible send date, so drop it
        val copyrightTexts = main.select("p.txt-copyright").map { element ->
            element.select(".ir-txt01").remove()
            element.remove()
            element.text().trim()
        }.filter { it.isNotEmpty() }

        val blocks = main.parseBlocks().toMutableList()

        // The reporter's email address sits in a plain paragraph right before the copyright
        val last = blocks.lastOrNull()
        if (last is ContentBlock.Text && last.isEmailOnly()) {
            blocks[blocks.lastIndex] = ContentBlock.Footer(last.text)
        }
        copyrightTexts.forEach { blocks.add(ContentBlock.Footer(it)) }

        // Look for the dateline in the first text block
        val firstTextBlockIndex = blocks.indexOfFirst { it is ContentBlock.Text }
        if (firstTextBlockIndex != -1) {
            val firstTextBlock = blocks[firstTextBlockIndex] as ContentBlock.Text
            val match = datelineRegex.find(firstTextBlock.text)
            if (match != null) {
                val datelineText = match.groupValues[0].trim().removeSuffix("=").trim()
                val remainingText = firstTextBlock.text.substring(match.range.last + 1).trim()

                // Adjust spans for the remaining text
                val offset = match.range.last + 1
                val adjustedSpans = firstTextBlock.spans
                    .filter { it.start >= offset }
                    .map { it.copy(start = it.start - offset, end = it.end - offset) }

                blocks.removeAt(firstTextBlockIndex)

                // Add the dateline block
                blocks.add(firstTextBlockIndex, ContentBlock.Dateline(datelineText))

                // Add the remaining text if not empty
                if (remainingText.isNotEmpty()) {
                    blocks.add(
                        firstTextBlockIndex + 1,
                        ContentBlock.Text(remainingText, adjustedSpans)
                    )
                }
            }
        }

        return blocks
    }

    private fun ContentBlock.Text.isEmailOnly(): Boolean =
        text.split(emailSeparatorRegex).filter { it.isNotEmpty() }
            .let { parts -> parts.isNotEmpty() && parts.all { emailRegex.matches(it) } }
}
