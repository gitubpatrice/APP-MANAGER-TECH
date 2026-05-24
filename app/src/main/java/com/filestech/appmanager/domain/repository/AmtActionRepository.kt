package com.filestech.appmanager.domain.repository

import com.filestech.appmanager.core.result.Outcome
import com.filestech.appmanager.domain.model.AmtActionEvent
import com.filestech.appmanager.domain.model.AmtActionResult
import com.filestech.appmanager.domain.model.AmtActionType
import kotlinx.coroutines.flow.Flow

/**
 * v0.4.0 — Domain contract for the in-app AMT action journal.
 *
 * Append-only : rows are inserted on every destructive call site of
 * AMT (uninstall, clear cache, move to trash…). The only allowed
 * mutations are the periodic retention purge and the explicit "Reset"
 * action exposed in Settings.
 *
 * Privacy : the data never leaves the device (no INTERNET permission).
 * The DB is excluded from cloud backup via `backup_rules.xml` (same
 * envelope as the existing `app_info` / `app_lifecycle_event` tables).
 */
interface AmtActionRepository {

    /** Hot stream of journal rows in `[sinceMs, +∞)`, newest first. */
    fun observeSince(sinceMs: Long): Flow<Outcome<List<AmtActionEvent>>>

    /**
     * Inserts a new journal row. Returns the auto-generated `id`. The
     * caller does NOT need to await the insert before completing its
     * own action — recording is fire-and-forget at the UI level.
     */
    suspend fun insert(
        packageName: String,
        labelSnapshot: String?,
        actionType: AmtActionType,
        result: AmtActionResult,
        timestamp: Long,
    ): Outcome<Long>

    /** Drops rows older than [cutoffMs]. Returns the count of rows deleted. */
    suspend fun purgeOlderThan(cutoffMs: Long): Outcome<Int>

    /** Wipes the entire journal — used by Settings "Reset action journal". */
    suspend fun deleteAll(): Outcome<Int>

    /** Total row count (Settings stats card). */
    suspend fun count(): Outcome<Int>
}
