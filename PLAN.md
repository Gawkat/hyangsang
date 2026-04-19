# 향상 Project Plan: Korean Immersion & Learning App

## 1. Architectural Foundation
*   **Language & UI**: Kotlin with Jetpack Compose (Material 3).
*   **Navigation**: Jetpack Navigation Compose.
*   **Local Storage**:
    *   **Room**: For reading progress, user stats, and article metadata.
    *   **DataStore**: For simple user preferences.
    *   **SQLite FTS5**: For high-performance dictionary lookups.
*   **Concurrency**: Kotlin Coroutines & Flow.

## 2. Reader Mode (Core Feature)
*   **Text Processing**: Split text into tokens for interaction.
*   **Morphological Analysis**: Integrate a library like **Open Korean Text (OKT)** or **Komoran** locally to handle conjugation/stemming.
*   **Interactive UI**: A custom layout that renders tokens as clickable elements.
*   **Progress Tracking**: Save scroll position or last visible paragraph index to Room.

## 3. Dictionary Management
*   **Offline First**: Built-in download manager for dictionary databases.
*   **Lookup Overlay**: Modal Bottom Sheet or Popup for definitions, POS, and examples.

## 4. Source Index & Discovery
*   **Integrated Sources**: RSS feeds/scrapers for news (e.g., Yonhap) or learning blogs.
*   **Search/Sort**: Filter by difficulty, length, or date.

## 5. Statistics & Gamification
*   **Word Tracking**: Mark unique stems as "encountered" in the database.
*   **Metrics**:
    *   Reading Heatmap.
    *   Vocabulary Level estimation.
    *   Total Immersion Time.

## 6. Advanced Features (Future)
*   **Local LLM Integration**: llama.cpp with JNI for contextual grammar/nuance explanations (not a chatbot).

---

## Roadmap
1.  **Phase 1: Reader UI & Navigation** (Completed) - Interactive text prototype, Navigation Drawer, and Settings UI.
2.  **Phase 2: Persistence & Settings** (Completed) - DataStore integration for theme, font size, and language. Dynamic theme/font size observers in UI.
3.  **Phase 3: Local Database (Room)** (Next) - Setup Room for article storage, reading progress (scroll position), and vocabulary tracking.
4.  **Phase 4: Morphological Analysis** - Integration of OKT/Komoran for stemming.
5.  **Phase 5: Dictionary Integration** - FTS search and lookup overlay.
6.  **Phase 6: Sources & Indexing** - Fetching real content.
