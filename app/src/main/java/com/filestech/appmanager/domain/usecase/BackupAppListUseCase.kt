package com.filestech.appmanager.domain.usecase

import com.filestech.appmanager.core.result.AppError
import com.filestech.appmanager.core.result.Outcome
import com.filestech.appmanager.core.result.runCatchingOutcome
import com.filestech.appmanager.domain.model.AppSummary
import com.filestech.appmanager.domain.model.BackupSnapshot
import com.filestech.appmanager.domain.repository.AppInfoRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Builds a compact [BackupSnapshot] of the current installed-apps catalogue
 * — meant to be serialised to JSON by an `ExportReportUseCase` consumer and
 * saved (Storage Access Framework, share intent, etc.) so the user can
 * restore the list on a new device.
 *
 * The "restore" UX is intentionally manual: there is no public API for an
 * app to install another app silently on non-root Android. The backup
 * therefore captures enough metadata (label, package, version, install
 * source) for the user to find each app on its respective store.
 */
class BackupAppListUseCase @Inject constructor(
    private val repository: AppInfoRepository,
) {

    suspend operator fun invoke(
        includeSystemApps: Boolean = false,
    ): Outcome<BackupSnapshot> = runCatchingOutcome(mapError = { AppError.Unknown(it) }) {
        val first = repository.observeApps(includeSystemApps).first { it !is Outcome.Loading }
        val apps = (first as? Outcome.Success)?.value.orEmpty()
        BackupSnapshot(
            createdAt = System.currentTimeMillis(),
            apps      = apps.map { it.toSummary() },
        )
    }

    private fun com.filestech.appmanager.domain.model.AppInfo.toSummary(): AppSummary = AppSummary(
        packageName       = packageName,
        label             = label,
        versionName       = versionName,
        versionCode       = versionCode,
        installerPackage  = installerPackage,
        installSizeBytes  = installSizeBytes,
        dataSizeBytes     = dataSizeBytes,
        cacheSizeBytes    = cacheSizeBytes,
        firstInstallTime  = firstInstallTime,
        lastUsedTime      = lastUsedTime,
        isSystemApp       = isSystemApp,
    )
}
