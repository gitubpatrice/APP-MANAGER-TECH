package com.filestech.appmanager.data.system

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.filestech.appmanager.domain.model.ScanInterval
import com.filestech.appmanager.data.system.workers.BackgroundScanWorker
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

    private companion object {
        const val UNIQUE_NAME = "app_manager_tech_background_scan"
    }
}
