package com.filestech.appmanager.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * v0.3.0 (schema v5) — one historical lifecycle event for one package.
 *
 * Schema choices:
 *  - Append-only: rows are inserted, never updated. The capture path lives in
 *    [com.filestech.appmanager.domain.usecase.RecordLifecycleEventUseCase].
 *  - `type` stored as TEXT (enum name). Forward-compat decoding falls back to
 *    BASELINE when the running app does not know the persisted value (e.g.
 *    downgrade after we add a new variant).
 *  - `granted_dangerous_perms` stored as a `|`-separated string for compactness
 *    (no Room TypeConverter dep). NULL or empty string = no permission. The
 *    `|` separator is invalid inside an Android permission constant so the
 *    encoding is unambiguous.
 *  - `user_reason` stored as enum name TEXT, NULL when the user dismissed the
 *    reason dialog or the prompt is disabled in Settings.
 *
 * Indices:
 *  - `(package_name, captured_at)` — composite, supports per-app timeline
 *    queries + the `latestByPackage` probe.
 *  - `captured_at` — supports global timeline + retention purge
 *    (`DELETE WHERE captured_at < cutoff`).
 *  - `type` — speeds up "all UNINSTALLED events" filters in the UI window.
 */
@Entity(
    tableName = "app_lifecycle_event",
    indices = [
        Index(value = ["package_name", "captured_at"]),
        Index(value = ["captured_at"]),
        Index(value = ["type"]),
    ],
)
data class AppLifecycleEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(name = "package_name")
    val packageName: String,

    /** Cached app label at capture time. NULL if PM could not resolve it. */
    @ColumnInfo(name = "label")
    val label: String?,

    /** Enum name from [com.filestech.appmanager.domain.model.LifecycleEventType]. */
    @ColumnInfo(name = "type")
    val type: String,

    @ColumnInfo(name = "captured_at")
    val capturedAt: Long,

    @ColumnInfo(name = "version_name")
    val versionName: String?,

    @ColumnInfo(name = "version_code")
    val versionCode: Long,

    @ColumnInfo(name = "installer_package")
    val installerPackage: String?,

    @ColumnInfo(name = "total_size_bytes", defaultValue = "0")
    val totalSizeBytes: Long,

    /**
     * Pipe-separated list of granted dangerous permissions at capture time.
     * Empty string means no dangerous permission was granted. NULL only on
     * older rows where the capture skipped the perm probe (BASELINE before
     * v0.3.0.x revisit).
     */
    @ColumnInfo(name = "granted_dangerous_perms")
    val grantedDangerousPerms: String?,

    /** Enum name from [com.filestech.appmanager.domain.model.UninstallReason], or NULL. */
    @ColumnInfo(name = "user_reason")
    val userReason: String?,

    /**
     * v0.3.4 — Hex SHA-256 of the base APK at capture time
     * (`MessageDigest.getInstance("SHA-256")` over the file at
     * `ApplicationInfo.sourceDir`). NULL when :
     *  - the row is from a v0.3.3 or earlier release (the column didn't exist),
     *  - the row is UNINSTALLED (the APK is gone by then),
     *  - reading / hashing the APK failed (IO or SecurityException —
     *    swallowed by the use case so the audit row is still written).
     *
     * Powers the tamper-detection UX : when a REPLACED row shares the
     * same `versionCode` as the prior event for the same package BUT a
     * different `apk_sha256`, the lifecycle history surfaces a red
     * "possible repackage / sideload swap" badge.
     */
    @ColumnInfo(name = "apk_sha256")
    val apkSha256: String?,
)
