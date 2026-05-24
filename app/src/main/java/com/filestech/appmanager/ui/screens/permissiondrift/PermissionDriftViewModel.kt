package com.filestech.appmanager.ui.screens.permissiondrift

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Intent
import com.filestech.appmanager.core.ext.STATEFLOW_STOP_TIMEOUT_MS
import com.filestech.appmanager.core.ext.asFlow
import com.filestech.appmanager.core.ext.oneShotEvents
import com.filestech.appmanager.core.result.Outcome
import com.filestech.appmanager.data.local.datastore.SettingsRepository
import com.filestech.appmanager.data.system.IntentFactory
import com.filestech.appmanager.domain.model.PermissionDrift
import com.filestech.appmanager.domain.model.SnapshotStats
import com.filestech.appmanager.domain.repository.PermissionSnapshotRepository
import com.filestech.appmanager.domain.usecase.CapturePermissionSnapshotsUseCase
import com.filestech.appmanager.domain.usecase.ObservePermissionDriftsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import javax.inject.Inject

/**
 * ViewModel for the Permission Drift Tracker screen.
 *
 * - Exposes a hot list of [PermissionDrift] driven by the user-selected
 *   window (30 / 90 / all days).
 * - "Capture now" button fires the same use case the periodic worker uses, so
 *   the user can force a snapshot without waiting 24h.
 * - Re-entrancy guard prevents double-tap of "Capturer".
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PermissionDriftViewModel @Inject constructor(
    private val observeDrifts: ObservePermissionDriftsUseCase,
    private val capture: CapturePermissionSnapshotsUseCase,
    private val settings: SettingsRepository,
    private val snapshotRepository: PermissionSnapshotRepository,
    private val intents: IntentFactory,
) : ViewModel() {

    private val _window = MutableStateFlow(Window.DAYS_30)
    val window: StateFlow<Window> = _window.asStateFlow()

    private val _isCapturing = MutableStateFlow(false)
    val isCapturing: StateFlow<Boolean> = _isCapturing.asStateFlow()

    /** v0.2.1 audit C1c — atomic re-entrancy guard separated from the
     *  UI StateFlow. Previous `if (_isCapturing.value) return` was racy. */
    private val capturingGuard = AtomicBoolean(false)

    private val _events = oneShotEvents<Event>()
    val events: Flow<Event> = _events.asFlow()

    val drifts: StateFlow<List<PermissionDrift>> = _window
        .flatMapLatest { observeDrifts(windowDays = it.days) }
        .stateIn(
            scope        = viewModelScope,
            started      = SharingStarted.WhileSubscribed(STATEFLOW_STOP_TIMEOUT_MS),
            initialValue = emptyList(),
        )

    /**
     * Live capture-history stats — surfaced by the screen's "Surveillance
     * active" header to communicate that the feature is actually capturing
     * data, even when the drift feed is empty (1st-capture state, or no
     * permission change since last capture).
     */
    val stats: StateFlow<SnapshotStats> = snapshotRepository.observeStats()
        .stateIn(
            scope        = viewModelScope,
            started      = SharingStarted.WhileSubscribed(STATEFLOW_STOP_TIMEOUT_MS),
            initialValue = SnapshotStats.EMPTY,
        )

    fun setWindow(window: Window) {
        _window.update { window }
    }

    /**
     * v0.2.0 — Tap on a drift row deep-links straight to Android Settings →
     * App → Permissions for [packageName]. One-tap path to re-grant a
     * revoked permission, no AppDetail detour ("qui me renvoie au bon
     * endroit sans chercher 2 heures" — v0.2.0 day-one feedback).
     */
    fun openPermissionsForApp(packageName: String) {
        _events.trySend(Event.LaunchIntentChain(intents.appPermissionsSettingsChain(packageName)))
    }

    fun captureNow() {
        if (!capturingGuard.compareAndSet(false, true)) return
        _isCapturing.update { true }
        viewModelScope.launch {
            try {
                val privacy = settings.flow.first().privacyMonitor
                // v0.2.1 audit C7d fix — capture iterates over every user app
                // + per-app DangerousPermissionInspector probe + Room writes.
                // Wrap in withTimeout so a stalled PM IPC on a misbehaving
                // OEM cannot freeze the Capture button indefinitely.
                val r = try {
                    withTimeout(CAPTURE_TIMEOUT_MS) {
                        capture(includeSystemApps = privacy.permissionDriftIncludeSystemApps)
                    }
                } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                    timber.log.Timber.w(e, "PermissionDrift capture timed out after %d ms", CAPTURE_TIMEOUT_MS)
                    _events.trySend(Event.ShowError("Capture trop longue — réessayez"))
                    return@launch
                }
                when (r) {
                    is Outcome.Success -> _events.trySend(
                        Event.CaptureDone(
                            baselines = r.value.baselines,
                            drifts    = r.value.drifts,
                        ),
                    )
                    is Outcome.Failure -> _events.trySend(Event.ShowError(r.error.toString()))
                    Outcome.Loading    -> Unit
                }
            } finally {
                _isCapturing.update { false }
                capturingGuard.set(false)
            }
        }
    }

    private companion object {
        /** 30s cap on capture sweep (typical < 2s for ~50 user apps). */
        const val CAPTURE_TIMEOUT_MS: Long = 30_000L
    }

    /** UX time-window options exposed by the picker. */
    enum class Window(val days: Long) {
        DAYS_30(30L),
        DAYS_90(90L),
        ALL(Int.MAX_VALUE.toLong()),
    }

    sealed interface Event {
        /**
         * Fired after a manual capture completes.
         * @property baselines number of first-ever snapshot rows (not drifts).
         * @property drifts number of actual change events detected.
         */
        data class CaptureDone(
            val baselines: Int,
            val drifts: Int,
        ) : Event
        /**
         * v0.2.0 — Open Android Settings deep-link from a drift row tap.
         * Carries an ORDERED chain — UI tries each intent in order, falling
         * through on [android.content.ActivityNotFoundException]. See
         * [com.filestech.appmanager.data.system.IntentFactory.appPermissionsSettingsChain]
         * for the rationale (Android 11+ resolveActivity false negatives).
         */
        data class LaunchIntentChain(val intents: List<Intent>) : Event
        data class ShowError(val message: String) : Event
    }
}
