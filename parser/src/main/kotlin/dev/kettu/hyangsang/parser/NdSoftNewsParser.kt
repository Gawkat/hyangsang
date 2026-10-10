package dev.kettu.hyangsang.parser

import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode

/**
 * Articles from sites built on ND Soft's news CMS, such as 어린이동아, 미디어오늘 and 시사저널.
 * Their feeds are at `/rss/allArticle.xml` and article pages at `/news/articleView.html`.
 */
class NdSoftNewsParser : ContentsParser {
    companion object {
        const val BODY_ID = "article-view-content-div"

        private val emailRegex = Regex("""[\w.+-]+@[\w-]+(\.[\w-]+)+""")
        private val boldStyleRegex = Regex("""font-weight\s*:\s*(bold|[6-9]00)""")

        // The reporter line some sites end the body with, e.g. "/정종엽 기자" or
        // "김재호 기자 kimyital@kyosu.net"
        private const val MAX_SIGNATURE_LENGTH = 40
        private val signatureSuffixes = listOf("기자", "특파원", "통신원")

        // Paragraphs made up only of bold text, used as section headings within the body. Bold
        // sentences, such as quoted passages, are left as text
        private const val MAX_BOLD_HEADING_LENGTH = 60
        private val headingMarkers = charArrayOf('△', '▲', '■', '□', '◆', '◇')
    }

    override fun extractContents(document: Document, title: String?): List<ContentBlock> {
        val body = document.getElementById(BODY_ID)
            ?: return GenericContentsParser().extractContents(document, title)

        // Read before cleaning the body, which on some sites also holds the reporter box
        val reporter = document.reporter()
        val emails = document
            .select("article.writer .email, .view-editors .user-email, .user-info .email")
            .map { it.text().trim() }
            .filter { it.isNotEmpty() }
            .toMutableList()
        val copyright = document.select(".article-copy, .article-copyright, .view-copyright")
            .firstOrNull()?.text()?.trim()

        body.select(
            ".ad-template, .info-options2, .reveal, .view-editors, .view-copyright, " +
                "button, form, ins, iframe, script, style"
        ).remove()

        // The subtitle is either in the body or just before it, depending on the site's skin
        val subheading = (body.select("h2.subheading, .article-head-sub").firstOrNull()
            ?: document.select("header h2.subheading").firstOrNull())
            ?.also { it.remove() }
            ?.lines()

        body.select("p").forEach { p ->
            val text = p.text().trim()
            if (p.isAllBold() && text.length <= MAX_BOLD_HEADING_LENGTH && !text.isSentence()) {
                // Some sites mark headings with a symbol, "△한글날 100주년··· 6~17일은 ‘한글주간’"
                p.replaceWith(Element("h3").text(text.trimStart(*headingMarkers).trim()))
            }
        }
        // Captions on some sites start with a pointer, "▲영화 '암살자(들)'에 등장하는 ..."
        body.select("figcaption").forEach { caption ->
            val first = caption.textNodes().firstOrNull { it.text().isNotBlank() }
            first?.text(first.text().trimStart().removePrefix("▲"))
        }

        val blocks = body.parseBlocks().toMutableList()

        val signature = blocks.takeSignature(reporter)
        signature?.emails?.forEach { if (it !in emails) emails.add(it) }

        val footers = mutableListOf<String>()
        // A heading has nothing under it at the end of the body, where it's a note such as the
        // issue number, "어린이 경제신문 1367호"
        (blocks.lastOrNull() as? ContentBlock.Heading)?.let {
            blocks.removeAt(blocks.lastIndex)
            footers.add(it.text)
        }
        if (emails.isNotEmpty()) footers.add(emails.joinToString(", "))
        if (!copyright.isNullOrEmpty()) footers.add(copyright)

        if (!subheading.isNullOrEmpty()) {
            blocks.add(0, ContentBlock.Heading(subheading.joinToString("\n"), 4))
        }

        val dateline = reporter ?: signature?.name
        if (!dateline.isNullOrEmpty()) {
            blocks.add(0, ContentBlock.Dateline(dateline))
        }

        footers.forEach { blocks.add(ContentBlock.Footer(it)) }
        return blocks
    }

    private class Signature(val name: String?, val emails: List<String>)

    private fun MutableList<ContentBlock>.takeSignature(reporter: String?): Signature? {
        val last = lastOrNull() as? ContentBlock.Text ?: return null
        val emails = emailRegex.findAll(last.text).map { it.value }.toList()
        val name = last.text.replace(emailRegex, "").trim().removePrefix("/").trim()
        if (name.length > MAX_SIGNATURE_LENGTH) return null

        val isSignature = name.isEmpty() && emails.isNotEmpty() ||
            name == reporter ||
            signatureSuffixes.any { name.endsWith(it) }
        if (!isSignature) return null

        removeAt(lastIndex)
        return Signature(name.takeIf { it.isNotEmpty() }, emails)
    }

    // The reporter, from the box after the body or the line under the title, which each site's
    // skin lays out differently
    private fun Document.reporter(): String? {
        select(".show-for-sr").remove()
        return select(
            "article.writer .name, .view-editors .names, .user-info .name, " +
                ".info-group .writer .name, .info-group li.info-name, " +
                ".info-group .infomation li:has(.icon-user-o)"
        ).asSequence()
            .map { it.text() }
            .plus(select("meta[property=og:article:author]").attr("content"))
            .map { it.replace(emailRegex, "").replace("()", "").replace(Regex("""\s+"""), " ").trim() }
            .firstOrNull { it.isNotEmpty() }
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

    // Ends in a full stop, possibly inside quotes
    private fun String.isSentence(): Boolean = trimEnd('”', '"', '’', '\'').endsWith(".")

    // Bold either through <b> and <strong>, or through the style of the paragraph or the box
    // around it
    private fun Element.isAllBold(): Boolean {
        val text = text().trim()
        if (text.isEmpty()) return false
        if (boldStyleRegex.containsMatchIn(attr("style")) ||
            parent()?.id() != BODY_ID && boldStyleRegex.containsMatchIn(parent()?.attr("style").orEmpty())
        ) {
            return true
        }
        val boldText = select("b, strong").joinToString("") { it.text() }
            .replace(Regex("""\s+"""), "")
        return boldText == text.replace(Regex("""\s+"""), "")
    }
}
