package com.filestech.appmanager.domain.usecase

import android.content.Intent
import com.filestech.appmanager.core.ext.isValidPackageName
import com.filestech.appmanager.core.result.AppError
import com.filestech.appmanager.core.result.Outcome
import com.filestech.appmanager.core.result.onFailure
import com.filestech.appmanager.data.system.IntentFactory
import com.filestech.appmanager.domain.repository.AppInfoRepository
import timber.log.Timber
import javax.inject.Inject

/**
 * Toggles enabled state of an installed app.
 *
 * Result shape:
 * - `Outcome.Success(Result.Done)` — silent toggle worked (rooted device or
 *   privileged app). Cache row is updated.
 * - `Outcome.Success(Result.NeedsUserAction(intent))` — silent toggle failed
 *   with [AppError.Permission] (the usual case on stock Android). The Intent
 *   opens OS Settings → App info where the user can flip the toggle manually.
 * - `Outcome.Failure(error)` — validation / other error.
 */
class DisableEnableAppUseCase @Inject constructor(
    private val repository: AppInfoRepository,
    private val intents: IntentFactory,
) {

    sealed interface Result {
        data object Done : Result
        data class NeedsUserAction(val intent: Intent) : Result
    }

    suspend operator fun invoke(packageName: String, enabled: Boolean): Outcome<Result> {
        if (!packageName.isValidPackageName()) {
            return Outcome.Failure(AppError.Validation("Invalid package name"))
        }
        return when (val attempt = repository.setEnabled(packageName, enabled)) {
            is Outcome.Success -> Outcome.Success(Result.Done)
            is Outcome.Failure -> when (attempt.error) {
                is AppError.Permission -> {
                    Timber.d(
                        "setEnabled(%s, %s) requires privileged permission; falling back to Settings",
                        packageName,
                        enabled,
                    )
                    Outcome.Success(
                        Result.NeedsUserAction(intents.appDetailsSettingsIntent(packageName)),
                    )
                }
                else -> attempt
            }
            Outcome.Loading -> Outcome.Loading
        }.onFailure { Timber.w("setEnabled(%s) failed: %s", packageName, it) }
    }
}
