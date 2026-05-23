package com.filestech.appmanager.domain.usecase

import com.filestech.appmanager.core.ext.isValidPackageName
import com.filestech.appmanager.core.result.AppError
import com.filestech.appmanager.core.result.Outcome
import com.filestech.appmanager.domain.repository.AppInfoRepository
import javax.inject.Inject

/**
 * Best-effort force-stop of background processes for an installed app.
 *
 * NOTE: `ActivityManager.killBackgroundProcesses` (the only public non-root API)
 * silently no-ops when the target is in the foreground or running a foreground
 * service. There is no workaround on stock Android. UI should communicate that
 * "force stop only works for apps already in background" so users aren't
 * surprised when a visible app remains running.
 *
 * Requires `KILL_BACKGROUND_PROCESSES` (declared in the manifest, normal-level).
 *
 * Phase VIII C4 fix: defence-in-depth validation of [packageName] at the
 * UseCase boundary, in addition to the repository's own check.
 */
class ForceStopAppUseCase @Inject constructor(
    private val repository: AppInfoRepository,
) {

    suspend operator fun invoke(packageName: String): Outcome<Unit> {
        if (!packageName.isValidPackageName()) {
            return Outcome.Failure(AppError.Validation("Invalid package name"))
        }
        return repository.forceStop(packageName)
    }
}
