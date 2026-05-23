package com.filestech.appmanager.domain.usecase

import com.filestech.appmanager.core.result.AppError
import com.filestech.appmanager.core.result.Outcome
import com.filestech.appmanager.core.result.runCatchingOutcome
import com.filestech.appmanager.data.system.DangerousPermissionInspector
import com.filestech.appmanager.domain.model.PermissionSnapshot
import com.filestech.appmanager.domain.repository.PermissionSnapshotRepository
import timber.log.Timber
import javax.inject.Inject

/**
 * Captures the current dangerous-permission state of every (user-) installed
 * app and persists ONLY the changed (pkg, perm) pairs — see
 * [com.filestech.appmanager.data.local.db.entity.PermissionSnapshotEntity]
 * for the insert-on-change strategy that keeps the table tiny.
 *
 * Returns a [CaptureResult] that distinguishes **baselines** (first-ever
 * snapshot for a (pkg, perm) pair — NOT a drift) from **drifts** (snapshot
 * for a pair that already had a row, with a different `granted` value).
 *
 * UX consequence: on the very first capture for the device, EVERY observation
 * is a baseline (latest == null for all pairs). The drift feed correctly
 * shows nothing — the snackbar must report this as "baseline captured", not
 * as "N changes detected", or users see the count + an empty feed and
 * conclude the feature is broken (v0.2.0 day-one feedback).
 *
 * Called by:
 *  - [com.filestech.appmanager.data.system.workers.PermissionSnapshotWorker]
 *    on its periodic tick (24h cadence by default).
 *  - Manual "Capturer maintenant" button in the Drift screen.
 *
 * Performance:
 *  - The PM enumeration runs in one pass via [DangerousPermissionInspector]
 *    (one IPC into system_server per distinct permission string, cached).
 *  - DB writes are interleaved with `getLatest` reads but each pair touches
 *    at most one row — overall O(observations) IO.
 */
class CapturePermissionSnapshotsUseCase @Inject constructor(
    private val inspector: DangerousPermissionInspector,
    private val repository: PermissionSnapshotRepository,
) {

    /**
     * @property baselines first-ever snapshot rows inserted for a (pkg, perm)
     *   pair. These are NOT drifts — they establish the reference state.
     * @property drifts inserted rows that flipped `granted` vs a prior row.
     *   These ARE the drift events the user cares about.
     */
    data class CaptureResult(val baselines: Int, val drifts: Int) {
        val totalInserted: Int get() = baselines + drifts
        val isFirstCapture: Boolean get() = baselines > 0 && drifts == 0
    }

    suspend operator fun invoke(
        includeSystemApps: Boolean = false,
        nowMs: Long = System.currentTimeMillis(),
    ): Outcome<CaptureResult> = runCatchingOutcome(mapError = { AppError.Unknown(it) }) {
        val observations = inspector.observeAll(includeSystemApps = includeSystemApps)
        var baselines = 0
        var drifts = 0
        for (obs in observations) {
            val latest = repository.getLatest(obs.packageName, obs.permission)
            if (latest == null) {
                repository.insert(snapshot(obs, nowMs))
                baselines++
            } else if (latest.granted != obs.granted) {
                repository.insert(snapshot(obs, nowMs))
                drifts++
            }
        }
        Timber.i(
            "Permission snapshot capture: observed=%d baselines=%d drifts=%d (includeSystemApps=%b)",
            observations.size,
            baselines,
            drifts,
            includeSystemApps,
        )
        CaptureResult(baselines = baselines, drifts = drifts)
    }

    private fun snapshot(obs: DangerousPermissionInspector.Observation, nowMs: Long): PermissionSnapshot =
        PermissionSnapshot(
            packageName = obs.packageName,
            permission  = obs.permission,
            granted     = obs.granted,
            capturedAt  = nowMs,
        )
}
