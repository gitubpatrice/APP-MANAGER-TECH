package com.filestech.appmanager.ui.screens.storage

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.filestech.appmanager.core.ext.asFlow
import com.filestech.appmanager.core.ext.oneShotEvents
import com.filestech.appmanager.core.result.Outcome
import com.filestech.appmanager.data.system.IntentFactory
import com.filestech.appmanager.domain.model.StorageReport
import com.filestech.appmanager.domain.repository.AppInfoRepository
import com.filestech.appmanager.domain.usecase.AnalyzeStorageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
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
 * ViewModel for the Storage analyser screen.
 *
 * Phase VII C2 fix: no longer injects `AppInfoRepository` directly. Rescan is
 * triggered via the `forceRescan = true` parameter on [AnalyzeStorageUseCase],
 * keeping the strict "ViewModel → UseCase only" layering.
 *
 * v0.2.1 fix: re-introduced `AppInfoRepository` injection PURELY for the
 * `hasUsageStatsAccess()` probe — without that permission,
 * `StorageStatsManager.queryStatsForUid` returns 0 for cache/data sizes and
 * the user sees stale (empty) numbers no matter how many times they refresh.
 * User report: "si j'actualise, ça met pas à jour la taille des fichiers ou
 * le cache utilisé". The banner now guides them to Settings → Usage access.
 *
 * Loads a [StorageReport] on demand (the underlying SUM queries are O(N) over
 * the cache, fast enough — no auto-refresh on focus to avoid surprising the
 * user). [analyzeAllApps] is what the screen calls on first render.
 * [rescanAndAnalyze] is what the screen calls on pull-to-refresh.
 */
@HiltViewModel
class StorageViewModel @Inject constructor(
    private val analyzeStorage: AnalyzeStorageUseCase,
    private val appInfoRepo: AppInfoRepository,
    private val intents: IntentFactory,
) : ViewModel() {

    // v0.2.1 audit M-1 fix — initial UiState no longer calls
    // hasUsageStatsAccess() on the main thread (AppOps IPC). The probe
    // runs on Dispatchers.IO in init {} and emits via _state.update.
    // Default `true` avoids flashing the banner during the first ~ms.
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _events = oneShotEvents<Event>()
    val events: Flow<Event> = _events.asFlow()

    init {
        viewModelScope.launch {
            val granted = withContext(Dispatchers.IO) { appInfoRepo.hasUsageStatsAccess() }
            _state.update { it.copy(usageStatsGranted = granted) }
        }
        analyzeAllApps()
    }

    fun analyzeAllApps() {
        _state.update { it.copy(reportOutcome = Outcome.Loading) }
        viewModelScope.launch {
            // v0.2.1 audit C7c fix — wrap in withTimeout so a stalled
            // StorageStatsManager.queryStatsForUid (slow OEM, large catalogue)
            // cannot leave the screen in Loading forever.
            val outcome = try {
                withTimeout(ANALYZE_TIMEOUT_MS) {
                    analyzeStorage(
                        includeSystemApps = _state.value.includeSystemApps,
                        topN              = TOP_N,
                    )
                }
            } catch (e: TimeoutCancellationException) {
                Timber.w(e, "analyzeAllApps timed out after %d ms", ANALYZE_TIMEOUT_MS)
                _events.trySend(Event.ShowError("Analyse trop longue — réessayez"))
                Outcome.Failure(com.filestech.appmanager.core.result.AppError.Unknown(e))
            }
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
            // v0.2.1 audit C7c fix — same withTimeout protection as
            // analyzeAllApps(), plus rescan does a full PackageManager crawl
            // which is even more vulnerable to OEM stalls.
            val outcome = try {
                withTimeout(RESCAN_TIMEOUT_MS) {
                    analyzeStorage(
                        includeSystemApps = _state.value.includeSystemApps,
                        topN              = TOP_N,
                        forceRescan       = true,
                    )
                }
            } catch (e: TimeoutCancellationException) {
                Timber.w(e, "rescanAndAnalyze timed out after %d ms", RESCAN_TIMEOUT_MS)
                _events.trySend(Event.ShowError("Rescan trop long — réessayez"))
                Outcome.Failure(com.filestech.appmanager.core.result.AppError.Unknown(e))
            }
            _state.update { it.copy(reportOutcome = outcome, isRescanning = false) }
            (outcome as? Outcome.Failure)?.let {
                _events.trySend(Event.ShowError(it.error.toString()))
            }
        }
    }

    /**
     * Re-probes PACKAGE_USAGE_STATS on ON_RESUME and triggers a fresh rescan
     * + report when the permission just flipped from denied → granted. Same
     * pattern as [com.filestech.appmanager.ui.screens.smartcleaner.SmartCleanerViewModel.onResumed].
     */
    fun onResumed() {
        // v0.2.1 audit M-1 fix — re-probe on Dispatchers.IO (AppOps IPC).
        viewModelScope.launch {
            val wasGranted = _state.value.usageStatsGranted
            val nowGranted = withContext(Dispatchers.IO) { appInfoRepo.hasUsageStatsAccess() }
            if (wasGranted != nowGranted) {
                _state.update { it.copy(usageStatsGranted = nowGranted) }
                if (nowGranted) rescanAndAnalyze()
            }
        }
    }

    /** Emit the Settings → Usage access deep-link Intent for the screen to launch. */
    fun requestUsageStatsPermission() {
        _events.trySend(Event.LaunchIntent(intents.usageAccessSettingsIntent()))
    }

    // -----------------------------------------------------------------------
    // UiState + Event
    // -----------------------------------------------------------------------

    data class UiState(
        val reportOutcome: Outcome<StorageReport> = Outcome.Loading,
        val includeSystemApps: Boolean = false,
        val isRescanning: Boolean = false,
        /** Set at construction + refreshed on ON_RESUME. False → banner visible. */
        val usageStatsGranted: Boolean = true,
    )

    sealed interface Event {
        data class ShowError(val message: String) : Event
        /** v0.2.1 — Settings → Usage access deep-link from the banner CTA. */
        data class LaunchIntent(val intent: Intent) : Event
    }

    companion object {
        private const val TOP_N = 10
        /** 30s cap on read-only analyze (cache-backed, should be < 1s normally). */
        private const val ANALYZE_TIMEOUT_MS: Long = 30_000L
        /** 90s cap on rescan (full PackageManager + StorageStats crawl). */
        private const val RESCAN_TIMEOUT_MS: Long = 90_000L
    }
}
