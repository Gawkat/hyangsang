package dev.kettu.hyangsang.data.defaults

import dev.kettu.hyangsang.data.local.entity.RssFeed

object DefaultData {
    // TODO: really should be using resources strings here for localization and whatnot
    val defaultFeeds: List<RssFeed> = listOf(
        RssFeed(
            title = "Yonhap News",
            url = "https://www.yna.co.kr/rss/news.xml",
            category = "News"
        ),
        RssFeed(
            title = "Yonhap News - Politics",
            url = "https://www.yna.co.kr/rss/politics.xml",
            category = "Politics"
        ),
        RssFeed(
            title = "Yonhap News - North Korea",
            url = "https://www.yna.co.kr/rss/northkorea.xml",
            category = "North Korea"
        ),
        RssFeed(
            title = "Yonhap News - Economy",
            url = "https://www.yna.co.kr/rss/economy.xml",
            category = "Economy"
        ),
        RssFeed(
            title = "Yonhap News - Culture",
            url = "https://www.yna.co.kr/rss/culture.xml",
            category = "Culture"
        ),
        RssFeed(
            title = "Yonhap News - Entertainment",
            url = "https://www.yna.co.kr/rss/entertainment.xml",
            category = "Entertainment"
        ),
        RssFeed(
            title = "Yonhap News - Sports",
            url = "https://www.yna.co.kr/rss/sports.xml",
            category = "Sports"
        ),
        RssFeed(
            title = "BBC News 코리아",
            url = "https://feeds.bbci.co.uk/korean/rss.xml",
            category = "News"
        )
    )
}