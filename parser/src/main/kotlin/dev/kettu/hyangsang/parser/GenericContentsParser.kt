package dev.kettu.hyangsang.parser

import org.jsoup.nodes.Document

class GenericContentsParser : ContentsParser {
    override fun extractContents(document: Document, title: String?): List<ContentBlock> {
        /*
         look at these:
         https://www.ccs.neu.edu/home/vip/teach/IRcourse/6_ML/other_notes/Boilerplate%20Detection%20using%20Shallow%20Text%20Features.pdf
         https://www.researchgate.net/publication/257935932_Heuristics_to_Extract_the_Main_Text_from_a_Captured_Web_Page
         https://stackoverflow.com/questions/3652657/what-algorithm-does-readability-use-for-extracting-text-from-urls
         */

        // 1. Remove known "noise" globally before searching
        document.select("script, style, iframe, footer, nav, .ads, .comments, .sidebar, .related")
            .remove()

        // 2. Find the "Best" container using a tiered search
        val articleBody = document.select("article").firstOrNull()
            ?: document.select("[itemprop=articleBody]").firstOrNull()
            ?: document.select(".post-content, .article-content, .entry-content").firstOrNull()
            // Fallback: Find the div with the most <p> tags
            ?: document.select("div").maxByOrNull { it.select("p").size }
            ?: document.body()

        val blocks = articleBody.parseBlocks()

        // If no blocks were found, fallback to the old behavior but returning a single text block
        if (blocks.isEmpty()) {
            val text = articleBody.text().trim()
            if (text.isNotEmpty()) {
                return listOf(ContentBlock.Text(text))
            }
        }

        return blocks
    }
}
