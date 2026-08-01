package dev.kettu.hyangsang.parser

import org.jsoup.nodes.Document

class YonhapNewsParser : ContentsParser {
    companion object {
        // Regex to match Yonhap dateline: (Location=연합뉴스) Reporter Name =
        private val datelineRegex = Regex("""^\(([^)]+연합뉴스)\)\s*(.*?)\s*=\s*""")
    }

    override fun extractContents(document: Document): List<ContentBlock> {
        document.select("aside, script, meta, .writer-zone01, .related-zone, #newsWriterCarousel01")
            .remove()

        val main = document.select("div[class=story-news article]").firstOrNull() ?: document.body()
        val blocks = main.parseBlocks().toMutableList()

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
}
