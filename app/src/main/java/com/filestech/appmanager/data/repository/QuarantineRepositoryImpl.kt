package com.filestech.appmanager.data.repository

import com.filestech.appmanager.data.local.db.dao.QuarantineEntryDao
import com.filestech.appmanager.data.local.db.entity.QuarantineEntryEntity
import com.filestech.appmanager.domain.model.QuarantineEntry
import com.filestech.appmanager.domain.model.QuarantineMode
import com.filestech.appmanager.domain.repository.QuarantineRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed [QuarantineRepository]. Confines all entity↔domain mapping +
 * mode-name parsing here.
 *
 * Mode parsing is defensive: an unknown persisted name (e.g. after a future
 * enum rename — which is FORBIDDEN by the [QuarantineMode] contract, but still
 * defended against) falls back to [QuarantineMode.SOFT_REMINDER], the safer
 * default (no data loss interaction implied).
 */
@Singleton
class QuarantineRepositoryImpl @Inject constructor(
    private val dao: QuarantineEntryDao,
) : QuarantineRepository {

    override fun observeAll(): Flow<List<QuarantineEntry>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override fun observeCount(): Flow<Int> = dao.observeCount()

    override suspend fun getAll(): List<QuarantineEntry> =
        dao.getAll().map { it.toDomain() }

    override suspend fun getByPackage(packageName: String): QuarantineEntry? =
        dao.getByPackage(packageName)?.toDomain()

    override suspend fun isQuarantined(packageName: String): Boolean =
        dao.isQuarantined(packageName)

    override suspend fun getExpiredUnnotified(nowMs: Long): List<QuarantineEntry> =
        dao.getExpiredUnnotified(nowMs).map { it.toDomain() }

    override suspend fun upsert(entry: QuarantineEntry) {
        dao.upsert(entry.toEntity())
    }

    override suspend fun markNotified(packageName: String) {
        dao.markNotified(packageName)
    }

    override suspend fun delete(packageName: String): Int =
        dao.deleteByPackage(packageName)

    override suspend fun deleteAll(): Int = dao.deleteAll()

    private fun QuarantineEntryEntity.toDomain(): QuarantineEntry = QuarantineEntry(
        packageName        = packageName,
        label              = label,
        mode               = parseMode(mode),
        quarantinedAt      = quarantinedAt,
        restoreAt          = restoreAt,
        versionName        = versionName,
        versionCode        = versionCode,
        apkBackupUri       = apkBackupUri,
        autoRestoreEnabled = autoRestoreEnabled,
        notified           = notified,
    )

    private fun QuarantineEntry.toEntity(): QuarantineEntryEntity = QuarantineEntryEntity(
        packageName        = packageName,
        label              = label,
        mode               = mode.name,
        quarantinedAt      = quarantinedAt,
        restoreAt          = restoreAt,
        versionName        = versionName,
        versionCode        = versionCode,
        apkBackupUri       = apkBackupUri,
        autoRestoreEnabled = autoRestoreEnabled,
        notified           = notified,
    )

    private fun parseMode(persisted: String): QuarantineMode =
        runCatching { QuarantineMode.valueOf(persisted) }.getOrDefault(QuarantineMode.SOFT_REMINDER)
}
