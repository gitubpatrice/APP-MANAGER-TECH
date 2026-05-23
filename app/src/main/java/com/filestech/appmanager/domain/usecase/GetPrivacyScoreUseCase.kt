package com.filestech.appmanager.domain.usecase

import com.filestech.appmanager.core.ext.isValidPackageName
import com.filestech.appmanager.core.result.AppError
import com.filestech.appmanager.core.result.Outcome
import com.filestech.appmanager.domain.model.AppInfo
import com.filestech.appmanager.domain.model.PrivacyScore
import com.filestech.appmanager.domain.model.PrivacyTier
import com.filestech.appmanager.domain.repository.AppInfoRepository
import javax.inject.Inject

/**
 * Computes the [PrivacyScore] of an installed app from its declared
 * permissions and install source.
 *
 * Pure-static analysis — no network, no behavioural inspection. Designed
 * to be cheap (one PackageManager probe per call) so it can be called
 * lazily from the AppDetail screen without blocking.
 *
 * **App Manager Tech innovation**: collapses the "look at my permissions"
 * UX into a single comparable number per app. Sort by privacy score to
 * see the top 5 most-intrusive apps on your device in one tap.
 *
 * Heuristic — see [PrivacyScore] doc.
 */
class GetPrivacyScoreUseCase @Inject constructor(
    private val repository: AppInfoRepository,
) {

    suspend operator fun invoke(packageName: String): Outcome<PrivacyScore> {
        if (!packageName.isValidPackageName()) {
            return Outcome.Failure(AppError.Validation("Invalid package name"))
        }
        // Need both the requested-permissions list AND the AppInfo (for installer).
        val infoR = repository.getApp(packageName)
        if (infoR is Outcome.Failure) return infoR
        val info = (infoR as Outcome.Success).value

        val permsR = repository.getRequestedPermissions(packageName)
        val perms = (permsR as? Outcome.Success)?.value.orEmpty()

        return Outcome.Success(score(info, perms))
    }

    /**
     * Bulk variant — accepts pre-loaded [apps] and a per-package permission
     * provider. Used by `SortByPrivacy` in AppListScreen without N round-trips.
     */
    suspend fun scoreMany(
        apps: List<AppInfo>,
        permissionsOf: suspend (String) -> List<String>,
    ): List<PrivacyScore> = apps.map { app ->
        score(app, permissionsOf(app.packageName))
    }

    // -----------------------------------------------------------------------
    // Heuristic
    // -----------------------------------------------------------------------

    private fun score(info: AppInfo, perms: List<String>): PrivacyScore {
        var value = PrivacyScore.MAX_SCORE
        val deductions = mutableListOf<String>()

        // Count dangerous permissions
        val dangerousCount = perms.count { it in DANGEROUS_PERMISSIONS }
        if (dangerousCount > 0) {
            val drop = dangerousCount * 5
            value -= drop
            deductions += "−$drop : $dangerousCount dangerous permission(s)"
        }

        // INTERNET — broad signal even though it's normal-level
        if ("android.permission.INTERNET" in perms) {
            value -= 10
            deductions += "−10 : declares INTERNET"
        }
        if ("android.permission.FOREGROUND_SERVICE" in perms) {
            value -= 10
            deductions += "−10 : runs foreground services"
        }
        if ("android.permission.SYSTEM_ALERT_WINDOW" in perms) {
            value -= 15
            deductions += "−15 : can draw over other apps"
        }
        if (perms.any { it in DEVICE_ADMIN_PERMS }) {
            value -= 20
            deductions += "−20 : device admin"
        }
        if (perms.any { it in ACCESSIBILITY_PERMS }) {
            value -= 20
            deductions += "−20 : accessibility service"
        }

        // Installer-based adjustments
        when (info.installerPackage) {
            "org.fdroid.fdroid", "org.fdroid.fdroid.privileged" -> {
                value += 5 // vetted source bonus
                deductions += "+5 : F-Droid (vetted)"
            }
            null -> {
                value -= 10
                deductions += "−10 : sideloaded (unknown origin)"
            }
            "com.android.vending",
            "com.aurora.store",
            "com.aurora.services" -> {
                // No adjustment — Play / Aurora are mainstream stores
            }
            else -> {
                value -= 15
                deductions += "−15 : non-standard installer (${info.installerPackage})"
            }
        }

        val clamped = value.coerceIn(PrivacyScore.MIN_SCORE, PrivacyScore.MAX_SCORE)
        return PrivacyScore(
            packageName = info.packageName,
            value       = clamped,
            tier        = PrivacyTier.of(clamped),
            deductions  = deductions.toList(),
        )
    }

    private companion object {
        // Most common dangerous permissions (Android 14). Used for the dangerousCount
        // signal. Not exhaustive but covers ~95% of typical app exposure.
        val DANGEROUS_PERMISSIONS: Set<String> = setOf(
            "android.permission.CAMERA",
            "android.permission.RECORD_AUDIO",
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_COARSE_LOCATION",
            "android.permission.ACCESS_BACKGROUND_LOCATION",
            "android.permission.READ_CONTACTS",
            "android.permission.WRITE_CONTACTS",
            "android.permission.READ_CALENDAR",
            "android.permission.WRITE_CALENDAR",
            "android.permission.READ_SMS",
            "android.permission.SEND_SMS",
            "android.permission.RECEIVE_SMS",
            "android.permission.READ_CALL_LOG",
            "android.permission.WRITE_CALL_LOG",
            "android.permission.READ_PHONE_STATE",
            "android.permission.READ_PHONE_NUMBERS",
            "android.permission.CALL_PHONE",
            "android.permission.ANSWER_PHONE_CALLS",
            "android.permission.PROCESS_OUTGOING_CALLS",
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE",
            "android.permission.MANAGE_EXTERNAL_STORAGE",
            "android.permission.READ_MEDIA_IMAGES",
            "android.permission.READ_MEDIA_VIDEO",
            "android.permission.READ_MEDIA_AUDIO",
            "android.permission.ACTIVITY_RECOGNITION",
            "android.permission.BODY_SENSORS",
            "android.permission.BLUETOOTH_CONNECT",
            "android.permission.BLUETOOTH_SCAN",
            "android.permission.NEARBY_WIFI_DEVICES",
            "android.permission.POST_NOTIFICATIONS",
            "android.permission.USE_BIOMETRIC",
        )

        val DEVICE_ADMIN_PERMS: Set<String> = setOf(
            "android.permission.BIND_DEVICE_ADMIN",
        )

        val ACCESSIBILITY_PERMS: Set<String> = setOf(
            "android.permission.BIND_ACCESSIBILITY_SERVICE",
        )
    }
}
