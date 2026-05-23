package com.filestech.appmanager.domain.usecase

import com.filestech.appmanager.core.result.Outcome
import com.filestech.appmanager.domain.model.StorageReport
import com.filestech.appmanager.domain.repository.AppInfoRepository
import javax.inject.Inject

/**
 * Builds a [StorageReport] from the current cache snapshot.
 *
 * Pure delegation to the repository — kept as a UseCase so the ViewModel layer
 * has a stable API and so tests can mock at the UseCase boundary.
 *
 * Phase VII C2 fix: added [forceRescan] so callers (StorageViewModel) no
 * longer need to inject the repository directly to refresh. Setting it
 * triggers a `repository.rescan()` before reading the report.
 */
class AnalyzeStorageUseCase @Inject constructor(
    private val repository: AppInfoRepository,
) {

    /**
     * @param includeSystemApps include system apps in totals and top-N.
     * @param topN              how many apps to surface in topByTotalSize / topByCacheSize.
     *                          `0` returns aggregate sums only with empty top-N lists.
     * @param forceRescan       when true, trigger a full `repository.rescan()` before
     *                          reading the report. Use after a noticeable install /
     *                          uninstall event.
     */
    suspend operator fun invoke(
        includeSystemApps: Boolean = false,
        topN: Int = 10,
        forceRescan: Boolean = false,
    ): Outcome<StorageReport> {
        if (forceRescan) {
            when (val rescan = repository.rescan()) {
                is Outcome.Failure -> return rescan
                else               -> Unit
            }
        }
        return repository.getStorageReport(includeSystemApps, topN)
    }
}
