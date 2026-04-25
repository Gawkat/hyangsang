package dev.kettu.hyangsang.data.defaults

import dev.kettu.hyangsang.data.local.entity.RssFeed

object DefaultData {
    val defaultFeeds: List<RssFeed> = listOf(
        RssFeed(
            title = "Yonhap News",
            url = "https://en.yna.co.kr/RSS/news.xml",
            category = "News"
        ),
        RssFeed(
            title = "Yonhap News - National",
            url = "https://en.yna.co.kr/RSS/national.xml",
            category = "National"
        ),
        RssFeed(
            title = "Yonhap News - North Korea",
            url = "https://en.yna.co.kr/RSS/nk.xml",
            category = "North Korea"
        ),
        RssFeed(
            title = "Yonhap News - Economy",
            url = "https://en.yna.co.kr/RSS/economy-finance.xml",
            category = "Economy"
        ),
        RssFeed(
            title = "Yonhap News - Business",
            url = "https://en.yna.co.kr/RSS/biz.xml",
            category = "Business"
        ),
        RssFeed(
            title = "Yonhap News - Culture",
            url = "https://en.yna.co.kr/RSS/culture.xml",
            category = "Culture"
        ),
        RssFeed(
            title = "Yonhap News - Sports",
            url = "https://en.yna.co.kr/RSS/sports.xml",
            category = "Sports"
        ),
        RssFeed(
            title = "BBC News 코리아",
            url = "https://feeds.bbci.co.uk/korean/rss.xml",
            category = "News"
        )
    )
}