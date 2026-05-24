package com.filestech.appmanager.domain.model

/**
 * Pure domain model representing an installed Android application.
 *
 * No Android/Room/DataStore dependencies — safe to use in unit tests without
 * Robolectric or instrumentation.
 *
 * Phase II: populate via PackageManager + StorageStatsManager in the repository layer.
 */
data class AppInfo(
    /** Android package name, e.g. "com.filestech.appmanager". */
    val packageName: String,
    /** Human-readable label from PackageManager. */
    val label: String,
    /** Version string from PackageInfo.versionName. */
    val versionName: String,
    /** Version code from PackageInfo.longVersionCode. */
    val versionCode: Long,
    /** Total installed size in bytes (APK + lib + dex). */
    val installSizeBytes: Long,
    /** Cache size in bytes (from StorageStatsManager). */
    val cacheSizeBytes: Long,
    /** Data size in bytes (from StorageStatsManager). */
    val dataSizeBytes: Long,
    /** First install timestamp (epoch ms from PackageInfo.firstInstallTime). */
    val firstInstallTime: Long,
    /** Last update timestamp (epoch ms from PackageInfo.lastUpdateTime). */
    val lastUpdateTime: Long,
    /** Last used timestamp (epoch ms from UsageStats, 0 if unavailable). */
    val lastUsedTime: Long = 0L,
    /** True if this is a system app (ApplicationInfo.FLAG_SYSTEM set). */
    val isSystemApp: Boolean,
    /** True if the app can be uninstalled by the user. */
    val isUninstallable: Boolean,
    /** True if the app is currently enabled. System apps can be disabled but not uninstalled. */
    val isEnabled: Boolean = true,
    /** Category reported by ApplicationInfo.category, mapped to our enum. */
    val category: AppCategory = AppCategory.UNDEFINED,
    /**
     * Package name of the installer (e.g. `com.android.vending`, `org.fdroid.fdroid`,
     * `com.aurora.store`). `null` for sideloaded apps or when the OS does not record
     * the installer. Useful for provenance audit ("where did this app come from?").
     */
    val installerPackage: String? = null,
    /**
     * Absolute path to the app's base APK on the device filesystem
     * (`ApplicationInfo.sourceDir`). Used by Phase VI APK-extract features.
     * `null` for apps with shared UIDs that report no own sourceDir.
     */
    val apkSourceDir: String? = null,
    /**
     * v0.3.2 — true when the OS reports the app as inactive / hibernated.
     * Backed by `UsageStatsManager.isAppInactive(pkg)` (API 23+) — fires after
     * Android's adaptive battery decides the app stops receiving alarms /
     * jobs / network. Useful signal for the Zombies / RarelyUsed UX: a
     * hibernated app is the OS's own version of "this is dead weight".
     *
     * Defaults to `false` so existing tests and code paths that build [AppInfo]
     * manually don't need to pass the field. Populated by the repository
     * scan when PACKAGE_USAGE_STATS is granted; otherwise stays `false` (the
     * UsageStatsAccessBanner already nudges the user to grant it).
     */
    val isHibernated: Boolean = false,
) {
    /**
     * Total footprint = install + data + cache.
     * Derived getter — NOT persisted; do not annotate as a Room @ColumnInfo
     * field on the matching entity. Excluded from equals/hashCode by design.
     */
    val totalSizeBytes: Long get() = installSizeBytes + dataSizeBytes + cacheSizeBytes
}
