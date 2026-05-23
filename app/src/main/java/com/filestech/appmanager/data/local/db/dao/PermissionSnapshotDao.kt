package com.filestech.appmanager.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.filestech.appmanager.data.local.db.dto.SnapshotStatsRow
import com.filestech.appmanager.data.local.db.entity.PermissionSnapshotEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for the historical permission-state snapshots.
 *
 * Convention mirrors [TrashItemDao]:
 *  - `observe*` returns a hot [Flow] for UI subscriptions.
 *  - one-shot `get*` snapshots for workers + use cases.
 *  - `insert` is plain (no REPLACE) because the table is append-only — the
 *    capture use case decides whether to insert, never overwrite.
 *
 * Drift query strategy:
 *  - Because the capture use case inserts only on change, any row that has
 *    a strictly-older row for the same (pkg, perm) pair IS, by construction,
 *    a drift event.
 *  - The EXISTS subquery is O(log n) thanks to the composite index
 *    `(package_name, captured_at)`.
 */
@Dao
interface PermissionSnapshotDao {

    /**
     * The latest snapshot for [packageName] / [permission], or null if we have
     * no record yet. Used by the capture use case to dedup unchanged states.
     */
    @Query(
        """
        SELECT * FROM permission_snapshot
        WHERE package_name = :packageName AND permission = :permission
        ORDER BY captured_at DESC LIMIT 1
        """,
    )
    suspend fun getLatest(packageName: String, permission: String): PermissionSnapshotEntity?

    /**
     * Hot stream of every drift event in [sinceMs, now]. A drift is defined as
     * a snapshot row that has at least one strictly-older snapshot for the
     * same (package_name, permission) — i.e. it represents a state change vs
     * the prior baseline.
     *
     * Ordered newest-first for chronological UI display.
     */
    @Query(
        """
        SELECT s1.* FROM permission_snapshot s1
        WHERE s1.captured_at >= :sinceMs
          AND EXISTS (
            SELECT 1 FROM permission_snapshot s2
            WHERE s2.package_name = s1.package_name
              AND s2.permission   = s1.permission
              AND s2.captured_at  < s1.captured_at
          )
        ORDER BY s1.captured_at DESC
        """,
    )
    fun observeDrifts(sinceMs: Long): Flow<List<PermissionSnapshotEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: PermissionSnapshotEntity): Long

    /**
     * Retention: drops rows older than [cutoffMs]. Returns the number of rows
     * deleted (for logging / metrics).
     *
     * IMPORTANT: this can break the "predecessor" invariant for a (pkg, perm)
     * pair if the deleted row WAS the predecessor. The capture use case
     * recovers from that automatically by re-baselining on the next tick.
     */
    @Query("DELETE FROM permission_snapshot WHERE captured_at < :cutoffMs")
    suspend fun purgeOlderThan(cutoffMs: Long): Int

    @Query("SELECT COUNT(*) FROM permission_snapshot")
    suspend fun count(): Int

    /**
     * Reactive stats for the "Surveillance active" header card.
     *  - `distinct_packages` : distinct apps with at least one snapshot row.
     *  - `distinct_permissions` : distinct (pkg, perm) pairs ever observed
     *    (i.e. the count of unique tracked permission slots).
     *  - `last_captured_at` : the most recent capture timestamp (epoch ms),
     *    or NULL if the table is empty.
     */
    @Query(
        """
        SELECT
          COUNT(DISTINCT package_name) AS distinct_packages,
          COUNT(DISTINCT package_name || '|' || permission) AS distinct_permissions,
          MAX(captured_at) AS last_captured_at
        FROM permission_snapshot
        """,
    )
    fun observeStats(): Flow<SnapshotStatsRow>

    /** Wipes everything. Used by Settings "Reset drift history". */
    @Query("DELETE FROM permission_snapshot")
    suspend fun deleteAll(): Int

    /**
     * Drops every snapshot for [packageName]. Called when an app is fully
     * uninstalled so its history doesn't clutter the drift feed indefinitely.
     */
    @Query("DELETE FROM permission_snapshot WHERE package_name = :packageName")
    suspend fun deleteByPackage(packageName: String): Int
}
