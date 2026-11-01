package com.filestech.appmanager.domain.usecase

import com.filestech.appmanager.data.local.datastore.AppSettings
import com.filestech.appmanager.domain.repository.AmtActionRepository
import com.filestech.appmanager.domain.repository.AppLifecycleRepository
import com.filestech.appmanager.domain.repository.PermissionSnapshotRepository
import kotlinx.coroutines.CancellationException
import timber.log.Timber
import javax.inject.Inject

/**
 * v0.5.1 — erases every history that is turned off: the lifecycle history, the action journal, the
 * permission-change history.
 *
 * Turning one off used to stop its recording AND cancel its purge worker, so what it held stayed
 * forever, while the privacy policy said turning a history off deletes it. Called by MainApplication
 * whenever those three settings change, and at every launch: a history already off is empty (nothing
 * records it), and the data an older version left behind goes the first time.
 */
class EraseDisabledHistoriesUseCase @Inject constructor(
    private val lifecycle: AppLifecycleRepository,
    private val journal: AmtActionRepository,
    private val permissions: PermissionSnapshotRepository,
) {

    /**
     * Erases the histories [settings] turn off — unless those settings are the defaults put back after a
     * corrupted settings file ([AppSettings.resetAfterCorruption]): every history reads "off" there, and
     * erasing on them would delete what the user had chosen to keep, for a damaged file.
     */
    suspend operator fun invoke(settings: AppSettings) {
        if (settings.resetAfterCorruption) return
        invoke(
            lifecycleOn   = settings.lifecycle.enabled,
            journalOn     = settings.actionJournal.enabled,
            permissionsOn = settings.privacyMonitor.permissionDriftEnabled,
        )
    }

    // The permission repository returns a count, not an Outcome, and Room fails with more than
    // SQLException (IllegalStateException on a database that cannot open). Called from the
    // application scope, which has no handler: a failed delete is logged, never a crash at launch —
    // the next launch tries again. Cancellation still propagates.
    @Suppress("TooGenericExceptionCaught")
    suspend operator fun invoke(lifecycleOn: Boolean, journalOn: Boolean, permissionsOn: Boolean) {
        if (!lifecycleOn) lifecycle.deleteAll()
        if (!journalOn) journal.deleteAll()
        if (!permissionsOn) {
            try {
                permissions.deleteAll()
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                Timber.w(e, "Permission history: delete on opt-out failed")
            }
        }
    }
}
