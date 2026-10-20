package dev.kettu.hyangsang.data.importing

/** What another app shared with Hyangsang, or what the user pasted. */
sealed interface SharedText {
    // The title is a hint; a link's own page title replaces a missing one when it's downloaded
    data class Link(val url: String, val title: String?) : SharedText
    data class Text(val text: String, val title: String?) : SharedText

    companion object {
        // Apps often share a link with the page title in front of it, such as "Title https://…".
        // Anything longer in front is text that happens to end with a link
        private const val MAX_LINK_PREFIX = 150
        private val TRAILING_URL = Regex("""https?://\S+$""")

        fun classify(text: String, subject: String? = null): SharedText {
            val trimmed = text.trim()
            val title = subject?.trim()?.takeIf { it.isNotEmpty() }
            val url = TRAILING_URL.find(trimmed)
            if (url != null && url.range.first <= MAX_LINK_PREFIX) {
                return Link(url.value, title)
            }
            return Text(trimmed, title)
        }
    }
}
