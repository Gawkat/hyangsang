package dev.kettu.hyangsang.parser

import org.jsoup.nodes.Document

class GenericContentsParser : ContentsParser {
    override fun extractContents(document: Document): String {
        /*
         look at these:
         https://www.ccs.neu.edu/home/vip/teach/IRcourse/6_ML/other_notes/Boilerplate%20Detection%20using%20Shallow%20Text%20Features.pdf
         https://www.researchgate.net/publication/257935932_Heuristics_to_Extract_the_Main_Text_from_a_Captured_Web_Page
         https://stackoverflow.com/questions/3652657/what-algorithm-does-readability-use-for-extracting-text-from-urls
         */

        // TODO: clean the input?
        //document = Jsoup.clean(document.text(), Safelist.basic())

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

        // Select all paragraphs, headers, and list items
        val contentElements = articleBody.select("p, h1, h2, h3, h4, li, blockquote")

        val sb = StringBuilder()
        for (el in contentElements) {
            val text = el.text().trim()
            if (text.isNotEmpty()) {
                sb.append(text).append("\n\n") // Double newline for paragraph spacing
            }
        }

        return sb.toString().trim()
    }
}