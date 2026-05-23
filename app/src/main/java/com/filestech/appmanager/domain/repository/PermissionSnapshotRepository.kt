package com.filestech.appmanager.domain.repository

import com.filestech.appmanager.domain.model.PermissionSnapshot
import com.filestech.appmanager.domain.model.SnapshotStats
import kotlinx.coroutines.flow.Flow

/**
 * Domain contract for the historical permission-state store.
 *
 * Backed by [com.filestech.appmanager.data.repository.PermissionSnapshotRepositoryImpl]
 * (Room). The capture use case talks to this repository — never the DAO
 * directly — so the layering rule (UseCase ↔ Repository ↔ DAO) holds.
 */
interface PermissionSnapshotRepository {

    /**
     * Returns the most-recent snapshot for [packageName]/[permission] or null
     * if we have no history yet. Used by the capture use case to dedup
     * unchanged states.
     */
    suspend fun getLatest(packageName: String, permission: String): PermissionSnapshot?

    /** Appends one row. Caller is responsible for change-vs-current deduplication. */
    suspend fun insert(snapshot: PermissionSnapshot)

    /**
     * Hot stream of snapshot rows representing drift events (any snapshot that
     * has at least one strictly-older snapshot for the same pkg/perm) captured
     * within [sinceMs, now], newest first.
     *
     * Pairing UI ([com.filestech.appmanager.domain.usecase.GetPermissionDriftsUseCase])
     * resolves the previous-state and decides GAINED vs LOST.
     */
    fun observeDriftRows(sinceMs: Long): Flow<List<PermissionSnapshot>>

    /** Total row count — for the Settings "drift history size" sub-title. */
    suspend fun count(): Int

    /**
     * Hot stream of capture-history stats — distinct apps + distinct
     * (pkg, perm) pairs + last capture timestamp. Powers the Drift screen's
     * "Surveillance active" header card.
     */
    fun observeStats(): Flow<SnapshotStats>

    /**
     * Deletes rows older than [cutoffMs]. Returns the number of rows deleted.
     * Idempotent — running it twice in a row deletes the second-run subset
     * (zero new rows aged into the cutoff in between).
     */
    suspend fun purgeOlderThan(cutoffMs: Long): Int

    /** Wipes the entire history. Used by Settings "Reset drift history". */
    suspend fun deleteAll(): Int

    /**
     * Drops every snapshot for [packageName]. Called when an app is uninstalled
     * so its drift history doesn't haunt the feed indefinitely.
     */
    suspend fun deleteByPackage(packageName: String): Int
}
