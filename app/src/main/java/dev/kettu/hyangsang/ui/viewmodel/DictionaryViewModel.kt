package dev.kettu.hyangsang.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.kettu.hyangsang.data.local.dao.DictionaryWithSenses
import dev.kettu.hyangsang.data.repository.DictionaryRepository
import kotlinx.coroutines.Job
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

    // True from a new lookup until its first result, so the sheet can tell loading from no results
    private val _isLookingUp = MutableStateFlow(false)
    val isLookingUp: StateFlow<Boolean> = _isLookingUp.asStateFlow()

    private var lookupJob: Job? = null

    fun lookupWord(word: String) {
        // Each lookup collects a Room Flow that never completes on its own. Without cancelling
        // the previous one, every tapped word left a collector running for the rest of the
        // session, and a slow earlier lookup could overwrite the result of a newer one.
        lookupJob?.cancel()
        _lookupResult.value = emptyMap()
        _isLookingUp.value = true
        lookupJob = viewModelScope.launch {
            dictionaryRepository.getDefinitionsForWord(word).collect { result ->
                _lookupResult.value = result
                _isLookingUp.value = false
            }
        }
    }

    fun clearLookup() {
        lookupJob?.cancel()
        lookupJob = null
        _lookupResult.value = emptyMap()
        _isLookingUp.value = false
    }
}
