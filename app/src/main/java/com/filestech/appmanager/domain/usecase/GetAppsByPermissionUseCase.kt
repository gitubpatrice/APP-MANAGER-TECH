package com.filestech.appmanager.domain.usecase

import com.filestech.appmanager.core.result.AppError
import com.filestech.appmanager.core.result.Outcome
import com.filestech.appmanager.domain.model.AppInfo
import com.filestech.appmanager.domain.repository.AppInfoRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Returns the subset of apps that declare [permission] in their manifest.
 *
 * Slow path: one PackageManager probe per app. Acceptable for a one-off
 * filter view (Settings → "Apps that ask for CAMERA"); not suitable for a
 * hot Flow on every list scroll.
 *
 * @param onlyGranted if true, keep only apps that have ACTUALLY been granted
 *                    the permission at the OS level (vs merely declared).
 */
class GetAppsByPermissionUseCase @Inject constructor(
    private val repository: AppInfoRepository,
) {

    suspend operator fun invoke(
        permission: String,
        onlyGranted: Boolean = false,
        includeSystemApps: Boolean = false,
    ): Outcome<List<AppInfo>> {
        if (permission.isBlank()) {
            return Outcome.Failure(AppError.Validation("permission name is required"))
        }

        // Drain the first non-Loading emission of the catalogue.
        // Phase VIII L-3 fix: idiomatic `when` instead of nested `as?` cascade.
        val source = repository.observeApps(includeSystemApps)
        val apps = when (val first = source.first { it !is Outcome.Loading }) {
            is Outcome.Success -> first.value
            is Outcome.Failure -> return first
            Outcome.Loading    -> return Outcome.Success(emptyList()) // unreachable
        }

        val matching = apps.mapNotNull { app ->
            val perms = if (onlyGranted) {
                repository.getGrantedPermissions(app.packageName)
            } else {
                repository.getRequestedPermissions(app.packageName)
            }
            when (perms) {
                is Outcome.Success -> if (permission in perms.value) app else null
                else               -> null // skip apps that fail probe
            }
        }
        return Outcome.Success(matching)
    }
}
