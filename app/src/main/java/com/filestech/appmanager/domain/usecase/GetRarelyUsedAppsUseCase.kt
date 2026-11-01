package com.filestech.appmanager.domain.usecase

import com.filestech.appmanager.core.result.Outcome
import com.filestech.appmanager.core.result.map
import com.filestech.appmanager.data.system.ActiveRoleApps
import com.filestech.appmanager.di.IoDispatcher
import com.filestech.appmanager.domain.model.AppInfo
import com.filestech.appmanager.domain.model.FilterOptions
import com.filestech.appmanager.domain.repository.AppInfoRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Returns apps not used for at least [thresholdDays] days, sorted by
 * `lastUsedTime` ascending (oldest first — most likely uninstall candidate).
 *
 * Apps never opened (lastUsedTime == 0) come first, if installed before the threshold. Without usage
 * access every app reads "never used": the list is empty rather than false. Apps that work without being
 * opened ([ActiveRoleApps]) are left out.
 */
class GetRarelyUsedAppsUseCase @Inject constructor(
    private val repository: AppInfoRepository,
    private val activeRoles: ActiveRoleApps,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    operator fun invoke(
        thresholdDays: Int = DEFAULT_THRESHOLD_DAYS,
    ): Flow<Outcome<List<AppInfo>>> {
        val filter = FilterOptions(
            includeSystemApps = false,
            unusedSinceDays   = thresholdDays,
        )
        // The access probe and the role reads are IPCs: this map runs on io, like the query upstream.
        return repository.observeFiltered(filter).map { outcome ->
            // No usage access: empty for want of data — but a failure upstream stays a failure.
            if (!repository.hasUsageStatsAccess()) return@map outcome.map { emptyList() }
            val inUse = activeRoles.packages()
            outcome.map { apps -> apps.filter { it.packageName !in inUse }.sortedBy { it.lastUsedTime } }
        }.flowOn(io)
    }

    private companion object {
        const val DEFAULT_THRESHOLD_DAYS = 30
    }
}
