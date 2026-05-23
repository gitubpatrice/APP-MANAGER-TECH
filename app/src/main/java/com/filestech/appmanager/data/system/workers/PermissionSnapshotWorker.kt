package com.filestech.appmanager.data.system.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.filestech.appmanager.core.result.Outcome
import com.filestech.appmanager.data.local.datastore.SettingsRepository
import com.filestech.appmanager.data.system.NotificationHelper
import com.filestech.appmanager.domain.usecase.CapturePermissionSnapshotsUseCase
import com.filestech.appmanager.domain.usecase.PurgeOldPermissionSnapshotsUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import timber.log.Timber

/**
 * Periodic worker that takes a permission snapshot of every installed app and
 * fires a "permission changed" notification if any drift was detected.
 *
 * Cadence: 24h via [com.filestech.appmanager.data.system.WorkScheduler.applyPermissionSnapshotTracking].
 *
 * Failure model:
 *  - Hard cap via `withTimeout(MAX_WORK_MS)` — if some misbehaving OEM blocks
 *    `getInstalledPackages` indefinitely, we don't hang the worker forever.
 *  - On timeout → [Result.retry] (WorkManager exponential backoff).
 *  - Capture failure → [Result.retry] (transient — retry the next tick).
 *  - Notification failure → swallowed by `notifyIfPermitted` → worker still
 *    returns success since the data was captured.
 *
 * F-Droid: no GMS / FCM — local WorkManager only.
 */
@HiltWorker
class PermissionSnapshotWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val capture: CapturePermissionSnapshotsUseCase,
    private val purge: PurgeOldPermissionSnapshotsUseCase,
    private val settings: SettingsRepository,
    private val notifications: NotificationHelper,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        Timber.i("PermissionSnapshotWorker tick")
        return try {
            withTimeout(MAX_WORK_MS) {
                val snapshot = settings.flow.first()
                val privacy = snapshot.privacyMonitor

                // Defensive: if the feature was disabled between scheduling
                // and this tick, succeed silently rather than do work. The
                // unique-work cancel call in WorkScheduler is authoritative
                // but we double-check here for race-free behaviour.
                if (!privacy.permissionDriftEnabled) {
                    Timber.i("PermissionSnapshotWorker: feature disabled, skipping")
                    return@withTimeout Result.success()
                }

                val captureResult = capture(
                    includeSystemApps = privacy.permissionDriftIncludeSystemApps,
                )
                val drifts = when (captureResult) {
                    is Outcome.Success -> captureResult.value.drifts
                    is Outcome.Failure -> {
                        Timber.w("Capture failed: %s — will retry", captureResult.error)
                        return@withTimeout Result.retry()
                    }
                    Outcome.Loading    -> 0
                }

                // Retention — best-effort, never block on failure.
                runCatching { purge(retentionDays = privacy.permissionDriftRetentionDays) }
                    .onFailure { Timber.w(it, "Purge failed (non-fatal)") }

                // Only fire notif on real drifts — never on first-capture
                // baselines (which would otherwise spam the user with a
                // "300 permission changes" notif the very first time the
                // worker runs after they enable the feature).
                if (drifts > 0 && privacy.permissionDriftNotify) {
                    notifications.postPermissionDriftSummary(drifts)
                }

                Result.success()
            }
        } catch (e: TimeoutCancellationException) {
            Timber.w(e, "PermissionSnapshotWorker exceeded %d ms — will retry", MAX_WORK_MS)
            Result.retry()
        }
    }

    private companion object {
        /** 8-minute cap (Android 14+ enforces 10 min; we stay conservative). */
        const val MAX_WORK_MS: Long = 8L * 60 * 1000
    }
}
