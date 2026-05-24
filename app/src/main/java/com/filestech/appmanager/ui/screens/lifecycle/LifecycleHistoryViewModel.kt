package com.filestech.appmanager.ui.screens.lifecycle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.filestech.appmanager.core.ext.STATEFLOW_STOP_TIMEOUT_MS
import com.filestech.appmanager.core.result.Outcome
import com.filestech.appmanager.core.result.getOrNull
import com.filestech.appmanager.domain.model.LifecycleEvent
import com.filestech.appmanager.domain.model.LifecycleEventType
import com.filestech.appmanager.domain.model.PermissionDelta
import com.filestech.appmanager.domain.usecase.DetectPermissionDeltaUseCase
import com.filestech.appmanager.domain.usecase.ObserveLifecycleEventsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * v0.3.0 — Drives the Lifecycle History screen.
 *
 * Owns the [ObserveLifecycleEventsUseCase.Window] state — flipping the picker
 * (`30j` / `90j` / `Tout`) re-bases the lower-bound timestamp via
 * `flatMapLatest`, swapping the upstream Room flow without leaking the prior
 * subscription.
 *
 * v0.3.2 — also exposes [permissionDeltas]: a map keyed by event id carrying
 * the precomputed REPLACED-event perm gain delta. Computed lazily for the
 * currently visible event list to keep the UI a synchronous lookup.
 */
@HiltViewModel
class LifecycleHistoryViewModel @Inject constructor(
    private val observeEvents: ObserveLifecycleEventsUseCase,
    private val detectDelta: DetectPermissionDeltaUseCase,
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

    /**
     * v0.3.2 — eventId → PermissionDelta lookup for REPLACED rows. Empty
     * delta entries are kept in the map so the UI can distinguish "no
     * change" from "not yet computed". Recomputed whenever the upstream
     * event list emits a new value.
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val permissionDeltas: StateFlow<Map<Long, PermissionDelta>> = events
        .mapLatest { outcome ->
            val list = outcome.getOrNull().orEmpty()
            val replaced = list.filter { it.type == LifecycleEventType.REPLACED }
            val computed = mutableMapOf<Long, PermissionDelta>()
            for (ev in replaced) {
                val delta = detectDelta(ev).getOrNull() ?: continue
                computed[ev.id] = delta
            }
            computed.toMap()
        }
        .stateIn(
            scope        = viewModelScope,
            started      = SharingStarted.WhileSubscribed(STATEFLOW_STOP_TIMEOUT_MS),
            initialValue = emptyMap(),
        )

    fun setWindow(value: ObserveLifecycleEventsUseCase.Window) {
        _window.value = value
    }
}
