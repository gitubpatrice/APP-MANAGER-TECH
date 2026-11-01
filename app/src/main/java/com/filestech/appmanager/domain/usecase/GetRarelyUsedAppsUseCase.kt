package com.filestech.appmanager.domain.usecase

import com.filestech.appmanager.core.ext.MS_PER_DAY
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
 * Apps never opened (lastUsedTime == 0) come first, if installed before the threshold. The last use is
 * read live ([AppInfoRepository.getLastUseTimes]), as on the Zombies screen and in the cleaner, never
 * from the cache, which a scan made without usage access left at 0. Without usage access every app reads
 * "never used": the list is empty rather than false. Apps that work without being opened
 * ([ActiveRoleApps]) are left out.
 */
class GetRarelyUsedAppsUseCase @Inject constructor(
    private val repository: AppInfoRepository,
    private val activeRoles: ActiveRoleApps,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    operator fun invoke(
        thresholdDays: Int = DEFAULT_THRESHOLD_DAYS,
    ): Flow<Outcome<List<AppInfo>>> {
        // User apps, enabled ones, as the list always showed; the "unused since" criterion is applied
        // below, on the live usage.
        val filter = FilterOptions(includeSystemApps = false)
        // The access probe, the usage query and the role reads are IPCs: this map runs on io.
        return repository.observeFiltered(filter).map { outcome ->
            // No usage access: empty for want of data — but a failure upstream stays a failure.
            if (!repository.hasUsageStatsAccess()) return@map outcome.map { emptyList() }
            val lastUse = repository.getLastUseTimes()
            val inUse = activeRoles.packages()
            val cutoff = System.currentTimeMillis() - thresholdDays * MS_PER_DAY
            outcome.map { apps ->
                apps.asSequence()
                    .filter { it.packageName !in inUse }
                    .map { it.copy(lastUsedTime = lastUse[it.packageName] ?: 0L) }
                    .filter { isUnusedSince(it, cutoff) }
                    .sortedBy { it.lastUsedTime }
                    .toList()
            }
        }.flowOn(io)
    }

    private companion object {
        const val DEFAULT_THRESHOLD_DAYS = 30
    }
}

/**
 * Not used since [cutoff]: last used before it, or never used and installed before it — an app installed
 * yesterday has not gone unused for a month.
 */
internal fun isUnusedSince(app: AppInfo, cutoff: Long): Boolean =
    if (app.lastUsedTime == 0L) app.firstInstallTime <= cutoff else app.lastUsedTime < cutoff
