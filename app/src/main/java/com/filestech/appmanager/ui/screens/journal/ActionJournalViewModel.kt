package com.filestech.appmanager.ui.screens.journal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.filestech.appmanager.core.ext.STATEFLOW_STOP_TIMEOUT_MS
import com.filestech.appmanager.core.result.Outcome
import com.filestech.appmanager.domain.model.AmtActionEvent
import com.filestech.appmanager.domain.usecase.ObserveAmtActionsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * v0.4.0 — Drives the Action Journal screen.
 *
 * Owns the [ObserveAmtActionsUseCase.Window] state — flipping the
 * picker (`30j` / `90j` / `Tout`) re-bases the lower-bound timestamp
 * via `flatMapLatest`, swapping the upstream Room flow without leaking
 * the prior subscription. Same shape as [LifecycleHistoryViewModel] to
 * preserve cross-screen consistency.
 */
@HiltViewModel
class ActionJournalViewModel @Inject constructor(
    private val observeActions: ObserveAmtActionsUseCase,
) : ViewModel() {

    private val _window = MutableStateFlow(ObserveAmtActionsUseCase.Window.LAST_30)
    val window: StateFlow<ObserveAmtActionsUseCase.Window> = _window.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val events: StateFlow<Outcome<List<AmtActionEvent>>> = _window
        .flatMapLatest { observeActions(it) }
        .stateIn(
            scope        = viewModelScope,
            started      = SharingStarted.WhileSubscribed(STATEFLOW_STOP_TIMEOUT_MS),
            initialValue = Outcome.Loading,
        )

    fun setWindow(value: ObserveAmtActionsUseCase.Window) {
        _window.value = value
    }
}
