package com.filestech.appmanager.data.system.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.filestech.appmanager.core.result.Outcome
import com.filestech.appmanager.data.local.datastore.SettingsRepository
import com.filestech.appmanager.domain.usecase.PurgeLifecycleEventsUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import timber.log.Timber

/**
 * v0.3.0 — Periodic worker that drops lifecycle events older than the user-
 * configured retention.
 *
 * Cadence: 24h via [com.filestech.appmanager.data.system.WorkScheduler.applyLifecyclePurgeScheduling].
 *
 * Failure model mirrors [PermissionSnapshotWorker]:
 *  - Hard cap via `withTimeout(MAX_WORK_MS)` — purge is a single DELETE
 *    query so it should never approach the cap, but defensive nonetheless.
 *  - Settings unreachable → graceful retry.
 *  - Purge failure → swallowed (logged) so the worker still returns success
 *    and the next tick re-attempts; retention is best-effort, not load-bearing.
 *
 * F-Droid: WorkManager only, no GMS.
 */
@HiltWorker
class LifecyclePurgeWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val purge: PurgeLifecycleEventsUseCase,
    private val settings: SettingsRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        Timber.i("LifecyclePurgeWorker tick")
        return try {
            withTimeout(MAX_WORK_MS) {
                val snapshot = settings.flow.first()
                if (!snapshot.lifecycle.enabled) {
                    Timber.i("LifecyclePurgeWorker: feature disabled, skipping")
                    return@withTimeout Result.success()
                }
                when (val r = purge(retentionDays = snapshot.lifecycle.retentionDays)) {
                    is Outcome.Success -> Timber.i("LifecyclePurgeWorker: %d rows purged", r.value)
                    is Outcome.Failure -> Timber.w("LifecyclePurgeWorker: purge failed: %s", r.error)
                    Outcome.Loading    -> Unit
                }
                Result.success()
            }
        } catch (e: TimeoutCancellationException) {
            Timber.w(e, "LifecyclePurgeWorker exceeded %d ms — will retry", MAX_WORK_MS)
            Result.retry()
        }
    }

    private companion object {
        /** 2-minute cap — a single DELETE should complete in milliseconds. */
        const val MAX_WORK_MS: Long = 2L * 60 * 1000
    }
}
