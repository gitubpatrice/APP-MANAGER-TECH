package com.filestech.appmanager.domain.usecase

import android.content.Context
import android.view.accessibility.AccessibilityManager
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
 * Returns the package names of all apps with at least one enabled
 * accessibility service.
 *
 * Why surface this: accessibility services have broad OS privileges
 * (read screen content, generate touches) and are a common abuse vector for
 * malware impersonating "system optimisers". An audit UX should make
 * accessibility-enabled apps trivial to find.
 *
 * No runtime permission required to read the list.
 */
class GetAccessibilityServiceAppsUseCase @Inject constructor(
    @ApplicationContext private val appContext: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    suspend operator fun invoke(): Outcome<List<String>> =
        runCatchingOutcome(mapError = { AppError.Unknown(it) }) {
            withContext(io) {
                val am = ContextCompat.getSystemService(
                    appContext,
                    AccessibilityManager::class.java,
                ) ?: return@withContext emptyList()
                am.getEnabledAccessibilityServiceList(
                    /* feedbackTypeFlags = */ android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK,
                )
                    .mapNotNull { it.resolveInfo?.serviceInfo?.packageName }
                    .distinct()
            }
        }
}
