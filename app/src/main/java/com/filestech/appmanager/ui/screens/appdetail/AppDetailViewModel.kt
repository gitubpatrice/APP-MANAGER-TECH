package com.filestech.appmanager.ui.screens.appdetail

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.filestech.appmanager.core.ext.asFlow
import com.filestech.appmanager.core.ext.oneShotEvents
import com.filestech.appmanager.core.result.Outcome
import com.filestech.appmanager.core.result.getOrNull
import com.filestech.appmanager.data.system.IntentFactory
import com.filestech.appmanager.domain.model.AppDetail
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
import com.filestech.appmanager.domain.usecase.UninstallAppUseCase
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
    private val ignoreList: IgnoreListRepository,
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

    fun uninstall() = withPackage { pkg ->
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

    fun openUsageAccessSettings() {
        _events.trySend(Event.LaunchIntent(intents.usageAccessSettingsIntent()))
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
        data class ActionDone(val message: String) : Event
        data class ShowError(val message: String) : Event
        /** Phase X — fired when the app has been staged into the Trash (no system intent launched). */
        data class MovedToTrash(val label: String) : Event
    }
}
