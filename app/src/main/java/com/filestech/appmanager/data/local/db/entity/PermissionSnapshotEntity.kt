package com.filestech.appmanager.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One historical snapshot row for a single (package, permission) pair.
 *
 * Schema v4 (v0.2.0 — Permission Drift Tracker feature).
 *
 * Storage strategy: **insert only on change**. The [com.filestech.appmanager.domain.usecase.CapturePermissionSnapshotsUseCase]
 * fetches the latest row for each (packageName, permission) before deciding to
 * insert. If `granted` matches the latest snapshot, the insert is skipped. This
 * keeps the table tiny — only true change events accumulate, not daily
 * baselines.
 *
 * Drift detection (cf. [com.filestech.appmanager.data.local.db.dao.PermissionSnapshotDao.observeDrifts])
 * exploits this property: every row that has a predecessor for its (pkg, perm)
 * pair is, by construction, a drift event.
 *
 * Indices:
 *  - `(package_name, captured_at)` — composite index speeds up both the
 *    "latest snapshot for this pkg/perm" probe and the drift JOIN.
 *  - `captured_at` alone — supports retention purge `WHERE captured_at < cutoff`.
 *
 * Privacy: stored in the app's private Room DB only — never leaves the device,
 * never sent over the network (no INTERNET permission in this app).
 */
@Entity(
    tableName = "permission_snapshot",
    indices = [
        Index(value = ["package_name", "captured_at"]),
        Index(value = ["captured_at"]),
    ],
)
data class PermissionSnapshotEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(name = "package_name")
    val packageName: String,

    /** Android permission constant, e.g. `android.permission.CAMERA`. */
    @ColumnInfo(name = "permission")
    val permission: String,

    /** True iff the permission was granted at [capturedAt]. */
    @ColumnInfo(name = "granted")
    val granted: Boolean,

    /** Epoch ms — set by the capture use case, not by SQLite. */
    @ColumnInfo(name = "captured_at")
    val capturedAt: Long,
)
