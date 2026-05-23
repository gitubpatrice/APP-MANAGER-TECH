package com.filestech.appmanager.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.filestech.appmanager.data.local.db.entity.TrashItemEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for the Trash staging area.
 *
 * Convention mirrors [AppInfoDao]:
 *  - `observe*` returns hot [Flow] for UI subscriptions.
 *  - one-shot `get*` snapshots for workers.
 *  - `upsert` uses REPLACE — re-trashing the same package just refreshes the
 *    snapshot (label / size / addedAt).
 */
@Dao
interface TrashItemDao {

    @Query("SELECT * FROM trash_item ORDER BY added_at DESC")
    fun observeAll(): Flow<List<TrashItemEntity>>

    @Query("SELECT * FROM trash_item ORDER BY added_at DESC")
    suspend fun getAll(): List<TrashItemEntity>

    @Query("SELECT COUNT(*) FROM trash_item")
    fun observeCount(): Flow<Int>

    @Query("SELECT * FROM trash_item WHERE package_name = :packageName LIMIT 1")
    suspend fun getByPackage(packageName: String): TrashItemEntity?

    @Query("SELECT COUNT(*) > 0 FROM trash_item WHERE package_name = :packageName")
    suspend fun isInTrash(packageName: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TrashItemEntity): Long

    @Query("DELETE FROM trash_item WHERE package_name = :packageName")
    suspend fun deleteByPackage(packageName: String): Int

    @Query("DELETE FROM trash_item")
    suspend fun deleteAll(): Int
}
