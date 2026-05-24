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
    /** v0.2.0 — Permission Drift Tracker preferences. */
    val privacyMonitor: PrivacyMonitor = PrivacyMonitor(),
    /** v0.2.0 — App Quarantine preferences. */
    val quarantine: Quarantine = Quarantine(),
    /** v0.3.0 — App Lifecycle History preferences. */
    val lifecycle: Lifecycle = Lifecycle(),
    /** v0.3.1 — Safety Guardrails user-customisation preferences. */
    val safety: Safety = Safety(),
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
        /**
         * Use the system's dynamic colour scheme (Material You, API 31+).
         *
         * v0.1.2: default **OFF** so the app ships with a stable, predictable
         * brand palette (BrandBlue light / GitHub-style dark) regardless of
         * the user's wallpaper. The earlier `true` default produced
         * surprising pink/rose surfaces under pink wallpapers and broke the
         * design discipline mirrored from Read Files Tech. Users who prefer
         * Material You can still toggle it ON in Settings.
         */
        val dynamicColor: Boolean = false,
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

    /**
     * v0.2.0 — Permission Drift Tracker preferences.
     *
     * The Permission Drift feature is **opt-in** (default OFF) for two reasons:
     *  1. Battery: even a 24-h periodic worker is overhead users should
     *     consciously accept.
     *  2. Mental model: the feed is most useful for users who actually want
     *     to monitor supply-chain-style permission changes. Pushing it on by
     *     default would feel noisy to a casual user.
     */
    data class PrivacyMonitor(
        /** Master switch — schedules / cancels the snapshot worker. */
        val permissionDriftEnabled: Boolean = false,
        /** Days of history to keep before purging. Defaults to 90. Clamped [7, 365] in UI. */
        val permissionDriftRetentionDays: Int = 90,
        /** Include system apps in the drift feed. Default false (noise reduction). */
        val permissionDriftIncludeSystemApps: Boolean = false,
        /** Fire a notification on detected drifts. */
        val permissionDriftNotify: Boolean = true,
    )

    /**
     * v0.2.0 — App Quarantine preferences.
     *
     * [backupTreeUri] is the persistable SAF tree URI the user picked once
     * via OPEN_DOCUMENT_TREE for HARD-mode APK backups. Stored as a string
     * (the [android.net.Uri] is parsed at use site). Null = user never picked
     * a folder; UI surfaces a CTA to pick one before allowing HARD mode.
     *
     * [restoreReminderEnabled] gates the [com.filestech.appmanager.data.system.workers.QuarantineRestoreWorker]
     * scheduling. If the user disables reminders, expired entries still sit
     * in the list (in-app surface), but no notif fires.
     */
    data class Quarantine(
        val backupTreeUri: String? = null,
        val restoreReminderEnabled: Boolean = true,
    )

    /**
     * v0.3.0 — App Lifecycle History preferences.
     *
     * The feature is **opt-in** (default OFF) for the same reasons as
     * [PrivacyMonitor]: avoid surprising background work + reserve the
     * feature for users who actually want a lifecycle journal. The
     * BroadcastReceiver registration in `MainApplication.onCreate` is gated
     * by [enabled]; flipping the toggle in Settings registers / unregisters
     * the receiver at runtime.
     *
     * [retentionDays] gates the periodic [com.filestech.appmanager.data.system.workers.LifecyclePurgeWorker]
     * cutoff. Clamped to `[30, 365]` by the picker.
     *
     * [promptReason] gates the optional uninstall-reason dialog: when true
     * (and the feature is enabled), the dialog is shown after we detect an
     * UNINSTALLED event for a package that App Manager Tech tracked. The
     * dialog answer is then attached to the just-inserted event via
     * `AppLifecycleRepository.setReason`.
     */
    data class Lifecycle(
        val enabled: Boolean = false,
        val retentionDays: Int = 180,
        val promptReason: Boolean = true,
    )

    /**
     * v0.3.1 — Safety Guardrails user-customisation.
     *
     * [userProtectedPackages] is the user's own list of packages that should
     * trigger the [com.filestech.appmanager.ui.components.dialogs.CriticalWarningDialog]
     * (hold-3s) on every destructive action. Loaded by
     * [com.filestech.appmanager.data.system.CriticalAppDetector] alongside the
     * hardcoded built-in whitelists and resolved to
     * [com.filestech.appmanager.domain.model.CriticalCategory.USER_PROTECTED].
     *
     * Capped at 200 entries (defensive — same intent as the ignore-list
     * `MAX_IGNORED_PACKAGES`). The UI rejects further adds beyond the cap with
     * a snackbar.
     */
    data class Safety(
        val userProtectedPackages: Set<String> = emptySet(),
    )
}
