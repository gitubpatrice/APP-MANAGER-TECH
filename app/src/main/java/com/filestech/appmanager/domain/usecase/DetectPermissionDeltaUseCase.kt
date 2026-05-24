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
 * [LifecycleEventType.REPLACED] or [LifecycleEventType.BASELINE] STRICTLY
 * older than [event]. We compare the granted-dangerous-perms snapshots
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
                val history = lifecycleRepository.observeByPackage(event.packageName).first()
                    .getOrNull()
                    .orEmpty()
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
     * Picks the most recent lifecycle event for [current]'s package that is
     * STRICTLY older AND of a type that carries a perm snapshot (BASELINE /
     * INSTALLED / REPLACED). Returns null if no such ancestor exists — the
     * REPLACED event sits at index 0 with nothing to compare against.
     *
     * The history is delivered newest-first by the DAO; we scan in order.
     */
    private fun findBaseline(
        history: List<LifecycleEvent>,
        current: LifecycleEvent,
    ): LifecycleEvent? = history
        .asSequence()
        .filter { it.id != current.id }
        .filter { it.capturedAt < current.capturedAt }
        .firstOrNull { ev ->
            ev.type == LifecycleEventType.BASELINE ||
                ev.type == LifecycleEventType.INSTALLED ||
                ev.type == LifecycleEventType.REPLACED
        }
}
