package com.filestech.appmanager.ui.screens.quarantine

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.filestech.appmanager.R
import com.filestech.appmanager.core.ext.STATEFLOW_STOP_TIMEOUT_MS
import com.filestech.appmanager.core.ext.asFlow
import com.filestech.appmanager.core.ext.oneShotEvents
import com.filestech.appmanager.data.local.datastore.SettingsRepository
import com.filestech.appmanager.domain.model.QuarantineEntry
import com.filestech.appmanager.domain.model.QuarantineMode
import com.filestech.appmanager.domain.usecase.DropQuarantineEntryUseCase
import com.filestech.appmanager.domain.usecase.ObserveQuarantinesUseCase
import com.filestech.appmanager.domain.usecase.QuarantineAppUseCase
import com.filestech.appmanager.domain.usecase.RestoreFromQuarantineUseCase
import com.filestech.appmanager.ui.text.UiText
import com.filestech.appmanager.ui.text.toUiText
import com.filestech.appmanager.ui.text.uiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

/**
 * ViewModel for the Quarantine list screen.
 *
 * Responsibilities:
 *  - Observe the list of quarantined apps (newest expiry first).
 *  - Restore one entry → emits launch intent (HARD install or SOFT settings deep-link).
 *  - Drop an entry without restoring (for stale rows / backup-missing recovery).
 *  - Trigger a new quarantine flow from any screen that holds an AppInfo (NOT
 *    from this list screen itself — the list is read-only after quarantine).
 *
 * Re-entrancy: actions are short-lived enough that a single in-flight guard
 * via [_isWorking] suffices; double-tap of "Restaurer" is held until the first
 * call returns.
 */
@HiltViewModel
class QuarantineViewModel @Inject constructor(
    private val observe: ObserveQuarantinesUseCase,
    private val quarantine: QuarantineAppUseCase,
    private val restore: RestoreFromQuarantineUseCase,
    private val drop: DropQuarantineEntryUseCase,
    private val settings: SettingsRepository,
) : ViewModel() {

    val items: StateFlow<List<QuarantineEntry>> = observe()
        .stateIn(
            scope        = viewModelScope,
            started      = SharingStarted.WhileSubscribed(STATEFLOW_STOP_TIMEOUT_MS),
            initialValue = emptyList(),
        )

    private val _events = oneShotEvents<Event>()
    val events: Flow<Event> = _events.asFlow()

    /** v0.2.1 audit C1d — re-entrancy guard. The KDoc above mentioned an
     *  `_isWorking` flag but none was implemented → double-tap on "Restaurer"
     *  could fire two PackageInstaller intents. AtomicBoolean now enforces
     *  single-in-flight per action across the 3 entry points. */
    private val isWorking = AtomicBoolean(false)

    /**
     * The package whose HARD restore was handed to the installer. Only picks the message shown at the
     * next resume; whether its entry goes is decided by [RestoreFromQuarantineUseCase.reconcileHardRestores],
     * on every resume, so losing this field (process death) loses a message, never an outcome.
     */
    private var pendingRestore: String? = null

    /** Quarantine an app (called from AppDetail / list screens via QuarantineLauncher). */
    fun quarantine(
        packageName: String,
        mode: QuarantineMode,
        durationDays: Int,
    ) {
        if (!isWorking.compareAndSet(false, true)) return
        viewModelScope.launch {
            try {
                val backupTreeUri = if (mode == QuarantineMode.HARD_UNINSTALL) {
                    settings.flow.first().quarantine.backupTreeUri?.let { Uri.parse(it) }
                } else {
                    null
                }

                if (mode == QuarantineMode.HARD_UNINSTALL && backupTreeUri == null) {
                    _events.trySend(Event.NeedsBackupFolder)
                    return@launch
                }

                val result = quarantine(
                    packageName   = packageName,
                    mode          = mode,
                    durationDays  = durationDays,
                    backupTreeUri = backupTreeUri,
                )
                when (result) {
                    is QuarantineAppUseCase.Result.HardReady ->
                        _events.trySend(Event.LaunchIntent(result.uninstallIntent))
                    is QuarantineAppUseCase.Result.SoftReady ->
                        _events.trySend(Event.LaunchIntent(result.appDetailsIntent))
                    is QuarantineAppUseCase.Result.Failure ->
                        _events.trySend(Event.ShowError(result.toUiText()))
                }
            } finally {
                isWorking.set(false)
            }
        }
    }

    fun restoreEntry(packageName: String) {
        if (!isWorking.compareAndSet(false, true)) return
        viewModelScope.launch {
            try {
                when (val r = restore(packageName)) {
                    is RestoreFromQuarantineUseCase.Result.HardReinstall -> {
                        pendingRestore = packageName
                        _events.trySend(Event.LaunchIntent(r.intent))
                    }
                    is RestoreFromQuarantineUseCase.Result.SoftReenable ->
                        _events.trySend(Event.LaunchIntent(r.intent))
                    RestoreFromQuarantineUseCase.Result.BackupMissing ->
                        _events.trySend(Event.BackupMissing(packageName))
                    RestoreFromQuarantineUseCase.Result.NotFound ->
                        _events.trySend(Event.ShowError(uiText(R.string.quarantine_error_entry_not_found, packageName)))
                    RestoreFromQuarantineUseCase.Result.InvalidPackage ->
                        _events.trySend(Event.ShowError(uiText(R.string.error_invalid_package)))
                }
            } finally {
                isWorking.set(false)
            }
        }
    }

    /**
     * v0.5.1 — The screen is back in front: drop every HARD entry whose app came back after its
     * quarantine (see [RestoreFromQuarantineUseCase.reconcileHardRestores]); the others stay, with
     * their backup. If a HARD restore was just handed to the installer, say once whether that app is
     * back; later resumes reconcile silently, so an install that finishes later still drops its entry.
     */
    fun onResumed() {
        val pending = pendingRestore
        pendingRestore = null
        viewModelScope.launch {
            val dropped = restore.reconcileHardRestores()
            if (pending == null) return@launch
            val message = if (pending in dropped) R.string.quarantine_restore_done else R.string.quarantine_restore_not_installed
            _events.trySend(Event.ShowMessage(uiText(message, pending)))
        }
    }

    fun dropEntry(packageName: String) {
        if (!isWorking.compareAndSet(false, true)) return
        viewModelScope.launch {
            try {
                drop(packageName)
            } finally {
                isWorking.set(false)
            }
        }
    }

    sealed interface Event {
        data class LaunchIntent(val intent: Intent) : Event
        data class ShowError(val text: UiText) : Event
        data class ShowMessage(val text: UiText) : Event
        /** HARD restore requested but APK file is gone — UI asks user to drop the entry. */
        data class BackupMissing(val packageName: String) : Event
        /** HARD quarantine requested but the user has not picked a backup folder yet. */
        data object NeedsBackupFolder : Event
    }
}
