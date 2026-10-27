package dev.kettu.hyangsang.tools.canary

import dev.kettu.hyangsang.parser.ArticleParser
import dev.kettu.hyangsang.parser.userAgent
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.io.File
import kotlin.system.exitProcess

/*
 * Parses the newest articles of the feeds in feeds.tsv, as the app would, and checks the results
 * (see Checks.kt) to catch sites that change their pages in a way that breaks a parser. Nothing
 * it downloads is kept, except with --save.
 *
 * Exits with 1 when an article has a problem or a feed can't be read. An article that can't be
 * downloaded is only reported, unless none of its feed's articles can.
 *
 * Args, with paths relative to the repository root:
 *   --articles <n>    articles to check from each feed, 3 by default
 *   --report <file>   also write the report, in Markdown, to this file
 *   --save <folder>   save the pages of articles with problems, to add to the parser tests
 */

private val FEEDS = File("tools/parser-canary/feeds.tsv")
private const val TIMEOUT_MILLIS = 20_000

// The app's, but naming the canary in place of the app's version
private val USER_AGENT = userAgent("canary")

// Between requests to the same site
private const val DELAY_MILLIS = 1_000L
private const val RETRY_DELAY_MILLIS = 10_000L

private class Feed(val source: String, val url: String, val parser: String, val checksLead: Boolean)

private class Result(val url: String, val title: String?, val problems: List<String>)

private class FeedResult(
    val feed: Feed,
    val results: List<Result> = emptyList(),
    val unreachable: List<String> = emptyList(),
    val error: String? = null
) {
    val failed: Boolean
        get() = error != null || results.any { it.problems.isNotEmpty() } ||
            (results.isEmpty() && unreachable.isNotEmpty())
}

fun main(args: Array<String>) {
    val options = args.toList().chunked(2).associate { it[0] to it.getOrNull(1) }
    val articles = options["--articles"]?.toInt() ?: 3
    val report = options["--report"]?.let(::File)
    val save = options["--save"]?.let(::File)

    val results = readFeeds().map { feed ->
        println("${feed.source}: ${feed.url}")
        check(feed, articles, save)
    }

    val markdown = report(results)
    println()
    println(markdown)
    report?.let {
        it.parentFile?.mkdirs()
        it.writeText(markdown)
    }
    if (results.any { it.failed }) exitProcess(1)
}

private fun readFeeds(): List<Feed> = FEEDS.readLines()
    .filter { it.isNotBlank() && !it.startsWith("#") }
    .map { line ->
        val (source, url, parser, lead) = line.split('\t')
        Feed(source, url, parser, checksLead = lead == "yes")
    }

private fun check(feed: Feed, articles: Int, save: File?): FeedResult {
    // The previous feed's last article may be from the same site
    Thread.sleep(DELAY_MILLIS)
    val items = try {
        retried { readItems(feed.url) }
    } catch (e: Exception) {
        return FeedResult(feed, error = "the feed can't be read: ${e.describe()}")
    }
    if (items.isEmpty()) return FeedResult(feed, error = "the feed has no articles")

    val results = mutableListOf<Result>()
    val unreachable = mutableListOf<String>()
    for (item in items.take(articles)) {
        Thread.sleep(DELAY_MILLIS)
        val document = try {
            retried { Jsoup.connect(item.url).userAgent(USER_AGENT).timeout(TIMEOUT_MILLIS).get() }
        } catch (e: Exception) {
            unreachable.add("${item.url}: ${e.describe()}")
            continue
        }
        val html = document.outerHtml()
        val result = checkArticle(feed, item, document)
        results.add(result)
        if (save != null && result.problems.isNotEmpty()) {
            save.mkdirs()
            File(save, "${feed.source.lowercase()}_${results.size}.html").writeText(html)
        }
    }
    return FeedResult(feed, results, unreachable)
}

private fun checkArticle(feed: Feed, item: Item, document: Document): Result {
    val parser = ArticleParser()
    val title = item.title ?: ArticleParser.pageTitle(document)
    // Read before parsing, which removes parts of the page
    val pageText = document.body().text()
    val leads = listOfNotNull(
        item.description,
        document.selectFirst("meta[property=og:description]")?.attr("content")
    )

    val problems = mutableListOf<String>()
    val chosen = parser.parserFor(item.url, document)
    val name = chosen::class.simpleName
    if (name != feed.parser) {
        problems.add("read with $name instead of ${feed.parser}")
    } else if (!chosen.recognizes(document)) {
        problems.add("$name doesn't recognize the page's layout")
    }

    val blocks = chosen.extractContents(document, title)
    val leadFound = if (feed.checksLead) leadFound(blocks, leads) else null
    problems.addAll(contentProblems(blocks, title, leadFound, pageText))
    if (leadFound == false) {
        problems.add("no sentence of the description the feed or page gives is in the text")
    }
    return Result(item.url, title, problems)
}

private class Item(val url: String, val title: String?, val description: String?)

// The newest items of an RSS or Atom feed, with the links the app would download
private fun readItems(feedUrl: String): List<Item> {
    val xml = Jsoup.connect(feedUrl)
        .userAgent(USER_AGENT)
        .timeout(TIMEOUT_MILLIS)
        .ignoreContentType(true)
        .parser(Parser.xmlParser())
        .get()
    return xml.select("item, entry").mapNotNull { item ->
        val linkElement: Element? = item.selectFirst("link")
        val link = linkElement?.let { it.text().ifBlank { it.attr("href") } }?.trim()
        val url = link?.takeIf { it.isNotEmpty() }?.let(ArticleParser::articleUrl) ?: return@mapNotNull null
        Item(
            url = url,
            title = item.selectFirst("title")?.text()?.let { Parser.unescapeEntities(it, false).trim() }
                ?.takeIf { it.isNotEmpty() },
            description = item.selectFirst("description, summary")?.text()?.let { Jsoup.parse(it).text() }
                ?.takeIf { it.isNotBlank() }
        )
    }
}

private fun report(results: List<FeedResult>): String = buildString {
    val failed = results.filter { it.failed }
    // For the workflow, which only comments on its issue when the failing feeds change
    appendLine("<!-- failing: ${failed.joinToString(" ") { it.feed.url }} -->")
    appendLine("## Parser canary")
    appendLine()
    appendLine(
        if (failed.isEmpty()) "All ${results.size} feeds passed."
        else "${failed.size} of ${results.size} feeds have problems."
    )
    appendLine()
    appendLine("| Source | Feed | Articles | With problems | Not downloaded |")
    appendLine("|---|---|---|---|---|")
    for (result in results) {
        val status = result.error ?: result.results.count { it.problems.isNotEmpty() }.toString()
        appendLine(
            "| ${result.feed.source} | ${result.feed.url} | ${result.results.size} | $status | " +
                "${result.unreachable.size} |"
        )
    }

    for (result in results.filter { it.results.any { r -> r.problems.isNotEmpty() } || it.unreachable.isNotEmpty() }) {
        appendLine()
        appendLine("### ${result.feed.source}")
        for (article in result.results.filter { it.problems.isNotEmpty() }) {
            appendLine()
            appendLine("[${article.title ?: article.url}](${article.url})")
            article.problems.forEach { appendLine("- $it") }
        }
        if (result.unreachable.isNotEmpty()) {
            appendLine()
            appendLine("Not downloaded:")
            result.unreachable.forEach { appendLine("- $it") }
        }
    }
}

// Tries a download a second time, after a pause, as sites sometimes drop a request
private fun <T> retried(download: () -> T): T = try {
    download()
} catch (_: Exception) {
    Thread.sleep(RETRY_DELAY_MILLIS)
    download()
}

private fun Exception.describe(): String = "${this::class.simpleName}: ${message.orEmpty().take(200)}"
