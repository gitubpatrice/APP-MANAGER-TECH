package com.filestech.appmanager.domain.usecase

import com.filestech.appmanager.core.ext.isValidPackageName
import com.filestech.appmanager.core.result.AppError
import com.filestech.appmanager.core.result.Outcome
import com.filestech.appmanager.core.result.flatMap
import com.filestech.appmanager.core.result.getOrElse
import com.filestech.appmanager.domain.model.AppDetail
import com.filestech.appmanager.domain.repository.AppInfoRepository
import javax.inject.Inject

/**
 * Builds the full [AppDetail] payload for one package: cached [AppInfo] plus
 * live-from-PackageManager permission lists.
 *
 * Permission queries are best-effort — if they fail (uninstalled mid-flight,
 * privilege error, etc.) we return an empty list rather than failing the
 * whole detail call. The base [AppInfo] is the load-bearing part; permissions
 * are a "nice to have" detail-screen enrichment.
 *
 * Phase VIII C4 fix: defence-in-depth validation of [packageName] at the
 * UseCase boundary, in addition to the repository's own check.
 */
class GetAppDetailUseCase @Inject constructor(
    private val repository: AppInfoRepository,
) {

    suspend operator fun invoke(packageName: String): Outcome<AppDetail> {
        if (!packageName.isValidPackageName()) {
            return Outcome.Failure(AppError.Validation("Invalid package name"))
        }
        return repository.getApp(packageName).flatMap { info ->
            val requested = repository.getRequestedPermissions(packageName)
                .getOrElse { emptyList() }
            val granted = repository.getGrantedPermissions(packageName)
                .getOrElse { emptyList() }
            Outcome.Success(
                AppDetail(
                    info                 = info,
                    requestedPermissions = requested,
                    grantedPermissions   = granted,
                ),
            )
        }
    }
}
