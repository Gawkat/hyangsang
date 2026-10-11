package dev.kettu.hyangsang.data.defaults

import androidx.annotation.StringRes
import dev.kettu.hyangsang.R

/**
 * A topic to pick in the first-run setup, which picks the built-in feeds in its [categories]
 * from every source. Every [DefaultCategory] belongs to one topic, except the ones in
 * [EASIER_READING_CATEGORIES] and [GENERAL_CATEGORIES]. Narrow categories that few would pick on
 * their own, such as markets or appointments and obituaries, are part of a broader topic.
 */
enum class FeedTopic(@StringRes val label: Int, val categories: Set<DefaultCategory>) {
    POLITICS(R.string.topic_politics, setOf(DefaultCategory.POLITICS)),
    NORTH_KOREA(R.string.topic_north_korea, setOf(DefaultCategory.NORTH_KOREA)),
    ECONOMY(R.string.topic_economy, setOf(DefaultCategory.ECONOMY, DefaultCategory.MARKET, DefaultCategory.INDUSTRY)),
    SOCIETY(R.string.topic_society, setOf(DefaultCategory.SOCIETY, DefaultCategory.LOCAL, DefaultCategory.PEOPLE)),
    WORLD(R.string.topic_world, setOf(DefaultCategory.INTERNATIONAL)),
    CULTURE(R.string.topic_culture, setOf(DefaultCategory.CULTURE)),
    ENTERTAINMENT(R.string.topic_entertainment, setOf(DefaultCategory.ENTERTAINMENT)),
    SCIENCE(R.string.topic_science, setOf(DefaultCategory.SCIENCE)),
    HEALTH(R.string.topic_health, setOf(DefaultCategory.HEALTH)),
    SPORTS(R.string.topic_sports, setOf(DefaultCategory.SPORTS)),
    OPINION(R.string.topic_opinion, setOf(DefaultCategory.OPINION)),
    EXPLAINERS(R.string.topic_explainers, setOf(DefaultCategory.EXPLAINERS)),
    LIFE(R.string.topic_life, setOf(DefaultCategory.LIFE));

    companion object {
        /** Feeds in plain language, picked by their own option rather than a topic. */
        val EASIER_READING_CATEGORIES = setOf(DefaultCategory.KIDS, DefaultCategory.LAW)

        /** Feeds that cover every topic, picked along with any topic. */
        val GENERAL_CATEGORIES = setOf(DefaultCategory.NEWS)

        /** The built-in feeds for [topics], and the plain-language feeds if [easierReading]. */
        fun feedsFor(topics: Set<FeedTopic>, easierReading: Boolean): List<DefaultFeed> {
            val categories = buildSet {
                topics.forEach { addAll(it.categories) }
                if (topics.isNotEmpty()) addAll(GENERAL_CATEGORIES)
                if (easierReading) addAll(EASIER_READING_CATEGORIES)
            }
            return DefaultData.defaultFeeds.filter { it.category in categories }
        }
    }
}
