package com.filestech.appmanager.domain.usecase

import com.filestech.appmanager.core.result.Outcome
import com.filestech.appmanager.domain.repository.AppInfoRepository
import javax.inject.Inject

/**
 * Forces a full rescan of installed packages.
 *
 * Wraps `AppInfoRepository.rescan()` so ViewModels never inject the repository
 * directly for this trivial operation. The rescan is atomic at the Room layer
 * (`@Transaction` on `deleteAll + upsertAll`) so observers of the catalogue
 * flow see exactly one update — either the old snapshot or the new one,
 * never a partial state.
 *
 * Triggered by:
 * - First-launch detection in [com.filestech.appmanager.MainApplication]
 *   when the cache is empty.
 * - The Home screen "Refresh" toolbar button.
 * - Pull-to-refresh gesture on the Home screen.
 * - `PACKAGE_USAGE_STATS` permission becoming granted (re-checked on ON_RESUME).
 */
class RescanAppsUseCase @Inject constructor(
    private val repository: AppInfoRepository,
) {
    suspend operator fun invoke(): Outcome<Unit> = repository.rescan()
}
