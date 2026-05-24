package com.filestech.appmanager.ui.screens.smartcleaner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.filestech.appmanager.core.ext.asFlow
import com.filestech.appmanager.core.ext.oneShotEvents
import com.filestech.appmanager.core.result.Outcome
import com.filestech.appmanager.core.result.getOrNull
import com.filestech.appmanager.data.system.CriticalAppDetector
import com.filestech.appmanager.data.system.IntentFactory
import com.filestech.appmanager.domain.model.CriticalClassification
import com.filestech.appmanager.domain.model.SmartCleanerReport
import com.filestech.appmanager.domain.repository.AppInfoRepository
import com.filestech.appmanager.domain.usecase.ClearAppCacheUseCase
import com.filestech.appmanager.domain.usecase.GetSmartSuggestionsUseCase
import com.filestech.appmanager.domain.usecase.IgnoreAppUseCase
import com.filestech.appmanager.domain.usecase.UninstallAppUseCase
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
) : ViewModel() {

    private val _state = MutableStateFlow(
        UiState(usageStatsGranted = appInfoRepo.hasUsageStatsAccess()),
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _events = oneShotEvents<Event>()
    val events: Flow<Event> = _events.asFlow()

    /** v0.2.1 audit C1b — re-entrancy guard separated from UiState.isAnalyzing.
     *  The previous `if (_state.value.isAnalyzing) return` read the StateFlow
     *  without compare-and-set, allowing 2 concurrent analyses to both pass
     *  the guard if they interleaved before the first `_state.update`. */
    private val isAnalyzing = AtomicBoolean(false)

    init {
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
        val wasGranted = _state.value.usageStatsGranted
        val nowGranted = appInfoRepo.hasUsageStatsAccess()
        if (wasGranted != nowGranted) {
            _state.update { it.copy(usageStatsGranted = nowGranted) }
            if (nowGranted) refresh()
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
        uninstallApp(packageName).getOrNull()?.let { intent ->
            _events.trySend(Event.LaunchIntent(intent))
        }
    }

    /** Per-row action menu: open App-info so the user can clear the cache themselves. */
    fun clearCache(packageName: String) {
        viewModelScope.launch {
            clearAppCache(packageName).getOrNull()?.let { intent ->
                _events.trySend(Event.LaunchIntent(intent))
            }
        }
    }

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
