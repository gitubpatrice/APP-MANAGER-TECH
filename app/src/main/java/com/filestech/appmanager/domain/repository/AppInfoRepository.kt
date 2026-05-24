package com.filestech.appmanager.domain.repository

import com.filestech.appmanager.core.result.Outcome
import com.filestech.appmanager.domain.model.AppInfo
import com.filestech.appmanager.domain.model.ExpertReport
import com.filestech.appmanager.domain.model.FilterOptions
import com.filestech.appmanager.domain.model.StorageReport
import kotlinx.coroutines.flow.Flow

/**
 * Domain contract for installed-application data.
 *
 * All methods return [Outcome] (typed errors) — no unchecked exceptions cross
 * the repository boundary. The UI layer never imports any `android.*` API.
 *
 * Implementation [com.filestech.appmanager.data.repository.AppInfoRepositoryImpl]
 * is backed by PackageManager + StorageStatsManager + UsageStatsManager + Room cache.
 */
interface AppInfoRepository {

    // -----------------------------------------------------------------------
    // Catalogue (Flow + one-shot)
    // -----------------------------------------------------------------------

    /**
     * Hot flow of the cached app catalogue (no in-process filter beyond the
     * system/user split — for richer filtering use [observeFiltered]).
     */
    fun observeApps(includeSystemApps: Boolean): Flow<Outcome<List<AppInfo>>>

    /**
     * Hot flow with the full [FilterOptions] applied in the data layer.
     * Permission-required filters (e.g. [FilterOptions.requiredPermissions])
     * trigger live PackageManager probes per app — use sparingly.
     */
    fun observeFiltered(filter: FilterOptions): Flow<Outcome<List<AppInfo>>>

    suspend fun getApp(packageName: String): Outcome<AppInfo>

    /**
     * Full rescan: queries PackageManager + StorageStatsManager + UsageStatsManager
     * and atomically replaces the cache in a single Room transaction.
     */
    suspend fun rescan(): Outcome<Unit>

    // -----------------------------------------------------------------------
    // Actions
    // -----------------------------------------------------------------------

    /**
     * Non-root Android has no public API to wipe another app's cache. Returns
     * [Outcome.Failure] with [com.filestech.appmanager.core.result.AppError.Permission];
     * the UseCase wraps this with an `IntentFactory.appDetailsSettingsIntent`
     * so the UI can prompt the user.
     */
    suspend fun clearCache(packageName: String): Outcome<Unit>

    /**
     * Toggles the enabled state of an installed app.
     *
     * On non-root devices this throws `SecurityException` at the OS level
     * (`CHANGE_COMPONENT_ENABLED_STATE` is signature-protected) and we return
     * [com.filestech.appmanager.core.result.AppError.Permission]. The UseCase
     * caller falls back to opening the OS Settings app-info screen which
     * exposes the enable/disable toggle.
     */
    suspend fun setEnabled(packageName: String, enabled: Boolean): Outcome<Unit>

    /**
     * Best-effort: tells `ActivityManager.killBackgroundProcesses` to stop
     * background processes of [packageName]. Has NO effect if the app is in
     * the foreground or has a visible foreground service — that is by design
     * on non-root Android and cannot be worked around without root.
     *
     * Requires `KILL_BACKGROUND_PROCESSES` (declared in our manifest).
     */
    suspend fun forceStop(packageName: String): Outcome<Unit>

    // -----------------------------------------------------------------------
    // Aggregates / reports
    // -----------------------------------------------------------------------

    /**
     * Builds a [StorageReport] from the current cache snapshot.
     *
     * @param includeSystemApps include system apps in the totals and top-N lists.
     * @param topN how many apps to surface in the topByTotalSize / topByCacheSize lists.
     */
    suspend fun getStorageReport(
        includeSystemApps: Boolean,
        topN: Int = 10,
    ): Outcome<StorageReport>

    // -----------------------------------------------------------------------
    // On-demand metadata (NOT cached — read live from PackageManager)
    // -----------------------------------------------------------------------

    suspend fun getRequestedPermissions(packageName: String): Outcome<List<String>>

    suspend fun getGrantedPermissions(packageName: String): Outcome<List<String>>

    /**
     * SHA-256 fingerprint of the app's signing certificate, formatted as
     * colon-separated uppercase hex (e.g. `AB:CD:EF:...`).
     */
    suspend fun getSignatureSha256(packageName: String): Outcome<String>

    /**
     * v0.2.2 — Expert Mode payload: identity / SDK / ABI / APK paths / signature /
     * declared components (activities, services, receivers, providers, with the
     * `exported` flag) / permissions / app-ops best-effort.
     *
     * Read live from PackageManager — NOT cached. The OS may surface fewer app-ops
     * than requested when the caller lacks `GET_APP_OPS_STATS` (the common case on
     * non-rooted devices); the report carries an `isFullyAccessible` flag and a
     * pre-rendered mode label so the UI never has to format raw constants.
     */
    suspend fun getExpertReport(packageName: String): Outcome<ExpertReport>

    // -----------------------------------------------------------------------
    // Permission probes (no side effect)
    // -----------------------------------------------------------------------

    /**
     * Returns true if PACKAGE_USAGE_STATS has been granted to App Manager Tech.
     * Without it, [observeApps] still works but every app's `cacheSizeBytes`,
     * `dataSizeBytes`, `installSizeBytes` and `lastUsedTime` fall back to 0.
     */
    fun hasUsageStatsAccess(): Boolean
}
