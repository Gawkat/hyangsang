# 향상 Project Plan

## General

* **Background Sync**: Implement `WorkManager` to prune old articles (e.g., delete bodies of unread
  articles older than 7 days) to save space
* Update default theme to Solarized, add different themes
* Add OLED theme
* Include licensing for dictionary (https://krdict.korean.go.kr/eng/kboardPolicy/copyRightTermsInfo)
* Allow users to add own content
* Use Dagger for Dependency Injection
* Programmatically build ID from commit hash and version number from resource(?)
* Research if full-text search would speed up dictionary lookups
* Add initial startup configuration screens to allow user to select preferred feeds

## Parser

* Update parsers to handle subheadings
* Improve parsers

## Reader

* Add basic formatting for subheadings
* Track reading progress by saving scroll position or last visible paragraph index to Room
* Let user choose reader font type
* Improve dictionary lookups for compound words
* Update dictionary lookups for homonyms, for example 무상
* Investigate performance issues
* Support images?

## Discover/Start

* Add pull to refresh
* Filter by feed, category, date?
* Add search
* Add visual to indicate if article has been read

## Feeds

* Allow user to restore built-in feeds if removed
* Allow user to enable and disable feeds
* Show if last pull from feed was successful
* Yonhap News seems to contain all Yonhap feeds, probably handle this in some way during feed setup

## Saved

* Allow users to save/unsave articles

## Statistics

* **Word Tracking**: Mark unique stems as "encountered" in the database
* **Metrics**:
    * Reading Heatmap
    * Vocabulary Level estimation
    * Total Immersion Time

## Bugfixes

* Ensure status bar legibility with all themes