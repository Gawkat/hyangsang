package dev.kettu.hyangsang.parser

import android.util.Xml
import dev.kettu.hyangsang.data.local.entity.RssItem
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException

private val ns: String? = null

class RssFeedParser {
    @Throws(XmlPullParserException::class, IOException::class)
    fun parse(xml: String): List<RssItem> {
        val parser: XmlPullParser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(xml.reader())
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
        var description = ""
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) {
                continue
            }

            when (parser.name) {
                "title" -> title = readText(parser, "title")
                "link" -> link = readText(parser, "link")
                "pubDate" -> pubDate = readText(parser, "pubDate")
                "description" -> description = readText(parser, "description")
                else -> skip(parser)
            }
        }

        return RssItem(title, link, pubDate, description)
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