package com.filestech.appmanager.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.filestech.appmanager.data.local.db.dto.AggregateRow
import com.filestech.appmanager.data.local.db.entity.AppInfoEntity
import kotlinx.coroutines.flow.Flow

/**
 * Persisted view of the installed-app catalogue.
 *
 * Convention:
 * - `observe*` returns [Flow] for hot UI subscriptions.
 * - `get*` are one-shot `suspend` snapshots used by background workers.
 * - `upsert` uses [OnConflictStrategy.REPLACE] — natural fit since the row's
 *   uniqueness is the packageName (PK).
 *
 * Phase II uses these methods from `AppInfoRepositoryImpl`. ViewModels and
 * UseCases NEVER inject a Dao directly — that would leak the data layer.
 */
@Dao
interface AppInfoDao {

    // -----------------------------------------------------------------------
    // Reads — hot flows
    // -----------------------------------------------------------------------

    @Query("SELECT * FROM app_info ORDER BY label COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<AppInfoEntity>>

    @Query("SELECT * FROM app_info WHERE is_system_app = 0 ORDER BY label COLLATE NOCASE ASC")
    fun observeUserApps(): Flow<List<AppInfoEntity>>

    @Query("SELECT * FROM app_info WHERE is_system_app = 1 ORDER BY label COLLATE NOCASE ASC")
    fun observeSystemApps(): Flow<List<AppInfoEntity>>

    // -----------------------------------------------------------------------
    // Reads — one-shot snapshots
    // -----------------------------------------------------------------------

    @Query("SELECT * FROM app_info ORDER BY label COLLATE NOCASE ASC")
    suspend fun getAll(): List<AppInfoEntity>

    @Query("SELECT * FROM app_info WHERE package_name = :packageName LIMIT 1")
    suspend fun getByPackage(packageName: String): AppInfoEntity?

    @Query("SELECT * FROM app_info WHERE is_system_app = 0 ORDER BY label COLLATE NOCASE ASC")
    suspend fun getUserApps(): List<AppInfoEntity>

    @Query("SELECT * FROM app_info WHERE is_system_app = 1 ORDER BY label COLLATE NOCASE ASC")
    suspend fun getSystemApps(): List<AppInfoEntity>

    @Query("SELECT COUNT(*) FROM app_info")
    suspend fun count(): Int

    // -----------------------------------------------------------------------
    // Writes
    // -----------------------------------------------------------------------

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AppInfoEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<AppInfoEntity>): List<Long>

    @Query("UPDATE app_info SET is_enabled = :enabled WHERE package_name = :packageName")
    suspend fun updateEnabled(packageName: String, enabled: Boolean): Int

    @Query("UPDATE app_info SET cache_size_bytes = :cacheBytes WHERE package_name = :packageName")
    suspend fun updateCacheSize(packageName: String, cacheBytes: Long): Int

    @Query("DELETE FROM app_info WHERE package_name = :packageName")
    suspend fun deleteByPackage(packageName: String): Int

    /** Used during a full rescan: caller wraps `deleteAll() + upsertAll()` in a `@Transaction`. */
    @Query("DELETE FROM app_info")
    suspend fun deleteAll(): Int

    // -----------------------------------------------------------------------
    // Aggregates (Phase III — fuels StorageReport)
    // -----------------------------------------------------------------------

    /**
     * Counts and size sums across the cached catalogue.
     *
     * @param includeSystem when false, only counts apps with is_system_app = 0.
     *                      COALESCE returns 0 for empty result sets so callers
     *                      never deal with NULL.
     */
    @Query(
        """
        SELECT
          COUNT(*)                                AS appCount,
          COALESCE(SUM(install_size_bytes), 0)   AS installSum,
          COALESCE(SUM(data_size_bytes), 0)      AS dataSum,
          COALESCE(SUM(cache_size_bytes), 0)     AS cacheSum
        FROM app_info
        WHERE :includeSystem OR is_system_app = 0
        """,
    )
    suspend fun aggregateSizes(includeSystem: Boolean): AggregateRow
}
