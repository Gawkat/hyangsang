package dev.kettu.hyangsang.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.kettu.hyangsang.data.local.dao.DictionaryWithSenses
import dev.kettu.hyangsang.data.repository.DictionaryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DictionaryViewModel(
    private val dictionaryRepository: DictionaryRepository
) : ViewModel() {

    private val _wordDefinitions = MutableStateFlow<List<DictionaryWithSenses>>(emptyList())
    val wordDefinitions: StateFlow<List<DictionaryWithSenses>> = _wordDefinitions.asStateFlow()

    private val _stemmedWord = MutableStateFlow<String?>(null)
    val stemmedWord: StateFlow<String?> = _stemmedWord.asStateFlow()

    fun lookupWord(word: String) {
        val stem = dictionaryRepository.getStem(word)
        _stemmedWord.value = stem

        viewModelScope.launch {
            // New offline dictionary lookup
            dictionaryRepository.getOfflineEntriesByWord(word).collect {
                _wordDefinitions.value = it
            }
        }
    }

    fun clearLookup() {
        _wordDefinitions.value = emptyList()
        _stemmedWord.value = null
    }
}
