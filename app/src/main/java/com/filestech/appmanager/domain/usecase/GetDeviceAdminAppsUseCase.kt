package com.filestech.appmanager.domain.usecase

import android.app.admin.DevicePolicyManager
import android.content.Context
import androidx.core.content.ContextCompat
import com.filestech.appmanager.core.result.AppError
import com.filestech.appmanager.core.result.Outcome
import com.filestech.appmanager.core.result.runCatchingOutcome
import com.filestech.appmanager.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Returns the list of package names that are currently registered as device
 * administrators (`DevicePolicyManager.getActiveAdmins`).
 *
 * Why surface this: device-admin apps can block their own uninstall, force
 * the user to factory-reset to remove them; an audit UX should make them
 * trivial to find.
 *
 * Non-root, no special permission required to read the list.
 */
class GetDeviceAdminAppsUseCase @Inject constructor(
    @ApplicationContext private val appContext: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    suspend operator fun invoke(): Outcome<List<String>> =
        runCatchingOutcome(mapError = { AppError.Unknown(it) }) {
            withContext(io) {
                val dpm = ContextCompat.getSystemService(appContext, DevicePolicyManager::class.java)
                    ?: return@withContext emptyList()
                dpm.activeAdmins?.map { it.packageName }?.distinct().orEmpty()
            }
        }
}
