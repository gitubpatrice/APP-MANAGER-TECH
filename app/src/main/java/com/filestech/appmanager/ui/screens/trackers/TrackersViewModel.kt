package com.filestech.appmanager.ui.screens.trackers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.filestech.appmanager.core.ext.asFlow
import com.filestech.appmanager.core.ext.oneShotEvents
import com.filestech.appmanager.core.result.Outcome
import com.filestech.appmanager.domain.usecase.ScanAllTrackersUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

/**
 * ViewModel for the Trackers screen.
 *
 * Triggers a full-catalogue scan on init. Subsequent re-scans go through
 * [rescan]; the user can toggle `includeSystemApps`.
 *
 * v0.2.1 audits fixes:
 * - C7a / [withTimeout] wraps the scan so a hung PackageManager probe on a
 *   misbehaving OEM cannot freeze the Refresh button forever.
 * - C1a / [AtomicBoolean] re-entrancy guard separated from the UI
 *   `isScanning` flag — protects against concurrent `rescan()` /
 *   `toggleIncludeSystem()` calls firing two competing scans, AND guarantees
 *   `isScanning` is always reset in `finally` so an exception cannot leave
 *   the spinner stuck.
 */
@HiltViewModel
class TrackersViewModel @Inject constructor(
    private val scan: ScanAllTrackersUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _events = oneShotEvents<Event>()
    val events: Flow<Event> = _events.asFlow()

    private val isScanning = AtomicBoolean(false)

    init {
        runScan()
    }

    fun rescan() = runScan()

    fun toggleIncludeSystem(include: Boolean) {
        _state.update { it.copy(includeSystemApps = include) }
        runScan()
    }

    private fun runScan() {
        if (!isScanning.compareAndSet(false, true)) return
        _state.update { it.copy(isScanning = true) }
        viewModelScope.launch {
            try {
                withTimeout(SCAN_TIMEOUT_MS) {
                    when (val outcome = scan(_state.value.includeSystemApps)) {
                        is Outcome.Success -> {
                            _state.update {
                                it.copy(
                                    isScanning = false,
                                    result     = outcome.value,
                                )
                            }
                            // v0.2.1 audit C3a fix — snackbar feedback so the
                            // refresh button's effect is visible on small
                            // catalogues where the spinner is too brief.
                            _events.trySend(
                                Event.ScanDone(
                                    appsScanned   = outcome.value.totalAppCount,
                                    trackersFound = outcome.value.totalDetections,
                                ),
                            )
                        }
                        is Outcome.Failure -> {
                            _state.update { it.copy(isScanning = false) }
                            _events.trySend(Event.ShowError(outcome.error.toString()))
                        }
                        Outcome.Loading -> Unit
                    }
                }
            } catch (e: TimeoutCancellationException) {
                Timber.w(e, "Trackers scan timed out after %d ms", SCAN_TIMEOUT_MS)
                _state.update { it.copy(isScanning = false) }
                _events.trySend(Event.ShowError("Analyse trop longue — réessayez"))
            } finally {
                isScanning.set(false)
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
        /** v0.2.1 audit C3a — scan completion snackbar. */
        data class ScanDone(val appsScanned: Int, val trackersFound: Int) : Event
    }

    companion object {
        /** 60-second cap on the scan — 150+ apps × PM probes can be slow on OEM. */
        private const val SCAN_TIMEOUT_MS: Long = 60_000L
    }
}
