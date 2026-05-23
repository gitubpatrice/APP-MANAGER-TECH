package com.filestech.appmanager.data.local.datastore

import com.filestech.appmanager.domain.model.AppSortOrder
import com.filestech.appmanager.domain.model.ExportFormat
import com.filestech.appmanager.domain.model.ScanInterval
import com.filestech.appmanager.domain.model.ThemeMode

/**
 * Hierarchical settings model for App Manager Tech.
 *
 * This is the single source of truth for all user preferences persisted in
 * DataStore<Preferences>. Pure Kotlin data classes — no Android/Room dependency.
 *
 * Phase VIII C5 fix: the four enum types referenced here ([ThemeMode],
 * [AppSortOrder], [ScanInterval], [ExportFormat]) now live in `domain/model/`
 * so use cases and ViewModels never have to import from `data/` just to
 * reference an enum. This file imports them back to compose the settings tree.
 *
 * Design principles:
 * - Defaults MUST be the safest / most conservative option.
 * - Each sub-object groups related settings to avoid a flat namespace explosion.
 * - Adding a new field = add with a default value (backward-compat with existing DataStore).
 * - Removing a field = keep the DataStore key orphaned (never reclaim a key to avoid collisions).
 */
data class AppSettings(
    val appearance: Appearance = Appearance(),
    val scanner: Scanner = Scanner(),
    val privacy: Privacy = Privacy(),
    /**
     * Packages explicitly excluded from batch actions (uninstall, force stop,
     * cache clean) and from background-scan notifications. Phase VI feature.
     * Stored as a [Set] of package names in DataStore via [androidx.datastore.preferences.core.stringSetPreferencesKey].
     */
    val ignoredPackages: Set<String> = emptySet(),
) {

    /**
     * Visual presentation preferences.
     */
    data class Appearance(
        /** Use the system's dynamic colour scheme (Material You, API 31+). */
        val dynamicColor: Boolean = true,
        /** Dark / light / system-follow. */
        val themeMode: ThemeMode = ThemeMode.SYSTEM,
        /** App list sort order. */
        val appSortOrder: AppSortOrder = AppSortOrder.NAME_ASC,
    )

    /**
     * Scanner and analysis settings.
     */
    data class Scanner(
        /** Include system apps in scans. */
        val includeSystemApps: Boolean = false,
        /** Automatically scan on launch. */
        val autoScanOnLaunch: Boolean = false,
        /** Include files in the analysis (Phase IX: CorpseFinder, Duplicates). */
        val includeFileAnalysis: Boolean = true,
        /** Background scan cadence — Phase VI WorkManager periodic worker. */
        val autoScanInterval: ScanInterval = ScanInterval.OFF,
        /** Cache size threshold in MB above which a notification is fired (0 = disabled). */
        val cacheThresholdMb: Int = 0,
        /** Days without use before an app is flagged "rarely used". */
        val rarelyUsedThresholdDays: Int = 30,
        /** Preferred export format for the Storage report. */
        val exportFormat: ExportFormat = ExportFormat.JSON,
    )

    /**
     * Privacy and security preferences.
     */
    data class Privacy(
        /** Prevent screenshots and app-switcher previews. */
        val flagSecure: Boolean = false,
        /** Confirm before deleting cache. */
        val confirmBeforeDelete: Boolean = true,
    )
}
