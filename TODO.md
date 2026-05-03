# 향상 Project Plan
---

## General
* **Background Sync**: Implement `WorkManager` to prune old articles (e.g., delete bodies of unread articles older than 7 days) to save space
*   Update default theme to Solarized, add different themes
*   Include open source notices (https://developers.google.com/android/guides/opensource)
* Move from side drawer to bottom app bar?
*   Allow users to add own content
*   Use Dagger for Dependency Injection
*   Programmatically update version number and build ID from commit hash
*   Research if full-text search would speed up dictionary lookups

## Reader
* **Progress Tracking**: Save scroll position or last visible paragraph index to Room
*   Improve dictionary lookup visuals
*   Let user choose reader font type

## Discover/Main Feed
* **Search/Sort**: Filter by difficulty, length, or date

## Favorites
* Allow users to save/unsave articles

## Statistics
* **Word Tracking**: Mark unique stems as "encountered" in the database
* **Metrics**:
  *   Reading Heatmap
  *   Vocabulary Level estimation
  *   Total Immersion Time

## Bugfixes
*   Ensure articles are unique
*   Ensure status bar legibility with all themes