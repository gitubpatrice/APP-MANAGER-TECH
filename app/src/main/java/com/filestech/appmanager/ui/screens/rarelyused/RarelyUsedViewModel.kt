package com.filestech.appmanager.ui.screens.rarelyused

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.filestech.appmanager.core.ext.STATEFLOW_STOP_TIMEOUT_MS
import com.filestech.appmanager.core.result.Outcome
import com.filestech.appmanager.domain.model.AppInfo
import com.filestech.appmanager.domain.usecase.GetRarelyUsedAppsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * ViewModel for the Rarely-Used screen. Threshold is reactive — changing
 * [setThresholdDays] re-runs the use case and re-emits the new list.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RarelyUsedViewModel @Inject constructor(
    private val getRarelyUsed: GetRarelyUsedAppsUseCase,
) : ViewModel() {

    private val _thresholdDays = MutableStateFlow(DEFAULT_THRESHOLD_DAYS)
    val thresholdDays: StateFlow<Int> = _thresholdDays

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

    companion object {
        const val DEFAULT_THRESHOLD_DAYS = 30
        val THRESHOLD_OPTIONS: List<Int> = listOf(7, 14, 30, 60, 90, 180)
    }
}
