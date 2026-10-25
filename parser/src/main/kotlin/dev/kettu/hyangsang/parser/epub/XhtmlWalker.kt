package dev.kettu.hyangsang.parser.epub

import dev.kettu.hyangsang.parser.ContentBlock
import dev.kettu.hyangsang.parser.ContentSpan
import dev.kettu.hyangsang.parser.extractTextAndSpans
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

internal sealed interface Piece {
    data class Block(val block: ContentBlock) : Piece

    /** Where a table of contents entry starts a new chapter */
    data class Marker(val index: Int) : Piece
}

// Elements that start a new block. Anything else is inline, part of the paragraph around it.
private val BLOCK_TAGS = setOf(
    "address", "article", "aside", "blockquote", "body", "caption", "center", "dd", "details",
    "dialog", "div", "dl", "dt", "fieldset", "figcaption", "figure", "footer", "form", "h1", "h2",
    "h3", "h4", "h5", "h6", "header", "hgroup", "hr", "html", "image", "img", "li", "main", "nav",
    "ol", "p", "pre", "section", "summary", "svg", "table", "tbody", "td", "tfoot", "th", "thead",
    "tr", "ul", TOC_MARKER
)

private val HEADINGS = setOf("h1", "h2", "h3", "h4", "h5", "h6")

private val SKIPPED_TAGS = setOf("head", "script", "style", "title", "link", "meta", "noscript")

private val NOTE_TYPES = setOf("footnote", "endnote", "rearnote", "note", "doc-footnote", "doc-endnote")
private val NOTE_LIST_TYPES = setOf("footnotes", "endnotes", "rearnotes", "doc-endnotes")
private val NOTEREF_TYPES = setOf("noteref", "doc-noteref")

private val HANGUL = Regex("[가-힣ㄱ-ㅎㅏ-ㅣ]")

// Images this small are usually characters missing from the font, drawn inline in the text
private const val MIN_IMAGE_SIZE = 32

/**
 * Turns the body of one XHTML document of a book into content blocks. Text that sits directly
 * in containers, as it often does in books, becomes paragraphs of its own.
 */
internal class XhtmlWalker(private val folder: String, private val images: ImageStore) {
    private val pieces = mutableListOf<Piece>()
    private val pending = mutableListOf<Node>()

    /** The document's footnotes and endnotes, to show after its text */
    val notes = mutableListOf<ContentBlock>()

    fun walk(body: Element): List<Piece> {
        prepare(body)
        visit(body)
        flush()
        return pieces
    }

    private fun prepare(body: Element) {
        body.descendants(null).filter { it.localName() in SKIPPED_TAGS }.forEach { it.remove() }
        body.descendants("ruby").forEach { it.replaceWith(TextNode(rubyText(it))) }
        extractNotes(body)
        body.descendants(null).filter { it.types().any { type -> type in NOTEREF_TYPES } }.forEach { ref ->
            val text = ref.text().trim()
            ref.replaceWith(TextNode(if (text.isEmpty() || text.startsWith("[")) text else "[$text]"))
        }
    }

    /**
     * Ruby glosses are kept in parentheses after the text they gloss, Hangul first so lookups
     * find it: 학교(學校), whichever of the two is the base.
     */
    private fun rubyText(ruby: Element): String {
        val gloss = ruby.descendants("rt").joinToString("") { it.text() }.trim()
        ruby.descendants(null).filter { it.localName() == "rt" || it.localName() == "rp" }.forEach { it.remove() }
        val base = ruby.text().trim()
        return when {
            gloss.isEmpty() || gloss == base -> base
            !HANGUL.containsMatchIn(base) && HANGUL.containsMatchIn(gloss) -> "$gloss($base)"
            else -> "$base($gloss)"
        }
    }

    private fun extractNotes(body: Element) {
        val removed = mutableSetOf<Element>()
        for (element in body.descendants(null)) {
            if (element.parents().any { it in removed }) continue
            val types = element.types()
            when {
                types.any { it in NOTE_LIST_TYPES } -> {
                    // One note a list item, or a child that isn't the list's heading
                    element.descendants("li")
                        .ifEmpty { element.children().filter { it.localName() !in HEADINGS } }
                        .forEach { addNote(it) }
                }

                types.any { it in NOTE_TYPES } -> addNote(element)
                else -> continue
            }
            removed.add(element)
        }
        removed.forEach { it.remove() }
    }

    private fun addNote(element: Element) {
        val (text, _) = element.extractTextAndSpans()
        if (text.isNotBlank()) notes.add(ContentBlock.Footer(text))
    }

    private fun visit(element: Element) {
        for (node in element.childNodes().toList()) {
            when (node) {
                is TextNode -> if (pending.isNotEmpty() || !node.isBlank) pending.add(node)
                is Element -> visitElement(node)
                else -> Unit
            }
        }
    }

    private fun visitElement(element: Element) {
        val name = element.localName()
        if (name !in BLOCK_TAGS) {
            // Inline, unless it wraps blocks, as links sometimes do
            if (element.hasBlocks()) {
                flush()
                visit(element)
                flush()
            } else if (name != "br" || pending.isNotEmpty()) {
                pending.add(element)
            }
            return
        }
        flush()
        when (name) {
            TOC_MARKER -> pieces.add(Piece.Marker(element.attr("data-index").toInt()))
            "h1", "h2", "h3", "h4", "h5", "h6" -> heading(element, name.last().digitToInt())
            "img", "image" -> image(element, caption = null)
            "svg" -> element.descendants("image").firstOrNull()?.let { image(it, caption = null) }
            "figure" -> figure(element)
            "table" -> table(element)
            "hr" -> add(ContentBlock.Text("* * *"))
            else -> if (element.hasBlocks()) visit(element) else paragraph(element)
        }
        flush()
    }

    private fun flush() {
        if (pending.isEmpty()) return
        val paragraph = Element("p")
        pending.forEach { paragraph.appendChild(it.clone()) }
        pending.clear()
        paragraph(paragraph)
    }

    private fun paragraph(element: Element) {
        val (text, spans) = element.extractTextAndSpans()
        if (text.isNotBlank()) add(ContentBlock.Text(text, spans))
    }

    private fun heading(element: Element, level: Int) {
        val text = element.text().trim()
        if (text.isEmpty()) {
            element.descendants(null).firstOrNull { it.localName() == "img" || it.localName() == "image" }
                ?.let { image(it, caption = null) }
            return
        }
        // The chapter's title takes the first level in the reader
        add(ContentBlock.Heading(text, level.coerceIn(2, 4)))
    }

    private fun figure(element: Element) {
        val img = element.descendants(null).firstOrNull { it.localName() == "img" || it.localName() == "image" }
        if (img == null) {
            visit(element)
            return
        }
        val caption = element.descendants("figcaption").firstOrNull()?.extractTextAndSpans()
        image(img, caption)
    }

    private fun image(element: Element, caption: Pair<String, List<ContentSpan>>?) {
        val width = element.dimension("width")
        val height = element.dimension("height")
        if ((width != null && width < MIN_IMAGE_SIZE) || (height != null && height < MIN_IMAGE_SIZE)) return
        val src = element.attr("src").ifEmpty { element.attrIgnoreCase("href") }
        val path = EpubArchive.resolve(folder, src) ?: return
        val name = images.store(path) ?: return
        add(
            ContentBlock.Image(
                url = EpubParser.IMAGE_PREFIX + name,
                caption = caption?.first?.takeIf { it.isNotBlank() },
                captionSpans = caption?.second.orEmpty(),
                width = width,
                height = height
            )
        )
    }

    // A row a line, its cells separated by dots
    private fun table(element: Element) {
        for (row in element.descendants("tr")) {
            val cells = row.children()
                .filter { it.localName() == "td" || it.localName() == "th" }
                .map { it.text().trim() }
                .filter { it.isNotEmpty() }
            if (cells.isNotEmpty()) add(ContentBlock.Text(cells.joinToString(" · ")))
        }
    }

    private fun add(block: ContentBlock) {
        pieces.add(Piece.Block(block))
    }

    private fun Element.hasBlocks(): Boolean = descendants(null).any { it.localName() in BLOCK_TAGS }

    private fun Element.types(): List<String> =
        (attr("epub:type") + " " + attr("role")).split(' ').filter { it.isNotEmpty() }

    private fun Element.dimension(name: String): Int? =
        attr(name).trim().removeSuffix("px").toIntOrNull()?.takeIf { it > 0 }
}
