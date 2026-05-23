package com.filestech.appmanager.domain.usecase

import com.filestech.appmanager.core.ext.isValidPackageName
import com.filestech.appmanager.core.result.AppError
import com.filestech.appmanager.core.result.Outcome
import com.filestech.appmanager.domain.model.AppInfo
import com.filestech.appmanager.domain.model.TrashItem
import com.filestech.appmanager.domain.repository.AppInfoRepository
import com.filestech.appmanager.domain.repository.TrashRepository
import javax.inject.Inject

/**
 * Stages an app into the Corbeille / Trash. The app stays installed — this
 * only records the user's intent so they can review and confirm later.
 *
 * Snapshots `label` and `totalSizeBytes` at insert time so the Trash screen
 * does not need to round-trip PackageManager (the app may have already been
 * uninstalled out-of-band between trashing and listing).
 *
 * Two call paths:
 *  - From AppDetail (we already have the cached [AppInfo]) — pass it in to
 *    skip the cache lookup.
 *  - From a list / one-shot caller — pass the package name only and let the
 *    use case fetch the cached info itself.
 */
class MoveAppToTrashUseCase @Inject constructor(
    private val trash: TrashRepository,
    private val appInfo: AppInfoRepository,
) {

    suspend operator fun invoke(packageName: String): Outcome<TrashItem> {
        if (!packageName.isValidPackageName()) {
            return Outcome.Failure(AppError.Validation("Invalid package name"))
        }
        val info = when (val r = appInfo.getApp(packageName)) {
            is Outcome.Success -> r.value
            is Outcome.Failure -> return Outcome.Failure(r.error)
            Outcome.Loading    -> return Outcome.Failure(AppError.Validation("Cache not ready for $packageName"))
        }
        return invoke(info)
    }

    suspend operator fun invoke(info: AppInfo): Outcome<TrashItem> {
        val item = TrashItem(
            packageName    = info.packageName,
            label          = info.label,
            totalSizeBytes = info.totalSizeBytes,
            addedAt        = System.currentTimeMillis(),
        )
        return try {
            trash.moveToTrash(item)
            Outcome.Success(item)
        } catch (e: Exception) {
            // Room may throw SQLiteFullException / SQLiteAbortException etc.
            // Wrap so the UI gets a typed error instead of crashing viewModelScope.
            Outcome.Failure(AppError.DatabaseError(e))
        }
    }
}
