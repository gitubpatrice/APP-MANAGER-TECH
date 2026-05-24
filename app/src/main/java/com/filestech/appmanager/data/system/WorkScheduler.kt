package com.filestech.appmanager.data.system

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.filestech.appmanager.domain.model.ScanInterval
import com.filestech.appmanager.data.system.workers.AmtActionJournalPurgeWorker
import com.filestech.appmanager.data.system.workers.BackgroundScanWorker
import com.filestech.appmanager.data.system.workers.LifecyclePurgeWorker
import com.filestech.appmanager.data.system.workers.PermissionSnapshotWorker
import com.filestech.appmanager.data.system.workers.QuarantineRestoreWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules / cancels the periodic [BackgroundScanWorker] based on the
 * user's [ScanInterval] preference.
 *
 * Single entry point: [apply]. Idempotent — uses [ExistingPeriodicWorkPolicy.UPDATE]
 * so re-applying the same interval keeps the same scheduled instance.
 *
 * Constraints are minimal (battery not low) — the worker is cheap (a few
 * hundred milliseconds of PackageManager calls), so we don't gate on
 * charging or idle.
 *
 * F-Droid: WorkManager is AndroidX (Apache 2.0); no GMS dep.
 */
@Singleton
class WorkScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    // VIII L-2 fix: `by lazy` cached — was `get()` calling .getInstance() on every access.
    private val workManager: WorkManager by lazy { WorkManager.getInstance(context) }

    fun apply(interval: ScanInterval) {
        when (interval) {
            ScanInterval.OFF -> {
                workManager.cancelUniqueWork(UNIQUE_NAME)
                Timber.i("Background scan disabled")
            }
            ScanInterval.DAILY  -> schedule(repeatHours = 24)
            ScanInterval.WEEKLY -> schedule(repeatHours = 24 * 7)
        }
    }

    private fun schedule(repeatHours: Long) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .setRequiresBatteryNotLow(true)
            .build()

        val request = PeriodicWorkRequestBuilder<BackgroundScanWorker>(
            repeatHours,
            TimeUnit.HOURS,
        )
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniquePeriodicWork(
            UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
        Timber.i("Background scan scheduled every %d hours", repeatHours)
    }

    /**
     * v0.2.0 — schedules / cancels the [PermissionSnapshotWorker].
     *
     * Idempotent ([ExistingPeriodicWorkPolicy.UPDATE]) — re-applying with the
     * same [enabled] value is a no-op. When [enabled] is false, the unique
     * work is cancelled outright.
     */
    fun applyPermissionSnapshotTracking(enabled: Boolean) {
        if (!enabled) {
            workManager.cancelUniqueWork(UNIQUE_PERMISSION_DRIFT)
            Timber.i("Permission Drift snapshot worker cancelled")
            return
        }
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .setRequiresBatteryNotLow(true)
            .build()

        val request = PeriodicWorkRequestBuilder<PermissionSnapshotWorker>(
            24L,
            TimeUnit.HOURS,
        )
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniquePeriodicWork(
            UNIQUE_PERMISSION_DRIFT,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
        Timber.i("Permission Drift snapshot worker scheduled (24h)")
    }

    /**
     * v0.2.0 — schedules / cancels the [QuarantineRestoreWorker].
     *
     * The reminder worker is cheap (one Room query + 0..N notifs) so it runs
     * unconstrained except for `setRequiresBatteryNotLow`. Disabling it stops
     * the expiry notifs but the entries themselves stay persisted — the in-
     * app Quarantine screen remains the source of truth.
     */
    fun applyQuarantineRestoreScheduling(enabled: Boolean) {
        if (!enabled) {
            workManager.cancelUniqueWork(UNIQUE_QUARANTINE)
            Timber.i("Quarantine restore worker cancelled")
            return
        }
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .setRequiresBatteryNotLow(true)
            .build()

        val request = PeriodicWorkRequestBuilder<QuarantineRestoreWorker>(
            24L,
            TimeUnit.HOURS,
        )
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniquePeriodicWork(
            UNIQUE_QUARANTINE,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
        Timber.i("Quarantine restore worker scheduled (24h)")
    }

    /**
     * v0.3.0 — schedules / cancels the [LifecyclePurgeWorker].
     *
     * Idempotent ([ExistingPeriodicWorkPolicy.UPDATE]) — re-applying with the
     * same [enabled] value is a no-op. When [enabled] is false the unique
     * work is cancelled outright so no purge tick runs after the user opts out.
     *
     * The receiver (PackageMonitor) is registered separately at startup time
     * from MainApplication — this scheduler ONLY owns the periodic purge of
     * old rows.
     */
    fun applyLifecyclePurgeScheduling(enabled: Boolean) {
        if (!enabled) {
            workManager.cancelUniqueWork(UNIQUE_LIFECYCLE)
            Timber.i("Lifecycle purge worker cancelled")
            return
        }
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .setRequiresBatteryNotLow(true)
            .build()

        val request = PeriodicWorkRequestBuilder<LifecyclePurgeWorker>(
            24L,
            TimeUnit.HOURS,
        )
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniquePeriodicWork(
            UNIQUE_LIFECYCLE,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
        Timber.i("Lifecycle purge worker scheduled (24h)")
    }

    /**
     * v0.4.0 — schedules / cancels the [AmtActionJournalPurgeWorker].
     *
     * Same shape as [applyLifecyclePurgeScheduling] : 24-hour periodic
     * tick, `UPDATE` policy for idempotency, cancellation on opt-out.
     * The worker no-ops internally when the feature flag is off too,
     * so the journal stops growing the moment the toggle is flipped
     * even before the next 24-hour tick fires.
     */
    fun applyAmtActionJournalScheduling(enabled: Boolean) {
        if (!enabled) {
            workManager.cancelUniqueWork(UNIQUE_ACTION_JOURNAL)
            Timber.i("Action journal purge worker cancelled")
            return
        }
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .setRequiresBatteryNotLow(true)
            .build()

        val request = PeriodicWorkRequestBuilder<AmtActionJournalPurgeWorker>(
            24L,
            TimeUnit.HOURS,
        )
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniquePeriodicWork(
            UNIQUE_ACTION_JOURNAL,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
        Timber.i("Action journal purge worker scheduled (24h)")
    }

    private companion object {
        const val UNIQUE_NAME             = "app_manager_tech_background_scan"
        const val UNIQUE_PERMISSION_DRIFT = "app_manager_tech_permission_drift"
        const val UNIQUE_QUARANTINE       = "app_manager_tech_quarantine_restore"
        const val UNIQUE_LIFECYCLE        = "app_manager_tech_lifecycle_purge"
        const val UNIQUE_ACTION_JOURNAL   = "app_manager_tech_action_journal_purge"
    }
}
