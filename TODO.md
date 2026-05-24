# 향상 Project Plan

## General

* **Background Sync**: Implement `WorkManager` to prune old articles (e.g., delete bodies of unread
  articles older than 7 days) to save space
* Update default theme to Solarized (Gruvbox?), add different themes
* Use Dagger for Dependency Injection
* Research if full-text search would speed up dictionary lookups
* Add initial startup configuration screens to allow user to select preferred feeds
* Let user add own content

## Parser

* Update parsers to handle subheadings
* Improve parsers

## Reader

* Add basic formatting for subheadings
* Track reading progress by saving scroll position or last visible paragraph index to Room
* Text and layout settings:
    * text size (update from current implementation)
    * font
    * font weight
    * line spacing
* Investigate performance issues
* Support images?
* Consider hiding examples from dictionary overlay (currently only showing 2, move somewhere else?)
* Dictionary lookup can finish after overlay appears (if lookup is slow). Show a shimmering
  placeholder while this happens instead of current "No definitions found"
* If no definitions found, allow user to look up word themselves (search in browser or something)
* Improve word lookup order, (e.g. nouns before verbs... other things?)

## Discover/Start

* Filter by feed, category, read
* Add search
* Add scroll-to-top thing

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