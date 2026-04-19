package dev.kettu.hyangsang.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.kettu.hyangsang.data.local.entity.WordEntry
import dev.kettu.hyangsang.data.repository.DictionaryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class DictionaryViewModel(
    private val dictionaryRepository: DictionaryRepository
) : ViewModel() {

    private val _lookupResult = MutableStateFlow<WordEntry?>(null)
    val lookupResult: StateFlow<WordEntry?> = _lookupResult

    fun lookupWord(word: String) {
        viewModelScope.launch {
            // In the future, this will include morphological analysis
            // For now, it's a direct exact match lookup
            _lookupResult.value = dictionaryRepository.getEntryByWord(word)
        }
    }

    fun clearLookup() {
        _lookupResult.value = null
    }
}
