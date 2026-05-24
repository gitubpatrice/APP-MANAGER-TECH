package com.filestech.appmanager.ui.screens.expert

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.filestech.appmanager.core.ext.asFlow
import com.filestech.appmanager.core.ext.oneShotEvents
import com.filestech.appmanager.core.result.Outcome
import com.filestech.appmanager.di.IoDispatcher
import com.filestech.appmanager.domain.model.ExpertReport
import com.filestech.appmanager.domain.usecase.GetExpertReportUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import javax.inject.Inject

/**
 * Drives the Expert Mode screen. One job: load the [ExpertReport] for a given
 * package on entry, expose [UiState], emit one-shot errors to the snackbar.
 *
 * Mirrors the AppDetail load pattern — `Outcome.Loading` initial state, IO
 * dispatcher on the suspend call, `withTimeout` cap so a slow PackageManager
 * probe (large app on OEM device) cannot leave the screen spinning forever.
 */
@HiltViewModel
class ExpertViewModel @Inject constructor(
    private val getExpertReport: GetExpertReportUseCase,
    /**
     * v0.4.0 audit C3 fix — was `Dispatchers.IO` hardcoded ; injected
     * as `@IoDispatcher` for testability + cross-codebase consistency.
     */
    @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _events = oneShotEvents<Event>()
    val events: Flow<Event> = _events.asFlow()

    /** Idempotent: re-calling with the same package reloads (used by retry button). */
    fun load(packageName: String) {
        _state.update {
            it.copy(packageName = packageName, report = Outcome.Loading)
        }
        viewModelScope.launch {
            try {
                val outcome = withTimeout(LOAD_TIMEOUT_MS) {
                    withContext(io) { getExpertReport(packageName) }
                }
                _state.update { it.copy(report = outcome) }
                if (outcome is Outcome.Failure) {
                    _events.trySend(Event.ShowError(outcome.error.toString()))
                }
            } catch (e: TimeoutCancellationException) {
                Timber.w(e, "ExpertReport load timed out after %d ms", LOAD_TIMEOUT_MS)
                _state.update {
                    it.copy(
                        report = Outcome.Failure(
                            com.filestech.appmanager.core.result.AppError.Unknown(e),
                        ),
                    )
                }
            }
        }
    }

    data class UiState(
        val packageName: String = "",
        val report: Outcome<ExpertReport> = Outcome.Loading,
    )

    sealed interface Event {
        data class ShowError(val message: String) : Event
    }

    private companion object {
        /** 10s cap — typical < 1s, but PackageManager can stall on OEM systems. */
        const val LOAD_TIMEOUT_MS: Long = 10_000L
    }
}
