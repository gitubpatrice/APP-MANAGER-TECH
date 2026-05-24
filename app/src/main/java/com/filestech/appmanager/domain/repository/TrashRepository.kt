package com.filestech.appmanager.domain.repository

import com.filestech.appmanager.domain.model.TrashItem
import kotlinx.coroutines.flow.Flow

/**
 * Domain contract for the Corbeille / Trash staging area.
 *
 * The trash is a *user-facing review buffer* on top of the system uninstall
 * intent. Moving an app to the trash does NOT uninstall it — the row simply
 * records the user's intent so they can later either restore (no-op) or
 * confirm by launching the system intent.
 *
 * Backed by [com.filestech.appmanager.data.repository.TrashRepositoryImpl]
 * (Room + private DB). UseCases inject this interface, never the DAO.
 */
interface TrashRepository {

    /** Hot flow of every trashed app, sorted by added-at desc. */
    fun observe(): Flow<List<TrashItem>>

    /** Hot flow of the count, cheap source for badge UIs / settings sub-titles. */
    fun observeCount(): Flow<Int>

    /** One-shot snapshot (workers, single-shot logic). */
    suspend fun getAll(): List<TrashItem>

    /** Probe — used by AppDetail to decide whether to surface "In trash" badge. */
    suspend fun isInTrash(packageName: String): Boolean

    /**
     * Idempotent — re-trashing the same package refreshes label + size +
     * addedAt to "now". Useful if the app was updated meanwhile.
     */
    suspend fun moveToTrash(item: TrashItem)

    /** Restore = remove from the trash. The app stays installed either way. */
    suspend fun restore(packageName: String)

    /** Bulk restore — single Room transaction (delegated to DAO.deleteAll). */
    suspend fun restoreAll()

    /**
     * Removes rows whose `package_name` is no longer installed on the device.
     *
     * Why: the Trash is a soft-delete staging buffer ON TOP of the system
     * uninstall intent — once the user confirms the OS dialog, the app is
     * gone from `PackageManager` but the trash row stays "orphaned" with
     * stale Restore / Uninstall buttons. v0.2.0 user report:
     * `"quand je désinstalle une appli depuis la corbeille, [...] l'appli
     *  ne disparait pas de la corbeille"`.
     *
     * Called on TrashScreen lifecycle ON_RESUME (after returning from the
     * OS uninstall dialog) and on ViewModel `init`. Idempotent — running
     * twice in a row is a no-op the second time.
     *
     * Returns the number of orphaned rows purged (for logging / debug).
     */
    suspend fun purgeOrphaned(): Int
}
