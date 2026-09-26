# 향상 Project Plan

## General

* **Background Sync**: Implement `WorkManager` to prune old articles (e.g., delete bodies of unread
  articles older than 7 days) to save space, maybe pull from feeds in background? (Feeds contain the latest articles, so not pulling for a few days will result in missing articles)
* Use Dagger for Dependency Injection?
* Add initial startup configuration screens to allow user to select preferred feeds
* Let user add own content

## Article Parser

* Research other extraction techniques for generic sources
* Add parsers for other sources

## Reader

* Track reading progress by saving scroll position or last visible paragraph index to Room

## Lookup overlay

* Revisit the order of stem chips?
* Improve dictionary entry ranking
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

