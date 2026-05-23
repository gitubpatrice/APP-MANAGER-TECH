package com.filestech.appmanager.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Soft-delete staging row — apps the user has moved to the Trash but not yet
 * uninstalled. The actual uninstall is still performed via the system
 * `ACTION_UNINSTALL_PACKAGE` intent (Android non-root has no other way).
 *
 * The trash is purely a *user-facing* review buffer: as long as a row is in
 * `trash_item`, the app stays installed. Calling "Uninstall now" or
 * "Empty trash" launches the system uninstall intent and the row is removed
 * once the package disappears from PackageManager on the next scan.
 *
 * `label` and `total_size_bytes` are snapshotted at the moment the row is
 * inserted so the Trash screen does not need to round-trip PackageManager
 * (the app may have already been uninstalled out-of-band).
 *
 * Schema v3 (Phase X — Trash feature). Migration is additive only.
 */
@Entity(
    tableName = "trash_item",
    indices = [
        Index(value = ["added_at"]),
    ],
)
data class TrashItemEntity(
    @PrimaryKey
    @ColumnInfo(name = "package_name")
    val packageName: String,

    @ColumnInfo(name = "label")
    val label: String,

    @ColumnInfo(name = "total_size_bytes", defaultValue = "0")
    val totalSizeBytes: Long = 0L,

    /** Epoch ms when the user moved the app to the trash. */
    @ColumnInfo(name = "added_at")
    val addedAt: Long,
)
