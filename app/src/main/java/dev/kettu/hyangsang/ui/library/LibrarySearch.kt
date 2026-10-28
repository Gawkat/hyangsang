package dev.kettu.hyangsang.ui.library

import dev.kettu.hyangsang.data.local.entity.ArticleSummaryWithFeed
import dev.kettu.hyangsang.data.local.entity.BookWithChapter
import dev.kettu.hyangsang.ui.components.siteOf
import dev.kettu.hyangsang.ui.utils.SearchTerms

// An article matches on what its row shows: its title, description and where it's from. The text
// isn't searched, since a match in it wouldn't show in the row
internal fun SearchTerms.matches(item: ArticleSummaryWithFeed): Boolean = matches(
    item.article.title,
    item.article.description,
    item.feed?.title,
    item.article.sourceUrl?.let(::siteOf)
)

internal fun SearchTerms.matches(item: BookWithChapter): Boolean =
    matches(item.book.title, item.book.author)
