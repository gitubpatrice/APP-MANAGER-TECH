package com.filestech.appmanager.ui.screens.storage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.filestech.appmanager.core.ext.asFlow
import com.filestech.appmanager.core.ext.oneShotEvents
import com.filestech.appmanager.core.result.Outcome
import com.filestech.appmanager.domain.model.StorageReport
import com.filestech.appmanager.domain.usecase.AnalyzeStorageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Storage analyser screen.
 *
 * Phase VII C2 fix: no longer injects `AppInfoRepository` directly. Rescan is
 * triggered via the `forceRescan = true` parameter on [AnalyzeStorageUseCase],
 * keeping the strict "ViewModel → UseCase only" layering.
 *
 * Loads a [StorageReport] on demand (the underlying SUM queries are O(N) over
 * the cache, fast enough — no auto-refresh on focus to avoid surprising the
 * user). [analyzeAllApps] is what the screen calls on first render.
 * [rescanAndAnalyze] is what the screen calls on pull-to-refresh.
 */
@HiltViewModel
class StorageViewModel @Inject constructor(
    private val analyzeStorage: AnalyzeStorageUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _events = oneShotEvents<Event>()
    val events: Flow<Event> = _events.asFlow()

    init {
        analyzeAllApps()
    }

    fun analyzeAllApps() {
        _state.update { it.copy(reportOutcome = Outcome.Loading) }
        viewModelScope.launch {
            val outcome = analyzeStorage(
                includeSystemApps = _state.value.includeSystemApps,
                topN              = TOP_N,
            )
            _state.update { it.copy(reportOutcome = outcome) }
            (outcome as? Outcome.Failure)?.let {
                _events.trySend(Event.ShowError(it.error.toString()))
            }
        }
    }

    fun toggleIncludeSystemApps(include: Boolean) {
        _state.update { it.copy(includeSystemApps = include) }
        analyzeAllApps()
    }

    /**
     * Forces a fresh PackageManager scan via the UseCase's `forceRescan` flag,
     * then re-reads the aggregate report.
     */
    fun rescanAndAnalyze() {
        _state.update { it.copy(reportOutcome = Outcome.Loading, isRescanning = true) }
        viewModelScope.launch {
            val outcome = analyzeStorage(
                includeSystemApps = _state.value.includeSystemApps,
                topN              = TOP_N,
                forceRescan       = true,
            )
            _state.update { it.copy(reportOutcome = outcome, isRescanning = false) }
            (outcome as? Outcome.Failure)?.let {
                _events.trySend(Event.ShowError(it.error.toString()))
            }
        }
    }

    // -----------------------------------------------------------------------
    // UiState + Event
    // -----------------------------------------------------------------------

    data class UiState(
        val reportOutcome: Outcome<StorageReport> = Outcome.Loading,
        val includeSystemApps: Boolean = false,
        val isRescanning: Boolean = false,
    )

    sealed interface Event {
        data class ShowError(val message: String) : Event
    }

    companion object {
        private const val TOP_N = 10
    }
}
