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

// A link's text that looks like a footnote marker, such as 1, [2], 3), * or †
private val NOTE_MARKER = Regex("""^[\[(]?[0-9*†‡]{1,3}[)\]]?$""")

// Note text beyond this is cut, since the sheet that shows it isn't meant for whole pages
private const val MAX_NOTE_LENGTH = 2_000

// An element with less text than this, such as an empty anchor, is only where its note starts
private const val MIN_NOTE_ELEMENT_TEXT = 8

private val NOTE_CONTAINERS = setOf("aside", "dd", "div", "li", "p", "section", "td")

internal const val NOTE_MARKER_TAG = "hyangsang-noteref"

// Images this small are usually characters missing from the font, drawn inline in the text
private const val MIN_IMAGE_SIZE = 32

/**
 * Turns the body of one XHTML document of a book into content blocks. Text that sits directly
 * in containers, as it often does in books, becomes paragraphs of its own.
 */
internal class XhtmlWalker(
    private val folder: String,
    private val images: ImageStore,
    private val styles: Styles = Styles.EMPTY,
    // Finds the element a link points to, in this document or another one of the book
    private val linkTarget: (href: String) -> Element? = { null }
) {
    private val pieces = mutableListOf<Piece>()
    private val pending = mutableListOf<Node>()

    // Notes found by a raised number, to take out of the text with the marked ones
    private val numberedNotes = mutableSetOf<Element>()

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
        applyTextStyles(body)
        // Before the notes are taken out of the text, so markers can still find them
        markNoteReferences(body)
        markRaisedNumbers(body)
        extractNotes(body)
    }

    /**
     * Replaces footnote markers with marker elements that carry their note's text, which become
     * NOTE spans. Markers are links marked as note references, or short numbered links.
     */
    private fun markNoteReferences(body: Element) {
        val refs = body.descendants(null).filter { element ->
            val text = element.text().trim()
            val isRef = element.types().any { it in NOTEREF_TYPES } ||
                (element.localName() == "a" && '#' in element.attr("href") && NOTE_MARKER.matches(text))
            // Links back from a note to its marker aren't markers themselves
            isRef && element.parents().none { parent -> parent.types().any { it in NOTE_TYPES || it in NOTE_LIST_TYPES } }
        }
        for (ref in refs) {
            // One style of marker, in brackets that keep it apart from the word it follows
            val core = ref.text().trim().trim('[', ']', '(', ')')
            val label = if (core.isEmpty()) "" else "[$core]"
            val href = ref.attr("href").ifEmpty { ref.descendants("a").firstOrNull()?.attr("href").orEmpty() }
            val note = linkTarget(href)?.let { noteText(it) }
            if (note == null || label.isEmpty()) {
                ref.replaceWith(TextNode(label))
            } else {
                ref.replaceWith(Element(NOTE_MARKER_TAG).attr("data-note", note).appendText(label))
            }
        }
    }

    /**
     * Wraps the contents of elements styled as italic or bold in i and b elements, which become
     * spans. Only the element's own styles count. For an element around blocks, each block it
     * holds is wrapped instead.
     */
    private fun applyTextStyles(body: Element) {
        val wrapped = mapOf("i" to mutableSetOf<Element>(), "b" to mutableSetOf())
        for (element in body.descendants(null)) {
            val tags = listOfNotNull(
                "i".takeIf { styles.isItalic(element) && element.localName() !in setOf("i", "em") },
                "b".takeIf { styles.isBold(element) && element.localName() !in setOf("b", "strong") }
            )
            if (tags.isEmpty()) continue
            val targets = if (element.hasBlocks()) {
                element.descendants(null).filter { it.localName() in BLOCK_TAGS && !it.hasBlocks() && it.text().isNotBlank() }
            } else {
                listOf(element)
            }
            for (tag in tags) {
                val done = wrapped.getValue(tag)
                // Inside an element already wrapped, as a span in an italic paragraph
                for (target in targets.filter { target -> target !in done && target.parents().none { it in done } }) {
                    val wrapper = Element(tag)
                    target.childNodes().toList().forEach { wrapper.appendChild(it) }
                    target.appendChild(wrapper)
                    done.add(target)
                }
            }
        }
    }

    /**
     * Turns raised numbers, in sup elements or styled as superscript, into markers of the note
     * that starts with the same number further on, set in smaller type than the text the number
     * is in. Some books mark their notes only this way, without links. A raised number without
     * such a note, such as the 2 of m², stays as it is.
     */
    private fun markRaisedNumbers(body: Element) {
        fun isRaisedNumber(element: Element) =
            element.localName() !in BLOCK_TAGS && NOTE_MARKER.matches(element.text().trim()) &&
                styles.isSuperscript(element) && element.descendants(NOTE_MARKER_TAG).isEmpty()

        // A number split into raised elements next to each other, as in <sup>1</sup><sup>7</sup>
        for (element in body.descendants(null).filter { isRaisedNumber(it) }) {
            while (true) {
                val next = element.nextSibling() as? Element ?: break
                val joined = element.text() + next.text()
                if (next.localName() != element.localName() || next.className() != element.className() ||
                    !isRaisedNumber(next) || !NOTE_MARKER.matches(joined.trim())
                ) {
                    break
                }
                element.text(joined)
                next.remove()
            }
        }

        val elements = body.descendants(null)
        val raised = elements.filter { element ->
            isRaisedNumber(element) &&
                element.parents().none { parent -> parent.types().any { it in NOTE_TYPES || it in NOTE_LIST_TYPES } }
        }
        val replaced = mutableSetOf<Element>()
        for (number in raised) {
            // A number inside another one, or at the start of a note already found
            if (number.parents().any { it in replaced || it in numberedNotes }) continue
            val core = number.text().trim().trim('[', ']', '(', ')')
            val note = elements.drop(elements.indexOf(number) + 1)
                .firstOrNull { isNumberedNote(it, core, number) }
                ?: continue
            val text = noteText(note) ?: continue
            numberedNotes.add(note)
            replaced.add(number)
            number.replaceWith(Element(NOTE_MARKER_TAG).attr("data-note", text).appendText("[$core]"))
        }
    }

    // Whether element is a block that starts with the number and is smaller than the text at marker
    private fun isNumberedNote(element: Element, number: String, marker: Element): Boolean {
        if (element.localName() !in NOTE_CONTAINERS || element.hasBlocks() || element in numberedNotes) return false
        val text = element.text().trim().trimStart('[', '(')
        // Not the 2 of 2016
        if (!text.startsWith(number) || text.getOrNull(number.length)?.isDigit() == true) return false
        val rest = text.substring(number.length).trimStart(')', ']', '.').trim()
        if (rest.isEmpty()) return false
        // Sizes set on the blocks around both apply to both, so only the ones below those count
        val shared = marker.parents().toSet()
        val size = (listOf(element) + element.parents())
            .takeWhile { it !in shared }
            .firstNotNullOfOrNull { styles.value(it, "font-size") }
            ?.let { relativeSize(it) }
        return size != null && size < 1f
    }

    // The text of the note at target: the element itself, or the block around an anchor
    private fun noteText(target: Element): String? {
        var element = target
        while (element.text().trim().length < MIN_NOTE_ELEMENT_TEXT) {
            element = element.parents().firstOrNull { it.localName() in NOTE_CONTAINERS } ?: break
        }
        val text = element.text().replace("↩", "").trim()
        return text.takeIf { it.isNotEmpty() }?.take(MAX_NOTE_LENGTH)
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

                types.any { it in NOTE_TYPES } || element in numberedNotes -> addNote(element)
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
            "hr" -> add(ContentBlock.Divider)
            else -> if (element.hasBlocks()) visit(element) else paragraph(element, emptyIsBreak = name == "p")
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

    private fun paragraph(element: Element, emptyIsBreak: Boolean = false) {
        val (text, spans) = element.extractTextAndSpans(noteFor = ::markedNote)
        when {
            text.isNotBlank() -> add(ContentBlock.Text(text, spans))
            // Books leave empty paragraphs between their parts, as blank lines
            emptyIsBreak -> add(ContentBlock.Break)
        }
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

    private fun markedNote(element: Element): String? =
        if (element.tagName() == NOTE_MARKER_TAG) element.attr("data-note") else null

    private fun Element.hasBlocks(): Boolean = descendants(null).any { it.localName() in BLOCK_TAGS }

    private fun Element.types(): List<String> =
        (attr("epub:type") + " " + attr("role")).split(' ').filter { it.isNotEmpty() }

    private fun Element.dimension(name: String): Int? =
        attr(name).trim().removeSuffix("px").toIntOrNull()?.takeIf { it > 0 }
}
