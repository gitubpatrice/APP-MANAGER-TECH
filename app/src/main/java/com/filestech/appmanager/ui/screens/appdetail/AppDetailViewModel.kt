package com.filestech.appmanager.ui.screens.appdetail

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.filestech.appmanager.core.ext.asFlow
import com.filestech.appmanager.core.ext.oneShotEvents
import com.filestech.appmanager.core.result.Outcome
import com.filestech.appmanager.core.result.getOrNull
import com.filestech.appmanager.data.system.CriticalAppDetector
import com.filestech.appmanager.data.system.IntentFactory
import com.filestech.appmanager.domain.model.AppDetail
import com.filestech.appmanager.domain.model.CriticalClassification
import com.filestech.appmanager.domain.model.PrivacyScore
import com.filestech.appmanager.domain.model.TrackerReport
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
import android.net.Uri
import kotlinx.coroutines.flow.first
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val intents: IntentFactory,
) : ViewModel() {

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _events = oneShotEvents<Event>()
    val events: Flow<Event> = _events.asFlow()

    // -----------------------------------------------------------------------
    // Load — parallel fan-out for detail + privacy + trackers + isIgnored
    // -----------------------------------------------------------------------

    fun load(packageName: String) {
        _state.update {
            it.copy(
                packageName    = packageName,
                detailOutcome  = Outcome.Loading,
                privacyScore   = null,
                trackerReport  = null,
            )
        }
        viewModelScope.launch {
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
        uninstallApp(pkg).getOrNull()?.let { intent ->
            _events.trySend(Event.LaunchIntent(intent))
        } ?: _events.trySend(Event.ShowError("Cannot uninstall $pkg"))
    }

    /**
     * Phase X — soft delete: stage the app in the Corbeille / Trash instead of
     * launching the system uninstall intent. The app stays installed; the
     * user can later restore or uninstall-for-real from the Trash screen.
     *
     * Snapshots `label` + `totalSizeBytes` from the cached [AppDetail] so the
     * trash row stays consistent even if the app is uninstalled out-of-band.
     */
    fun moveToTrash() {
        val detail = (_state.value.detailOutcome as? Outcome.Success)?.value ?: return
        viewModelScope.launch {
            when (val r = moveAppToTrash(detail.info)) {
                is Outcome.Success -> _events.trySend(Event.MovedToTrash(detail.info.label))
                is Outcome.Failure -> _events.trySend(Event.ShowError(r.error.toString()))
                Outcome.Loading    -> Unit
            }
        }
    }

    fun clearCache() = withPackage { pkg ->
        clearCache(pkg).getOrNull()?.let { intent ->
            _events.trySend(Event.LaunchIntent(intent))
        }
    }

    fun clearData() = withPackage { pkg ->
        _events.trySend(Event.LaunchIntent(intents.appDetailsSettingsIntent(pkg)))
    }

    fun forceStop() {
        val pkg = currentPackage() ?: return
        viewModelScope.launch {
            when (val outcome = forceStopApp(pkg)) {
                is Outcome.Success -> _events.trySend(Event.ActionDone("Force stop requested"))
                is Outcome.Failure -> _events.trySend(Event.ShowError(outcome.error.toString()))
                Outcome.Loading    -> Unit
            }
        }
    }

    fun disable() = setEnabled(false)
    fun enable() = setEnabled(true)

    private fun setEnabled(enabled: Boolean) {
        val pkg = currentPackage() ?: return
        viewModelScope.launch {
            when (val outcome = disableEnable(pkg, enabled)) {
                is Outcome.Success -> when (val r = outcome.value) {
                    DisableEnableAppUseCase.Result.Done ->
                        _events.trySend(Event.ActionDone("Application ${if (enabled) "enabled" else "disabled"}"))
                    is DisableEnableAppUseCase.Result.NeedsUserAction ->
                        _events.trySend(Event.LaunchIntent(r.intent))
                }
                is Outcome.Failure -> _events.trySend(Event.ShowError(outcome.error.toString()))
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
            when (result) {
                is QuarantineAppUseCase.Result.HardReady ->
                    _events.trySend(Event.LaunchIntent(result.uninstallIntent))
                is QuarantineAppUseCase.Result.SoftReady ->
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
                is QuarantineAppUseCase.Result.Failure ->
                    _events.trySend(Event.ShowError(result.message))
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
    enum class CriticalAction { UNINSTALL, QUARANTINE_HARD }
}
