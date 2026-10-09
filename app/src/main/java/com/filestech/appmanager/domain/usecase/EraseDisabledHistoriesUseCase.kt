package com.filestech.appmanager.domain.usecase

import android.database.SQLException
import com.filestech.appmanager.domain.repository.AmtActionRepository
import com.filestech.appmanager.domain.repository.AppLifecycleRepository
import com.filestech.appmanager.domain.repository.PermissionSnapshotRepository
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

    suspend operator fun invoke(lifecycleOn: Boolean, journalOn: Boolean, permissionsOn: Boolean) {
        if (!lifecycleOn) lifecycle.deleteAll()
        if (!journalOn) journal.deleteAll()
        if (!permissionsOn) {
            // Returns a count, not an Outcome: a failed delete is logged, never fatal.
            try {
                permissions.deleteAll()
            } catch (e: SQLException) {
                Timber.w(e, "Permission history: delete on opt-out failed")
            }
        }
    }
}
