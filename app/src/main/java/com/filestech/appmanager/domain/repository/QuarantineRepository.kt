package com.filestech.appmanager.domain.repository

import com.filestech.appmanager.domain.model.QuarantineEntry
import kotlinx.coroutines.flow.Flow

/**
 * Domain contract for the App Quarantine staging store.
 *
 * Backed by [com.filestech.appmanager.data.repository.QuarantineRepositoryImpl]
 * (Room). All callers go through use cases — the repository never enforces
 * business rules (e.g. "warn before HARD mode"). That lives one layer up.
 */
interface QuarantineRepository {

    /** Hot stream of every quarantined app, sorted by [QuarantineEntry.restoreAt] asc. */
    fun observeAll(): Flow<List<QuarantineEntry>>

    /** Hot row-count for badge UIs. */
    fun observeCount(): Flow<Int>

    /** One-shot snapshot — used by workers. */
    suspend fun getAll(): List<QuarantineEntry>

    /** Null if not quarantined. */
    suspend fun getByPackage(packageName: String): QuarantineEntry?

    suspend fun isQuarantined(packageName: String): Boolean

    /**
     * Returns entries that have expired (`restore_at <= nowMs`) AND not yet
     * been notified by the worker. Used to drive the expiry notification +
     * auto-restore prompt.
     */
    suspend fun getExpiredUnnotified(nowMs: Long): List<QuarantineEntry>

    /** REPLACE semantics — re-quarantining a package overwrites the prior entry. */
    suspend fun upsert(entry: QuarantineEntry)

    /** Marks `notified = true` so the worker never re-fires the expiry notif. Idempotent. */
    suspend fun markNotified(packageName: String)

    /** Removes an entry (e.g. after manual restore or after notif acknowledged). */
    suspend fun delete(packageName: String): Int

    /** Bulk wipe — used by Settings "Empty quarantine list". */
    suspend fun deleteAll(): Int
}
