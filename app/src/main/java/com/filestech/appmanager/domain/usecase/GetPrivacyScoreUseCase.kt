package com.filestech.appmanager.domain.usecase

import com.filestech.appmanager.core.ext.isValidPackageName
import com.filestech.appmanager.core.result.AppError
import com.filestech.appmanager.core.result.Outcome
import com.filestech.appmanager.domain.model.AppInfo
import com.filestech.appmanager.domain.model.PermissionCapability
import com.filestech.appmanager.domain.model.PrivacyScore
import com.filestech.appmanager.domain.model.PrivacyTier
import com.filestech.appmanager.domain.repository.AppInfoRepository
import javax.inject.Inject

/**
 * Computes the [PrivacyScore] of an installed app from its declared permissions, the permissions
 * guarding its own components, and its install source.
 *
 * Pure-static analysis — no network, no behavioural inspection. Cheap (two PackageManager probes per
 * call), so the AppDetail screen calls it lazily without blocking.
 *
 * **App Manager Tech innovation**: collapses the "look at my permissions" UX into a single comparable
 * number per app.
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
        // Need the AppInfo (installer, system flag), the requested permissions and the component guards.
        // A list that cannot be read is not an empty one: scored as empty, any app would read as having
        // a minimal footprint. The failure goes up instead, and the screen shows no score.
        val infoR = repository.getApp(packageName)
        if (infoR is Outcome.Failure) return infoR
        val permsR = repository.getRequestedPermissions(packageName)
        if (permsR is Outcome.Failure) return permsR
        val guardsR = repository.getComponentPermissions(packageName)
        if (guardsR is Outcome.Failure) return guardsR

        return Outcome.Success(
            score(
                info           = (infoR as Outcome.Success).value,
                perms          = (permsR as Outcome.Success).value,
                componentPerms = (guardsR as Outcome.Success).value,
            ),
        )
    }

    // -----------------------------------------------------------------------
    // Heuristic
    // -----------------------------------------------------------------------

    internal fun score(info: AppInfo, perms: List<String>, componentPerms: Set<String>): PrivacyScore {
        var value = PrivacyScore.MAX_SCORE
        val deductions = mutableListOf<String>()

        // Each weight is written once: the label shown to the user is built from the same constant as
        // the deduction, so the two cannot drift apart.
        fun deduct(points: Int, reason: String) {
            value -= points
            deductions += "−$points : $reason"
        }

        // One penalty per capability the dangerous permissions give, not per permission: READ and
        // WRITE_CONTACTS, or precise and approximate location, are one access.
        val groups = perms.mapNotNull { PermissionCapability.capabilityOf(it)?.scoreGroup }.toSet()
        if (groups.isNotEmpty()) {
            deduct(groups.size * PENALTY_PER_DANGEROUS_GROUP, "${groups.size} dangerous permission group(s)")
        }
        if (MANAGE_EXTERNAL_STORAGE in perms) deduct(PENALTY_ALL_FILES, "access to all files")

        // INTERNET — broad signal even though it's normal-level
        if ("android.permission.INTERNET" in perms) deduct(PENALTY_INTERNET, "declares INTERNET")
        if ("android.permission.FOREGROUND_SERVICE" in perms) {
            deduct(PENALTY_FOREGROUND_SERVICE, "runs foreground services")
        }
        if ("android.permission.SYSTEM_ALERT_WINDOW" in perms) deduct(PENALTY_OVERLAY, "can draw over other apps")

        // An app does not request BIND_DEVICE_ADMIN or BIND_ACCESSIBILITY_SERVICE: it puts them on its
        // receiver or service as android:permission, so that only the system can bind to it.
        val deviceAdmin = BIND_DEVICE_ADMIN in componentPerms
        val accessibility = BIND_ACCESSIBILITY_SERVICE in componentPerms
        if (deviceAdmin) deduct(PENALTY_DEVICE_ADMIN, "device admin")
        if (accessibility) deduct(PENALTY_ACCESSIBILITY, "accessibility service")

        // Installer-based adjustment
        installSourceAdjustment(info)?.let { (points, reason) ->
            if (points > 0) {
                value += points
                deductions += "+$points : $reason"
            } else {
                deduct(-points, reason)
            }
        }

        val clamped = value.coerceIn(PrivacyScore.MIN_SCORE, PrivacyScore.MAX_SCORE)
        return PrivacyScore(
            packageName                  = info.packageName,
            value                        = clamped,
            tier                         = PrivacyTier.of(clamped),
            deductions                   = deductions.toList(),
            declaresAccessibilityService = accessibility,
            declaresDeviceAdmin          = deviceAdmin,
        )
    }

    /** What the install source adds to the score, as (signed points, reason), or null when nothing. */
    private fun installSourceAdjustment(info: AppInfo): Pair<Int, String>? {
        val installer = info.installerPackage
        return when {
            installer in FDROID_CLIENTS -> BONUS_VETTED_SOURCE to "F-Droid client"
            // Preinstalled by the maker: no install source to judge.
            installer == null && info.isSystemApp -> null
            installer == null || installer in PACKAGE_INSTALLERS ->
                -PENALTY_SIDELOADED to "sideloaded (unknown origin)"
            installer in MAINSTREAM_STORES -> null
            else -> -PENALTY_NON_STANDARD_INSTALLER to "non-standard installer ($installer)"
        }
    }

    private companion object {
        // The weights of the heuristic, out of PrivacyScore.MAX_SCORE.
        const val PENALTY_PER_DANGEROUS_GROUP = 5
        const val PENALTY_ALL_FILES = 5
        const val PENALTY_INTERNET = 10
        const val PENALTY_FOREGROUND_SERVICE = 10
        const val PENALTY_OVERLAY = 15
        const val PENALTY_DEVICE_ADMIN = 20
        const val PENALTY_ACCESSIBILITY = 20
        const val BONUS_VETTED_SOURCE = 5
        const val PENALTY_SIDELOADED = 10
        const val PENALTY_NON_STANDARD_INSTALLER = 15

        const val MANAGE_EXTERNAL_STORAGE = "android.permission.MANAGE_EXTERNAL_STORAGE"
        const val BIND_DEVICE_ADMIN = "android.permission.BIND_DEVICE_ADMIN"
        const val BIND_ACCESSIBILITY_SERVICE = "android.permission.BIND_ACCESSIBILITY_SERVICE"

        /** F-Droid and the clients that install from F-Droid repositories. */
        val FDROID_CLIENTS: Set<String> = setOf(
            "org.fdroid.fdroid",
            "org.fdroid.fdroid.privileged",
            "org.fdroid.basic",
            "com.looker.droidify",
            "com.machiav3lli.fdroid",
        )

        /** Play, its open-source front-end Aurora, and the stores phone makers ship. */
        val MAINSTREAM_STORES: Set<String> = setOf(
            "com.android.vending",
            "com.aurora.store",
            "com.aurora.services",
            "com.sec.android.app.samsungapps",
            "com.huawei.appmarket",
            "com.amazon.venezia",
            "com.xiaomi.mipicks",
        )

        /** What installs an APK file by hand: the system package installer, or adb. */
        val PACKAGE_INSTALLERS: Set<String> = setOf(
            "com.google.android.packageinstaller",
            "com.android.packageinstaller",
            "com.android.shell",
        )
    }
}
