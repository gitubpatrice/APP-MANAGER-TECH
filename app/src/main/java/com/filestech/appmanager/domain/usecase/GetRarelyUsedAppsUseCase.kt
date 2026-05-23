package com.filestech.appmanager.domain.usecase

import com.filestech.appmanager.core.result.Outcome
import com.filestech.appmanager.core.result.map
import com.filestech.appmanager.domain.model.AppInfo
import com.filestech.appmanager.domain.model.FilterOptions
import com.filestech.appmanager.domain.repository.AppInfoRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Returns apps not used for at least [thresholdDays] days, sorted by
 * `lastUsedTime` ascending (oldest first — most likely uninstall candidate).
 *
 * Apps never opened (lastUsedTime == 0) come first.
 */
class GetRarelyUsedAppsUseCase @Inject constructor(
    private val repository: AppInfoRepository,
) {

    operator fun invoke(
        thresholdDays: Int = DEFAULT_THRESHOLD_DAYS,
    ): Flow<Outcome<List<AppInfo>>> {
        val filter = FilterOptions(
            includeSystemApps = false,
            unusedSinceDays   = thresholdDays,
        )
        // VII C3 fix: direct .map preserves upstream flowOn(io) — see GetInstalledAppsUseCase.
        return repository.observeFiltered(filter).map { outcome ->
            outcome.map { apps -> apps.sortedBy { it.lastUsedTime } }
        }
    }

    private companion object {
        const val DEFAULT_THRESHOLD_DAYS = 30
    }
}
