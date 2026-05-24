package com.filestech.appmanager.ui.screens.lifecycle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.filestech.appmanager.core.ext.STATEFLOW_STOP_TIMEOUT_MS
import com.filestech.appmanager.core.result.Outcome
import com.filestech.appmanager.domain.model.LifecycleEvent
import com.filestech.appmanager.domain.usecase.ObserveLifecycleEventsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * v0.3.0 — Drives the Lifecycle History screen.
 *
 * Owns the [ObserveLifecycleEventsUseCase.Window] state — flipping the picker
 * (`30j` / `90j` / `Tout`) re-bases the lower-bound timestamp via
 * `flatMapLatest`, swapping the upstream Room flow without leaking the prior
 * subscription.
 */
@HiltViewModel
class LifecycleHistoryViewModel @Inject constructor(
    private val observeEvents: ObserveLifecycleEventsUseCase,
) : ViewModel() {

    private val _window = MutableStateFlow(ObserveLifecycleEventsUseCase.Window.LAST_30)
    val window: StateFlow<ObserveLifecycleEventsUseCase.Window> = _window.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val events: StateFlow<Outcome<List<LifecycleEvent>>> = _window
        .flatMapLatest { observeEvents(it) }
        .stateIn(
            scope        = viewModelScope,
            started      = SharingStarted.WhileSubscribed(STATEFLOW_STOP_TIMEOUT_MS),
            initialValue = Outcome.Loading,
        )

    fun setWindow(value: ObserveLifecycleEventsUseCase.Window) {
        _window.value = value
    }
}
