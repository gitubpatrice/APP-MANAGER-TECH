package com.filestech.appmanager.ui.screens.ignorelist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.filestech.appmanager.core.ext.STATEFLOW_STOP_TIMEOUT_MS
import com.filestech.appmanager.domain.repository.IgnoreListRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Ignore-list screen.
 *
 * Observes the current set of ignored packages from the repository and
 * exposes a `remove` action. Adding to the list is done from the AppDetail
 * screen (Phase IX) — the Ignore-list screen is read+remove only.
 */
@HiltViewModel
class IgnoreListViewModel @Inject constructor(
    private val repository: IgnoreListRepository,
) : ViewModel() {

    val ignored: StateFlow<Set<String>> = repository.observe().stateIn(
        scope        = viewModelScope,
        started      = SharingStarted.WhileSubscribed(STATEFLOW_STOP_TIMEOUT_MS),
        initialValue = emptySet(),
    )

    fun remove(packageName: String) = viewModelScope.launch {
        repository.remove(packageName)
    }
}
