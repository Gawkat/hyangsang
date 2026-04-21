package dev.kettu.hyangsang.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.kettu.hyangsang.data.local.dao.DictionaryWithSenses
import dev.kettu.hyangsang.data.local.entity.WordEntry
import dev.kettu.hyangsang.data.repository.DictionaryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DictionaryViewModel(
    private val dictionaryRepository: DictionaryRepository
) : ViewModel() {

    private val _lookupResult = MutableStateFlow<WordEntry?>(null)
    val lookupResult: StateFlow<WordEntry?> = _lookupResult

    private val _offlineResult = MutableStateFlow<List<DictionaryWithSenses>>(emptyList())
    val offlineResult: StateFlow<List<DictionaryWithSenses>> = _offlineResult.asStateFlow()

    fun lookupWord(word: String) {
        viewModelScope.launch {
            // Existing WordEntry lookup (deprecated?)
            _lookupResult.value = dictionaryRepository.getEntryByWord(word)
            
            // New offline dictionary lookup
            dictionaryRepository.getOfflineEntriesByWord(word).collect {
                _offlineResult.value = it
            }
        }
    }

    fun clearLookup() {
        _lookupResult.value = null
        _offlineResult.value = emptyList()
    }
}
