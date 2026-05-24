package com.filestech.appmanager.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.filestech.appmanager.domain.model.AppSortOrder
import com.filestech.appmanager.domain.model.ExportFormat
import com.filestech.appmanager.domain.model.ScanInterval
import com.filestech.appmanager.domain.model.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// ---------------------------------------------------------------------------
// DataStore instance — one per application (process-scoped)
// ---------------------------------------------------------------------------
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "app_manager_tech_settings",
)

/**
 * Concrete DataStore<Preferences> implementation of [SettingsRepository].
 *
 * Key naming convention: <group>_<field>
 * Example: "appearance_dynamic_color", "scanner_include_system_apps"
 *
 * Backward-compatibility rule:
 * - Never reuse a retired key name.
 * - Never change a key's type.
 * - New keys always have a default value in [AppSettings].
 *
 * Phase VI additions: scanner_auto_scan_interval, scanner_cache_threshold_mb,
 * scanner_rarely_used_threshold_days, scanner_export_format, ignored_packages.
 */
@Singleton
class SettingsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : SettingsRepository {

    // ---------------------------------------------------------------------------
    // Preference keys
    // ---------------------------------------------------------------------------
    private object Keys {
        // Appearance
        val DYNAMIC_COLOR              = booleanPreferencesKey("appearance_dynamic_color")
        val THEME_MODE                 = stringPreferencesKey("appearance_theme_mode")
        val APP_SORT_ORDER             = stringPreferencesKey("appearance_app_sort_order")

        // Scanner
        val INCLUDE_SYSTEM_APPS        = booleanPreferencesKey("scanner_include_system_apps")
        val AUTO_SCAN_ON_LAUNCH        = booleanPreferencesKey("scanner_auto_scan_on_launch")
        val INCLUDE_FILE_ANALYSIS      = booleanPreferencesKey("scanner_include_file_analysis")
        // Phase VI
        val AUTO_SCAN_INTERVAL         = stringPreferencesKey("scanner_auto_scan_interval")
        val CACHE_THRESHOLD_MB         = intPreferencesKey("scanner_cache_threshold_mb")
        val RARELY_USED_THRESHOLD_DAYS = intPreferencesKey("scanner_rarely_used_threshold_days")
        val EXPORT_FORMAT              = stringPreferencesKey("scanner_export_format")

        // Privacy
        val FLAG_SECURE                = booleanPreferencesKey("privacy_flag_secure")
        val CONFIRM_BEFORE_DELETE      = booleanPreferencesKey("privacy_confirm_before_delete")

        // Privacy Monitor (v0.2.0 — Permission Drift Tracker)
        val PMON_DRIFT_ENABLED         = booleanPreferencesKey("privacy_monitor_drift_enabled")
        val PMON_DRIFT_RETENTION_DAYS  = intPreferencesKey("privacy_monitor_drift_retention_days")
        val PMON_DRIFT_INCLUDE_SYSTEM  = booleanPreferencesKey("privacy_monitor_drift_include_system")
        val PMON_DRIFT_NOTIFY          = booleanPreferencesKey("privacy_monitor_drift_notify")

        // Quarantine (v0.2.0)
        val QUAR_BACKUP_TREE_URI       = stringPreferencesKey("quarantine_backup_tree_uri")
        val QUAR_RESTORE_REMINDER      = booleanPreferencesKey("quarantine_restore_reminder")

        // Lifecycle History (v0.3.0)
        val LIFECYCLE_ENABLED          = booleanPreferencesKey("lifecycle_enabled")
        val LIFECYCLE_RETENTION_DAYS   = intPreferencesKey("lifecycle_retention_days")
        val LIFECYCLE_PROMPT_REASON    = booleanPreferencesKey("lifecycle_prompt_reason")

        // Safety Guardrails user-customisation (v0.3.1)
        val SAFETY_USER_PROTECTED      = stringSetPreferencesKey("safety_user_protected_packages")

        // User-assigned tags (v0.3.3) — set of `pkg=ENUM_NAME` entries.
        val APP_TAGS                   = stringSetPreferencesKey("app_tags")

        // Ignore list (Phase VI)
        val IGNORED_PACKAGES           = stringSetPreferencesKey("ignored_packages")
    }

    // ---------------------------------------------------------------------------
    // Deserialisation — single source of truth for Preferences → AppSettings.
    // Used by both [flow] (read path) and [update] (read-modify-write path).
    // Adding a setting here automatically covers both code paths.
    // ---------------------------------------------------------------------------
    private fun Preferences.toAppSettings(): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            appearance = AppSettings.Appearance(
                dynamicColor = this[Keys.DYNAMIC_COLOR]
                    ?: defaults.appearance.dynamicColor,
                themeMode = this[Keys.THEME_MODE]
                    ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                    ?: defaults.appearance.themeMode,
                appSortOrder = this[Keys.APP_SORT_ORDER]
                    ?.let { runCatching { AppSortOrder.valueOf(it) }.getOrNull() }
                    ?: defaults.appearance.appSortOrder,
            ),
            scanner = AppSettings.Scanner(
                includeSystemApps = this[Keys.INCLUDE_SYSTEM_APPS]
                    ?: defaults.scanner.includeSystemApps,
                autoScanOnLaunch = this[Keys.AUTO_SCAN_ON_LAUNCH]
                    ?: defaults.scanner.autoScanOnLaunch,
                includeFileAnalysis = this[Keys.INCLUDE_FILE_ANALYSIS]
                    ?: defaults.scanner.includeFileAnalysis,
                autoScanInterval = this[Keys.AUTO_SCAN_INTERVAL]
                    ?.let { runCatching { ScanInterval.valueOf(it) }.getOrNull() }
                    ?: defaults.scanner.autoScanInterval,
                cacheThresholdMb = this[Keys.CACHE_THRESHOLD_MB]
                    ?: defaults.scanner.cacheThresholdMb,
                rarelyUsedThresholdDays = this[Keys.RARELY_USED_THRESHOLD_DAYS]
                    ?: defaults.scanner.rarelyUsedThresholdDays,
                exportFormat = this[Keys.EXPORT_FORMAT]
                    ?.let { runCatching { ExportFormat.valueOf(it) }.getOrNull() }
                    ?: defaults.scanner.exportFormat,
            ),
            privacy = AppSettings.Privacy(
                flagSecure = this[Keys.FLAG_SECURE]
                    ?: defaults.privacy.flagSecure,
                confirmBeforeDelete = this[Keys.CONFIRM_BEFORE_DELETE]
                    ?: defaults.privacy.confirmBeforeDelete,
            ),
            privacyMonitor = AppSettings.PrivacyMonitor(
                permissionDriftEnabled = this[Keys.PMON_DRIFT_ENABLED]
                    ?: defaults.privacyMonitor.permissionDriftEnabled,
                permissionDriftRetentionDays = this[Keys.PMON_DRIFT_RETENTION_DAYS]
                    ?.coerceIn(MIN_RETENTION_DAYS, MAX_RETENTION_DAYS)
                    ?: defaults.privacyMonitor.permissionDriftRetentionDays,
                permissionDriftIncludeSystemApps = this[Keys.PMON_DRIFT_INCLUDE_SYSTEM]
                    ?: defaults.privacyMonitor.permissionDriftIncludeSystemApps,
                permissionDriftNotify = this[Keys.PMON_DRIFT_NOTIFY]
                    ?: defaults.privacyMonitor.permissionDriftNotify,
            ),
            quarantine = AppSettings.Quarantine(
                backupTreeUri = this[Keys.QUAR_BACKUP_TREE_URI]
                    ?: defaults.quarantine.backupTreeUri,
                restoreReminderEnabled = this[Keys.QUAR_RESTORE_REMINDER]
                    ?: defaults.quarantine.restoreReminderEnabled,
            ),
            lifecycle = AppSettings.Lifecycle(
                enabled = this[Keys.LIFECYCLE_ENABLED]
                    ?: defaults.lifecycle.enabled,
                retentionDays = this[Keys.LIFECYCLE_RETENTION_DAYS]
                    ?.coerceIn(MIN_LIFECYCLE_RETENTION_DAYS, MAX_LIFECYCLE_RETENTION_DAYS)
                    ?: defaults.lifecycle.retentionDays,
                promptReason = this[Keys.LIFECYCLE_PROMPT_REASON]
                    ?: defaults.lifecycle.promptReason,
            ),
            safety = AppSettings.Safety(
                userProtectedPackages = this[Keys.SAFETY_USER_PROTECTED]
                    ?: defaults.safety.userProtectedPackages,
            ),
            ignoredPackages = this[Keys.IGNORED_PACKAGES]
                ?: defaults.ignoredPackages,
            appTags = decodeTags(this[Keys.APP_TAGS]).ifEmpty { defaults.appTags },
        )
    }

    /**
     * v0.3.3 — Decodes the `Set<String>` of `pkg=ENUM_NAME` entries into a
     * `Map<String, AppTag>`. Tolerant : malformed entries (missing `=`,
     * empty pkg / tag, unknown enum value) are silently dropped — the
     * write path always produces well-formed entries, so a tolerant read
     * only matters under downgrade / external tampering.
     */
    private fun decodeTags(raw: Set<String>?): Map<String, com.filestech.appmanager.domain.model.AppTag> {
        if (raw.isNullOrEmpty()) return emptyMap()
        val out = HashMap<String, com.filestech.appmanager.domain.model.AppTag>(raw.size)
        for (entry in raw) {
            val sep = entry.indexOf('=')
            if (sep <= 0 || sep == entry.lastIndex) continue
            val pkg = entry.substring(0, sep).trim()
            val tag = entry.substring(sep + 1).trim()
            if (pkg.isEmpty() || tag.isEmpty()) continue
            val parsed = runCatching {
                com.filestech.appmanager.domain.model.AppTag.valueOf(tag)
            }.getOrNull() ?: continue
            out[pkg] = parsed
        }
        return out
    }

    private fun encodeTags(map: Map<String, com.filestech.appmanager.domain.model.AppTag>): Set<String> =
        map.entries.asSequence()
            .map { (pkg, tag) -> "$pkg=${tag.name}" }
            .toSet()

    // ---------------------------------------------------------------------------
    // Flow
    // ---------------------------------------------------------------------------
    override val flow: Flow<AppSettings> = context.dataStore.data.map { it.toAppSettings() }

    // ---------------------------------------------------------------------------
    // Write
    // ---------------------------------------------------------------------------
    override suspend fun update(transform: AppSettings.() -> AppSettings) {
        // DataStore.edit is atomic: the lambda reads current Preferences,
        // reconstructs the AppSettings snapshot, applies [transform], writes
        // all keys back. DataStore deduplicates keys that did not change.
        context.dataStore.edit { prefs ->
            val updated = transform(prefs.toAppSettings())
            prefs[Keys.DYNAMIC_COLOR]              = updated.appearance.dynamicColor
            prefs[Keys.THEME_MODE]                 = updated.appearance.themeMode.name
            prefs[Keys.APP_SORT_ORDER]             = updated.appearance.appSortOrder.name
            prefs[Keys.INCLUDE_SYSTEM_APPS]        = updated.scanner.includeSystemApps
            prefs[Keys.AUTO_SCAN_ON_LAUNCH]        = updated.scanner.autoScanOnLaunch
            prefs[Keys.INCLUDE_FILE_ANALYSIS]      = updated.scanner.includeFileAnalysis
            prefs[Keys.AUTO_SCAN_INTERVAL]         = updated.scanner.autoScanInterval.name
            prefs[Keys.CACHE_THRESHOLD_MB]         = updated.scanner.cacheThresholdMb
            prefs[Keys.RARELY_USED_THRESHOLD_DAYS] = updated.scanner.rarelyUsedThresholdDays
            prefs[Keys.EXPORT_FORMAT]              = updated.scanner.exportFormat.name
            prefs[Keys.FLAG_SECURE]                = updated.privacy.flagSecure
            prefs[Keys.CONFIRM_BEFORE_DELETE]      = updated.privacy.confirmBeforeDelete

            // Privacy Monitor
            prefs[Keys.PMON_DRIFT_ENABLED]         = updated.privacyMonitor.permissionDriftEnabled
            prefs[Keys.PMON_DRIFT_RETENTION_DAYS]  = updated.privacyMonitor.permissionDriftRetentionDays
                .coerceIn(MIN_RETENTION_DAYS, MAX_RETENTION_DAYS)
            prefs[Keys.PMON_DRIFT_INCLUDE_SYSTEM]  = updated.privacyMonitor.permissionDriftIncludeSystemApps
            prefs[Keys.PMON_DRIFT_NOTIFY]          = updated.privacyMonitor.permissionDriftNotify

            // Quarantine — string keys can't hold null, so we remove the key
            // when the URI is null instead of writing an empty string (which
            // would later deserialize as a non-null empty Uri).
            val treeUri = updated.quarantine.backupTreeUri
            if (treeUri.isNullOrBlank()) {
                prefs.remove(Keys.QUAR_BACKUP_TREE_URI)
            } else {
                prefs[Keys.QUAR_BACKUP_TREE_URI] = treeUri
            }
            prefs[Keys.QUAR_RESTORE_REMINDER]      = updated.quarantine.restoreReminderEnabled

            // Lifecycle (v0.3.0)
            prefs[Keys.LIFECYCLE_ENABLED]          = updated.lifecycle.enabled
            prefs[Keys.LIFECYCLE_RETENTION_DAYS]   = updated.lifecycle.retentionDays
                .coerceIn(MIN_LIFECYCLE_RETENTION_DAYS, MAX_LIFECYCLE_RETENTION_DAYS)
            prefs[Keys.LIFECYCLE_PROMPT_REASON]    = updated.lifecycle.promptReason

            // Safety (v0.3.1) — cap defensively to avoid pathologically large sets.
            prefs[Keys.SAFETY_USER_PROTECTED]      = updated.safety.userProtectedPackages
                .take(MAX_USER_PROTECTED_PACKAGES).toSet()

            // Tags (v0.3.3) — encode the Map<pkg, AppTag> as Set<"pkg=ENUM">.
            prefs[Keys.APP_TAGS]                   = encodeTags(updated.appTags)

            prefs[Keys.IGNORED_PACKAGES]           = updated.ignoredPackages
        }
    }

    private companion object {
        /** UX-clamped retention range for the drift tracker (defensive read+write). */
        const val MIN_RETENTION_DAYS = 7
        const val MAX_RETENTION_DAYS = 365

        /**
         * Lifecycle history retention floor is higher than the drift tracker's
         * because the user-facing UX is "history" not "feed" — a 7-day window
         * would defeat the purpose. Ceiling matches drift for symmetry.
         */
        const val MIN_LIFECYCLE_RETENTION_DAYS = 30
        const val MAX_LIFECYCLE_RETENTION_DAYS = 365

        /**
         * Defensive cap on the v0.3.1 user-protected list — same intent as
         * `MAX_IGNORED_PACKAGES`: a runaway add loop (paste of a long list,
         * malicious intent from external IPC if we ever expose the writer)
         * can't bloat DataStore beyond this floor. The UI rejects further
         * adds beyond the cap with a snackbar.
         */
        const val MAX_USER_PROTECTED_PACKAGES = 200
    }
}
