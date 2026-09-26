# 향상 Project Plan

## General

* **Background Sync**: Implement `WorkManager` to prune old articles (e.g., delete bodies of unread
  articles older than 7 days) to save space, maybe pull from feeds in background? (Feeds contain the latest articles, so not pulling for a few days will result in missing articles)
* Use Dagger for Dependency Injection?
* Research if full-text search would speed up dictionary lookups
* Add initial startup configuration screens to allow user to select preferred feeds
* Let user add own content

## Parser

* Research other extraction techniques for generic sources
* Add parsers for other sources

## Reader

* Track reading progress by saving scroll position or last visible paragraph index to Room

## Lookup overlay

* Revisit the order of stem chips
* Improve dictionary entry ranking
  * Investigate embedding models for sense rankings
  * The feed category signal matches the feed's stored category label against the current
    language's labels, so it stops working after changing the app language (or for custom
    categories). Store a category key on `RssFeed` instead?
* Consider if clicks on new words when overlay is opened should trigger a new lookup instead of closing the overlay

## Feeds

* Support Atom feeds?

## Vocabulary/Statistics (maybe)

Vocabulary screen?
* Recap looked up words
* (For lookups) Learn from choices. If the user swaps to entry B for a word, prefer B for that word next time

* **Word Tracking**: Mark unique stems as "encountered" in the database
* **Metrics**:
  * Reading Heatmap
  * Vocabulary Level estimation
  * Total Immersion Time

## Bugfixes

* Tapping a word selects everything between spaces, so punctuation without a space joins words
  (e.g. "포프모빌…콘서트장" is looked up as one word). Split on "…" and similar punctuation too.
