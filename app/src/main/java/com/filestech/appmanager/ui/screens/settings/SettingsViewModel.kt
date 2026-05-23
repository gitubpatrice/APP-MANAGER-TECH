package com.filestech.appmanager.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.filestech.appmanager.core.ext.STATEFLOW_STOP_TIMEOUT_MS
import com.filestech.appmanager.data.local.datastore.AppSettings
import com.filestech.appmanager.data.local.datastore.SettingsRepository
import com.filestech.appmanager.domain.model.AppSortOrder
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

    // VIII C7 fix: STOP_TIMEOUT_MS factored to core.ext.STATEFLOW_STOP_TIMEOUT_MS.
}
