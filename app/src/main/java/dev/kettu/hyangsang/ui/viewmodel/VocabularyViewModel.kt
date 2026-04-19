package dev.kettu.hyangsang.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.kettu.hyangsang.data.local.entity.VocabularyWord
import dev.kettu.hyangsang.data.repository.VocabularyRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class VocabularyViewModel(private val vocabularyRepository: VocabularyRepository) : ViewModel() {
    val allVocabulary: StateFlow<List<VocabularyWord>> = vocabularyRepository.getAllVocabulary()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun markWordAsKnown(word: String) {
        viewModelScope.launch {
            val existing = vocabularyRepository.getWord(word)
            if (existing != null) {
                vocabularyRepository.updateStatus(word, 2) // 2 = Known
            } else {
                vocabularyRepository.upsertWord(
                    VocabularyWord(word = word, status = 2, timesEncountered = 1)
                )
            }
        }
    }

    fun markWordAsLearning(word: String) {
        viewModelScope.launch {
            val existing = vocabularyRepository.getWord(word)
            if (existing != null) {
                vocabularyRepository.updateStatus(word, 1) // 1 = Learning
            } else {
                vocabularyRepository.upsertWord(
                    VocabularyWord(word = word, status = 1, timesEncountered = 1)
                )
            }
        }
    }

    fun recordEncounter(word: String) {
        viewModelScope.launch {
            val existing = vocabularyRepository.getWord(word)
            if (existing != null) {
                vocabularyRepository.incrementEncounter(word)
            } else {
                vocabularyRepository.upsertWord(
                    VocabularyWord(word = word, status = 0, timesEncountered = 1)
                )
            }
        }
    }
}
