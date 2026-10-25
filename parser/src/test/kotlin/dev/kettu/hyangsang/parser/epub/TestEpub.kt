package dev.kettu.hyangsang.parser.epub

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Builds small EPUB files for tests, so no third-party books need to be committed. */
class TestEpub {
    private val files = linkedMapOf<String, ByteArray>()
    private val manifest = mutableListOf<String>()
    private val spine = mutableListOf<String>()
    private var metadata = """<dc:title>테스트 책</dc:title><dc:creator>김작가</dc:creator><dc:language>ko</dc:language>"""
    private var spineAttributes = ""
    private var withPackage = true

    fun metadata(xml: String) = apply { metadata = xml }

    fun spineAttributes(attributes: String) = apply { spineAttributes = attributes }

    fun withoutPackage() = apply { withPackage = false }

    /** Adds a chapter document at OEBPS/[name], in the spine unless [attributes] say otherwise. */
    fun chapter(name: String, body: String, itemref: String = "") = apply {
        val id = "c${manifest.size}"
        file("OEBPS/$name", xhtml(body))
        manifest.add("""<item id="$id" href="$name" media-type="application/xhtml+xml"/>""")
        spine.add("""<itemref idref="$id" $itemref/>""")
    }

    /** Adds an EPUB 3 navigation document listing [entries] of href to title. */
    fun nav(vararg entries: Pair<String, String>) = apply {
        val items = entries.joinToString("") { (href, title) -> """<li><a href="$href">$title</a></li>""" }
        file(
            "OEBPS/nav.xhtml",
            xhtml("""<nav epub:type="toc"><h1>목차</h1><ol>$items</ol></nav>""")
        )
        manifest.add("""<item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>""")
    }

    /** Adds an EPUB 2 NCX listing [entries] of src to title. */
    fun ncx(vararg entries: Pair<String, String>) = apply {
        val points = entries.withIndex().joinToString("") { (i, entry) ->
            """<navPoint id="p$i" playOrder="${i + 1}"><navLabel><text>${entry.second}</text></navLabel><content src="${entry.first}"/></navPoint>"""
        }
        file(
            "OEBPS/toc.ncx",
            """<?xml version="1.0" encoding="UTF-8"?>
            <ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1"><navMap>$points</navMap></ncx>"""
                .toByteArray()
        )
        manifest.add("""<item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>""")
        spineAttributes = """toc="ncx""""
    }

    fun manifestItem(xml: String) = apply { manifest.add(xml) }

    fun file(path: String, bytes: ByteArray) = apply { files[path] = bytes }

    fun file(path: String, text: String) = file(path, text.toByteArray())

    fun writeTo(target: File): File {
        ZipOutputStream(target.outputStream()).use { zip ->
            fun put(path: String, bytes: ByteArray) {
                zip.putNextEntry(ZipEntry(path))
                zip.write(bytes)
                zip.closeEntry()
            }
            put("mimetype", "application/epub+zip".toByteArray())
            if (withPackage) {
                put(
                    "META-INF/container.xml",
                    """<?xml version="1.0"?>
                    <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                    <rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles>
                    </container>""".toByteArray()
                )
                put(
                    "OEBPS/content.opf",
                    """<?xml version="1.0" encoding="UTF-8"?>
                    <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="id">
                    <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">$metadata</metadata>
                    <manifest>${manifest.joinToString("")}</manifest>
                    <spine $spineAttributes>${spine.joinToString("")}</spine>
                    </package>""".toByteArray()
                )
            }
            files.forEach { (path, bytes) -> put(path, bytes) }
        }
        return target
    }

    companion object {
        fun xhtml(body: String): String =
            """<?xml version="1.0" encoding="UTF-8"?>
            <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
            <head><title>t</title><style>p { margin: 0 }</style></head>
            <body>$body</body></html>"""

        // The PNG signature is enough for the parser, which never decodes images
        val PNG: ByteArray = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
        )
    }
}
