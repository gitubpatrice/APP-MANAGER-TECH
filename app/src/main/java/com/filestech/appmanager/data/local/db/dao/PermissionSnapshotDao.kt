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
     *
     * v0.5.1 — `id` breaks a `captured_at` tie, the same order [purgeOlderThan]
     * uses to pick the row it keeps.
     */
    @Query(
        """
        SELECT * FROM permission_snapshot
        WHERE package_name = :packageName AND permission = :permission
        ORDER BY captured_at DESC, id DESC LIMIT 1
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
     * Retention: drops rows older than [cutoffMs], except, for each (package,
     * permission) pair, the NEWEST of them. Returns the number of rows deleted
     * (for logging / metrics).
     *
     * v0.5.1 — that row is the state the pair was in at the cutoff: the
     * baseline of the first change kept after it, and of the next one if the
     * pair has not changed since. The query deleted every row older than the
     * cutoff, so the baseline aged out first: with a 90-day retention, a
     * baseline taken on day 0 went on day 91, and a change recorded on day 80
     * — 11 days old — lost its predecessor and vanished from [observeDrifts]
     * 79 days early. Nothing re-created it: the capture use case inserts only
     * on change.
     *
     * "Newest" is `captured_at`, then `id` on a tie (same order as [getLatest]).
     */
    @Query(
        """
        DELETE FROM permission_snapshot
        WHERE captured_at < :cutoffMs
          AND EXISTS (
            SELECT 1 FROM permission_snapshot newer
            WHERE newer.package_name = permission_snapshot.package_name
              AND newer.permission   = permission_snapshot.permission
              AND newer.captured_at  < :cutoffMs
              AND (newer.captured_at > permission_snapshot.captured_at
                OR (newer.captured_at = permission_snapshot.captured_at AND newer.id > permission_snapshot.id))
          )
        """,
    )
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
