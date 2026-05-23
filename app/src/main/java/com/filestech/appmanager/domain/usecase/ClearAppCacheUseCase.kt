package com.filestech.appmanager.domain.usecase

import android.content.Intent
import com.filestech.appmanager.core.ext.isValidPackageName
import com.filestech.appmanager.core.result.AppError
import com.filestech.appmanager.core.result.Outcome
import com.filestech.appmanager.data.system.IntentFactory
import javax.inject.Inject

/**
 * Returns the Intent the UI must launch to take the user to the OS app-info
 * screen where they can tap "Clear cache".
 *
 * Why no silent path: non-root Android exposes no public API to clear another
 * app's cache. `StorageManager.allocateBytes` / `freeStorageAndNotify` reserve
 * storage but do not wipe specific apps' caches. The legitimate path is to
 * defer to the OS UI; this UseCase encodes that contract in one place.
 *
 * App Manager Tech NEVER calls `startActivity` — UI layer does.
 */
class ClearAppCacheUseCase @Inject constructor(
    private val intents: IntentFactory,
) {

    operator fun invoke(packageName: String): Outcome<Intent> {
        if (!packageName.isValidPackageName()) {
            return Outcome.Failure(AppError.Validation("Invalid package name"))
        }
        return Outcome.Success(intents.appDetailsSettingsIntent(packageName))
    }
}
