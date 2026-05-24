package com.filestech.appmanager.data.system

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Inspects user-installed apps for **dangerous-protection** permissions and
 * reports their current grant state.
 *
 * Why "dangerous only"?
 *  - Normal permissions are auto-granted at install and never change → no
 *    drift to detect.
 *  - Signature / system permissions cannot be revoked by the user.
 *  - Special / install permissions (SYSTEM_ALERT_WINDOW, etc.) follow a
 *    different API. Out of scope for v0.2.0 — the dangerous set already
 *    covers Camera / Mic / Location / SMS / Storage / Contacts / etc., which
 *    is what users care about for drift surveillance.
 *
 * Protection-level cache:
 *  - `PackageManager.getPermissionInfo(perm, 0)` is non-trivial (IPC into
 *    system_server). We cache per permission string in a [ConcurrentHashMap]
 *    so a scan of N apps × M permissions costs O(distinct permissions) PM
 *    queries, not O(N × M).
 *
 * Read-only — never writes anything to the OS or to user storage.
 */
@Singleton
class DangerousPermissionInspector @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val pm: PackageManager get() = context.packageManager

    /** Permission → isDangerous. Resolved lazily, cached for the process lifetime. */
    private val isDangerousCache = ConcurrentHashMap<String, Boolean>()

    /**
     * One observation row: an installed app and one of its dangerous permissions
     * with its current grant state.
     */
    data class Observation(
        val packageName: String,
        val permission: String,
        val granted: Boolean,
    )

    /**
     * Returns the dangerous-permission observations for every user-installed,
     * enabled app currently on the device.
     *
     * Excludes:
     *  - System apps (System app drift is uninteresting + would dwarf the feed).
     *  - Disabled apps (their grant state is frozen until re-enabled).
     *  - App Manager itself (self-monitoring is noise).
     *  - Apps that fail to resolve (uninstalled mid-scan, etc.).
     *
     * @param includeSystemApps when true, also report on system apps (for power
     *   users that want full coverage). Default false.
     */
    fun observeAll(includeSystemApps: Boolean = false): List<Observation> {
        val installed = runCatching { allPackages() }.getOrElse { e ->
            Timber.w(e, "DangerousPermissionInspector: failed to list packages")
            return emptyList()
        }

        val ourPackage = context.packageName
        val result = ArrayList<Observation>(installed.size * 4)

        for (pi in installed) {
            val pkg = pi.packageName ?: continue
            if (pkg == ourPackage) continue

            val appInfo = pi.applicationInfo ?: continue
            if (!appInfo.enabled) continue

            val isSystem = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
            if (isSystem && !includeSystemApps) continue

            val requested = pi.requestedPermissions ?: continue
            val flags = pi.requestedPermissionsFlags ?: continue
            // Defensive: array lengths must match — Android guarantees this but
            // a corrupt OEM image could violate it. Skip such packages safely.
            if (flags.size != requested.size) continue

            for (i in requested.indices) {
                val perm = requested[i] ?: continue
                if (!isDangerous(perm)) continue
                val granted = (flags[i] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
                result.add(Observation(packageName = pkg, permission = perm, granted = granted))
            }
        }
        return result
    }

    private fun allPackages(): List<PackageInfo> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledPackages(
                PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
        }
    }

    /**
     * Returns true iff [permission] has protection level DANGEROUS.
     * Unknown permissions (PM throws NameNotFoundException — e.g. a permission
     * declared by an uninstalled app, or a vendor-specific perm) are cached as
     * "not dangerous" → excluded from drift tracking.
     *
     * Visibility: public so [com.filestech.appmanager.domain.usecase.RecordLifecycleEventUseCase]
     * can reuse the process-wide protection-level cache when capturing a
     * lifecycle event's `granted_dangerous_perms` snapshot — re-resolving the
     * level via a fresh `PackageManager.getPermissionInfo` IPC on every
     * broadcast would be wasteful.
     */
    fun isDangerous(permission: String): Boolean {
        isDangerousCache[permission]?.let { return it }
        val protection = runCatching { pm.getPermissionInfo(permission, 0) }
            .getOrNull()
            ?.let { info ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    info.protection
                } else {
                    @Suppress("DEPRECATION")
                    info.protectionLevel and PermissionInfo.PROTECTION_MASK_BASE
                }
            }
            ?: PermissionInfo.PROTECTION_NORMAL
        val dangerous = protection == PermissionInfo.PROTECTION_DANGEROUS
        isDangerousCache[permission] = dangerous
        return dangerous
    }
}
