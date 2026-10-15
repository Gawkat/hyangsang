package dev.kettu.hyangsang.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.kettu.hyangsang.appContainer
import dev.kettu.hyangsang.data.local.dao.DictionaryWithSenses
import dev.kettu.hyangsang.data.repository.DictionaryRepository
import dev.kettu.hyangsang.data.repository.LookupContext
import dev.kettu.hyangsang.data.repository.LookupReport
import dev.kettu.hyangsang.data.repository.LookupReportRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DictionaryViewModel(
    private val dictionaryRepository: DictionaryRepository,
    private val lookupReportRepository: LookupReportRepository
) : ViewModel() {

    private val _lookupResult =
        MutableStateFlow<Map<String, List<DictionaryWithSenses>>>(emptyMap())
    val lookupResult: StateFlow<Map<String, List<DictionaryWithSenses>>> =
        _lookupResult.asStateFlow()

    // True from a new lookup until its first result, so the sheet can tell loading from no results
    private val _isLookingUp = MutableStateFlow(false)
    val isLookingUp: StateFlow<Boolean> = _isLookingUp.asStateFlow()

    private var lookupJob: Job? = null

    // The latest lookup, for reporting it
    private var lookedUpWord = ""
    private var lookupContext = LookupContext()

    fun lookupWord(word: String, context: LookupContext = LookupContext()) {
        lookedUpWord = word
        lookupContext = context
        // Each lookup collects a Room Flow that never completes on its own. Without cancelling
        // the previous one, every tapped word left a collector running for the rest of the
        // session, and a slow earlier lookup could overwrite the result of a newer one.
        lookupJob?.cancel()
        _lookupResult.value = emptyMap()
        _isLookingUp.value = true
        lookupJob = viewModelScope.launch {
            dictionaryRepository.getDefinitionsForWord(word, context).collect { result ->
                _lookupResult.value = result
                _isLookingUp.value = false
            }
        }
    }

    /**
     * Saves the latest lookup as wrong. [shown] is the entry the sheet opened on and [picked] the
     * one on screen when reporting, which counts as the expected entry if the user switched to it.
     */
    fun reportLookup(
        shown: DictionaryWithSenses?,
        picked: DictionaryWithSenses?,
        note: String,
        articleUrl: String
    ) {
        val word = lookedUpWord
        val context = lookupContext
        val stems = _lookupResult.value.keys.filter { it != word }
        viewModelScope.launch {
            val tokens = withContext(Dispatchers.Default) { dictionaryRepository.describeTokens(word) }
            val expected = picked?.takeIf { it.entry.id != shown?.entry?.id }
            lookupReportRepository.add(
                LookupReport(
                    category = context.feedCategory,
                    tapped = word,
                    expected = expected?.id(),
                    gloss = expected?.senses?.firstOrNull()?.sense?.translationEn,
                    sentence = context.sentence,
                    shown = shown?.id(),
                    stems = stems,
                    tokens = tokens,
                    note = note,
                    articleUrl = articleUrl
                )
            )
        }
    }

    private fun DictionaryWithSenses.id() = "${entry.word}#${entry.homonymNumber}"

    fun clearLookup() {
        lookupJob?.cancel()
        lookupJob = null
        _lookupResult.value = emptyMap()
        _isLookingUp.value = false
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                DictionaryViewModel(appContainer.dictionaryRepository, appContainer.lookupReportRepository)
            }
        }
    }
}
