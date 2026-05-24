package com.filestech.appmanager.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.filestech.appmanager.core.ext.STATEFLOW_STOP_TIMEOUT_MS
import com.filestech.appmanager.core.ext.isValidPackageName
import com.filestech.appmanager.data.local.datastore.AppSettings
import com.filestech.appmanager.data.local.datastore.SettingsRepository
import com.filestech.appmanager.data.system.WorkScheduler
import com.filestech.appmanager.domain.model.AppSortOrder
import com.filestech.appmanager.domain.model.AppTag
import com.filestech.appmanager.domain.model.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Settings screen.
 *
 * Settings are persisted in DataStore via [SettingsRepository]; every write
 * is a transactional `update {}` that re-emits the latest snapshot to all
 * collectors. No local mutable state in the VM — single source of truth is
 * the repository flow.
 *
 * Write methods are fire-and-forget on [viewModelScope]; if the user changes
 * a toggle and immediately navigates away, the write completes anyway because
 * DataStore queues edits.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
    private val workScheduler: WorkScheduler,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = repository.flow.stateIn(
        scope        = viewModelScope,
        started      = SharingStarted.WhileSubscribed(STATEFLOW_STOP_TIMEOUT_MS),
        initialValue = AppSettings(),
    )

    // -----------------------------------------------------------------------
    // Appearance
    // -----------------------------------------------------------------------

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch {
        repository.update { copy(appearance = appearance.copy(themeMode = mode)) }
    }

    fun setDynamicColor(enabled: Boolean) = viewModelScope.launch {
        repository.update { copy(appearance = appearance.copy(dynamicColor = enabled)) }
    }

    fun setDefaultSortOrder(order: AppSortOrder) = viewModelScope.launch {
        repository.update { copy(appearance = appearance.copy(appSortOrder = order)) }
    }

    // -----------------------------------------------------------------------
    // Scanner
    // -----------------------------------------------------------------------

    fun setIncludeSystemApps(include: Boolean) = viewModelScope.launch {
        repository.update { copy(scanner = scanner.copy(includeSystemApps = include)) }
    }

    fun setAutoScanOnLaunch(enabled: Boolean) = viewModelScope.launch {
        repository.update { copy(scanner = scanner.copy(autoScanOnLaunch = enabled)) }
    }

    fun setIncludeFileAnalysis(enabled: Boolean) = viewModelScope.launch {
        repository.update { copy(scanner = scanner.copy(includeFileAnalysis = enabled)) }
    }

    // -----------------------------------------------------------------------
    // Privacy
    // -----------------------------------------------------------------------

    fun setFlagSecure(enabled: Boolean) = viewModelScope.launch {
        repository.update { copy(privacy = privacy.copy(flagSecure = enabled)) }
    }

    fun setConfirmBeforeDelete(enabled: Boolean) = viewModelScope.launch {
        repository.update { copy(privacy = privacy.copy(confirmBeforeDelete = enabled)) }
    }

    // -----------------------------------------------------------------------
    // Privacy Monitor (v0.2.0 — Permission Drift Tracker)
    // -----------------------------------------------------------------------

    fun setPermissionDriftEnabled(enabled: Boolean) = viewModelScope.launch {
        repository.update {
            copy(privacyMonitor = privacyMonitor.copy(permissionDriftEnabled = enabled))
        }
        // Apply the schedule change immediately — the user expects the worker
        // to start (or stop) as soon as they flip the toggle, not at next
        // process restart.
        workScheduler.applyPermissionSnapshotTracking(enabled = enabled)
    }

    fun setPermissionDriftRetentionDays(days: Int) = viewModelScope.launch {
        repository.update {
            copy(privacyMonitor = privacyMonitor.copy(permissionDriftRetentionDays = days))
        }
    }

    fun setPermissionDriftIncludeSystemApps(include: Boolean) = viewModelScope.launch {
        repository.update {
            copy(privacyMonitor = privacyMonitor.copy(permissionDriftIncludeSystemApps = include))
        }
    }

    fun setPermissionDriftNotify(enabled: Boolean) = viewModelScope.launch {
        repository.update {
            copy(privacyMonitor = privacyMonitor.copy(permissionDriftNotify = enabled))
        }
    }

    // -----------------------------------------------------------------------
    // Quarantine (v0.2.0)
    // -----------------------------------------------------------------------

    fun setQuarantineBackupTreeUri(uri: String?) = viewModelScope.launch {
        repository.update { copy(quarantine = quarantine.copy(backupTreeUri = uri)) }
    }

    fun setQuarantineRestoreReminderEnabled(enabled: Boolean) = viewModelScope.launch {
        repository.update {
            copy(quarantine = quarantine.copy(restoreReminderEnabled = enabled))
        }
        workScheduler.applyQuarantineRestoreScheduling(enabled = enabled)
    }

    // -----------------------------------------------------------------------
    // Lifecycle History (v0.3.0)
    // -----------------------------------------------------------------------

    /**
     * Master toggle. MainApplication's `observeLifecycleToggle` reacts to this
     * change to register / unregister [com.filestech.appmanager.data.system.PackageMonitor]
     * and to schedule / cancel the purge worker — we therefore only need to
     * persist the value here. The cross-process WorkScheduler call IS still
     * fired for parity with the other features so the toggle is immediately
     * effective even before MainApplication's flow collector ticks.
     */
    fun setLifecycleEnabled(enabled: Boolean) = viewModelScope.launch {
        repository.update { copy(lifecycle = lifecycle.copy(enabled = enabled)) }
        workScheduler.applyLifecyclePurgeScheduling(enabled = enabled)
    }

    fun setLifecycleRetentionDays(days: Int) = viewModelScope.launch {
        repository.update { copy(lifecycle = lifecycle.copy(retentionDays = days)) }
    }

    fun setLifecyclePromptReason(enabled: Boolean) = viewModelScope.launch {
        repository.update { copy(lifecycle = lifecycle.copy(promptReason = enabled)) }
    }

    // -----------------------------------------------------------------------
    // Action Journal (v0.4.0)
    // -----------------------------------------------------------------------

    /**
     * Master toggle for the AMT action journal. Persists the new value AND
     * applies the WorkManager scheduling change so the periodic retention
     * purge starts / stops immediately (without waiting for the next
     * process restart).
     */
    fun setActionJournalEnabled(enabled: Boolean) = viewModelScope.launch {
        repository.update { copy(actionJournal = actionJournal.copy(enabled = enabled)) }
        workScheduler.applyAmtActionJournalScheduling(enabled = enabled)
    }

    fun setActionJournalRetentionDays(days: Int) = viewModelScope.launch {
        repository.update { copy(actionJournal = actionJournal.copy(retentionDays = days)) }
    }

    // VIII C7 fix: STOP_TIMEOUT_MS factored to core.ext.STATEFLOW_STOP_TIMEOUT_MS.
}

/**
 * v0.3.3 / v0.3.4 — Tag write helper as a top-level extension so any
 * ViewModel can call it without going through [SettingsViewModel]. Keeps
 * the encoding logic centralised on the repository.
 *
 * v0.3.4 widens the value type to a [Set] : an app can now carry several
 * categories at once (e.g. WORK + TOOLS). Passing an empty set is the
 * "clear tag" path (the encoder drops empty entries on write, so DataStore
 * stays compact).
 *
 * @param packageName must satisfy [isValidPackageName]; invalid input is
 *   silently ignored (no exception thrown across the coroutine boundary).
 * @param tags pass [emptySet] to clear all tags for [packageName].
 */
suspend fun SettingsRepository.setAppTags(packageName: String, tags: Set<AppTag>) {
    if (!packageName.isValidPackageName()) return
    update {
        val next = appTags.toMutableMap()
        if (tags.isEmpty()) next.remove(packageName) else next[packageName] = tags
        copy(appTags = next.toMap())
    }
}
