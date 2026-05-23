package com.filestech.appmanager.domain.usecase

import android.content.Intent
import com.filestech.appmanager.core.ext.isValidPackageName
import com.filestech.appmanager.core.result.AppError
import com.filestech.appmanager.core.result.Outcome
import com.filestech.appmanager.data.system.IntentFactory
import javax.inject.Inject

/**
 * Returns the Intent the UI must launch to trigger the OS uninstall flow.
 *
 * App Manager Tech itself never calls `startActivity` — that is the UI layer's
 * job. This separation keeps the UseCase pure (no Activity context required)
 * and trivially testable: the test just verifies that the returned Intent has
 * the right action + data URI.
 *
 * The OS will show its own confirmation dialog; we never bypass it.
 */
class UninstallAppUseCase @Inject constructor(
    private val intents: IntentFactory,
) {

    operator fun invoke(packageName: String): Outcome<Intent> {
        if (!packageName.isValidPackageName()) {
            return Outcome.Failure(AppError.Validation("Invalid package name"))
        }
        return Outcome.Success(intents.uninstallIntent(packageName))
    }
}
