package com.filestech.appmanager.domain.usecase

import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.core.content.ContextCompat
import com.filestech.appmanager.core.result.AppError
import com.filestech.appmanager.core.result.Outcome
import com.filestech.appmanager.core.result.runCatchingOutcome
import com.filestech.appmanager.data.system.packagesOfComponentList
import com.filestech.appmanager.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Returns the package names of all apps with at least one enabled
 * accessibility service.
 *
 * Why surface this: accessibility services have broad OS privileges
 * (read screen content, generate touches) and are a common abuse vector for
 * malware impersonating "system optimisers". An audit UX should make
 * accessibility-enabled apps trivial to find.
 *
 * Two sources, joined: the services the system has bound right now, and the ones the user has switched
 * on (`Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES`). A service switched on but not bound — crashed,
 * or in the first seconds after boot — is in the second only, and is enabled all the same: it gets its
 * privileges back at the next bind.
 *
 * No runtime permission required to read either.
 */
class GetAccessibilityServiceAppsUseCase @Inject constructor(
    @ApplicationContext private val appContext: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    suspend operator fun invoke(): Outcome<List<String>> =
        runCatchingOutcome(mapError = { AppError.Unknown(it) }) {
            withContext(io) {
                val bound = ContextCompat.getSystemService(appContext, AccessibilityManager::class.java)
                    ?.getEnabledAccessibilityServiceList(
                        /* feedbackTypeFlags = */
                        android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK,
                    )
                    ?.mapNotNull { it.resolveInfo?.serviceInfo?.packageName }
                    .orEmpty()
                val switchedOn = runCatching {
                    Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                }.getOrNull()?.let(::packagesOfComponentList).orEmpty()
                (bound + switchedOn).distinct()
            }
        }
}
