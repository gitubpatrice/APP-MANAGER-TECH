package com.filestech.appmanager.domain.usecase

import com.filestech.appmanager.core.ext.MS_PER_DAY
import com.filestech.appmanager.core.result.Outcome
import com.filestech.appmanager.domain.model.PermissionDrift
import com.filestech.appmanager.domain.repository.AppInfoRepository
import com.filestech.appmanager.domain.repository.PermissionSnapshotRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import com.filestech.appmanager.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Inject

/**
 * Hot stream of [PermissionDrift] for the UI's chronological feed.
 *
 * Time window is expressed in days (UX picker offers 30 / 90 / all). The
 * "all" option is encoded as [Long.MAX_VALUE] day count → cutoff = 0 →
 * everything in the table.
 *
 * Each drift's `change` field is derived directly from `granted`:
 *  - granted = true  → [PermissionDrift.Change.GAINED] (was false, now true)
 *  - granted = false → [PermissionDrift.Change.LOST]   (was true, now false)
 *
 * The previous state doesn't need a separate query because
 * [com.filestech.appmanager.domain.repository.PermissionSnapshotRepository.observeDriftRows]
 * only returns rows that have a prior snapshot, and the capture use case only
 * inserts on change.
 *
 * App label enrichment is a best-effort lookup in [AppInfoRepository]'s cache.
 * If the app was uninstalled, the label is null and the UI renders the package
 * name as fallback.
 */
class ObservePermissionDriftsUseCase @Inject constructor(
    private val snapshotRepository: PermissionSnapshotRepository,
    private val appInfoRepository: AppInfoRepository,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    operator fun invoke(windowDays: Long, nowMs: Long = System.currentTimeMillis()): Flow<List<PermissionDrift>> {
        val cutoff = if (windowDays >= Int.MAX_VALUE.toLong()) 0L else nowMs - windowDays * MS_PER_DAY
        return snapshotRepository.observeDriftRows(cutoff)
            .map { rows ->
                // Resolve labels in batch to avoid one cache lookup per row.
                val packageNames = rows.map { it.packageName }.toSet()
                val labels = resolveLabels(packageNames)
                rows.map { row ->
                    PermissionDrift(
                        packageName = row.packageName,
                        appLabel    = labels[row.packageName],
                        permission  = row.permission,
                        change      = if (row.granted) PermissionDrift.Change.GAINED
                                      else PermissionDrift.Change.LOST,
                        whenMs      = row.capturedAt,
                    )
                }
            }
            .flowOn(io)
    }

    private suspend fun resolveLabels(packageNames: Set<String>): Map<String, String?> {
        val map = HashMap<String, String?>(packageNames.size)
        for (pkg in packageNames) {
            map[pkg] = when (val r = appInfoRepository.getApp(pkg)) {
                is Outcome.Success -> r.value.label
                else               -> null
            }
        }
        return map
    }
}

/**
 * Convenience flow constructor for "no apps yet, but still show empty list"
 * UX consistency. Avoids forcing the ViewModel to fork a separate flow when
 * the cutoff is zero.
 */
internal fun emptyDriftFlow(): Flow<List<PermissionDrift>> = flow { emit(emptyList()) }
