package com.filestech.appmanager.ui.screens.trash

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.filestech.appmanager.core.ext.STATEFLOW_STOP_TIMEOUT_MS
import com.filestech.appmanager.core.ext.asFlow
import com.filestech.appmanager.core.ext.oneShotEvents
import com.filestech.appmanager.core.result.getOrNull
import com.filestech.appmanager.domain.model.TrashItem
import com.filestech.appmanager.domain.usecase.ObserveTrashUseCase
import com.filestech.appmanager.domain.usecase.RestoreFromTrashUseCase
import com.filestech.appmanager.domain.usecase.UninstallAppUseCase
import com.filestech.appmanager.domain.repository.TrashRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Corbeille / Trash screen ViewModel.
 *
 * The Room flow is the single source of truth. Every action either restores
 * a row (delete row, app stays) or fires an uninstall Intent (system handles
 * the actual removal; row is cleared on the next scan or on explicit restore).
 */
@HiltViewModel
class TrashViewModel @Inject constructor(
    private val observeTrash: ObserveTrashUseCase,
    private val restoreFromTrash: RestoreFromTrashUseCase,
    private val trashRepo: TrashRepository,
    private val uninstallApp: UninstallAppUseCase,
) : ViewModel() {

    val items: StateFlow<List<TrashItem>> = observeTrash()
        .stateIn(
            scope        = viewModelScope,
            started      = SharingStarted.WhileSubscribed(STATEFLOW_STOP_TIMEOUT_MS),
            initialValue = emptyList(),
        )

    private val _events = oneShotEvents<Event>()
    val events: Flow<Event> = _events.asFlow()

    /** Restore a single entry (app stays installed, just leaves the trash). */
    fun restore(packageName: String) {
        viewModelScope.launch {
            restoreFromTrash(packageName)
            _events.trySend(Event.Restored(packageName))
        }
    }

    /** Restore everything (bulk wipe of the staging table). */
    fun restoreAll() {
        viewModelScope.launch {
            trashRepo.restoreAll()
        }
    }

    /** Fire the system uninstall intent for one trashed app. */
    fun uninstallNow(packageName: String) {
        uninstallApp(packageName).getOrNull()?.let { intent ->
            _events.trySend(Event.LaunchIntent(intent))
        } ?: _events.trySend(Event.ShowError("Cannot uninstall $packageName"))
    }

    /**
     * Empty trash = launch uninstall intent for every trashed app, sequentially.
     * The UI is responsible for `startActivity`-ing each one — Android shows
     * its own confirmation dialog per app, so the user can still abort mid-flow.
     *
     * Rows are NOT deleted from the trash here. They will be cleaned up on the
     * next full rescan once PackageManager confirms the app is gone, or the
     * user can manually restore stragglers (e.g. uninstall they cancelled).
     */
    fun emptyTrash() {
        val snapshot = items.value
        if (snapshot.isEmpty()) return
        val intents = snapshot.mapNotNull { uninstallApp(it.packageName).getOrNull() }
        if (intents.isEmpty()) return
        _events.trySend(Event.LaunchIntents(intents, snapshot.size))
    }

    sealed interface Event {
        data class LaunchIntent(val intent: Intent) : Event
        data class LaunchIntents(val intents: List<Intent>, val total: Int) : Event
        data class Restored(val packageName: String) : Event
        data class ShowError(val message: String) : Event
    }
}
