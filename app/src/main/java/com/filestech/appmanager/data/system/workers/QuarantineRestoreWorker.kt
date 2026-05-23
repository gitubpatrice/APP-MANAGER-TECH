package com.filestech.appmanager.data.system.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.filestech.appmanager.core.result.Outcome
import com.filestech.appmanager.data.system.NotificationHelper
import com.filestech.appmanager.domain.usecase.CheckExpiredQuarantinesUseCase
import com.filestech.appmanager.domain.usecase.MarkQuarantineNotifiedUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import timber.log.Timber

/**
 * Periodic worker that posts expiry notifications for every quarantine entry
 * whose [com.filestech.appmanager.domain.model.QuarantineEntry.restoreAt] is
 * in the past.
 *
 * Cadence: 24h via [com.filestech.appmanager.data.system.WorkScheduler.applyQuarantineRestoreScheduling].
 *
 * State machine (per row):
 *  - row inserted with `notified = false`
 *  - worker tick after restoreAt → fire notif + flip `notified = true`
 *  - user taps notif → opens the Quarantine screen, decides Restore vs Drop
 *  - user takes action → row deleted by the use case
 *
 * Defensive ordering: notif is fired FIRST, [MarkQuarantineNotifiedUseCase]
 * is called only if the notif succeeded. A SecurityException (no POST_NOTIFS)
 * is swallowed by [NotificationHelper.notifyIfPermitted], in which case
 * `markNotified` does NOT fire and the row is retried next tick (acceptable
 * — the user revoked notif permission and the in-app list is the fallback
 * surface).
 *
 * F-Droid: pure local WorkManager, no GMS / FCM.
 */
@HiltWorker
class QuarantineRestoreWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val checkExpired: CheckExpiredQuarantinesUseCase,
    private val markNotified: MarkQuarantineNotifiedUseCase,
    private val notifications: NotificationHelper,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        Timber.i("QuarantineRestoreWorker tick")
        return try {
            withTimeout(MAX_WORK_MS) {
                val outcome = checkExpired()
                val expired = when (outcome) {
                    is Outcome.Success -> outcome.value
                    is Outcome.Failure -> {
                        Timber.w("checkExpired failed: %s — will retry", outcome.error)
                        return@withTimeout Result.retry()
                    }
                    Outcome.Loading    -> return@withTimeout Result.success()
                }

                for (entry in expired) {
                    val posted = notifications.postQuarantineExpired(
                        packageName = entry.packageName,
                        label       = entry.label,
                    )
                    if (posted) {
                        markNotified(entry.packageName)
                    }
                }
                Result.success()
            }
        } catch (e: TimeoutCancellationException) {
            Timber.w(e, "QuarantineRestoreWorker exceeded %d ms — will retry", MAX_WORK_MS)
            Result.retry()
        }
    }

    private companion object {
        const val MAX_WORK_MS: Long = 2L * 60 * 1000
    }
}
