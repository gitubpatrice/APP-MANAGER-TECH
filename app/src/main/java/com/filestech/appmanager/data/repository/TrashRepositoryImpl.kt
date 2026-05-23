package com.filestech.appmanager.data.repository

import com.filestech.appmanager.data.local.db.dao.TrashItemDao
import com.filestech.appmanager.data.local.db.entity.TrashItemEntity
import com.filestech.appmanager.domain.model.TrashItem
import com.filestech.appmanager.domain.repository.TrashRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed [TrashRepository]. All mapping happens here so the domain layer
 * stays free of any androidx.room import.
 */
@Singleton
class TrashRepositoryImpl @Inject constructor(
    private val dao: TrashItemDao,
) : TrashRepository {

    override fun observe(): Flow<List<TrashItem>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override fun observeCount(): Flow<Int> = dao.observeCount()

    override suspend fun getAll(): List<TrashItem> =
        dao.getAll().map { it.toDomain() }

    override suspend fun isInTrash(packageName: String): Boolean =
        dao.isInTrash(packageName)

    override suspend fun moveToTrash(item: TrashItem) {
        dao.upsert(item.toEntity())
    }

    override suspend fun restore(packageName: String) {
        dao.deleteByPackage(packageName)
    }

    override suspend fun restoreAll() {
        dao.deleteAll()
    }

    private fun TrashItemEntity.toDomain(): TrashItem = TrashItem(
        packageName    = packageName,
        label          = label,
        totalSizeBytes = totalSizeBytes,
        addedAt        = addedAt,
    )

    private fun TrashItem.toEntity(): TrashItemEntity = TrashItemEntity(
        packageName    = packageName,
        label          = label,
        totalSizeBytes = totalSizeBytes,
        addedAt        = addedAt,
    )
}
