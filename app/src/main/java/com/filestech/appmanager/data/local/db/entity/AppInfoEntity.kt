package com.filestech.appmanager.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity caching the package list scanned from PackageManager +
 * StorageStatsManager + UsageStatsManager.
 *
 * Single PK = packageName (unique by definition on a device).
 *
 * Schema history (mirror the database version bumps in `AppDatabase`):
 * - v1 (2026-05-23): initial schema (15 cols).
 * - v2 (2026-05-23): adds `installer_package`, `apk_source_dir` (both TEXT NULLABLE).
 *
 * Indices:
 * - `is_system_app` — speeds up the user-vs-system filter on the apps screen.
 * - `last_used_time` — speeds up the "rarely used" / sort-by-last-used query.
 * - `installer_package` — speeds up Phase VI "filter by installer source" UX.
 */
@Entity(
    tableName = "app_info",
    indices = [
        Index(value = ["is_system_app"]),
        Index(value = ["last_used_time"]),
        Index(value = ["installer_package"]),
    ],
)
data class AppInfoEntity(
    @PrimaryKey
    @ColumnInfo(name = "package_name")
    val packageName: String,

    @ColumnInfo(name = "label")
    val label: String,

    @ColumnInfo(name = "version_name")
    val versionName: String,

    @ColumnInfo(name = "version_code")
    val versionCode: Long,

    @ColumnInfo(name = "install_size_bytes")
    val installSizeBytes: Long,

    @ColumnInfo(name = "cache_size_bytes")
    val cacheSizeBytes: Long,

    @ColumnInfo(name = "data_size_bytes")
    val dataSizeBytes: Long,

    @ColumnInfo(name = "first_install_time")
    val firstInstallTime: Long,

    @ColumnInfo(name = "last_update_time")
    val lastUpdateTime: Long,

    @ColumnInfo(name = "last_used_time", defaultValue = "0")
    val lastUsedTime: Long = 0L,

    @ColumnInfo(name = "is_system_app")
    val isSystemApp: Boolean,

    @ColumnInfo(name = "is_uninstallable")
    val isUninstallable: Boolean,

    @ColumnInfo(name = "is_enabled", defaultValue = "1")
    val isEnabled: Boolean = true,

    /** Stored as the enum name (TEXT) — survives reorderings of the enum class. */
    @ColumnInfo(name = "category", defaultValue = "'UNDEFINED'")
    val category: String = "UNDEFINED",

    /** Epoch ms when this row was last refreshed from the OS. Used for cache TTL. */
    @ColumnInfo(name = "cached_at")
    val cachedAt: Long,

    // ----- v2 (Phase II.J SD Maid enrichments) -----

    /**
     * Package name of the installer (Play Store, F-Droid, sideload). NULLABLE —
     * sideloaded apps and some OEM pre-installs report no installer.
     */
    @ColumnInfo(name = "installer_package")
    val installerPackage: String? = null,

    /**
     * Absolute path to the base APK on disk (ApplicationInfo.sourceDir).
     * NULLABLE — apps with shared UIDs may report null.
     */
    @ColumnInfo(name = "apk_source_dir")
    val apkSourceDir: String? = null,

    // ----- v6 (v0.3.2 — OS hibernation status surface) -----

    /**
     * v0.3.2 — true when the OS reports the app as inactive / hibernated
     * (`UsageStatsManager.isAppInactive(pkg)`, API 23+). `defaultValue = "0"`
     * for backward-compat on migrated rows: the next scan repopulates the
     * real state.
     */
    @ColumnInfo(name = "is_hibernated", defaultValue = "0")
    val isHibernated: Boolean = false,
)
