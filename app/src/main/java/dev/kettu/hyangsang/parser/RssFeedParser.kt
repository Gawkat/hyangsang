package dev.kettu.hyangsang.parser

import android.util.Xml
import dev.kettu.hyangsang.data.local.entity.RssItem
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException

private val ns: String? = null

// An & that doesn't start a named or numeric character reference
private val BARE_AMPERSAND = Regex("""&(?!(?:[A-Za-z_:][\w.:-]*|#[0-9]+|#x[0-9a-fA-F]+);)""")
private val CDATA_SECTION = Regex("""<!\[CDATA\[.*?]]>""", RegexOption.DOT_MATCHES_ALL)

// Some feeds leave a bare & in URLs, which makes the whole document malformed. A bare & is
// never valid XML, so escaping it can't change a valid feed. CDATA is literal text, so it's
// left as is
internal fun escapeBareAmpersands(xml: String): String {
    val result = StringBuilder(xml.length)
    var last = 0
    CDATA_SECTION.findAll(xml).forEach { cdata ->
        result.append(xml.substring(last, cdata.range.first).replace(BARE_AMPERSAND, "&amp;"))
        result.append(cdata.value)
        last = cdata.range.last + 1
    }
    result.append(xml.substring(last).replace(BARE_AMPERSAND, "&amp;"))
    return result.toString()
}

class RssFeedParser {
    @Throws(XmlPullParserException::class, IOException::class)
    fun parse(xml: String): List<RssItem> {
        val parser: XmlPullParser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(escapeBareAmpersands(xml).reader())
        parser.nextTag()

        return readFeed(parser)
    }

    @Throws(XmlPullParserException::class, IOException::class)
    private fun readFeed(parser: XmlPullParser): List<RssItem> {
        val items = mutableListOf<RssItem>()

        parser.require(XmlPullParser.START_TAG, ns, "rss")
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) {
                continue
            }

            if (parser.name == "channel") {
                items.addAll(readChannel(parser))
            } else {
                skip(parser)
            }
        }

        return items
    }

    @Throws(XmlPullParserException::class, IOException::class)
    private fun readChannel(parser: XmlPullParser): List<RssItem> {
        val items = mutableListOf<RssItem>()

        parser.require(XmlPullParser.START_TAG, ns, "channel")
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) {
                continue
            }

            if (parser.name == "item") {
                items.add(readItem(parser))
            } else {
                skip(parser)
            }
        }

        return items
    }

    @Throws(XmlPullParserException::class, IOException::class)
    private fun readItem(parser: XmlPullParser): RssItem {
        parser.require(XmlPullParser.START_TAG, ns, "item")
        var title = ""
        var link = ""
        var pubDate = ""
        // Dublin Core date, used by some feeds instead of pubDate
        var dcDate = ""
        var description = ""
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) {
                continue
            }

            when (parser.name) {
                "title" -> title = readText(parser, "title")
                "link" -> link = readText(parser, "link")
                "pubDate" -> pubDate = readText(parser, "pubDate")
                "dc:date" -> dcDate = readText(parser, "dc:date")
                "description" -> description = readText(parser, "description")
                else -> skip(parser)
            }
        }

        return RssItem(title, link, pubDate.ifBlank { dcDate }, description)
    }

    @Throws(IOException::class, XmlPullParserException::class)
    private fun readText(parser: XmlPullParser, tag: String): String {
        parser.require(XmlPullParser.START_TAG, ns, tag)
        var result = ""
        if (parser.next() == XmlPullParser.TEXT) {
            result = parser.text
            parser.nextTag()
        }
        parser.require(XmlPullParser.END_TAG, ns, tag)
        return result
    }

    @Throws(XmlPullParserException::class, IOException::class)
    private fun skip(parser: XmlPullParser) {
        if (parser.eventType != XmlPullParser.START_TAG) {
            throw IllegalStateException()
        }

        var depth = 1
        while (depth != 0) {
            when (parser.next()) {
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.START_TAG -> depth++
            }
        }
    }
}