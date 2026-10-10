package dev.kettu.hyangsang.parser

import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Articles from 슬로우뉴스 (slownews.kr), a WordPress site of explainers, essays and the daily
 * 슬로우레터 news digest.
 */
class SlowNewsParser : ContentsParser {
    companion object {
        const val HOST = "slownews.kr"
    }

    override fun extractContents(document: Document, title: String?): List<ContentBlock> {
        val body = document.select("article.single-entry .entry-content").firstOrNull()
            ?: return GenericContentsParser().extractContents(document, title)

        // The share buttons, and boxes asking readers to subscribe, which end with a button
        body.select(".social_share, script, style, noscript, iframe").remove()
        body.select(".wp-block-group:has(.wp-block-buttons)").remove()
        // Paragraphs that are only a link, such as the list of reports ending 슬로우레터
        body.select("p:has(a)").forEach { p ->
            val text = p.text().trim()
            if (text.isNotEmpty() && text == p.select("a").joinToString("") { it.text() }.trim()) p.remove()
        }

        // The table of contents starts with a summary of the article, or of the day's stories in
        // 슬로우레터, followed by a list of its headings
        val toc = body.getElementById("rank-math-toc")
        val subtitle = toc?.select("h1, h2, h3, h4, h5, h6")?.firstOrNull()?.text()?.trim()
        toc?.remove()

        // Images are loaded lazily, with a placeholder in src
        body.select("img[data-src]").forEach { it.attr("src", it.attr("data-src")) }
        body.normalizeHeadings()

        val blocks = body.parseBlocks().toMutableList()
        if (!subtitle.isNullOrEmpty()) blocks.add(0, ContentBlock.Heading(subtitle, 4))

        val author = document.select("a[rel=author]").firstOrNull()?.text()?.trim()
        if (!author.isNullOrEmpty()) blocks.add(0, ContentBlock.Dateline(author))
        return blocks
    }

    // Most articles use h3 for their sections, but 슬로우레터 groups its stories under h1. The
    // largest heading in the body becomes h3 and any smaller ones h4
    private fun Element.normalizeHeadings() {
        val headings = select("h1, h2, h3, h4, h5, h6")
        val top = headings.minOfOrNull { it.tagName().last().digitToInt() } ?: return
        headings.forEach { it.tagName(if (it.tagName().last().digitToInt() == top) "h3" else "h4") }
    }
}
