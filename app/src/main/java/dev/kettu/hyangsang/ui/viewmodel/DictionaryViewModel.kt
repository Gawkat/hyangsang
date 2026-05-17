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

    private val _lookupResult =
        MutableStateFlow<Map<String, List<DictionaryWithSenses>>>(emptyMap())
    val lookupResult: StateFlow<Map<String, List<DictionaryWithSenses>>> =
        _lookupResult.asStateFlow()

    fun lookupWord(word: String) {
        viewModelScope.launch {
            dictionaryRepository.getDefinitionsForWord(word).collect { result ->
                _lookupResult.value = result
            }
        }
    }

    fun clearLookup() {
        _lookupResult.value = emptyMap()
    }
}
