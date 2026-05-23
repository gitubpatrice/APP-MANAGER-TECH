package com.filestech.appmanager.domain.usecase

import com.filestech.appmanager.core.ext.MS_PER_DAY
import com.filestech.appmanager.core.result.AppError
import com.filestech.appmanager.core.result.Outcome
import com.filestech.appmanager.core.result.runCatchingOutcome
import com.filestech.appmanager.domain.repository.PermissionSnapshotRepository
import timber.log.Timber
import javax.inject.Inject

/**
 * Retention helper for the permission-snapshot history.
 *
 * Called by [com.filestech.appmanager.data.system.workers.PermissionSnapshotWorker]
 * at the end of each capture tick. The default retention is 90 days — old
 * enough to surface "what changed last quarter" but young enough that the
 * table stays small (a few hundred rows even on a busy device).
 *
 * Trade-off: dropping the oldest snapshot for a (pkg, perm) pair can break
 * the "predecessor" invariant used by the drift query. The next capture tick
 * re-establishes a baseline naturally — the only cost is that the next drift
 * event for that pair is one tick late. Acceptable given the 24h cadence.
 */
class PurgeOldPermissionSnapshotsUseCase @Inject constructor(
    private val repository: PermissionSnapshotRepository,
) {

    suspend operator fun invoke(
        retentionDays: Int,
        nowMs: Long = System.currentTimeMillis(),
    ): Outcome<Int> = runCatchingOutcome(mapError = { AppError.Unknown(it) }) {
        if (retentionDays <= 0) {
            // Defensive: a misconfigured 0/negative retention would wipe the table.
            Timber.w("Refusing to purge with retentionDays=%d", retentionDays)
            return@runCatchingOutcome 0
        }
        val cutoff = nowMs - retentionDays.toLong() * MS_PER_DAY
        val dropped = repository.purgeOlderThan(cutoff)
        if (dropped > 0) Timber.i("Purged %d permission snapshots older than %d days", dropped, retentionDays)
        dropped
    }
}
