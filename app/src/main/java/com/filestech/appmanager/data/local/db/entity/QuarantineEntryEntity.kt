package com.filestech.appmanager.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One active or expired quarantine entry — represents an app the user has
 * temporarily set aside, with a scheduled "review by" date ([restoreAt]).
 *
 * Schema v4 (v0.2.0 — App Quarantine feature).
 *
 * Two modes (see [com.filestech.appmanager.domain.model.QuarantineMode]):
 *  - **HARD_UNINSTALL**: APK backed up to a user-picked SAF tree, then OS
 *    uninstall intent fired. Data is lost (Android limitation — non-root has
 *    no API to disable a user-installed app). Restore = re-install from the
 *    backup APK via [Intent.ACTION_VIEW] on PackageInstaller.
 *  - **SOFT_REMINDER**: no uninstall. The user disables/freezes the app
 *    manually in OS Settings; we just track + fire a notification at
 *    [restoreAt] so they can decide whether to re-enable.
 *
 * `notified` flips to true once the expiry notif fires, so we never double-notify.
 *
 * Primary key is `package_name` (one active entry per package max — re-
 * quarantining the same package via [com.filestech.appmanager.data.repository.QuarantineRepositoryImpl.upsert]
 * replaces the previous entry, refreshing the deadline).
 *
 * Index on `restore_at` powers the worker's expired-rows query.
 *
 * Privacy: stored in the app's private Room DB only.
 */
@Entity(
    tableName = "quarantine_entry",
    indices = [
        Index(value = ["restore_at"]),
    ],
)
data class QuarantineEntryEntity(
    @PrimaryKey
    @ColumnInfo(name = "package_name")
    val packageName: String,

    /** Snapshot of the app label at quarantine time (the app may be uninstalled later). */
    @ColumnInfo(name = "label")
    val label: String,

    /** Persisted name of [com.filestech.appmanager.domain.model.QuarantineMode]. */
    @ColumnInfo(name = "mode")
    val mode: String,

    @ColumnInfo(name = "quarantined_at")
    val quarantinedAt: Long,

    /** Epoch ms — when the entry expires and the user should be reminded / app restored. */
    @ColumnInfo(name = "restore_at")
    val restoreAt: Long,

    @ColumnInfo(name = "version_name")
    val versionName: String?,

    @ColumnInfo(name = "version_code")
    val versionCode: Long,

    /**
     * SAF document URI of the backup APK (HARD mode only — null for SOFT).
     * The user picked a persistable URI via `OpenDocumentTree` in Settings;
     * we hold a child-document URI under that tree.
     */
    @ColumnInfo(name = "apk_backup_uri")
    val apkBackupUri: String?,

    @ColumnInfo(name = "auto_restore_enabled", defaultValue = "1")
    val autoRestoreEnabled: Boolean = true,

    /** Set once by the [com.filestech.appmanager.data.system.workers.QuarantineRestoreWorker] to avoid re-firing the expiry notification. */
    @ColumnInfo(name = "notified", defaultValue = "0")
    val notified: Boolean = false,
)
