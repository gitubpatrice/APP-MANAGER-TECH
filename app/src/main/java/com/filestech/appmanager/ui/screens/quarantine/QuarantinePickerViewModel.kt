package com.filestech.appmanager.ui.screens.quarantine

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.filestech.appmanager.core.ext.STATEFLOW_STOP_TIMEOUT_MS
import com.filestech.appmanager.core.ext.asFlow
import com.filestech.appmanager.core.ext.oneShotEvents
import com.filestech.appmanager.core.result.Outcome
import com.filestech.appmanager.data.local.datastore.SettingsRepository
import com.filestech.appmanager.data.system.CriticalAppDetector
import com.filestech.appmanager.domain.model.AppInfo
import com.filestech.appmanager.domain.model.CriticalClassification
import com.filestech.appmanager.domain.model.QuarantineMode
import com.filestech.appmanager.domain.repository.AppInfoRepository
import com.filestech.appmanager.domain.repository.QuarantineRepository
import com.filestech.appmanager.domain.usecase.QuarantineAppUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Quarantine Picker — choose an app + mode + duration, then
 * fire the actual [QuarantineAppUseCase].
 *
 * The app list is the cached user-app catalogue filtered by [search] +
 * "already quarantined" exclusion (re-quarantining the same package is not
 * supported; user must restore the existing entry first).
 *
 * Picker re-uses the existing [AppInfoRepository.observeApps] Flow — no
 * separate query path, no duplicated cache plumbing.
 */
@HiltViewModel
class QuarantinePickerViewModel @Inject constructor(
    private val appInfo: AppInfoRepository,
    private val quarantineRepo: QuarantineRepository,
    private val quarantineUseCase: QuarantineAppUseCase,
    private val settings: SettingsRepository,
    private val criticalDetector: CriticalAppDetector,
) : ViewModel() {

    private val _search = MutableStateFlow("")
    val search: StateFlow<String> = _search.asStateFlow()

    private val _isWorking = MutableStateFlow(false)
    val isWorking: StateFlow<Boolean> = _isWorking.asStateFlow()

    private val _events = oneShotEvents<Event>()
    val events: Flow<Event> = _events.asFlow()

    /**
     * Filtered + sorted user-app list. Combines:
     *  - the cached `observeApps(false)` Flow
     *  - the current search query (case-insensitive prefix match on label OR
     *    substring match on package name)
     *  - the live "already quarantined" set so the user can't double-quarantine
     */
    val apps: StateFlow<List<AppInfo>> = combine(
        appInfo.observeApps(includeSystemApps = false),
        quarantineRepo.observeAll(),
        _search,
    ) { outcome, quarantined, query ->
        val allOk = (outcome as? Outcome.Success)?.value.orEmpty()
        val quarantinedPkgs = quarantined.mapTo(HashSet(quarantined.size)) { it.packageName }
        val q = query.trim().lowercase()
        allOk.asSequence()
            .filter { it.packageName !in quarantinedPkgs }
            .filter { q.isEmpty() || it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q) }
            .sortedBy { it.label.lowercase() }
            .toList()
    }.stateIn(
        scope        = viewModelScope,
        started      = SharingStarted.WhileSubscribed(STATEFLOW_STOP_TIMEOUT_MS),
        initialValue = emptyList(),
    )

    fun setSearch(value: String) {
        _search.update { value }
    }

    /**
     * Fires the quarantine. UI handles the destructive-data warning before
     * calling this — the use case itself does NOT prompt; it just acts.
     */
    fun quarantine(
        packageName: String,
        mode: QuarantineMode,
        durationDays: Int,
        bypassCriticalCheck: Boolean = false,
    ) {
        if (_isWorking.value) return
        val label = apps.value.firstOrNull { it.packageName == packageName }?.label ?: packageName

        // v0.2.0 Safety Guardrails — same friction layer as AppDetail.
        // HARD mode wipes user data; require a hold-3s confirmation when the
        // target is a critical app (authenticator, banking, etc.).
        if (!bypassCriticalCheck && mode == QuarantineMode.HARD_UNINSTALL) {
            criticalDetector.classify(packageName)?.let { classification ->
                _events.trySend(
                    Event.RequiresCriticalConfirmation(
                        packageName    = packageName,
                        label          = label,
                        classification = classification,
                        durationDays   = durationDays,
                    ),
                )
                return
            }
        }

        _isWorking.update { true }
        viewModelScope.launch {
            try {
                val backupTreeUri = if (mode == QuarantineMode.HARD_UNINSTALL) {
                    settings.flow.first().quarantine.backupTreeUri?.let { Uri.parse(it) }
                } else null

                if (mode == QuarantineMode.HARD_UNINSTALL && backupTreeUri == null) {
                    _events.trySend(Event.NeedsBackupFolder)
                    return@launch
                }

                val result = quarantineUseCase(
                    packageName   = packageName,
                    mode          = mode,
                    durationDays  = durationDays,
                    backupTreeUri = backupTreeUri,
                )
                when (result) {
                    is QuarantineAppUseCase.Result.HardReady ->
                        // HARD mode launches the OS uninstall confirm directly
                        // — that screen IS self-explanatory ("Uninstall this
                        // app?" with Cancel / OK), no extra step needed.
                        _events.trySend(Event.QuarantineLaunched(result.uninstallIntent))
                    is QuarantineAppUseCase.Result.SoftReady ->
                        // v0.2.0 UX fix — SOFT mode no longer auto-opens
                        // Settings. We emit a typed event with label + days
                        // so the screen can surface the explanatory dialog
                        // first ("App enregistrée, vous devez DESACTIVER
                        // vous-même"), avoiding the v0.2.0-day-one confusion
                        // of landing on the OS App-info page with no context.
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
            } finally {
                _isWorking.update { false }
            }
        }
    }

    sealed interface Event {
        data class QuarantineLaunched(val intent: Intent) : Event
        data class ShowError(val message: String) : Event
        data object NeedsBackupFolder : Event
        /** v0.2.0 — SOFT-mode quarantine persisted; UI shows the explanatory dialog. */
        data class SoftQuarantineCreated(
            val intent: Intent,
            val label: String,
            val durationDays: Int,
        ) : Event
        /**
         * v0.2.0 Safety Guardrails — user picked a critical app for HARD
         * quarantine. Screen shows
         * [com.filestech.appmanager.ui.components.dialogs.CriticalWarningDialog]
         * and on hold-3s confirm re-invokes `quarantine(..., bypassCriticalCheck = true)`.
         */
        data class RequiresCriticalConfirmation(
            val packageName: String,
            val label: String,
            val classification: CriticalClassification,
            val durationDays: Int,
        ) : Event
    }
}
