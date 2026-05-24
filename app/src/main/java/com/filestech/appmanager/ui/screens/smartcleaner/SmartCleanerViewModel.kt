package com.filestech.appmanager.ui.screens.smartcleaner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.filestech.appmanager.core.ext.asFlow
import com.filestech.appmanager.core.ext.oneShotEvents
import com.filestech.appmanager.core.result.Outcome
import com.filestech.appmanager.core.result.getOrNull
import com.filestech.appmanager.data.system.AmtActionLogger
import com.filestech.appmanager.data.system.CriticalAppDetector
import com.filestech.appmanager.data.system.IntentFactory
import com.filestech.appmanager.di.IoDispatcher
import com.filestech.appmanager.domain.model.AmtActionResult
import com.filestech.appmanager.domain.model.AmtActionType
import com.filestech.appmanager.domain.model.CriticalClassification
import com.filestech.appmanager.domain.model.SmartCleanerReport
import com.filestech.appmanager.domain.repository.AppInfoRepository
import com.filestech.appmanager.domain.usecase.ClearAppCacheUseCase
import com.filestech.appmanager.domain.usecase.GetSmartSuggestionsUseCase
import com.filestech.appmanager.domain.usecase.IgnoreAppUseCase
import com.filestech.appmanager.domain.usecase.UninstallAppUseCase
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
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

/**
 * Smart Cleaner ViewModel — runs the suggestion aggregation on init and
 * exposes a [SmartCleanerReport] state.
 *
 * v0.1.3 hotfix: added `usageStatsGranted` probe + `withTimeout` on the
 * analyse coroutine + `ON_RESUME` re-probe. User report: "le nettoyeur
 * intelligent ne marche pas, même actualiser ne marche pas". Root cause was
 * one of the upstream Flows (Room or DataStore) failing to emit, leaving
 * `isAnalyzing = true` forever and disabling the Refresh button.
 */
@HiltViewModel
class SmartCleanerViewModel @Inject constructor(
    private val getSuggestions: GetSmartSuggestionsUseCase,
    private val appInfoRepo: AppInfoRepository,
    private val uninstallApp: UninstallAppUseCase,
    private val clearAppCache: ClearAppCacheUseCase,
    private val ignoreApp: IgnoreAppUseCase,
    private val intents: IntentFactory,
    private val criticalDetector: CriticalAppDetector,
    /**
     * v0.4.0 audit D2 fix — destructive actions fired from Smart
     * Cleaner now feed the in-app journal alongside the AppDetail /
     * AppList / Trash paths. Closes the forensic coverage gap.
     */
    private val actionLogger: AmtActionLogger,
    /**
     * v0.4.0 audit L1 fix — IO dispatcher injected so the AppOps IPC
     * probe `hasUsageStatsAccess()` runs off the main thread (parity
     * with the AppDetail / Storage / Expert VMs after audit C3).
     */
    @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {

    /**
     * v0.4.0 audit L1 fix — default `false` so we never call
     * `hasUsageStatsAccess()` (AppOps IPC) on the main thread at VM
     * construction. The real value is published from `init {}` below
     * on the IO dispatcher.
     */
    private val _state = MutableStateFlow(UiState(usageStatsGranted = false))
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _events = oneShotEvents<Event>()
    val events: Flow<Event> = _events.asFlow()

    /** v0.2.1 audit C1b — re-entrancy guard separated from UiState.isAnalyzing.
     *  The previous `if (_state.value.isAnalyzing) return` read the StateFlow
     *  without compare-and-set, allowing 2 concurrent analyses to both pass
     *  the guard if they interleaved before the first `_state.update`. */
    private val isAnalyzing = AtomicBoolean(false)

    init {
        // v0.4.0 audit L1 fix — publish the usage-stats grant off the
        // main thread immediately after construction.
        viewModelScope.launch {
            val granted = withContext(io) { appInfoRepo.hasUsageStatsAccess() }
            _state.update { it.copy(usageStatsGranted = granted) }
        }
        // First analysis on every screen entry — reads the Room cache only,
        // so it's fast (~50 ms). MainApplication.triggerInitialScanIfNeeded
        // already keeps the cache warm at app start.
        analyze(rescanFirst = false)
    }

    /** Public Refresh action — re-scans PackageManager + re-analyses. */
    fun refresh() = analyze(rescanFirst = true)

    /**
     * @param rescanFirst when true, re-scans PackageManager (≈ 1 s) before
     *   computing suggestions — used by the explicit Refresh button and the
     *   "include system apps" toggle. False at init so opening the screen is
     *   fast.
     */
    fun analyze(rescanFirst: Boolean = false) {
        // v0.2.1 audit C1b — atomic re-entrancy guard. Was StateFlow read
        // without CAS → 2 rapidly-triggered analyses could both pass.
        if (!isAnalyzing.compareAndSet(false, true)) return
        _state.update { it.copy(isAnalyzing = true) }

        viewModelScope.launch {
            try {
                val includeSys = _state.value.includeSystemApps
                val outcome = withTimeout(ANALYZE_TIMEOUT_MS) {
                    if (rescanFirst) appInfoRepo.rescan()
                    getSuggestions(includeSystemApps = includeSys)
                }
                when (outcome) {
                    is Outcome.Success -> {
                        _state.update {
                            it.copy(isAnalyzing = false, report = outcome.value)
                        }
                        // v0.1.3 user feedback — explicit snackbar so the Refresh
                        // button's effect is visible even when the suggestion
                        // list hasn't changed.
                        _events.trySend(Event.AnalyzeDone(outcome.value.suggestions.size))
                    }
                    is Outcome.Failure -> {
                        _state.update { it.copy(isAnalyzing = false) }
                        _events.trySend(Event.ShowError(outcome.error.toString()))
                    }
                    Outcome.Loading -> _state.update { it.copy(isAnalyzing = false) }
                }
            } catch (e: TimeoutCancellationException) {
                Timber.w(e, "Smart Cleaner analyze timed out after %d ms", ANALYZE_TIMEOUT_MS)
                _state.update { it.copy(isAnalyzing = false) }
                _events.trySend(Event.ShowError("Analyse trop longue — réessayez"))
            } finally {
                isAnalyzing.set(false)
            }
        }
    }

    /**
     * Re-probes PACKAGE_USAGE_STATS on ON_RESUME and triggers a fresh analysis
     * when the permission just flipped from denied → granted.
     */
    fun onResumed() {
        viewModelScope.launch {
            val wasGranted = _state.value.usageStatsGranted
            val nowGranted = withContext(io) { appInfoRepo.hasUsageStatsAccess() }
            if (wasGranted != nowGranted) {
                _state.update { it.copy(usageStatsGranted = nowGranted) }
                if (nowGranted) refresh()
            }
        }
    }

    /** Toggle "include system apps" filter + re-analyze immediately so the
     *  suggestion list reflects the new filter without a manual refresh. */
    fun toggleSystemApps() {
        _state.update { it.copy(includeSystemApps = !it.includeSystemApps) }
        // No need to rescan PackageManager — just re-filter the existing cache.
        analyze(rescanFirst = false)
    }

    fun requestUsageStatsPermission() {
        _events.trySend(Event.LaunchIntent(intents.usageAccessSettingsIntent()))
    }

    /**
     * Per-row action menu: launch system uninstall for [packageName].
     *
     * v0.3.1 Safety Phase B — same hold-3s friction as the AppDetail uninstall
     * path: if the suggested package is classified critical, emit a
     * [Event.RequiresCriticalConfirmation] instead of the intent. The screen
     * dialog re-invokes with [bypassCriticalCheck] = true on hold-3s confirm.
     */
    fun uninstall(packageName: String, bypassCriticalCheck: Boolean = false) {
        if (!bypassCriticalCheck) {
            criticalDetector.classify(packageName)?.let { classification ->
                _events.trySend(
                    Event.RequiresCriticalConfirmation(
                        packageName    = packageName,
                        classification = classification,
                    ),
                )
                return
            }
        }
        val intent = uninstallApp(packageName).getOrNull()
        // v0.4.0 audit D2 fix — record into the AMT action journal so
        // Smart-Cleaner-initiated uninstalls are tracked alongside the
        // AppDetail / AppList / Trash paths.
        val result = if (intent != null) AmtActionResult.INTENT_REQUESTED else AmtActionResult.FAILED
        actionLogger.log(packageName, labelOf(packageName), AmtActionType.UNINSTALL, result)
        if (intent != null) {
            _events.trySend(Event.LaunchIntent(intent))
        }
    }

    /** Per-row action menu: open App-info so the user can clear the cache themselves. */
    fun clearCache(packageName: String) {
        viewModelScope.launch {
            val intent = clearAppCache(packageName).getOrNull()
            // v0.4.0 audit D2 fix — journal coverage for Smart Cleaner.
            val result = if (intent != null) AmtActionResult.INTENT_REQUESTED else AmtActionResult.FAILED
            actionLogger.log(packageName, labelOf(packageName), AmtActionType.CLEAR_CACHE, result)
            if (intent != null) {
                _events.trySend(Event.LaunchIntent(intent))
            }
        }
    }

    /**
     * v0.4.0 — resolves the cached label for [pkg] off the latest
     * suggestion report so journal rows carry a human-readable label.
     */
    private fun labelOf(pkg: String): String? =
        _state.value.report.suggestions.firstOrNull { it.appInfo.packageName == pkg }?.appInfo?.label

    /** Per-row action menu: add [packageName] to the ignore list so it won't
     *  be re-suggested. Uses IgnoreAppUseCase so the 500-entry cap + package
     *  name validation are applied (audit M-1 fix). */
    fun ignore(packageName: String) {
        viewModelScope.launch {
            when (val r = ignoreApp(packageName)) {
                is Outcome.Failure -> _events.trySend(Event.ShowError(r.error.toString()))
                else               -> analyze(rescanFirst = false)
            }
        }
    }

    data class UiState(
        val isAnalyzing: Boolean = false,
        val report: SmartCleanerReport = SmartCleanerReport.EMPTY,
        val usageStatsGranted: Boolean = true,
        val includeSystemApps: Boolean = false,
    )

    sealed interface Event {
        data class ShowError(val message: String) : Event
        /** v0.1.3 audit L-3 fix — renamed from `LaunchUsageSettings` since it
         *  now carries any system Intent (usage settings, uninstall, app-info). */
        data class LaunchIntent(val intent: android.content.Intent) : Event
        data class AnalyzeDone(val suggestionsCount: Int) : Event
        /**
         * v0.3.1 — emitted when a suggested uninstall hits a critical package.
         * The screen surfaces [CriticalWarningDialog] (hold-3s); the confirm
         * callback dispatches back to `uninstall(pkg, bypassCriticalCheck = true)`.
         */
        data class RequiresCriticalConfirmation(
            val packageName: String,
            val classification: CriticalClassification,
        ) : Event
    }

    companion object {
        private const val ANALYZE_TIMEOUT_MS: Long = 20_000L
    }
}
