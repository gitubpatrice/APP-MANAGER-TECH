package com.filestech.appmanager.data.system

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds the OS-level Intents the UI must launch on the user's behalf.
 *
 * Lives in `data/system/` because Intent construction depends on the Android
 * platform — domain/UseCases stay pure Kotlin and receive Intents only as
 * opaque return values they hand off to the UI.
 *
 * All intents include `FLAG_ACTIVITY_NEW_TASK` so a non-Activity context
 * (e.g. WorkManager Phase VI) can also start them safely.
 */
@Singleton
class IntentFactory @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /**
     * System uninstall confirmation dialog. The OS shows a confirmation
     * sheet; the user must tap "Uninstall" themselves.
     *
     * Requires `REQUEST_DELETE_PACKAGES` (declared in our manifest).
     */
    fun uninstallIntent(packageName: String): Intent =
        Intent(Intent.ACTION_DELETE, "package:$packageName".toPackageUri())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * OS Settings → App info screen for [packageName]. Used as the cache-clear
     * fallback (no public API to clear another app's cache on non-root) and
     * the enable/disable fallback (system toggle is inside this screen).
     */
    fun appDetailsSettingsIntent(packageName: String): Intent =
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            "package:$packageName".toPackageUri(),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * OS Settings → Usage access screen so the user can grant
     * PACKAGE_USAGE_STATS to App Manager Tech.
     *
     * v0.1.2 — adds `Uri.fromParts("package", ourPackage, null)` so most OEM
     * launchers (Samsung One UI, Pixel, etc.) jump straight to OUR row +
     * scroll the list to it, instead of dumping the user on a 200-app list
     * where they have to scroll forever to find "App Manager Tech".
     *
     * Some OEMs ignore the package URI and still show the full list — there
     * is no API to force per-app navigation. The user complaint that
     * triggered this fix was "il y a trop d'applications c'est la galère" —
     * we cannot add a "select all" on a system screen Google locked down for
     * security reasons (granting Usage Access to every app would let any
     * app read every other app's usage history), but we can at least skip
     * the scroll step on launchers that honour the package URI.
     */
    fun usageAccessSettingsIntent(): Intent =
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            .setData(Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun String.toPackageUri(): Uri = Uri.parse(this)
}
