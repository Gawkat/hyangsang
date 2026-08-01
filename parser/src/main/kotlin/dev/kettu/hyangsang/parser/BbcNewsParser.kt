package dev.kettu.hyangsang.parser

import org.jsoup.nodes.Document

class BbcNewsParser : ContentsParser {
    override fun extractContents(document: Document): List<ContentBlock> {
        // 1. Handle Byline specifically
        val byline = document.select("section[data-testid=byline]").firstOrNull()
        var bylineBlock: ContentBlock.Dateline? = null
        if (byline != null) {
            // Remove publication time and reading time
            byline.select("li:has(time), li:has([data-testid=read-time]), #article-byline").remove()

            val text = byline.text().trim().replace(Regex("\\s+"), " ")
            if (text.isNotEmpty()) {
                bylineBlock = ContentBlock.Dateline(text)
            }
            byline.remove() // Remove from DOM so it's not processed by parseBlocks
        }

        // 2. Remove other noise
        document.select("section[data-component=related-content], section[data-e2e=recommendations-heading], section[data-e2e=article-links-block]")
            .remove()

        val main = document.select("main").firstOrNull() ?: document.body()
        val blocks = main.parseBlocks().toMutableList()

        // 3. Prepend byline if found
        if (bylineBlock != null) {
            blocks.add(0, bylineBlock)
        }

        return blocks
    }
}
