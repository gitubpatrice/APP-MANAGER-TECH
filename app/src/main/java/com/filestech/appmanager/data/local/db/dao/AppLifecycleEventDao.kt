package com.filestech.appmanager.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.filestech.appmanager.data.local.db.entity.AppLifecycleEventEntity
import kotlinx.coroutines.flow.Flow

/**
 * v0.3.0 — DAO for the append-only app lifecycle event log.
 *
 * Convention mirrors [PermissionSnapshotDao]:
 *  - `observe*` exposes hot [Flow] for UI consumption.
 *  - one-shot `get*` suspend functions for use cases / workers.
 *  - `insert` ABORTs on conflict (no UPSERT) — the table is append-only and
 *    the autoincrement PK guarantees no collision under normal operation.
 *
 * Range queries use `>=` lower bound + `< now` is enforced by the caller
 * (system clock not visible to SQL).
 */
@Dao
interface AppLifecycleEventDao {

    /**
     * Global hot stream of events in `[sinceMs, +∞)`, newest first. Powers the
     * Lifecycle History screen timeline + the window picker (30/90/all).
     */
    @Query(
        """
        SELECT * FROM app_lifecycle_event
        WHERE captured_at >= :sinceMs
        ORDER BY captured_at DESC
        """,
    )
    fun observeSince(sinceMs: Long): Flow<List<AppLifecycleEventEntity>>

    /**
     * Per-package timeline (newest first) — used by the future "lifecycle
     * inline" section on AppDetail or a row-tap drill-down dialog.
     */
    @Query(
        """
        SELECT * FROM app_lifecycle_event
        WHERE package_name = :packageName
        ORDER BY captured_at DESC
        """,
    )
    fun observeByPackage(packageName: String): Flow<List<AppLifecycleEventEntity>>

    /** Latest event for [packageName], or null if no row exists yet. */
    @Query(
        """
        SELECT * FROM app_lifecycle_event
        WHERE package_name = :packageName
        ORDER BY captured_at DESC LIMIT 1
        """,
    )
    suspend fun getLatestByPackage(packageName: String): AppLifecycleEventEntity?

    /**
     * Latest UNINSTALLED event for [packageName] (if any). Used by the
     * uninstall-reason capture path: when the user taps a reason in the dialog
     * we UPDATE that row's user_reason via [setReasonForId] referencing the id
     * carried in the dialog state.
     */
    @Query(
        """
        SELECT * FROM app_lifecycle_event
        WHERE package_name = :packageName AND type = 'UNINSTALLED'
        ORDER BY captured_at DESC LIMIT 1
        """,
    )
    suspend fun getLatestUninstallByPackage(packageName: String): AppLifecycleEventEntity?

    /**
     * Sets the [reason] on a single row by [id]. Used by the uninstall-reason
     * dialog to attach a category to the event we just inserted. Returns the
     * number of rows updated (0 = row was purged by retention before the user
     * answered, which is handled silently).
     *
     * This is the ONE allowed mutation on this table — every other write is an
     * append-only insert. The narrow `WHERE id = :id` keeps the audit trail
     * monotonic.
     */
    @Query("UPDATE app_lifecycle_event SET user_reason = :reason WHERE id = :id")
    suspend fun setReasonForId(id: Long, reason: String?): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: AppLifecycleEventEntity): Long

    /**
     * Returns true if AT LEAST one BASELINE row exists for [packageName].
     * Used by the baseline scan to avoid duplicating BASELINE rows on every
     * app launch (the scan runs idempotently — we insert one BASELINE per
     * package, then skip on subsequent launches).
     */
    @Query(
        """
        SELECT EXISTS(
          SELECT 1 FROM app_lifecycle_event
          WHERE package_name = :packageName AND type = 'BASELINE'
        )
        """,
    )
    suspend fun hasBaseline(packageName: String): Boolean

    /** Retention purge — drops rows older than [cutoffMs]. Returns count. */
    @Query("DELETE FROM app_lifecycle_event WHERE captured_at < :cutoffMs")
    suspend fun purgeOlderThan(cutoffMs: Long): Int

    /** Total row count (Settings stats + tests). */
    @Query("SELECT COUNT(*) FROM app_lifecycle_event")
    suspend fun count(): Int

    /** Wipes everything — used by Settings "Reset lifecycle history". */
    @Query("DELETE FROM app_lifecycle_event")
    suspend fun deleteAll(): Int
}
