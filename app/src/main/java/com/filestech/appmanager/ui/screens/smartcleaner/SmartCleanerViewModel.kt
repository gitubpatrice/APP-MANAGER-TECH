package com.filestech.appmanager.ui.screens.smartcleaner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.filestech.appmanager.core.ext.asFlow
import com.filestech.appmanager.core.ext.oneShotEvents
import com.filestech.appmanager.core.result.Outcome
import com.filestech.appmanager.domain.model.SmartCleanerReport
import com.filestech.appmanager.domain.usecase.GetSmartSuggestionsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Smart Cleaner ViewModel — runs the suggestion aggregation on init and
 * exposes a [SmartCleanerReport] state.
 */
@HiltViewModel
class SmartCleanerViewModel @Inject constructor(
    private val getSuggestions: GetSmartSuggestionsUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _events = oneShotEvents<Event>()
    val events: Flow<Event> = _events.asFlow()

    init {
        analyze()
    }

    fun analyze() {
        _state.update { it.copy(isAnalyzing = true) }
        viewModelScope.launch {
            when (val outcome = getSuggestions()) {
                is Outcome.Success -> _state.update {
                    it.copy(isAnalyzing = false, report = outcome.value)
                }
                is Outcome.Failure -> {
                    _state.update { it.copy(isAnalyzing = false) }
                    _events.trySend(Event.ShowError(outcome.error.toString()))
                }
                Outcome.Loading -> Unit
            }
        }
    }

    data class UiState(
        val isAnalyzing: Boolean = false,
        val report: SmartCleanerReport = SmartCleanerReport.EMPTY,
    )

    sealed interface Event {
        data class ShowError(val message: String) : Event
    }
}
