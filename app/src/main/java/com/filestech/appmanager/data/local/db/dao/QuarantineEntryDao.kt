package com.filestech.appmanager.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.filestech.appmanager.data.local.db.entity.QuarantineEntryEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for the App Quarantine staging area.
 *
 * Mirror of [TrashItemDao]'s shape:
 *  - `observe*` for hot Flow UI subscriptions.
 *  - one-shot `get*` for workers + use cases.
 *  - `upsert` uses REPLACE so re-quarantining a package overwrites the prior
 *    entry (refreshes deadline, mode, etc.).
 *
 * Only ONE active entry per package by design (PK = package_name). A user who
 * wants two distinct quarantine windows for the same app must first restore
 * the existing entry — UX-enforced in [com.filestech.appmanager.domain.usecase.QuarantineAppUseCase].
 */
@Dao
interface QuarantineEntryDao {

    @Query("SELECT * FROM quarantine_entry ORDER BY restore_at ASC")
    fun observeAll(): Flow<List<QuarantineEntryEntity>>

    @Query("SELECT * FROM quarantine_entry ORDER BY restore_at ASC")
    suspend fun getAll(): List<QuarantineEntryEntity>

    @Query("SELECT COUNT(*) FROM quarantine_entry")
    fun observeCount(): Flow<Int>

    @Query("SELECT * FROM quarantine_entry WHERE package_name = :packageName LIMIT 1")
    suspend fun getByPackage(packageName: String): QuarantineEntryEntity?

    @Query("SELECT COUNT(*) > 0 FROM quarantine_entry WHERE package_name = :packageName")
    suspend fun isQuarantined(packageName: String): Boolean

    /**
     * Worker-side query: every entry whose expiry has passed and which has
     * NOT yet been notified. Idempotent — once the worker flips `notified=1`
     * (via [markNotified]), the row is excluded on subsequent ticks.
     */
    @Query(
        """
        SELECT * FROM quarantine_entry
        WHERE restore_at <= :nowMs AND notified = 0
        ORDER BY restore_at ASC
        """,
    )
    suspend fun getExpiredUnnotified(nowMs: Long): List<QuarantineEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: QuarantineEntryEntity): Long

    @Query("UPDATE quarantine_entry SET notified = 1 WHERE package_name = :packageName")
    suspend fun markNotified(packageName: String): Int

    @Query("DELETE FROM quarantine_entry WHERE package_name = :packageName")
    suspend fun deleteByPackage(packageName: String): Int

    @Query("DELETE FROM quarantine_entry")
    suspend fun deleteAll(): Int
}
