package com.filestech.appmanager.data.local.db.dto

import androidx.room.ColumnInfo

/**
 * Aggregated row returned by [com.filestech.appmanager.data.local.db.dao.PermissionSnapshotDao.observeStats].
 *
 * The three counters describe the **state of the permission-snapshot history**
 * — surfaced in the Drift screen header to communicate "the feature is
 * actually capturing data" even when no drift rows exist yet (v0.2.0 UX
 * issue: users tap Capture twice, get "no changes detected" and assume the
 * tool is broken).
 *
 * Sits in `db/dto/` next to [AggregateRow] — pure Room mapping target, no
 * domain semantics. The Domain layer mirrors it as [com.filestech.appmanager.domain.model.SnapshotStats].
 */
data class SnapshotStatsRow(
    @ColumnInfo(name = "distinct_packages")
    val distinctPackages: Int,
    @ColumnInfo(name = "distinct_permissions")
    val distinctPermissions: Int,
    /** Null when the snapshot table is empty (no capture ever performed). */
    @ColumnInfo(name = "last_captured_at")
    val lastCapturedAt: Long?,
)
