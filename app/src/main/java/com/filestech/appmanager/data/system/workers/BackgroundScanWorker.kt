package com.filestech.appmanager.data.system.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.filestech.appmanager.core.result.Outcome
import com.filestech.appmanager.data.local.datastore.SettingsRepository
import com.filestech.appmanager.data.system.NotificationHelper
import com.filestech.appmanager.domain.repository.AppInfoRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import timber.log.Timber

/**
 * Periodic worker that refreshes the Room cache from PackageManager and posts
 * a notification if the total cache size has crossed the user-configured
 * threshold.
 *
 * Failure handling:
 * - Repository rescan failure → return [Result.retry] (WorkManager backs off
 *   exponentially and re-runs).
 * - Notification failure (e.g. POST_NOTIFICATIONS not granted) → swallowed
 *   by [NotificationHelper.notifyIfPermitted]; the worker still succeeds.
 *
 * F-Droid: no FCM dependency; pure local WorkManager job.
 */
@HiltWorker
class BackgroundScanWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repository: AppInfoRepository,
    private val settings: SettingsRepository,
    private val notifications: NotificationHelper,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        Timber.i("BackgroundScanWorker tick")

        // VIII L-1 fix: hard cap so a hung `queryStatsForUid` on a misbehaving
        // OEM cannot block the worker forever. Android 14+ enforces a 10-minute
        // system cap; we set a tighter 8-minute application cap to also cover
        // API 26–33 where no auto-cap exists.
        return try {
            withTimeout(MAX_WORK_MS) {
                val rescan = repository.rescan()
                if (rescan is Outcome.Failure) {
                    Timber.w("Rescan failed: %s — will retry", rescan.error)
                    return@withTimeout Result.retry()
                }

                val thresholdMb = settings.flow.first().scanner.cacheThresholdMb
                if (thresholdMb > 0) {
                    val report = repository.getStorageReport(includeSystemApps = true, topN = 0)
                    if (report is Outcome.Success) {
                        val usedBytes = report.value.totalCacheBytes
                        val thresholdBytes = thresholdMb.toLong() * BYTES_PER_MB
                        if (usedBytes >= thresholdBytes) {
                            Timber.i(
                                "Cache threshold breached: %d B >= %d B (%d MB)",
                                usedBytes,
                                thresholdBytes,
                                thresholdMb,
                            )
                            notifications.postCacheThreshold(usedBytes, thresholdMb)
                        }
                    }
                }

                Result.success()
            }
        } catch (e: TimeoutCancellationException) {
            Timber.w(e, "BackgroundScanWorker exceeded %d ms — will retry", MAX_WORK_MS)
            Result.retry()
        }
    }

    private companion object {
        const val BYTES_PER_MB = 1024L * 1024L
        /** 8-minute cap (Android 14+ enforces 10 min; we stay conservative). */
        const val MAX_WORK_MS: Long = 8L * 60 * 1000
    }
}
