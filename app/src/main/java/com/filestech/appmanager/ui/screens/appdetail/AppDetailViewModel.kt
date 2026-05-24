package com.filestech.appmanager.ui.screens.appdetail

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.filestech.appmanager.core.ext.STATEFLOW_STOP_TIMEOUT_MS
import com.filestech.appmanager.core.ext.asFlow
import com.filestech.appmanager.core.ext.oneShotEvents
import com.filestech.appmanager.core.result.Outcome
import com.filestech.appmanager.core.result.getOrNull
import com.filestech.appmanager.data.system.AmtActionLogger
import com.filestech.appmanager.data.system.CriticalAppDetector
import com.filestech.appmanager.data.system.IntentFactory
import com.filestech.appmanager.ui.screens.settings.setAppTags
import com.filestech.appmanager.domain.model.AmtActionResult
import com.filestech.appmanager.domain.model.AmtActionType
import com.filestech.appmanager.domain.model.AppDetail
import com.filestech.appmanager.domain.model.AppTag
import com.filestech.appmanager.domain.model.CriticalClassification
import com.filestech.appmanager.domain.model.LifecycleEvent
import com.filestech.appmanager.domain.model.PrivacyScore
import com.filestech.appmanager.domain.model.TrackerReport
import com.filestech.appmanager.domain.repository.AppInfoRepository
import com.filestech.appmanager.domain.repository.AppLifecycleRepository
import com.filestech.appmanager.domain.repository.IgnoreListRepository
import com.filestech.appmanager.domain.usecase.ClearAppCacheUseCase
import com.filestech.appmanager.domain.usecase.DetectTrackersUseCase
import com.filestech.appmanager.domain.usecase.DisableEnableAppUseCase
import com.filestech.appmanager.domain.usecase.ForceStopAppUseCase
import com.filestech.appmanager.domain.usecase.GetAppDetailUseCase
import com.filestech.appmanager.domain.usecase.GetPrivacyScoreUseCase
import com.filestech.appmanager.domain.usecase.MoveAppToTrashUseCase
import com.filestech.appmanager.domain.usecase.QuarantineAppUseCase
import com.filestech.appmanager.domain.usecase.UninstallAppUseCase
import com.filestech.appmanager.domain.model.QuarantineMode
import com.filestech.appmanager.data.local.datastore.SettingsRepository
import com.filestech.appmanager.di.IoDispatcher
import android.net.Uri
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the App Detail screen.
 *
 * Loads, in PARALLEL on `load(packageName)`:
 *  - [AppDetail] (cached info + permissions)
 *  - [PrivacyScore] (Phase IX innovation)
 *  - [TrackerReport] (Phase IX innovation)
 *  - `isIgnored` flag (Phase X — show "Ignore/Unignore" toggle action)
 *
 * One-shot events for every action launched (uninstall, clear cache,
 * disable/enable, force stop, open app settings, ignore/unignore).
 */
@HiltViewModel
class AppDetailViewModel @Inject constructor(
    private val getDetail: GetAppDetailUseCase,
    private val getPrivacyScore: GetPrivacyScoreUseCase,
    private val detectTrackers: DetectTrackersUseCase,
    private val uninstallApp: UninstallAppUseCase,
    private val moveAppToTrash: MoveAppToTrashUseCase,
    private val clearCache: ClearAppCacheUseCase,
    private val forceStopApp: ForceStopAppUseCase,
    private val disableEnable: DisableEnableAppUseCase,
    private val quarantineApp: QuarantineAppUseCase,
    private val ignoreList: IgnoreListRepository,
    private val settings: SettingsRepository,
    private val criticalDetector: CriticalAppDetector,
    private val appInfoRepo: AppInfoRepository,
    private val lifecycleRepository: AppLifecycleRepository,
    private val intents: IntentFactory,
    private val actionLogger: AmtActionLogger,
    /**
     * v0.4.0 audit C3 fix — inject @IoDispatcher for the usage-stats
     * AppOps IPC probe so tests can substitute a test dispatcher
     * (previously `Dispatchers.IO` hardcoded, untestable).
     */
    @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    /**
     * v0.3.1 — Per-app lifecycle events (newest first, capped to 5 entries by
     * the UI). Driven by the current [_state.packageName] via `flatMapLatest`
     * so navigating to a different app reuses the same flow without leaking
     * the prior subscription. Returns `Outcome.Loading` until the package
     * name is non-empty (the screen renders the "no events" empty state in
     * that case, indistinguishable from a freshly-installed app with no
     * tracked events yet).
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val lifecycleEvents: StateFlow<List<LifecycleEvent>> = _state
        .map { it.packageName }
        .flatMapLatest { pkg ->
            if (pkg.isEmpty()) emptyFlow()
            else lifecycleRepository.observeByPackage(pkg)
                .map { outcome -> outcome.getOrNull().orEmpty() }
        }
        .stateIn(
            scope        = viewModelScope,
            started      = SharingStarted.WhileSubscribed(STATEFLOW_STOP_TIMEOUT_MS),
            initialValue = emptyList(),
        )

    /**
     * v0.3.3 / v0.3.4 — Current user-assigned tag set for
     * [_state.packageName], reactive on DataStore. Drives the AppDetail
     * TagPickerDialog's initial selection and the chips rendered on the
     * AppDetail header. An empty set means "no tag".
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val currentTags: StateFlow<Set<AppTag>> = _state
        .map { it.packageName }
        .flatMapLatest { pkg ->
            if (pkg.isEmpty()) emptyFlow()
            else settings.flow.map { snapshot -> snapshot.appTags[pkg].orEmpty() }
        }
        .stateIn(
            scope        = viewModelScope,
            started      = SharingStarted.WhileSubscribed(STATEFLOW_STOP_TIMEOUT_MS),
            initialValue = emptySet(),
        )

    /**
     * Replaces the tag set for the current package. Pass [emptySet] to clear
     * all tags. Delegates to the `SettingsRepository.setAppTags` extension
     * which validates the package name + does the atomic
     * `Map<pkg, Set<AppTag>>` update via DataStore.
     */
    fun setTags(tags: Set<AppTag>) {
        val pkg = currentPackage() ?: return
        viewModelScope.launch {
            settings.setAppTags(pkg, tags)
        }
    }

    private val _events = oneShotEvents<Event>()
    val events: Flow<Event> = _events.asFlow()

    // -----------------------------------------------------------------------
    // Load — parallel fan-out for detail + privacy + trackers + isIgnored
    // -----------------------------------------------------------------------

    fun load(packageName: String) {
        _state.update {
            it.copy(
                packageName       = packageName,
                detailOutcome     = Outcome.Loading,
                privacyScore      = null,
                trackerReport     = null,
                // Keep last known granted-state until the IO probe re-emits below.
                usageStatsGranted = it.usageStatsGranted,
                // v0.2.1 UX — reset on fresh load (new app or re-entry).
                recentlyMovedToTrash = false,
            )
        }
        viewModelScope.launch {
            // v0.2.1 audit M-1 fix — run hasUsageStatsAccess() on Dispatchers.IO,
            // not on the main thread (AppOps IPC). Probe runs in parallel with
            // the awaitAll fan-out below.
            launch {
                val granted = withContext(io) { appInfoRepo.hasUsageStatsAccess() }
                _state.update { it.copy(usageStatsGranted = granted) }
            }
            // v0.2.1 audit M4 fix — wrap the parallel fan-out in withTimeout
            // so any of the 4 awaitAll branches stalling (PM probe slow on
            // OEM, Room IO contention, tracker DB hash) cannot leave the
            // detail screen in Outcome.Loading forever.
            try {
                withTimeout(LOAD_TIMEOUT_MS) {
                    coroutineScope {
                        val detail   = async { getDetail(packageName) }
                        val score    = async { getPrivacyScore(packageName) }
                        val trackers = async { detectTrackers(packageName) }
                        val ignored  = async { ignoreList.isIgnored(packageName) }
                        val (detailR, scoreR, trackersR, ignoredR) = awaitAll(detail, score, trackers, ignored)

                        @Suppress("UNCHECKED_CAST")
                        _state.update {
                            it.copy(
                                detailOutcome = detailR as Outcome<AppDetail>,
                                privacyScore  = (scoreR as Outcome<PrivacyScore>).getOrNull(),
                                trackerReport = (trackersR as Outcome<TrackerReport>).getOrNull(),
                                isIgnored     = ignoredR as Boolean,
                            )
                        }
                    }
                }
            } catch (e: TimeoutCancellationException) {
                Timber.w(e, "AppDetail load timed out after %d ms", LOAD_TIMEOUT_MS)
                _state.update {
                    it.copy(
                        detailOutcome = Outcome.Failure(
                            com.filestech.appmanager.core.result.AppError.Unknown(e),
                        ),
                    )
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // Actions
    // -----------------------------------------------------------------------

    /**
     * Trigger the system uninstall flow.
     *
     * v0.2.0 Safety Guardrails: if [criticalDetector] classifies the current
     * package (authenticator, password manager, banking app, etc.) we DO NOT
     * launch the intent immediately — we emit [Event.RequiresCriticalConfirmation]
     * so the screen surfaces a hold-3s warning dialog. The user can then
     * cancel or re-invoke with [bypassCriticalCheck] = true to proceed.
     */
    fun uninstall(bypassCriticalCheck: Boolean = false) = withPackage { pkg ->
        if (!bypassCriticalCheck) {
            criticalDetector.classify(pkg)?.let { classification ->
                _events.trySend(
                    Event.RequiresCriticalConfirmation(
                        classification = classification,
                        action         = CriticalAction.UNINSTALL,
                    ),
                )
                return@withPackage
            }
        }
        val intent = uninstallApp(pkg).getOrNull()
        if (intent != null) {
            logAction(AmtActionType.UNINSTALL, AmtActionResult.INTENT_REQUESTED)
            _events.trySend(Event.LaunchIntent(intent))
        } else {
            logAction(AmtActionType.UNINSTALL, AmtActionResult.FAILED)
            _events.trySend(Event.ShowError("Cannot uninstall $pkg"))
        }
    }

    /**
     * Phase X — soft delete: stage the app in the Corbeille / Trash instead of
     * launching the system uninstall intent. The app stays installed; the
     * user can later restore or uninstall-for-real from the Trash screen.
     *
     * Snapshots `label` + `totalSizeBytes` from the cached [AppDetail] so the
     * trash row stays consistent even if the app is uninstalled out-of-band.
     *
     * v0.2.1 UX add — on success, flips `recentlyMovedToTrash = true` so the
     * ActionsCard surfaces a "Voir la corbeille" shortcut right below the
     * Uninstall button (user feedback: "ce serait bien que dessous
     * désinstaller apparaisse un bouton voir la corbeille").
     */
    fun moveToTrash() {
        val detail = (_state.value.detailOutcome as? Outcome.Success)?.value ?: return
        viewModelScope.launch {
            when (val r = moveAppToTrash(detail.info)) {
                is Outcome.Success -> {
                    logAction(AmtActionType.MOVE_TO_TRASH, AmtActionResult.SUCCESS)
                    _state.update { it.copy(recentlyMovedToTrash = true) }
                    _events.trySend(Event.MovedToTrash(detail.info.label))
                }
                is Outcome.Failure -> {
                    logAction(AmtActionType.MOVE_TO_TRASH, AmtActionResult.FAILED)
                    _events.trySend(Event.ShowError(r.error.toString()))
                }
                Outcome.Loading    -> Unit
            }
        }
    }

    fun clearCache() = withPackage { pkg ->
        val intent = clearCache(pkg).getOrNull()
        if (intent != null) {
            logAction(AmtActionType.CLEAR_CACHE, AmtActionResult.INTENT_REQUESTED)
            _events.trySend(Event.LaunchIntent(intent))
        } else {
            logAction(AmtActionType.CLEAR_CACHE, AmtActionResult.FAILED)
        }
    }

    fun clearData() = withPackage { pkg ->
        logAction(AmtActionType.CLEAR_DATA, AmtActionResult.INTENT_REQUESTED)
        _events.trySend(Event.LaunchIntent(intents.appDetailsSettingsIntent(pkg)))
    }

    fun forceStop() {
        val pkg = currentPackage() ?: return
        viewModelScope.launch {
            when (val outcome = forceStopApp(pkg)) {
                is Outcome.Success -> {
                    logAction(AmtActionType.FORCE_STOP, AmtActionResult.SUCCESS)
                    _events.trySend(Event.ActionDone("Force stop requested"))
                }
                is Outcome.Failure -> {
                    logAction(AmtActionType.FORCE_STOP, AmtActionResult.FAILED)
                    _events.trySend(Event.ShowError(outcome.error.toString()))
                }
                Outcome.Loading    -> Unit
            }
        }
    }

    fun disable(bypassCriticalCheck: Boolean = false) {
        val pkg = currentPackage() ?: return
        // v0.3.1 Safety Phase B — disabling a 2FA / banking / password manager
        // is functionally equivalent to uninstalling (the user loses access to
        // the app and may be locked out of associated accounts). Same hold-3s
        // friction.
        if (!bypassCriticalCheck) {
            criticalDetector.classify(pkg)?.let { classification ->
                _events.trySend(
                    Event.RequiresCriticalConfirmation(
                        classification = classification,
                        action         = CriticalAction.DISABLE,
                    ),
                )
                return
            }
        }
        setEnabled(false)
    }

    fun enable() = setEnabled(true)

    private fun setEnabled(enabled: Boolean) {
        val pkg = currentPackage() ?: return
        val journalType = if (enabled) AmtActionType.ENABLE else AmtActionType.DISABLE
        viewModelScope.launch {
            when (val outcome = disableEnable(pkg, enabled)) {
                is Outcome.Success -> when (val r = outcome.value) {
                    DisableEnableAppUseCase.Result.Done -> {
                        logAction(journalType, AmtActionResult.SUCCESS)
                        _events.trySend(Event.ActionDone("Application ${if (enabled) "enabled" else "disabled"}"))
                    }
                    is DisableEnableAppUseCase.Result.NeedsUserAction -> {
                        logAction(journalType, AmtActionResult.INTENT_REQUESTED)
                        _events.trySend(Event.LaunchIntent(r.intent))
                    }
                }
                is Outcome.Failure -> {
                    logAction(journalType, AmtActionResult.FAILED)
                    _events.trySend(Event.ShowError(outcome.error.toString()))
                }
                Outcome.Loading    -> Unit
            }
        }
    }

    fun openAppSettings() = withPackage { pkg ->
        _events.trySend(Event.LaunchIntent(intents.appDetailsSettingsIntent(pkg)))
    }

    /**
     * v0.2.0 — Deep-link straight to the OS Settings → App → Permissions
     * sub-page for the current package. Used by the AppDetail Permissions
     * card and (downstream) by the drift-row action dialog so the user can
     * re-grant a revoked permission in one tap.
     */
    fun openAppPermissionsSettings() = withPackage { pkg ->
        _events.trySend(Event.LaunchIntentChain(intents.appPermissionsSettingsChain(pkg)))
    }

    fun openUsageAccessSettings() {
        _events.trySend(Event.LaunchIntent(intents.usageAccessSettingsIntent()))
    }

    /**
     * v0.2.1 — re-probe PACKAGE_USAGE_STATS on ON_RESUME. If the user just
     * granted it in Settings and came back, refresh the detail load so the
     * Installation Info "Last used" field shows the real timestamp instead
     * of "Jamais utilisée".
     */
    fun onResumed() {
        // v0.2.1 audit M-1 fix — IO probe (AppOps IPC).
        viewModelScope.launch {
            val wasGranted = _state.value.usageStatsGranted
            val nowGranted = withContext(io) { appInfoRepo.hasUsageStatsAccess() }
            if (wasGranted != nowGranted) {
                _state.update { it.copy(usageStatsGranted = nowGranted) }
                if (nowGranted) {
                    val pkg = currentPackage()
                    if (pkg != null) load(pkg)
                }
            }
        }
    }

    /**
     * v0.2.0 — Quarantine the current app with the user-chosen mode + duration.
     *
     * HARD mode requires a SAF backup folder; if the user has not picked one
     * yet, we surface a typed event so the UI can prompt them to do so
     * (Settings → Quarantine → APK backup folder).
     *
     * SOFT mode never needs the backup folder.
     *
     * On success, the use case returns an [Intent] (uninstall confirm in HARD
     * mode, app-info deep-link in SOFT mode). We forward it to the UI via
     * [Event.LaunchIntent] — Android shows its own confirm step, never
     * bypassed.
     */
    fun quarantine(mode: QuarantineMode, durationDays: Int, bypassCriticalCheck: Boolean = false) {
        val pkg = currentPackage() ?: return
        val label = (_state.value.detailOutcome as? Outcome.Success)?.value?.info?.label ?: pkg

        // v0.2.0 Safety Guardrails — HARD mode wipes app data (Android non-
        // root limit) so it deserves the same friction as a raw uninstall.
        // SOFT mode is just a reminder, no destruction → no check needed.
        if (!bypassCriticalCheck && mode == QuarantineMode.HARD_UNINSTALL) {
            criticalDetector.classify(pkg)?.let { classification ->
                _events.trySend(
                    Event.RequiresCriticalConfirmation(
                        classification = classification,
                        action         = CriticalAction.QUARANTINE_HARD,
                        durationDays   = durationDays,
                    ),
                )
                return
            }
        }

        viewModelScope.launch {
            val backupTreeUri = if (mode == QuarantineMode.HARD_UNINSTALL) {
                settings.flow.first().quarantine.backupTreeUri?.let { Uri.parse(it) }
            } else null

            if (mode == QuarantineMode.HARD_UNINSTALL && backupTreeUri == null) {
                _events.trySend(Event.NeedsBackupFolder)
                return@launch
            }

            val result = quarantineApp(
                packageName   = pkg,
                mode          = mode,
                durationDays  = durationDays,
                backupTreeUri = backupTreeUri,
            )
            val journalType = if (mode == QuarantineMode.HARD_UNINSTALL)
                AmtActionType.QUARANTINE_HARD
            else
                AmtActionType.QUARANTINE_SOFT
            when (result) {
                is QuarantineAppUseCase.Result.HardReady -> {
                    logAction(journalType, AmtActionResult.INTENT_REQUESTED)
                    _events.trySend(Event.LaunchIntent(result.uninstallIntent))
                }
                is QuarantineAppUseCase.Result.SoftReady -> {
                    // SOFT-mode entry is already persisted in the
                    // quarantine_entry table at this point — that is a real
                    // AMT-side completion. The deep-link to OS App-info
                    // follows for the user to flip the OS toggle, but that
                    // is opaque from AMT's POV.
                    logAction(journalType, AmtActionResult.SUCCESS)
                    // v0.2.0 UX fix — SOFT mode no longer auto-opens Settings.
                    // We surface an explanation dialog first so the user knows
                    // they must tap DESACTIVER (or ARCHIVER) themselves in the
                    // OS App-info page. Without it, users were dropped on the
                    // System screen with no context.
                    _events.trySend(
                        Event.SoftQuarantineCreated(
                            intent       = result.appDetailsIntent,
                            label        = label,
                            durationDays = durationDays,
                        ),
                    )
                }
                is QuarantineAppUseCase.Result.Failure -> {
                    logAction(journalType, AmtActionResult.FAILED)
                    _events.trySend(Event.ShowError(result.message))
                }
            }
        }
    }

    /** Phase X — add or remove the current package from the ignore list. */
    fun toggleIgnore() {
        val pkg = currentPackage() ?: return
        viewModelScope.launch {
            ignoreList.toggle(pkg)
            val newState = ignoreList.isIgnored(pkg)
            _state.update { it.copy(isIgnored = newState) }
            _events.trySend(
                Event.ActionDone(
                    if (newState) "Added to ignore list" else "Removed from ignore list",
                ),
            )
        }
    }

    // -----------------------------------------------------------------------
    // Internals
    // -----------------------------------------------------------------------

    private fun currentPackage(): String? = _state.value.packageName.takeIf { it.isNotEmpty() }

    private fun currentLabel(): String? =
        (_state.value.detailOutcome as? Outcome.Success)?.value?.info?.label

    /**
     * v0.4.0 — shorthand that records one AMT action against the
     * currently loaded package + cached label. Cheap when the journal
     * is disabled (see [AmtActionLogger] doc). Safe to call from any
     * thread.
     */
    private fun logAction(type: AmtActionType, result: AmtActionResult) {
        val pkg = currentPackage() ?: return
        actionLogger.log(pkg, currentLabel(), type, result)
    }

    private inline fun withPackage(block: (String) -> Unit) {
        currentPackage()?.let(block)
    }

    // -----------------------------------------------------------------------
    // UiState + Event
    // -----------------------------------------------------------------------

    data class UiState(
        val packageName: String = "",
        val detailOutcome: Outcome<AppDetail> = Outcome.Loading,
        val privacyScore: PrivacyScore? = null,
        val trackerReport: TrackerReport? = null,
        val isIgnored: Boolean = false,
        /**
         * v0.2.1 — PACKAGE_USAGE_STATS probe set on every load + ON_RESUME.
         * When false, the `lastUsedTime` field on AppInfo is always 0 (which
         * Installation Info renders as "Jamais utilisée") and storage sizes
         * are also broken. The screen shows a banner inviting the user to
         * Settings → Usage access when this is false.
         */
        val usageStatsGranted: Boolean = true,
        /**
         * v0.2.1 UX add — set true after a successful [moveToTrash], reset on
         * the next [load]. Drives a "Voir la corbeille" shortcut button under
         * the Uninstall action so the user can jump straight to the Trash
         * after staging an app.
         */
        val recentlyMovedToTrash: Boolean = false,
    )

    sealed interface Event {
        data class LaunchIntent(val intent: Intent) : Event
        /**
         * v0.2.0 — ORDERED intent chain (try each in order, falling through on
         * ActivityNotFoundException). Used for deep-links where Android 11+
         * package visibility can lie about handler availability (cf.
         * [com.filestech.appmanager.data.system.IntentFactory.appPermissionsSettingsChain]).
         */
        data class LaunchIntentChain(val intents: List<Intent>) : Event
        data class ActionDone(val message: String) : Event
        data class ShowError(val message: String) : Event
        /** Phase X — fired when the app has been staged into the Trash (no system intent launched). */
        data class MovedToTrash(val label: String) : Event
        /** v0.2.0 — HARD-mode quarantine requested but the user has not picked a backup folder yet. */
        data object NeedsBackupFolder : Event
        /**
         * v0.2.0 — SOFT-mode quarantine successfully persisted. Carries the
         * intent to deep-link the user to the OS App-info screen + label/days
         * for the explanatory dialog. The UI must NOT auto-launch [intent] —
         * it shows the explanation dialog first so the user knows to tap
         * DESACTIVER / ARCHIVER themselves.
         */
        data class SoftQuarantineCreated(
            val intent: Intent,
            val label: String,
            val durationDays: Int,
        ) : Event
        /**
         * v0.2.0 Safety Guardrails — the user attempted a destructive action
         * on a CRITICAL app (authenticator, banking, etc.). The screen MUST
         * show [com.filestech.appmanager.ui.components.dialogs.CriticalWarningDialog]
         * and only re-invoke the matching action method with
         * `bypassCriticalCheck = true` on hold-3s confirm.
         */
        data class RequiresCriticalConfirmation(
            val classification: CriticalClassification,
            val action: CriticalAction,
            /** Carried only for [CriticalAction.QUARANTINE_HARD]. */
            val durationDays: Int = 0,
        ) : Event
    }

    /** Actions that trigger the Safety Guardrails dialog. */
    enum class CriticalAction {
        UNINSTALL,
        QUARANTINE_HARD,
        /** v0.3.1 Safety Phase B — disable a critical app (2FA / banking / etc.). */
        DISABLE,
    }

    private companion object {
        /** 15s cap on the parallel fan-out load (typical < 1s). */
        const val LOAD_TIMEOUT_MS: Long = 15_000L
    }
}
