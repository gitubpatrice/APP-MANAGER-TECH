package com.filestech.appmanager.domain.usecase

import com.filestech.appmanager.core.ext.isValidPackageName
import com.filestech.appmanager.core.result.AppError
import com.filestech.appmanager.core.result.Outcome
import com.filestech.appmanager.domain.model.ExpertReport
import com.filestech.appmanager.domain.repository.AppInfoRepository
import javax.inject.Inject

/**
 * v0.2.2 — Builds the Expert Mode payload for a single package.
 *
 * Thin wrapper around [AppInfoRepository.getExpertReport]: keeps the UseCase
 * boundary so the ViewModel never imports the repository contract directly
 * (consistent with [GetAppDetailUseCase] + the rest of the codebase).
 *
 * Defence-in-depth validation of [packageName] mirrors [GetAppDetailUseCase] —
 * the repo also validates, the UseCase does too, so callers can't slip a
 * non-validated value past the boundary even if the repo signature changes.
 *
 * **Behaviour vs Android version / root status:**
 * - PackageManager component queries always succeed on non-root.
 * - Native ABI (`primaryCpuAbi`) is read by reflection — null on stripped
 *   ApplicationInfo (apps with shared UIDs, system_server proxies, etc.).
 * - App-ops are best-effort: most ops are gated by `GET_APP_OPS_STATS`
 *   (signature|privileged) so the OS reports `MODE_DEFAULT` for non-root
 *   callers. The report carries `isFullyAccessible = false` in that case
 *   and the UI renders "—" instead of misleading "Denied".
 */
class GetExpertReportUseCase @Inject constructor(
    private val repository: AppInfoRepository,
) {

    suspend operator fun invoke(packageName: String): Outcome<ExpertReport> {
        if (!packageName.isValidPackageName()) {
            return Outcome.Failure(AppError.Validation("Invalid package name"))
        }
        return repository.getExpertReport(packageName)
    }
}
