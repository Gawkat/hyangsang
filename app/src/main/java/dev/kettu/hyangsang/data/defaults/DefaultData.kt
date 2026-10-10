package dev.kettu.hyangsang.data.defaults

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.StringRes
import dev.kettu.hyangsang.R
import dev.kettu.hyangsang.data.local.entity.RssFeed
import java.util.Locale

enum class DefaultCategory(@StringRes val label: Int) {
    NEWS(R.string.category_news),
    POLITICS(R.string.category_politics),
    NORTH_KOREA(R.string.category_north_korea),
    ECONOMY(R.string.category_economy),
    MARKET(R.string.category_market),
    INDUSTRY(R.string.category_industry),
    SOCIETY(R.string.category_society),
    LOCAL(R.string.category_local),
    INTERNATIONAL(R.string.category_international),
    CULTURE(R.string.category_culture),
    HEALTH(R.string.category_health),
    SCIENCE(R.string.category_science),
    ENTERTAINMENT(R.string.category_entertainment),
    SPORTS(R.string.category_sports),
    OPINION(R.string.category_opinion),
    PEOPLE(R.string.category_people),
    KIDS(R.string.category_kids);

    companion object {
        /**
         * Built-in categories keyed by their name in every app language. Feeds store their
         * category as the name shown when they were added, which may be in another language
         * than the current one.
         */
        fun byLabel(context: Context): Map<String, DefaultCategory> {
            val localizedContexts = localizedContexts(context)
            return entries.flatMap { category ->
                localizedContexts.map { it.getString(category.label) to category }
            }.toMap()
        }
    }
}

// Languages the built-in names are translated into
private val APP_LOCALES = listOf(Locale.ENGLISH, Locale.KOREA)

private fun localizedContexts(context: Context): List<Context> = APP_LOCALES.map { locale ->
    context.createConfigurationContext(
        Configuration(context.resources.configuration).apply { setLocale(locale) }
    )
}

/**
 * A built-in feed. Section feeds are titled "<source> - <category>", other feeds just "<source>".
 */
data class DefaultFeed(
    @StringRes val source: Int,
    val url: String,
    val category: DefaultCategory,
    val isSection: Boolean = true
)

object DefaultData {
    // Yonhap's main news.xml feed is left out, as its articles all appear in the section feeds
    val defaultFeeds: List<DefaultFeed> = listOf(
        yonhap("politics", DefaultCategory.POLITICS),
        yonhap("northkorea", DefaultCategory.NORTH_KOREA),
        yonhap("economy", DefaultCategory.ECONOMY),
        yonhap("market", DefaultCategory.MARKET),
        yonhap("industry", DefaultCategory.INDUSTRY),
        yonhap("society", DefaultCategory.SOCIETY),
        yonhap("local", DefaultCategory.LOCAL),
        yonhap("international", DefaultCategory.INTERNATIONAL),
        yonhap("culture", DefaultCategory.CULTURE),
        yonhap("health", DefaultCategory.HEALTH),
        yonhap("entertainment", DefaultCategory.ENTERTAINMENT),
        yonhap("sports", DefaultCategory.SPORTS),
        yonhap("opinion", DefaultCategory.OPINION),
        yonhap("people", DefaultCategory.PEOPLE),
        DefaultFeed(
            source = R.string.source_bbc_korean,
            url = "https://feeds.bbci.co.uk/korean/rss.xml",
            category = DefaultCategory.NEWS,
            isSection = false
        ),
        sbs("01", DefaultCategory.POLITICS),
        sbs("02", DefaultCategory.ECONOMY),
        sbs("03", DefaultCategory.SOCIETY),
        sbs("07", DefaultCategory.INTERNATIONAL),
        sbs("08", DefaultCategory.CULTURE),
        sbs("14", DefaultCategory.ENTERTAINMENT),
        sbs("09", DefaultCategory.SPORTS),
        // As with Yonhap, the main /rss/ feed is left out, as it combines the section feeds
        hani("politics", DefaultCategory.POLITICS),
        hani("economy", DefaultCategory.ECONOMY),
        hani("society", DefaultCategory.SOCIETY),
        hani("international", DefaultCategory.INTERNATIONAL),
        hani("culture", DefaultCategory.CULTURE),
        hani("science", DefaultCategory.SCIENCE),
        hani("sports", DefaultCategory.SPORTS),
        hani("opinion", DefaultCategory.OPINION),
        // A children's newspaper, with the simplest language of the built-in feeds
        DefaultFeed(
            source = R.string.source_kids_donga,
            url = "https://kids.donga.com/rss/allArticle.xml",
            category = DefaultCategory.KIDS,
            isSection = false
        )
    )

    // Titles and categories are stored as plain text, since the user can edit them,
    // so they're resolved in the app language at the time the feeds are added, and
    // translated by [localizeFeeds] when the language changes
    fun resolveFeeds(context: Context): List<RssFeed> = defaultFeeds.map { feed ->
        val category = context.getString(feed.category.label)
        val source = context.getString(feed.source)
        RssFeed(
            title = if (feed.isSection) {
                context.getString(R.string.default_feed_title, source, category)
            } else {
                source
            },
            url = feed.url,
            category = category
        )
    }

    /**
     * The [feeds] whose built-in category names or default titles are in another app language
     * than the current one, translated into it. Names the user has changed are left alone.
     */
    fun localizeFeeds(context: Context, feeds: List<RssFeed>): List<RssFeed> {
        val categoriesByLabel = DefaultCategory.byLabel(context)
        val currentTitles = resolveFeeds(context).associate { it.url to it.title }
        val allTitles = localizedContexts(context)
            .flatMap { resolveFeeds(it) }
            .groupBy({ it.url }, { it.title })

        return feeds.mapNotNull { feed ->
            val category = categoriesByLabel[feed.category]?.let { context.getString(it.label) }
                ?: feed.category
            val title = currentTitles[feed.url]
                ?.takeIf { feed.title in allTitles[feed.url].orEmpty() }
                ?: feed.title
            if (category != feed.category || title != feed.title) {
                feed.copy(title = title, category = category)
            } else {
                null
            }
        }
    }

    private fun yonhap(section: String, category: DefaultCategory) = DefaultFeed(
        source = R.string.source_yonhap_news,
        url = "https://www.yna.co.kr/rss/$section.xml",
        category = category
    )

    private fun sbs(sectionId: String, category: DefaultCategory) = DefaultFeed(
        source = R.string.source_sbs_news,
        url = "https://news.sbs.co.kr/news/SectionRssFeed.do?sectionId=$sectionId&plink=RSSREADER",
        category = category
    )

    private fun hani(section: String, category: DefaultCategory) = DefaultFeed(
        source = R.string.source_hankyoreh,
        url = "https://www.hani.co.kr/rss/$section",
        category = category
    )
}
