package com.filestech.appmanager.domain.usecase

import com.filestech.appmanager.core.result.Outcome
import com.filestech.appmanager.core.result.map
import com.filestech.appmanager.domain.model.AppSortOrder
import com.filestech.appmanager.domain.model.AppInfo
import com.filestech.appmanager.domain.model.FilterOptions
import com.filestech.appmanager.domain.model.InstallerFilter
import com.filestech.appmanager.domain.repository.AppInfoRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Returns the filtered + sorted list of installed apps as a hot flow.
 *
 * Filtering happens in the data layer (Room + in-memory predicate); sorting
 * is applied here (domain layer) so the repository stays close to SQL and
 * the UseCase owns presentational concerns.
 *
 * Why sorting in the UseCase: the same repository flow is consumed by
 * different screens with different default orderings (list = NAME_ASC,
 * storage = SIZE_DESC). Pushing the sort into the repo would force a
 * coupling between Room queries and UI concerns.
 */
class GetInstalledAppsUseCase @Inject constructor(
    private val repository: AppInfoRepository,
) {

    /**
     * @param filter What to keep (system/user, categories, installer, size envelope, etc.)
     * @param sortOrder How to order the resulting list.
     */
    operator fun invoke(
        filter: FilterOptions = FilterOptions.DEFAULT,
        sortOrder: AppSortOrder = AppSortOrder.NAME_ASC,
    ): Flow<Outcome<List<AppInfo>>> {
        // Fast path when no enrichment-only filter is requested.
        val source = if (isPlainFilter(filter)) {
            repository.observeApps(includeSystemApps = filter.includeSystemApps)
        } else {
            repository.observeFiltered(filter)
        }
        // VII C3 fix: use Flow.map directly instead of wrapping in `flow { collect { emit } }`
        // — that wrapper masks the upstream flowOn(io) and pushes the sort onto the collector
        // dispatcher (Main in the ViewModel).
        return source.map { outcome -> outcome.map { it.sort(sortOrder) } }
    }

    private fun isPlainFilter(filter: FilterOptions): Boolean =
        filter.includeUserApps &&
            filter.includeDisabled &&
            filter.categories.isEmpty() &&
            filter.installerFilter == InstallerFilter.ANY && // VII M-1: use enum equality
            filter.minSizeBytes == 0L &&
            filter.maxSizeBytes == Long.MAX_VALUE &&
            filter.unusedSinceDays == null &&
            filter.requiredPermissions.isEmpty()

    private fun List<AppInfo>.sort(order: AppSortOrder): List<AppInfo> = when (order) {
        AppSortOrder.NAME_ASC          -> sortedBy { it.label.lowercase() }
        AppSortOrder.NAME_DESC         -> sortedByDescending { it.label.lowercase() }
        AppSortOrder.SIZE_DESC         -> sortedByDescending { it.totalSizeBytes }
        AppSortOrder.LAST_USED_DESC    -> sortedByDescending { it.lastUsedTime }
        AppSortOrder.INSTALL_DATE_DESC -> sortedByDescending { it.firstInstallTime }
    }
}
