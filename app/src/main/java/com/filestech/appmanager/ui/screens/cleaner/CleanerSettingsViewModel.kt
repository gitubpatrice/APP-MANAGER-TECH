package com.filestech.appmanager.ui.screens.cleaner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.filestech.appmanager.core.ext.STATEFLOW_STOP_TIMEOUT_MS
import com.filestech.appmanager.data.local.datastore.AppSettings
import com.filestech.appmanager.data.local.datastore.SettingsRepository
import com.filestech.appmanager.domain.model.ExportFormat
import com.filestech.appmanager.domain.model.ScanInterval
import com.filestech.appmanager.domain.usecase.ScheduleBackgroundScanUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Cleaner settings screen.
 *
 * Bundles the four "background scan + threshold + rarely used + export
 * format" prefs that live under `AppSettings.Scanner`. Setting the scan
 * interval goes through [ScheduleBackgroundScanUseCase] so the WorkManager
 * job is rescheduled atomically with the settings write.
 */
@HiltViewModel
class CleanerSettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val scheduleScan: ScheduleBackgroundScanUseCase,
) : ViewModel() {

    val state: StateFlow<AppSettings> = settings.flow.stateIn(
        scope        = viewModelScope,
        started      = SharingStarted.WhileSubscribed(STATEFLOW_STOP_TIMEOUT_MS),
        initialValue = AppSettings(),
    )

    fun setScanInterval(interval: ScanInterval) = viewModelScope.launch {
        scheduleScan(interval)
    }

    fun setCacheThresholdMb(value: Int) = viewModelScope.launch {
        settings.update { copy(scanner = scanner.copy(cacheThresholdMb = value.coerceAtLeast(0))) }
    }

    fun setRarelyUsedThresholdDays(value: Int) = viewModelScope.launch {
        settings.update { copy(scanner = scanner.copy(rarelyUsedThresholdDays = value.coerceAtLeast(1))) }
    }

    fun setExportFormat(format: ExportFormat) = viewModelScope.launch {
        settings.update { copy(scanner = scanner.copy(exportFormat = format)) }
    }
}
