package dev.kettu.hyangsang.parser.epub

import dev.kettu.hyangsang.parser.ContentBlock
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.io.File
import java.io.IOException

data class EpubBook(
    val title: String?,
    val author: String?,
    val language: String?,
    /** The cover's file name in the image folder, or null if the book names none */
    val cover: String?,
    val chapters: List<EpubChapter>
)

/** A chapter of a book, titled from the table of contents or its first heading if it has one. */
data class EpubChapter(
    val title: String?,
    val blocks: List<ContentBlock>
)

sealed interface EpubResult {
    data class Success(val book: EpubBook) : EpubResult
    data class Failure(val reason: EpubFailure) : EpubResult
}

enum class EpubFailure {
    /** Copy-protected, so the text can't be read */
    DRM,

    /** Laid out as fixed pages, like comics, rather than text that reflows */
    FIXED_LAYOUT,

    /** Readable, but without any text to show */
    NO_TEXT,

    /** Bigger than [EpubLimits] allow, unpacked */
    TOO_LARGE,

    /** Not an EPUB, or too damaged to read */
    INVALID
}

/**
 * Turns an EPUB file into chapters of content blocks. The file is untrusted: sizes are capped
 * (see [EpubLimits]), only images are written out, under names made up here, and XML is parsed
 * without loading DTDs or external entities.
 */
class EpubParser(private val limits: EpubLimits = EpubLimits()) {
    /**
     * Parses [file], writing the book's images to [imageDir]. Image blocks refer to them as
     * [IMAGE_PREFIX] followed by the file name.
     */
    fun parse(file: File, imageDir: File): EpubResult =
        try {
            EpubArchive(file, limits).use { BookReader(it, imageDir).read() }
        } catch (_: EpubTooLargeException) {
            EpubResult.Failure(EpubFailure.TOO_LARGE)
        } catch (_: IOException) {
            EpubResult.Failure(EpubFailure.INVALID)
        } catch (_: RuntimeException) {
            // Malformed archives and documents can fail in many ways deep in the libraries
            EpubResult.Failure(EpubFailure.INVALID)
        }

    companion object {
        /** Raise when a change should reach books already imported, which are then parsed again. */
        const val VERSION = 1

        const val IMAGE_PREFIX = "book-image:"

        // Longer chapters are split into parts, so one stays well under the 2 MB a database row
        // can have when read back. Korean text takes about 3 bytes a character as JSON.
        internal const val MAX_CHAPTER_CHARS = 150_000
    }
}

private val FONT_OBFUSCATION = setOf(
    "http://www.idpf.org/2008/embedding",
    "http://ns.adobe.com/pdf/enc#RC"
)

private val IMAGE_TYPES = mapOf(
    "image/jpeg" to "jpg",
    "image/png" to "png",
    "image/gif" to "gif",
    "image/webp" to "webp"
)

private val IMAGE_EXTENSIONS = mapOf(
    "jpg" to "jpg",
    "jpeg" to "jpg",
    "png" to "png",
    "gif" to "gif",
    "webp" to "webp"
)

private class ManifestItem(val path: String, val mediaType: String, val properties: List<String>)

private class TocEntry(val path: String, val fragment: String?, val title: String)

private class BookReader(private val archive: EpubArchive, imageDir: File) {
    private val images = ImageStore(archive, imageDir)

    fun read(): EpubResult {
        if (isCopyProtected()) return EpubResult.Failure(EpubFailure.DRM)

        val opfPath = rootFile() ?: throw EpubInvalidException("No package document")
        val opf = xml(archive.readText(opfPath) ?: throw EpubInvalidException("Missing $opfPath"))
        val opfFolder = EpubArchive.folderOf(opfPath)
        if (isFixedLayout(opf)) return EpubResult.Failure(EpubFailure.FIXED_LAYOUT)

        val manifest = opf.descendants("item").mapNotNull { item ->
            val path = EpubArchive.resolve(opfFolder, item.attr("href")) ?: return@mapNotNull null
            item.attr("id") to ManifestItem(
                path = path,
                mediaType = item.attr("media-type").lowercase(),
                properties = item.attr("properties").split(' ').filter { it.isNotEmpty() }
            )
        }.toMap()
        images.mediaTypes = manifest.values.associate { it.path to it.mediaType }

        val spineElement = opf.descendants("spine").firstOrNull()
            ?: throw EpubInvalidException("No spine")
        val spine = spineElement.descendants("itemref")
            .filter { it.attr("linear") != "no" }
            .mapNotNull { manifest[it.attr("idref")] }
            .filter { it.isDocument() }
        if (spine.isEmpty()) throw EpubInvalidException("Empty spine")

        val toc = navToc(manifest.values) ?: ncxToc(manifest, spineElement) ?: emptyList()
        val chapters = ChapterAssembler(archive, images, toc).assemble(spine.map { it.path })
        if (chapters.none { chapter -> chapter.blocks.any { it is ContentBlock.Text } }) {
            return EpubResult.Failure(EpubFailure.NO_TEXT)
        }

        val metadata = opf.descendants("metadata").firstOrNull()
        return EpubResult.Success(
            EpubBook(
                title = metadata?.firstText("title"),
                author = metadata?.firstText("creator"),
                language = metadata?.firstText("language"),
                cover = coverPath(opf, manifest)?.let { images.store(it) },
                chapters = chapters
            )
        )
    }

    private fun isCopyProtected(): Boolean {
        if (archive.contains("META-INF/rights.xml") || archive.contains("META-INF/license.lcpl")) {
            return true
        }
        // Font obfuscation is common in books without DRM, and only hides the fonts
        val encryption = archive.readText("META-INF/encryption.xml") ?: return false
        return xml(encryption).descendants("encryptionmethod").any {
            it.attrIgnoreCase("algorithm") !in FONT_OBFUSCATION
        }
    }

    private fun rootFile(): String? {
        val container = archive.readText("META-INF/container.xml") ?: return null
        val rootFiles = xml(container).descendants("rootfile")
        val rootFile = rootFiles.firstOrNull {
            it.attrIgnoreCase("media-type") == "application/oebps-package+xml"
        } ?: rootFiles.firstOrNull()
        return rootFile?.let { EpubArchive.resolve("", it.attrIgnoreCase("full-path")) }
    }

    private fun isFixedLayout(opf: Document): Boolean {
        val metas = opf.descendants("meta")
        if (metas.any { it.attr("property") == "rendition:layout" && it.text().trim() == "pre-paginated" }) {
            return true
        }
        if (metas.any { it.attr("name") == "fixed-layout" && it.attr("content") == "true" }) {
            return true
        }
        val appleOptions = archive.readText("META-INF/com.apple.ibooks.display-options.xml")
            ?: return false
        return xml(appleOptions).descendants("option").any {
            it.attr("name") == "fixed-layout" && it.text().trim() == "true"
        }
    }

    /** The EPUB 3 table of contents, the nav element of the navigation document. */
    private fun navToc(items: Collection<ManifestItem>): List<TocEntry>? {
        val nav = items.firstOrNull { "nav" in it.properties } ?: return null
        val document = xml(archive.readText(nav.path) ?: return null)
        val navs = document.descendants("nav")
        val tocNav = navs.firstOrNull { "toc" in it.attr("epub:type").split(' ') }
            ?: navs.firstOrNull()
            ?: return null
        val folder = EpubArchive.folderOf(nav.path)
        return tocNav.descendants("a").mapNotNull { link ->
            tocEntry(folder, link.attr("href"), link.text())
        }.ifEmpty { null }
    }

    /** The EPUB 2 table of contents, an NCX file. */
    private fun ncxToc(manifest: Map<String, ManifestItem>, spine: Element): List<TocEntry>? {
        val ncx = manifest[spine.attr("toc")]
            ?: manifest.values.firstOrNull { it.mediaType == "application/x-dtbncx+xml" }
            ?: return null
        val document = xml(archive.readText(ncx.path) ?: return null)
        val folder = EpubArchive.folderOf(ncx.path)
        // Nested points come after their parent in document order, like the chapters they point to
        return document.descendants("navpoint").mapNotNull { point ->
            val label = point.children().firstOrNull { it.localName() == "navlabel" }
                ?.descendants("text")?.firstOrNull()?.text().orEmpty()
            val src = point.children().firstOrNull { it.localName() == "content" }?.attr("src").orEmpty()
            tocEntry(folder, src, label)
        }.ifEmpty { null }
    }

    private fun tocEntry(folder: String, href: String, title: String): TocEntry? {
        val path = EpubArchive.resolve(folder, href) ?: return null
        val text = title.trim().replace(WHITESPACE, " ")
        if (text.isEmpty()) return null
        return TocEntry(path, href.substringAfter('#', "").ifEmpty { null }, text)
    }

    private fun coverPath(opf: Document, manifest: Map<String, ManifestItem>): String? {
        manifest.values.firstOrNull { "cover-image" in it.properties }?.let { return it.path }
        val coverId = opf.descendants("meta").firstOrNull { it.attr("name") == "cover" }?.attr("content")
        return coverId?.let { manifest[it] }?.takeIf { it.mediaType.startsWith("image/") }?.path
    }
}

/**
 * Splits the spine's documents into chapters at the table of contents' entries. Documents and
 * parts of them that the table of contents doesn't point to continue the chapter before them, as
 * when a long chapter is stored in several files.
 */
private class ChapterAssembler(
    private val archive: EpubArchive,
    private val images: ImageStore,
    private val toc: List<TocEntry>
) {
    private class Draft(val title: String?, val blocks: MutableList<ContentBlock> = mutableListOf())

    private val drafts = mutableListOf<Draft>()

    fun assemble(paths: List<String>): List<EpubChapter> {
        val tocByPath = toc.groupBy { it.path }
        for (path in paths) {
            val html = archive.readText(path) ?: continue
            addDocument(path, xml(html), tocByPath[path].orEmpty())
        }
        return drafts
            .map { finish(it) }
            .let { mergeEmpty(it) }
            .flatMap { split(it) }
    }

    private fun addDocument(path: String, document: Document, entries: List<TocEntry>) {
        val body = document.descendants("body").firstOrNull() ?: return
        val markers = mutableListOf<TocEntry>()
        for (entry in entries) {
            val fragment = entry.fragment ?: continue
            if (markers.any { it.fragment == fragment }) continue
            val target = body.descendants(null).firstOrNull { it.attr("id") == fragment } ?: continue
            val marker = Element(TOC_MARKER).attr("data-index", markers.size.toString())
            if (target == body) body.prependChild(marker) else target.before(marker)
            markers.add(entry)
        }

        val walker = XhtmlWalker(EpubArchive.folderOf(path), images)
        val pieces = walker.walk(body)
        val wholeDocumentTitle = entries.firstOrNull { it.fragment == null }?.title

        var current: Draft? = when {
            wholeDocumentTitle != null -> Draft(wholeDocumentTitle).also { drafts.add(it) }
            // Without a table of contents, each document is a chapter
            toc.isEmpty() || drafts.isEmpty() -> Draft(null).also { drafts.add(it) }
            else -> drafts.last()
        }
        for (piece in pieces) {
            when (piece) {
                is Piece.Block -> current!!.blocks.add(piece.block)
                is Piece.Marker -> {
                    current = Draft(markers[piece.index].title)
                    drafts.add(current)
                }
            }
        }
        current!!.blocks.addAll(walker.notes)
    }

    // Titles chapters without one by their first heading, and drops a heading that repeats it
    private fun finish(draft: Draft): EpubChapter {
        val firstHeading = draft.blocks.firstOrNull() as? ContentBlock.Heading
        val title = draft.title ?: firstHeading?.text
        val blocks = if (firstHeading != null && firstHeading.text.normalized() == title?.normalized()) {
            draft.blocks.drop(1)
        } else {
            draft.blocks
        }
        return EpubChapter(title, blocks)
    }

    // A chapter without text, such as a cover page or an illustration, is kept with the chapter
    // before it, or dropped at the start of the book
    private fun mergeEmpty(chapters: List<EpubChapter>): List<EpubChapter> {
        val result = mutableListOf<EpubChapter>()
        for (chapter in chapters) {
            val hasText = chapter.blocks.any { it is ContentBlock.Text || it is ContentBlock.Heading }
            when {
                hasText -> result.add(chapter)

                result.isNotEmpty() -> {
                    val previous = result.removeAt(result.lastIndex)
                    result.add(previous.copy(blocks = previous.blocks + chapter.blocks))
                }
            }
        }
        return result
    }

    private fun split(chapter: EpubChapter): List<EpubChapter> {
        if (chapter.blocks.sumOf { it.length() } <= EpubParser.MAX_CHAPTER_CHARS) {
            return listOf(chapter)
        }
        val parts = mutableListOf<List<ContentBlock>>()
        var part = mutableListOf<ContentBlock>()
        var length = 0
        for (block in chapter.blocks) {
            if (length + block.length() > EpubParser.MAX_CHAPTER_CHARS && part.isNotEmpty()) {
                // Prefer to start the next part at a heading in the second half of this one
                val heading = part.indexOfLast { it is ContentBlock.Heading }
                val cut = if (heading > part.size / 2) heading else part.size
                parts.add(part.subList(0, cut).toList())
                part = part.subList(cut, part.size).toMutableList()
                length = part.sumOf { it.length() }
            }
            part.add(block)
            length += block.length()
        }
        parts.add(part)
        return parts.mapIndexed { i, blocks ->
            val title = if (i == 0 || chapter.title == null) chapter.title else "${chapter.title} (${i + 1})"
            EpubChapter(title, blocks)
        }
    }
}

/** Writes the book's images out under made-up names, each once. */
internal class ImageStore(private val archive: EpubArchive, private val dir: File) {
    var mediaTypes: Map<String, String> = emptyMap()
    private val names = HashMap<String, String>()
    private val failed = HashSet<String>()

    /** The file name of the image at [path] in the archive, or null if it isn't a usable image. */
    fun store(path: String): String? {
        names[path]?.let { return it }
        if (path in failed) return null
        val extension = IMAGE_TYPES[mediaTypes[path]]
            ?: IMAGE_EXTENSIONS[path.substringAfterLast('.', "").lowercase()]
        val bytes = extension?.let { archive.read(path) }
        if (extension == null || bytes == null) {
            failed.add(path)
            return null
        }
        val name = "${names.size + 1}.$extension"
        dir.mkdirs()
        File(dir, name).writeBytes(bytes)
        names[path] = name
        return name
    }
}

internal const val TOC_MARKER = "hyangsang-toc"

private val WHITESPACE = Regex("\\s+")

// Jsoup's XML parser keeps HTML entities working and never loads DTDs or external entities
internal fun xml(text: String): Document = Jsoup.parse(text, "", Parser.xmlParser())

internal fun Element.localName(): String = tagName().substringAfter(':').lowercase()

/** Descendants with the local name [name], ignoring namespace prefixes, or all with null. */
internal fun Element.descendants(name: String?): List<Element> =
    allElements.drop(1).filter { name == null || it.localName() == name }

internal fun Element.attrIgnoreCase(name: String): String =
    attributes().firstOrNull { it.key.substringAfter(':').equals(name, ignoreCase = true) }?.value.orEmpty()

private fun Element.firstText(name: String): String? =
    descendants(name).firstNotNullOfOrNull { it.text().trim().ifEmpty { null } }

private fun ManifestItem.isDocument(): Boolean =
    mediaType == "application/xhtml+xml" || mediaType == "text/html" ||
        path.substringAfterLast('.').lowercase() in setOf("xhtml", "html", "htm")

private fun String.normalized() = trim().replace(WHITESPACE, " ")

private fun ContentBlock.length(): Int = when (this) {
    is ContentBlock.Text -> text.length
    is ContentBlock.Heading -> text.length
    is ContentBlock.Image -> caption?.length ?: 0
    is ContentBlock.Dateline -> text.length
    is ContentBlock.Footer -> text.length
}
