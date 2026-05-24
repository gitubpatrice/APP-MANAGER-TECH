package com.filestech.appmanager.domain.repository

import com.filestech.appmanager.core.result.Outcome
import com.filestech.appmanager.domain.model.LifecycleEvent
import com.filestech.appmanager.domain.model.LifecycleEventType
import com.filestech.appmanager.domain.model.UninstallReason
import kotlinx.coroutines.flow.Flow

/**
 * v0.3.0 — Domain contract for the App Lifecycle History feature.
 *
 * The history is append-only: rows are inserted on every detected OS broadcast
 * (`PACKAGE_ADDED` / `_REMOVED` / `_REPLACED`) and on the one-shot baseline
 * scan that runs on first launch post-upgrade. The single mutation allowed is
 * [setReason], used to attach an [UninstallReason] to an event the user
 * answered in the optional uninstall dialog.
 *
 * Privacy: the data never leaves the device (no INTERNET permission). The DB
 * is excluded from cloud backup via `backup_rules.xml` (same envelope as the
 * existing `app_info` / `permission_snapshot` tables).
 */
interface AppLifecycleRepository {

    /** Hot stream of events in `[sinceMs, +∞)`, newest first. */
    fun observeSince(sinceMs: Long): Flow<Outcome<List<LifecycleEvent>>>

    /** Hot stream of events for a single package, newest first. */
    fun observeByPackage(packageName: String): Flow<Outcome<List<LifecycleEvent>>>

    /**
     * Inserts a new event row. Returns the auto-generated `id` so the caller
     * can attach a [UninstallReason] later via [setReason] (the uninstall
     * dialog flow uses this).
     */
    suspend fun insert(
        packageName: String,
        label: String?,
        type: LifecycleEventType,
        capturedAt: Long,
        versionName: String?,
        versionCode: Long,
        installerPackage: String?,
        totalSizeBytes: Long,
        grantedDangerousPermissions: List<String>,
    ): Outcome<Long>

    /**
     * Attaches a [reason] to the event identified by [eventId]. Returns true
     * if the row was updated (false = row already purged by retention).
     */
    suspend fun setReason(eventId: Long, reason: UninstallReason?): Outcome<Boolean>

    /** True when at least one BASELINE row exists for [packageName]. */
    suspend fun hasBaseline(packageName: String): Outcome<Boolean>

    /** Latest event for [packageName] (any type), or null. */
    suspend fun latestByPackage(packageName: String): Outcome<LifecycleEvent?>

    /** Latest UNINSTALLED event for [packageName] (any type), or null. */
    suspend fun latestUninstallByPackage(packageName: String): Outcome<LifecycleEvent?>

    /** Drops rows older than [cutoffMs]. Returns the count of rows deleted. */
    suspend fun purgeOlderThan(cutoffMs: Long): Outcome<Int>

    /** Wipes the entire log — used by Settings "Reset lifecycle history". */
    suspend fun deleteAll(): Outcome<Int>

    /** Total row count (Settings stats card). */
    suspend fun count(): Outcome<Int>
}
