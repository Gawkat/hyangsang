package dev.kettu.hyangsang.parser

import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode

class HaniNewsParser : ContentsParser {
    companion object {
        // Image URLs carry their size, e.g. ".../flexible/normal/800/534/imgdb/original/..."
        private val imageSizeRegex = Regex("""/flexible/normal/(\d+)/(\d+)/""")
        private val emailRegex = Regex("""[\w.+-]+@[\w-]+(\.[\w-]+)+""")

        // "<이 기사는 숭실대학교에서 제공한 정보기사로, 한겨레의 의견과 다를 수 있습니다>"
        private val sponsoredNoteRegex = Regex("""^<이 기사는 .*>$""")

        // The reporter line at the end, e.g. "곽노필 선임기자" or "워싱턴/김원철 특파원"
        private const val MAX_SIGNATURE_LENGTH = 40

        // Paragraphs made up only of bold text, used as section headings within the body
        private const val MAX_BOLD_HEADING_LENGTH = 60
    }

    override fun extractContents(document: Document, title: String?): List<ContentBlock> {
        val article = document.select("article#renewal2023").firstOrNull()
        val body = article?.select("div.article-text")?.firstOrNull()
            ?: return GenericContentsParser().extractContents(document, title)

        body.select("[class*=BaseAd_], [class*=adWrap], .embeded-area, button, iframe, script, style")
            .remove()

        // Opinion columns start with the writer in bold, e.g. "김진해 | 한겨레말글연구소 연구위원"
        val columnist = body.select("p").firstOrNull()
            ?.takeIf { it.isAllBold() && it.text().contains("|") }
            ?.also { it.remove() }
            ?.text()?.trim()

        body.select(".cont-midtitle").forEach { it.replaceWith(Element("h3").text(it.text())) }
        body.select("p").forEach { p ->
            val text = p.text().trim()
            if (p.isAllBold() && text.length <= MAX_BOLD_HEADING_LENGTH) {
                p.replaceWith(Element("h3").text(text))
            }
        }
        body.select("figure img").forEach { it.applySizeFromUrl() }

        val blocks = body.parseBlocks().toMutableList()
        val footers = mutableListOf<String>()

        (blocks.lastOrNull() as? ContentBlock.Text)
            ?.takeIf { sponsoredNoteRegex.matches(it.text) }
            ?.let {
                blocks.removeAt(blocks.lastIndex)
                footers.add(it.text)
            }

        val signature = blocks.takeSignature()
        signature?.emails?.let { footers.add(0, it) }

        document.select("[class*=copyrightBottom]").firstOrNull()?.text()?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { footers.add(it) }

        val subtitle = article.select("[class*=ArticleDetailView_subtitle]").firstOrNull()?.lines()
        if (!subtitle.isNullOrEmpty()) {
            blocks.add(0, ContentBlock.Heading(subtitle.joinToString("\n"), 4))
        }

        val dateline = signature?.name ?: columnist ?: article.reporters()
        if (!dateline.isNullOrEmpty()) {
            blocks.add(0, ContentBlock.Dateline(dateline))
        }

        footers.forEach { blocks.add(ContentBlock.Footer(it)) }
        return blocks
    }

    private class Signature(val name: String?, val emails: String)

    // Removes the reporter line and email addresses that end the body. They're either one
    // paragraph, "고경주 기자 goh@hani.co.kr", or the name and the address in two
    private fun MutableList<ContentBlock>.takeSignature(): Signature? {
        val last = lastOrNull() as? ContentBlock.Text ?: return null
        val emails = emailRegex.findAll(last.text).map { it.value }.toList()
        if (emails.isEmpty()) return null

        val name = last.text.replace(emailRegex, "")
            .replace(Regex("""\s*,\s*"""), ", ")
            .replace(Regex("""\s+"""), " ")
            .trim(' ', ',')
        if (name.length > MAX_SIGNATURE_LENGTH) return null
        removeAt(lastIndex)

        if (name.isNotEmpty()) return Signature(name, emails.joinToString(", "))

        val previous = lastOrNull() as? ContentBlock.Text
        val previousName = previous?.text?.takeIf { it.length <= MAX_SIGNATURE_LENGTH }
        if (previousName != null) removeAt(lastIndex)
        return Signature(previousName, emails.joinToString(", "))
    }

    // The reporters listed under the title, as "최원형, 고경주 기자"
    private fun Element.reporters(): String? {
        val list = select("[class*=ArticleDetailView_reporterList]").firstOrNull() ?: return null
        val names = list.select("a").map { it.text().trim() }.filter { it.isNotEmpty() }
        if (names.isEmpty()) return null
        val title = list.ownText().replace(",", "").trim()
        return listOf(names.joinToString(", "), title).filter { it.isNotEmpty() }.joinToString(" ")
    }

    // Lines of an element split on <br>
    private fun Element.lines(): List<String> {
        val lines = mutableListOf(StringBuilder())
        traverse { node, _ ->
            when {
                node is TextNode -> lines.last().append(node.text())
                node is Element && node.tagName() == "br" -> lines.add(StringBuilder())
            }
        }
        return lines.map { it.toString().replace(Regex("""\s+"""), " ").trim() }
            .filter { it.isNotEmpty() }
    }

    private fun Element.isAllBold(): Boolean {
        val text = text().trim()
        if (text.isEmpty()) return false
        val boldText = select("b, strong").joinToString("") { it.text() }
            .replace(Regex("""\s+"""), "")
        return boldText == text.replace(Regex("""\s+"""), "")
    }

    // The <img> has only a width, which doesn't always match the image
    private fun Element.applySizeFromUrl() {
        val match = imageSizeRegex.find(attr("src")) ?: return
        attr("width", match.groupValues[1])
        attr("height", match.groupValues[2])
    }
}
