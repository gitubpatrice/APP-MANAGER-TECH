package com.filestech.appmanager.ui.screens.trackers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.filestech.appmanager.core.ext.asFlow
import com.filestech.appmanager.core.ext.oneShotEvents
import com.filestech.appmanager.core.result.Outcome
import com.filestech.appmanager.domain.usecase.ScanAllTrackersUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Trackers screen.
 *
 * Triggers a full-catalogue scan on init. Subsequent re-scans go through
 * [rescan]; the user can toggle `includeSystemApps`.
 */
@HiltViewModel
class TrackersViewModel @Inject constructor(
    private val scan: ScanAllTrackersUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _events = oneShotEvents<Event>()
    val events: Flow<Event> = _events.asFlow()

    init {
        runScan()
    }

    fun rescan() = runScan()

    fun toggleIncludeSystem(include: Boolean) {
        _state.update { it.copy(includeSystemApps = include) }
        runScan()
    }

    private fun runScan() {
        _state.update { it.copy(isScanning = true) }
        viewModelScope.launch {
            when (val outcome = scan(_state.value.includeSystemApps)) {
                is Outcome.Success -> _state.update {
                    it.copy(
                        isScanning = false,
                        result     = outcome.value,
                    )
                }
                is Outcome.Failure -> {
                    _state.update { it.copy(isScanning = false) }
                    _events.trySend(Event.ShowError(outcome.error.toString()))
                }
                Outcome.Loading -> Unit
            }
        }
    }

    data class UiState(
        val isScanning: Boolean = false,
        val includeSystemApps: Boolean = false,
        val result: ScanAllTrackersUseCase.Result? = null,
    )

    sealed interface Event {
        data class ShowError(val message: String) : Event
    }
}
