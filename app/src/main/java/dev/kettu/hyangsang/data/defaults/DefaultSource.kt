package dev.kettu.hyangsang.data.defaults

import androidx.annotation.StringRes
import dev.kettu.hyangsang.R

/**
 * A site the built-in feeds come from. [about] describes it in the same few facts for every
 * source: what it is, when it was founded, who owns or funds it, and what its feeds contain.
 * The facts and their citations are in docs/feed-sources.md, which should change with them.
 */
enum class DefaultSource(@StringRes val label: Int, @StringRes val about: Int, val website: String) {
    YONHAP(R.string.source_yonhap_news, R.string.about_yonhap_news, "https://www.yna.co.kr"),
    BBC_KOREAN(R.string.source_bbc_korean, R.string.about_bbc_korean, "https://www.bbc.com/korean"),
    SBS(R.string.source_sbs_news, R.string.about_sbs_news, "https://news.sbs.co.kr"),
    HANKYOREH(R.string.source_hankyoreh, R.string.about_hankyoreh, "https://www.hani.co.kr"),
    DONGA(R.string.source_donga, R.string.about_donga, "https://www.donga.com"),
    KIDS_DONGA(R.string.source_kids_donga, R.string.about_kids_donga, "https://kids.donga.com"),
    KIDS_HANKOOK(R.string.source_kids_hankook, R.string.about_kids_hankook, "https://www.kidshankook.kr"),
    ECONOI(R.string.source_econoi, R.string.about_econoi, "https://www.econoi.com"),
    EASYLAW(R.string.source_easylaw, R.string.about_easylaw, "https://www.easylaw.go.kr"),
    SLOWNEWS(R.string.source_slownews, R.string.about_slownews, "https://slownews.kr");

    val feeds: List<DefaultFeed> get() = DefaultData.defaultFeeds.filter { it.source == this }

    companion object {
        private val byHost by lazy {
            DefaultData.defaultFeeds
                .mapNotNull { feed -> FeedSources.hostOf(feed.url)?.let { it to feed.source } }
                .toMap()
        }

        /** The built-in source of a feed on the same host as a built-in feed, as in [FeedSources]. */
        fun forUrl(url: String): DefaultSource? = FeedSources.hostOf(url)?.let { byHost[it] }
    }
}
