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
    ENTERTAINMENT(R.string.category_entertainment),
    SPORTS(R.string.category_sports),
    OPINION(R.string.category_opinion),
    PEOPLE(R.string.category_people);

    companion object {
        // Languages the built-in category names are translated into
        private val LOCALES = listOf(Locale.ENGLISH, Locale.KOREA)

        /**
         * Built-in categories keyed by their name in every app language. Feeds store their
         * category as the name shown when they were added, which may be in another language
         * than the current one.
         */
        fun byLabel(context: Context): Map<String, DefaultCategory> {
            val localizedContexts = LOCALES.map { locale ->
                context.createConfigurationContext(
                    Configuration(context.resources.configuration).apply { setLocale(locale) }
                )
            }
            return entries.flatMap { category ->
                localizedContexts.map { it.getString(category.label) to category }
            }.toMap()
        }
    }
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
        )
    )

    // Titles and categories are stored as plain text, since the user can edit them,
    // so they're resolved in the app language at the time the feeds are added
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

    private fun yonhap(section: String, category: DefaultCategory) = DefaultFeed(
        source = R.string.source_yonhap_news,
        url = "https://www.yna.co.kr/rss/$section.xml",
        category = category
    )
}
