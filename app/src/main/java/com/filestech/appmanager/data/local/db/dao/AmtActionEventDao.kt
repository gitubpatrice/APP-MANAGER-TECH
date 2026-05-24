package com.filestech.appmanager.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.filestech.appmanager.data.local.db.entity.AmtActionEventEntity
import kotlinx.coroutines.flow.Flow

/**
 * v0.4.0 — DAO for the append-only AMT action journal.
 *
 * Convention mirrors [AppLifecycleEventDao] :
 *  - `observeSince` exposes a hot [Flow] for UI consumption.
 *  - one-shot suspend `count` for the Settings stats line.
 *  - `insert` ABORTs on conflict (no UPSERT) — the table is append-only
 *    and the autoincrement PK guarantees no collision under normal
 *    operation.
 *
 * Range queries use `>=` on the lower bound; the upper bound (`< now`)
 * is enforced by the caller (system clock not visible to SQL).
 */
@Dao
interface AmtActionEventDao {

    /**
     * Global hot stream of journal rows in `[sinceMs, +∞)`, newest
     * first. Powers the ActionJournal screen timeline + the window
     * picker (30/90/all).
     */
    @Query(
        """
        SELECT * FROM amt_action_event
        WHERE timestamp >= :sinceMs
        ORDER BY timestamp DESC
        """,
    )
    fun observeSince(sinceMs: Long): Flow<List<AmtActionEventEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: AmtActionEventEntity): Long

    /** Retention purge — drops rows older than [cutoffMs]. Returns count. */
    @Query("DELETE FROM amt_action_event WHERE timestamp < :cutoffMs")
    suspend fun purgeOlderThan(cutoffMs: Long): Int

    /** Total row count (Settings stats + tests). */
    @Query("SELECT COUNT(*) FROM amt_action_event")
    suspend fun count(): Int

    /** Wipes everything — used by Settings "Reset action journal". */
    @Query("DELETE FROM amt_action_event")
    suspend fun deleteAll(): Int
}
