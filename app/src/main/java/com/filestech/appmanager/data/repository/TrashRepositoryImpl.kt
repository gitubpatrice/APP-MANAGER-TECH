package com.filestech.appmanager.data.repository

import android.content.Context
import android.content.pm.PackageManager
import com.filestech.appmanager.data.local.db.dao.TrashItemDao
import com.filestech.appmanager.data.local.db.entity.TrashItemEntity
import com.filestech.appmanager.di.IoDispatcher
import com.filestech.appmanager.domain.model.TrashItem
import com.filestech.appmanager.domain.repository.TrashRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed [TrashRepository]. All mapping happens here so the domain layer
 * stays free of any androidx.room import.
 */
@Singleton
class TrashRepositoryImpl @Inject constructor(
    private val dao: TrashItemDao,
    @ApplicationContext private val context: Context,
    /**
     * v0.4.0 audit SECU-H1 fix — IO dispatcher injected so
     * `purgeOrphaned()` can be unit-tested with a substitute
     * dispatcher (previously `Dispatchers.IO` hardcoded — untestable).
     */
    @IoDispatcher private val io: CoroutineDispatcher,
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

    /**
     * Iterates over every trash row and asks PackageManager whether the
     * package is still installed. Rows whose package is gone are deleted.
     *
     * `PackageManager.getPackageInfo` throws [PackageManager.NameNotFoundException]
     * for a missing package — caught per row so one missing entry does not
     * abort the sweep.
     *
     * Runs on [Dispatchers.IO] because PM lookups are IPC + the per-row
     * `deleteByPackage` writes hit Room IO.
     */
    override suspend fun purgeOrphaned(): Int = withContext(io) {
        val pm = context.packageManager
        val snapshot = dao.getAll()
        // v0.2.1 audit H-1 fix — batch the deletes. Previous implementation
        // did N IPC probes + N individual Room writes serially. With ~20 rows
        // that's noticeable jank on slow devices. Collect orphans first, then
        // delete in a single transaction via the new `deleteByPackages` query.
        val orphaned = snapshot.mapNotNull { row ->
            val stillInstalled = try {
                pm.getPackageInfo(row.packageName, 0)
                true
            } catch (e: PackageManager.NameNotFoundException) {
                false
            } catch (e: Exception) {
                // Any other PM failure (e.g. transient binder death) — leave
                // the row alone. We will retry next ON_RESUME tick.
                Timber.w(e, "purgeOrphaned: PM probe failed for %s — keeping row", row.packageName)
                true
            }
            if (!stillInstalled) row.packageName else null
        }
        if (orphaned.isNotEmpty()) {
            dao.deleteByPackages(orphaned)
            Timber.i("Trash purge: %d orphaned rows removed", orphaned.size)
        }
        orphaned.size
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
