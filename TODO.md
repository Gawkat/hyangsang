# 향상 Project Plan

## General

* **Background Sync**: Implement `WorkManager` to prune old articles (e.g., delete bodies of unread
  articles older than 7 days) to save space, maybe pull from feeds in background? (Feeds contain the latest articles, so not pulling for a few days will result in missing articles)
* Use Dagger for Dependency Injection?
* Add initial startup configuration screens to allow user to select preferred feeds
* Let user add own content
* Add license information about Solarized/Gruvbox

## Article Parser

* Research other extraction techniques for generic sources
* Add parsers for other sources

## Reader

* Track reading progress by saving scroll position or last visible paragraph index to Room
* Add setting to toggle between:
  * Mark as read when opened
  * Otherwise, mark read at the end of the article

## Lookup overlay

* Revisit the order of stem chips?
* Improve the Lesk-style sentence overlap (`contextOverlap` in `HomonymRanking.kt`)?
* Consider if clicks on new words when overlay is opened should trigger a new lookup instead of closing the overlay

## Saved

* Add search functionality to saved articles
* Add chip to show unread articles only

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

* Selecting serif font does not affect image captions
* Affixes have character entities in them, maybe from original JSON dictionary source
* Opening the feed drawer will sometimes result in a blank screen - unknown cause
  * Rare, not reproducible on demand; seen on both emulator and device
  * Content area goes blank while the bottom navigation bar stays visible; switching bottom tab recovers
  * Still present after updating to Compose BOM 2026.09.00 (material3 1.4.0)
  * Suspects: left-edge predictive back gesture (targetSdk 36) competing with drawer swipe, or NavHost content not being redrawn
