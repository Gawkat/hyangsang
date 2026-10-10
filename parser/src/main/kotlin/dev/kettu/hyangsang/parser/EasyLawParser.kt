package dev.kettu.hyangsang.parser

import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI

/**
 * Pages from 찾기쉬운 생활법령정보 (easylaw.go.kr), the Ministry of Government Legislation's
 * plain-language guide to everyday law. Its update feed mixes several kinds of pages, of which
 * chapters of the law guides and questions from 백문백답 are read.
 */
class EasyLawParser : ContentsParser {
    companion object {
        const val HOST = "easylaw.go.kr"

        // The pages the feed links to that have text. Its links to card news, webtoons, videos,
        // news, newsletters and notices lead to a list, the home page or an error page instead.
        // Cases from 솔로몬의 재판 all link to the page of the case being voted on, so every case
        // would share one URL and show whichever case is current
        private val articlePages = setOf(
            "CnpClsMain.laf",
            "OnhunqueansInfoRetrieve.laf"
        )

        // Blocks of the guides and answers are divs classed by their depth, e.g. "plv3a", or
        // "tplv2d" inside a box
        private val blockClassRegex = Regex("""^t?plv(\d)""")

        // Icons, such as the arrows marking each depth, and buttons
        private val decorationImageRegex = Regex("""/(CSP/images|common/images)/""")

        /**
         * The page to download for a feed [link], or null when the link doesn't lead to an article.
         * Some links use http, which Android blocks, so they're changed to https.
         */
        fun articleUrl(link: String): String? {
            val url = link.trim().replaceFirst(Regex("^http://", RegexOption.IGNORE_CASE), "https://")
            val page = try {
                URI(url).path.orEmpty().substringAfterLast('/')
            } catch (_: Exception) {
                return null
            }
            return url.takeIf { page in articlePages }
        }
    }

    override fun extractContents(document: Document, title: String?): List<ContentBlock> {
        val guide: Element? = document.getElementById("ovDiv")
        if (guide != null) return guide(document, guide, title)
        document.select("ul.question li.qa").firstOrNull()?.let { return answer(document, it) }
        return GenericContentsParser().extractContents(document, title)
    }

    // A chapter of a law guide, whose sections and topics are its first two depths
    private fun guide(document: Document, body: Element, title: String?): List<ContentBlock> {
        val footers = document.takeFooters()
        // The chapter title, which the feed title already ends with
        body.select(".cnpClsTitle").forEach { heading ->
            if (title?.endsWith(heading.text().trim()) == true) {
                heading.remove()
            } else {
                heading.tagName("h3")
            }
        }
        body.cleanBlocks { depth, isBoxed ->
            when {
                isBoxed -> "p"
                depth == 1 -> "h3"
                depth == 2 -> "h4"
                else -> "p"
            }
        }
        return body.parseBlocks() + footers
    }

    // A question from 백문백답, with its answer
    private fun answer(document: Document, qa: Element): List<ContentBlock> {
        val footers = document.takeFooters()
        val question = qa.select(".ttl").firstOrNull()?.text()?.trim()
        val answer = qa.select(".ans").firstOrNull() ?: return emptyList()
        // The recommend, bookmark and share buttons
        answer.select(".recBtn").remove()
        answer.cleanBlocks { _, _ -> "p" }

        val blocks = mutableListOf<ContentBlock>()
        if (!question.isNullOrEmpty()) blocks.add(ContentBlock.Heading(question, 3))
        blocks.addAll(answer.parseBlocks())
        return blocks + footers
    }

    // Turns the depth divs into blocks parseBlocks reads, named by [tagFor], and drops their
    // icons, print checkboxes and copy and bookmark buttons
    private fun Element.cleanBlocks(tagFor: (depth: Int, isBoxed: Boolean) -> String) {
        select(
            "label.labelnone, a[href=#copyAddress], a[href=#addBookmark], a[onclick^=openBtrCard], " +
                ".sns_pop, button"
        ).remove()
        select("img").forEach { if (decorationImageRegex.containsMatchIn(it.attr("src"))) it.remove() }
        select("img[src^=http://]").forEach { it.attr("src", it.attr("src").replaceFirst("http://", "https://")) }

        select("div").forEach { div ->
            val match = blockClassRegex.find(div.className()) ?: return@forEach
            // Boxes hold tables or paragraphs of their own, which are read as they are
            if (div.select("p, table, div").any { it !== div }) return@forEach
            div.tagName(tagFor(match.groupValues[1].toInt(), div.className().startsWith("t")))
        }
        // Table cells with bare text
        select("td, th").forEach { cell ->
            if (cell.select("p, div, table").firstOrNull() == null) cell.tagName("p")
        }
        // Headings in answers are marked with a diamond, "◇ 국내 전문인력 양성 지원"
        select("p").forEach { p ->
            val text = p.text().trim()
            if (text.startsWith("◇")) p.replaceWith(Element("h4").text(text.removePrefix("◇").trim()))
        }
    }

    // Removes the box after the body with the date the information was last checked against the
    // law and the note that it isn't legal advice, and returns those two as footers
    private fun Document.takeFooters(): List<ContentBlock> {
        val box = select(".info_box").firstOrNull() ?: return emptyList()
        box.remove()
        return listOfNotNull(
            box.select("strong").firstOrNull()?.text(),
            box.select("li").firstOrNull()?.text()
        ).map { it.replace(Regex("""\s+"""), " ").trim() }
            .filter { it.isNotEmpty() }
            .map { ContentBlock.Footer(it) }
    }
}
