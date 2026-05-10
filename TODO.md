# 향상 Project Plan

## General
* **Background Sync**: Implement `WorkManager` to prune old articles (e.g., delete bodies of unread articles older than 7 days) to save space
* Update default theme to Solarized, add different themes
* Add OLED theme
* Include licensing for dictionary (https://krdict.korean.go.kr/eng/kboardPolicy/copyRightTermsInfo)
* Allow users to add own content
* Use Dagger for Dependency Injection
* Programmatically update version number and build ID from commit hash
* Research if full-text search would speed up dictionary lookups
* Add initial startup configuration screens to allow user to select preferred feeds
* Update dates to be stored and displayed in a consistent manner

## Parser
* Add tests
* Add specific parsers for known feeds

## Reader
* **Progress Tracking**: Save scroll position or last visible paragraph index to Room
* Let user choose reader font type
* Improve dictionary lookups for compound words
* Update dictionary lookups for homonyms, for example 무상
* Investigate performance issues

## Discover/Start
* Add pull to refresh
* **Search/Sort**: Filter by difficulty, length, or date
* Add visual to indicate if article has been read

## Feeds
* Allow user to restore built-in feeds if removed
* Allow user to enable and disable feeds

## Saved
* Allow users to save/unsave articles

## Statistics
* **Word Tracking**: Mark unique stems as "encountered" in the database
* **Metrics**:
  * Reading Heatmap
  * Vocabulary Level estimation
  * Total Immersion Time

## Bugfixes
* Ensure articles are unique
* Ensure status bar legibility with all themes