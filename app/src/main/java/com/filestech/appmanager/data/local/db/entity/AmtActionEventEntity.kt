package com.filestech.appmanager.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * v0.4.0 (schema v8) — one AMT-initiated action recorded in the
 * in-app journal.
 *
 * Schema choices :
 *  - Append-only : rows are inserted, never updated. The single
 *    capture path lives in
 *    [com.filestech.appmanager.domain.usecase.RecordAmtActionUseCase].
 *  - `action_type` and `result` stored as TEXT (enum name). Forward-
 *    compat decoding tolerates unknown values (downgrade scenario) by
 *    dropping the row from the UI feed — the row stays on disk for
 *    forensic recovery should a downgraded user re-upgrade.
 *  - `label_snapshot` is NULLable for the rare case where the caller
 *    could not resolve a label (package gone between dialog confirm
 *    and Intent launch). The UI falls back to `package_name`.
 *
 * Indices :
 *  - `(package_name, timestamp)` composite — supports per-app journal
 *    queries (future "show me what AMT did to this app").
 *  - `timestamp` — supports the global timeline + retention purge
 *    (`DELETE WHERE timestamp < cutoff`).
 *  - `action_type` — speeds up "all UNINSTALL events" style filters in
 *    the UI window.
 */
@Entity(
    tableName = "amt_action_event",
    indices = [
        Index(value = ["package_name", "timestamp"]),
        Index(value = ["timestamp"]),
        Index(value = ["action_type"]),
    ],
)
data class AmtActionEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(name = "package_name")
    val packageName: String,

    /**
     * Cached app label at capture time. NULL if the caller could not
     * resolve it (e.g. package uninstalled between dialog confirm and
     * Intent dispatch). The UI falls back to [packageName].
     */
    @ColumnInfo(name = "label_snapshot")
    val labelSnapshot: String?,

    /** Enum name from [com.filestech.appmanager.domain.model.AmtActionType]. */
    @ColumnInfo(name = "action_type")
    val actionType: String,

    /** Enum name from [com.filestech.appmanager.domain.model.AmtActionResult]. */
    @ColumnInfo(name = "result")
    val result: String,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long,
)
