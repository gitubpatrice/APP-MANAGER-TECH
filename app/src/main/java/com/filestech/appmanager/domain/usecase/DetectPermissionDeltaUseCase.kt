package com.filestech.appmanager.domain.usecase

import com.filestech.appmanager.core.ext.isValidPackageName
import com.filestech.appmanager.core.result.AppError
import com.filestech.appmanager.core.result.Outcome
import com.filestech.appmanager.core.result.getOrNull
import com.filestech.appmanager.core.result.runCatchingOutcome
import com.filestech.appmanager.di.IoDispatcher
import com.filestech.appmanager.domain.model.LifecycleEvent
import com.filestech.appmanager.domain.model.LifecycleEventType
import com.filestech.appmanager.domain.model.PermissionDelta
import com.filestech.appmanager.domain.repository.AppLifecycleRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * v0.3.2 — For a REPLACED lifecycle event, surface which dangerous permissions
 * the new version GAINED relative to the previous baseline.
 *
 * Source of truth: the append-only `app_lifecycle_event` log. The previous
 * baseline is the latest event of type [LifecycleEventType.INSTALLED],
 * [LifecycleEventType.REPLACED] or [LifecycleEventType.BASELINE] recorded
 * before [event]. We compare the granted-dangerous-perms snapshots
 * captured by [RecordLifecycleEventUseCase] (no live PM call — pure history
 * arithmetic).
 *
 * Returns:
 * - [PermissionDelta] with `gained` = perms in the new snapshot but not in
 *   the baseline. Sorted, short-name-friendly.
 * - `null` when [event] is not a REPLACED row, or when no usable baseline
 *   exists yet (first event recorded — no comparison possible).
 *
 * Pure domain — zero Android dependency. Trivially testable.
 */
class DetectPermissionDeltaUseCase @Inject constructor(
    @IoDispatcher private val io: CoroutineDispatcher,
    private val lifecycleRepository: AppLifecycleRepository,
) {

    suspend operator fun invoke(event: LifecycleEvent): Outcome<PermissionDelta?> {
        if (event.type != LifecycleEventType.REPLACED) {
            return Outcome.Success(null)
        }
        if (!event.packageName.isValidPackageName()) {
            return Outcome.Failure(AppError.Validation("Invalid package name"))
        }
        return runCatchingOutcome(mapError = { AppError.Unknown(it) }) {
            withContext(io) {
                // A history that cannot be read is not an empty one: "no comparison" would hide a gained
                // permission. The failure goes up instead.
                val history = lifecycleRepository.observeByPackage(event.packageName)
                    .first { it !is Outcome.Loading }
                    .getOrNull()
                    ?: error("Lifecycle history of ${event.packageName} unreadable")
                val baseline = findBaseline(history = history, current = event)
                    ?: return@withContext null

                val before = baseline.grantedDangerousPermissions.toSet()
                val after  = event.grantedDangerousPermissions.toSet()
                val gained = (after - before).sorted()
                PermissionDelta(
                    packageName = event.packageName,
                    eventId     = event.id,
                    gained      = gained,
                )
            }
        }
    }

    /**
     * Picks the latest lifecycle event for [current]'s package recorded BEFORE it and of a type that
     * carries a perm snapshot (BASELINE / INSTALLED / REPLACED). Returns null if no such ancestor exists
     * — the first REPLACED event has nothing to compare against.
     *
     * "Before" is the insertion order, read from the auto-generated id: the wall clock behind
     * `capturedAt` can go back (a manual or time-zone change) and two events can share a millisecond,
     * so ordering by it could skip the real ancestor.
     */
    private fun findBaseline(
        history: List<LifecycleEvent>,
        current: LifecycleEvent,
    ): LifecycleEvent? = history
        .filter { ev -> ev.id < current.id && ev.type in SNAPSHOT_TYPES }
        .maxByOrNull { it.id }

    private companion object {
        val SNAPSHOT_TYPES = setOf(
            LifecycleEventType.BASELINE,
            LifecycleEventType.INSTALLED,
            LifecycleEventType.REPLACED,
        )
    }
}
