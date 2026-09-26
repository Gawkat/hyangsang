# 향상 Project Plan

## General

* **Background Sync**: Implement `WorkManager` to prune old articles (e.g., delete bodies of unread
  articles older than 7 days) to save space, maybe pull from feeds in background? (Feeds contain the latest articles, so not pulling for a few days will result in missing articles)
* Use Dagger for Dependency Injection?
* Research if full-text search would speed up dictionary lookups
* Add initial startup configuration screens to allow user to select preferred feeds
* Let user add own content
* Remove the legacy article compatibility and all references to the legacy format

## Parser

* Research other extraction techniques for generic sources
* Add parsers for other sources
* Parse Yonhap footer into other content block to distinguish from article body

## Reader

* Track reading progress by saving scroll position or last visible paragraph index to Room

## Lookup overlay

* Improve dictionary entry ranking
  * Investigate embedding models for sense rankings

## Feeds

* Default feeds strings are hardcoded and not localized
* Yonhap News seems to contain all Yonhap feeds, probably handle this in some way during feed setup
* Support Atom feeds?

## Vocabulary/Statistics (maybe)

Vocabulary screen?
* Recap looked up words

* **Word Tracking**: Mark unique stems as "encountered" in the database
* **Metrics**:
  * Reading Heatmap
  * Vocabulary Level estimation
  * Total Immersion Time

## Bugfixes

* The launch splash always follows the system theme, so with the app set to Dark it flashes light on a light-mode phone