package dev.kettu.hyangsang.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.kettu.hyangsang.appContainer
import dev.kettu.hyangsang.data.local.entity.Book
import dev.kettu.hyangsang.data.local.entity.BookChapter
import dev.kettu.hyangsang.data.local.entity.ChapterInfo
import dev.kettu.hyangsang.data.repository.BookRepository
import dev.kettu.hyangsang.data.repository.charCount
import dev.kettu.hyangsang.parser.epub.EpubParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

/** A chapter open in the reader, and the block to show first. */
data class OpenChapter(val chapter: BookChapter, val startBlock: Int)

class BookReaderViewModel(
    private val bookId: Long,
    // A chapter to open at its start, as from the contents in the Library, instead of the position
    private val startChapter: Int?,
    private val bookRepository: BookRepository
) : ViewModel() {
    val book: StateFlow<Book?> = bookRepository.getBook(bookId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val chapters: StateFlow<List<ChapterInfo>> = bookRepository.getChapterInfos(bookId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _openChapter = MutableStateFlow<OpenChapter?>(null)
    val openChapter: StateFlow<OpenChapter?> = _openChapter.asStateFlow()

    // While a book from an older parser is parsed again, before it opens
    private val _updating = MutableStateFlow(false)
    val updating: StateFlow<Boolean> = _updating.asStateFlow()

    // Once the end of the book shows, it counts as read, wherever the list rests after that
    private var reachedEnd = false

    init {
        viewModelScope.launch {
            var current = bookRepository.getBook(bookId).first() ?: return@launch
            if (current.parserVersion < EpubParser.VERSION) {
                _updating.value = true
                bookRepository.reparse(current)
                current = bookRepository.getBook(bookId).first() ?: return@launch
                _updating.value = false
            }
            if (startChapter != null) {
                open(startChapter, 0)
            } else {
                open(current.chapterIndex, current.blockIndex)
            }
        }
    }

    fun imageDir(book: Book): File = bookRepository.imageDir(book.folder)

    /** Opens a chapter at its start, from the contents or the end of another chapter. */
    fun openChapter(index: Int) {
        viewModelScope.launch { open(index, 0) }
    }

    private suspend fun open(index: Int, block: Int) {
        val chapter = bookRepository.getChapter(bookId, index)
            ?: bookRepository.getChapter(bookId, 0)
            ?: return
        val startBlock = if (chapter.chapterIndex == index) block.coerceIn(0, maxOf(0, chapter.content.lastIndex)) else 0
        reachedEnd = false
        _openChapter.value = OpenChapter(chapter, startBlock)
        savePosition(chapter, startBlock)
    }

    /** Stores [blockIndex] of the open chapter as the reading position. */
    fun onBlockVisible(blockIndex: Int) {
        val chapter = _openChapter.value?.chapter ?: return
        viewModelScope.launch { savePosition(chapter, blockIndex) }
    }

    /** Marks the book as read to the end, once the end of its last chapter shows. */
    fun onEndOfBook() {
        val chapter = _openChapter.value?.chapter ?: return
        reachedEnd = true
        viewModelScope.launch { savePosition(chapter, chapter.content.size) }
    }

    private suspend fun savePosition(chapter: BookChapter, blockIndex: Int) {
        val totalChars = book.value?.totalChars ?: bookRepository.getBook(bookId).first()?.totalChars ?: 0
        val progress = if (reachedEnd) {
            totalChars
        } else {
            chapter.charsBefore + chapter.content.take(blockIndex).sumOf { it.charCount() }
        }
        bookRepository.savePosition(bookId, chapter.chapterIndex, blockIndex, progress)
    }

    companion object {
        fun factory(bookId: Long, startChapter: Int?): ViewModelProvider.Factory = viewModelFactory {
            initializer { BookReaderViewModel(bookId, startChapter, appContainer.bookRepository) }
        }
    }
}
