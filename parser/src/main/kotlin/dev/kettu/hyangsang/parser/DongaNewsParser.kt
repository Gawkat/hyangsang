package dev.kettu.hyangsang.parser

import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/**
 * Articles from 동아일보 (www.donga.com). The body is text separated by `<br><br>` rather than
 * paragraphs, with photos and ads between them, and bold lines as section headings.
 */
class DongaNewsParser : ContentsParser {
    companion object {
        const val HOST = "donga.com"

        private val emailRegex = Regex("""[\w.+-]+@[\w-]+(\.[\w-]+)+""")

        // Wire stories end with their source instead of a byline, "[서울=뉴시스]" or "(서울=뉴스1)"
        private val wireCreditRegex = Regex("""^[\[(]([^\[\]()]+=[^\[\]()]+)[\])]$""")

        // Lines made up only of bold text, used as section headings within the body
        private const val MAX_BOLD_HEADING_LENGTH = 60
        private val headingMarkers = charArrayOf('●', '■', '◆', '▲', '△')

        private val blockTags = setOf(
            "figure", "div", "p", "h1", "h2", "h3", "h4", "h5", "h6", "ul", "ol", "table", "blockquote"
        )
    }

    override fun extractContents(document: Document, title: String?): List<ContentBlock> {
        val body = document.select("section.news_view").firstOrNull()
            ?: return GenericContentsParser().extractContents(document, title)

        body.select(
            "[class^=view_ad], [class^=view_m_ad], .ad, .a1, .btn_img, button, iframe, script, style"
        ).remove()

        val subtitle = body.select("h2.sub_tit").firstOrNull()
            ?.also { it.remove() }
            ?.lines()

        val blocks = body.toParagraphs().parseBlocks().toMutableList()

        val wireCredit = (blocks.lastOrNull() as? ContentBlock.Text)
            ?.let { wireCreditRegex.matchEntire(it.text) }
            ?.also { blocks.removeAt(blocks.lastIndex) }
            ?.groupValues?.get(1)

        // Each reporter on a line of their own, "광주=이형주 기자 peneye09@donga.com"
        val bylines = document.select(".byline").firstOrNull()?.lines().orEmpty()
        val reporters = bylines.map { it.replace(emailRegex, "").trim() }.filter { it.isNotEmpty() }
        val emails = bylines.flatMap { line -> emailRegex.findAll(line).map { it.value } }

        val footers = mutableListOf<String>()
        if (emails.isNotEmpty()) footers.add(emails.joinToString(", "))
        document.select(".caution_text").firstOrNull()?.text()?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { footers.add(it) }

        if (!subtitle.isNullOrEmpty()) {
            blocks.add(0, ContentBlock.Heading(subtitle.joinToString("\n"), 4))
        }

        val dateline = reporters.joinToString(", ").ifEmpty { wireCredit }
        if (!dateline.isNullOrEmpty()) {
            blocks.add(0, ContentBlock.Dateline(dateline))
        }

        footers.forEach { blocks.add(ContentBlock.Footer(it)) }
        return blocks
    }

    /**
     * The body with its text wrapped in paragraphs. A blank line ends a paragraph, a single line
     * break stays a line break within it, and a line that's only bold text becomes a heading.
     * Photos and other boxes are kept as they are.
     */
    private fun Element.toParagraphs(): Element {
        // Headings carry their line break inside the bold text, "<b>● 제목<br></b><br>"
        select("b, strong").forEach { bold ->
            while (bold.childNodes().firstOrNull { !it.isSpace() }?.isBr() == true) {
                bold.childNodes().first { !it.isSpace() }.also { it.remove(); bold.before(it) }
            }
            while (bold.childNodes().lastOrNull { !it.isSpace() }?.isBr() == true) {
                bold.childNodes().last { !it.isSpace() }.also { it.remove(); bold.after(it) }
            }
        }

        val result = Element("div")
        var lines = mutableListOf<List<Node>>()
        var line = mutableListOf<Node>()

        fun endParagraph() {
            if (lines.isEmpty()) return
            val p = Element("p")
            lines.forEachIndexed { i, nodes ->
                if (i > 0) p.appendChild(Element("br"))
                nodes.forEach { p.appendChild(it) }
            }
            result.appendChild(p)
            lines = mutableListOf()
        }

        fun endLine() {
            if (line.all { it.isSpace() }) {
                // A blank line, the second <br> in a row
                endParagraph()
            } else {
                val heading = line.headingText()
                if (heading != null) {
                    endParagraph()
                    result.appendChild(Element("h3").text(heading))
                } else {
                    lines.add(line)
                }
            }
            line = mutableListOf()
        }

        for (node in childNodes().toList()) {
            when {
                node.isBr() -> endLine()
                node is Element && node.tagName() in blockTags -> {
                    endLine()
                    endParagraph()
                    result.appendChild(node)
                }
                else -> line.add(node)
            }
        }
        endLine()
        endParagraph()
        return result
    }

    // The text of a line that's only bold, short and not a sentence, without its marker
    private fun List<Node>.headingText(): String? {
        val content = filterNot { it.isSpace() }
        val bold = content.singleOrNull() as? Element ?: return null
        if (bold.tagName() !in setOf("b", "strong")) return null
        val text = bold.text().trim().trimStart(*headingMarkers).trim()
        return text.takeIf { it.isNotEmpty() && it.length <= MAX_BOLD_HEADING_LENGTH && !it.endsWith(".") }
    }

    private fun Node.isBr(): Boolean = this is Element && tagName() == "br"

    // Whitespace, or comments such as <!--BYLINE-->
    private fun Node.isSpace(): Boolean = when (this) {
        is TextNode -> isBlank()
        is Element -> false
        else -> true
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
}
