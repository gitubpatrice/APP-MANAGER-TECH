package com.filestech.appmanager.ui.screens.rarelyused

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.filestech.appmanager.core.ext.STATEFLOW_STOP_TIMEOUT_MS
import com.filestech.appmanager.core.ext.asFlow
import com.filestech.appmanager.core.ext.oneShotEvents
import com.filestech.appmanager.core.result.Outcome
import com.filestech.appmanager.data.system.IntentFactory
import com.filestech.appmanager.domain.model.AppInfo
import com.filestech.appmanager.domain.repository.AppInfoRepository
import com.filestech.appmanager.domain.usecase.GetRarelyUsedAppsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * ViewModel for the Rarely-Used screen. Threshold is reactive — changing
 * [setThresholdDays] re-runs the use case and re-emits the new list.
 *
 * v0.2.1 audits C2a + C8a fix — added `usageStatsGranted` probe + `onResumed()`
 * + `requestUsageStatsPermission()`. Without PACKAGE_USAGE_STATS, every app's
 * `lastUsedTime == 0` and the use case classifies the whole catalogue as
 * "rarely used" → noise. The banner now tells the user why the results may
 * be empty/wrong and lets them grant the permission in 1 tap.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RarelyUsedViewModel @Inject constructor(
    private val getRarelyUsed: GetRarelyUsedAppsUseCase,
    private val appInfoRepo: AppInfoRepository,
    private val intents: IntentFactory,
) : ViewModel() {

    private val _thresholdDays = MutableStateFlow(DEFAULT_THRESHOLD_DAYS)
    val thresholdDays: StateFlow<Int> = _thresholdDays

    private val _usageStatsGranted = MutableStateFlow(appInfoRepo.hasUsageStatsAccess())
    val usageStatsGranted: StateFlow<Boolean> = _usageStatsGranted.asStateFlow()

    private val _events = oneShotEvents<Event>()
    val events: Flow<Event> = _events.asFlow()

    val listOutcome: StateFlow<Outcome<List<AppInfo>>> = _thresholdDays
        .flatMapLatest { days -> getRarelyUsed(days) }
        .stateIn(
            scope        = viewModelScope,
            started      = SharingStarted.WhileSubscribed(STATEFLOW_STOP_TIMEOUT_MS),
            initialValue = Outcome.Loading,
        )

    fun setThresholdDays(days: Int) {
        _thresholdDays.value = days.coerceAtLeast(1)
    }

    /**
     * v0.2.1 — re-probe PACKAGE_USAGE_STATS on ON_RESUME. When the user grants
     * the permission in Settings and comes back, the banner disappears.
     * Re-emission of `listOutcome` happens automatically via the reactive
     * Flow (the underlying repository observes Room which gets a fresh
     * lastUsedTime on next rescan tick — handled elsewhere).
     */
    fun onResumed() {
        val now = appInfoRepo.hasUsageStatsAccess()
        if (_usageStatsGranted.value != now) {
            _usageStatsGranted.update { now }
        }
    }

    fun requestUsageStatsPermission() {
        _events.trySend(Event.LaunchIntent(intents.usageAccessSettingsIntent()))
    }

    sealed interface Event {
        data class LaunchIntent(val intent: Intent) : Event
    }

    companion object {
        const val DEFAULT_THRESHOLD_DAYS = 30
        val THRESHOLD_OPTIONS: List<Int> = listOf(7, 14, 30, 60, 90, 180)
    }
}
