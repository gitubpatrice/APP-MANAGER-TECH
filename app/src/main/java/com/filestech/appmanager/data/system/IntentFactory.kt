package com.filestech.appmanager.data.system

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.filestech.appmanager.core.ext.isValidPackageName
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
     *
     * v0.2.1 audit M1 fix — defence-in-depth `require` on the packageName.
     * Every existing caller already validates via [isValidPackageName] in the
     * UseCase layer, but a future caller could forget; the require here makes
     * any malformed input fail fast instead of producing a malformed URI that
     * Android would silently mishandle.
     */
    fun uninstallIntent(packageName: String): Intent {
        require(packageName.isValidPackageName()) { "Invalid packageName: $packageName" }
        return Intent(Intent.ACTION_DELETE, "package:$packageName".toPackageUri())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

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
     * Returns an ORDERED chain of intents that deep-link to the OS Permissions
     * UI for [packageName] — caller invokes them in order, catching
     * [android.content.ActivityNotFoundException] on each, falling through to
     * the next until one launches successfully.
     *
     * Why a chain instead of a single intent with a resolveActivity probe?
     *  - Android 11+ package-visibility rules make `resolveActivity` return
     *    null for [Intent.ACTION_MANAGE_APP_PERMISSIONS] even when the
     *    Settings activity exists, unless we declare every action in a
     *    `<queries>` element. Probe → false negatives → silent fallback to
     *    App-info page (one extra tap) at best, no UI at worst.
     *  - With the chain, we just TRY the direct deep-link first; the OS
     *    returns ActivityNotFoundException only if no handler exists — which
     *    is the source of truth.
     *
     * Order:
     *  1. `ACTION_MANAGE_APP_PERMISSIONS` — direct to Permissions sub-page.
     *  2. `ACTION_APPLICATION_DETAILS_SETTINGS` — App-info page (one tap away
     *     from Permissions; always exists on Android).
     */
    fun appPermissionsSettingsChain(packageName: String): List<Intent> {
        val candidates = mutableListOf<Intent>()
        val pm = context.packageManager

        // Resolve at RUNTIME every activity that declares an intent filter
        // for ACTION_MANAGE_APP_PERMISSIONS on THIS device. We bind each
        // discovered activity by explicit component so the launch is
        // unambiguous (no chooser, no resolveActivity false negatives).
        //
        // The activity class name varies across OEMs and Android versions
        // (AOSP `com.android.permissioncontroller…`, Samsung One UI 7
        //  variants, Google `com.google.android.permissioncontroller…`).
        // A runtime scan is the only way to stay vendor-agnostic and
        // future-proof.
        val probe = Intent("android.intent.action.MANAGE_APP_PERMISSIONS")
        val handlers = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(probe, PackageManager.ResolveInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(probe, 0)
            }
        }.getOrElse { emptyList() }

        for (handler in handlers) {
            // Try each handler twice: once with EXTRA_PACKAGE_NAME, once with
            // data URI — both forms are seen in the wild depending on the
            // permission-controller implementation.
            candidates.add(
                Intent(probe.action).apply {
                    setClassName(handler.activityInfo.packageName, handler.activityInfo.name)
                    putExtra("android.intent.extra.PACKAGE_NAME", packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
            candidates.add(
                Intent(probe.action).apply {
                    setClassName(handler.activityInfo.packageName, handler.activityInfo.name)
                    data = Uri.fromParts("package", packageName, null)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
        }

        // Also try the implicit form last (no explicit component) — covers
        // future Android versions where queryIntentActivities is restricted
        // but resolveActivity at startActivity time still picks a winner.
        candidates.add(
            Intent("android.intent.action.MANAGE_APP_PERMISSIONS").apply {
                putExtra("android.intent.extra.PACKAGE_NAME", packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )

        // Final fallback — App Info page (always exists).
        candidates.add(appDetailsSettingsIntent(packageName))

        return candidates
    }

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

    /**
     * OS Settings → Security screen, from where the user can drill into
     * "Device admin apps" and disable individual administrators. Android
     * does not let third-party apps revoke device-admin programmatically
     * (signature/system permission), so opening this screen is the closest
     * we can do.
     */
    fun deviceAdminSettingsIntent(): Intent =
        Intent(Settings.ACTION_SECURITY_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * OS Settings → Accessibility, listing every app holding an
     * AccessibilityService binding. The user can disable individual ones
     * from there.
     */
    fun accessibilitySettingsIntent(): Intent =
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun String.toPackageUri(): Uri = Uri.parse(this)
}
