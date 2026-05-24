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
import java.util.concurrent.atomic.AtomicBoolean
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

    /** v0.2.1 audit H-1 fix — shared atomic guard between `init` purge and
     *  ON_RESUME purge. The two were firing back-to-back at the first
     *  composition (init runs, then Lifecycle hits ON_RESUME ~1 frame later),
     *  duplicating the PM IPC sweep. CAS guarantees a single in-flight purge. */
    private val isPurging = AtomicBoolean(false)

    init {
        // v0.2.1 bug fix — orphaned-row sweep at ViewModel creation. Covers
        // the case where the user opens the Trash for the first time after
        // having uninstalled apps from elsewhere (Settings, Smart Cleaner,
        // direct uninstall outside this app).
        launchPurge()
    }

    /**
     * Re-runs the orphaned-row sweep. Called by the screen on
     * `Lifecycle.Event.ON_RESUME` — typically right after returning from the
     * OS uninstall confirmation dialog, where the app may have just been
     * uninstalled and its trash row needs to disappear.
     *
     * v0.2.1 fix to user report: "quand je désinstalle une appli depuis la
     * corbeille, l'appli ne disparait pas de la corbeille, j'ai encore
     * désinstaller ou restaurer malgré que l'appli ait été désinstallée
     * complètement".
     */
    fun onResumed() {
        launchPurge()
    }

    private fun launchPurge() {
        if (!isPurging.compareAndSet(false, true)) return
        viewModelScope.launch {
            try {
                trashRepo.purgeOrphaned()
            } finally {
                isPurging.set(false)
            }
        }
    }

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
