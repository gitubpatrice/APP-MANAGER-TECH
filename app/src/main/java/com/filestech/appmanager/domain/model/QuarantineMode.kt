package com.filestech.appmanager.domain.model

/**
 * Two strategies for the App Quarantine feature.
 *
 * Lives in `domain/model/` (not in `data/`) so use cases and ViewModels can
 * branch on the enum without touching the persistence layer.
 *
 * The persisted name is the [name] of the enum constant — never rename
 * existing constants (would break backwards compat with existing
 * `quarantine_entry.mode` rows on user devices).
 */
enum class QuarantineMode {

    /**
     * **Real quarantine** — the user opts in to lose the app's data.
     *
     * Flow (non-root Android — no API to disable a user-installed app):
     *  1. App Manager copies the app's base APK from `ApplicationInfo.sourceDir`
     *     to a user-picked SAF tree URI (persistable permission grant required).
     *  2. App Manager fires `Intent.ACTION_DELETE` → OS shows uninstall dialog.
     *  3. Once `PackageMonitor` confirms uninstall, the entry is recorded with
     *     mode = HARD_UNINSTALL and `apkBackupUri` set.
     *  4. On expiry (or manual restore), App Manager fires `Intent.ACTION_VIEW`
     *     on the backup APK URI → PackageInstaller re-installs.
     *
     * IMPORTANT: app data (preferences, accounts, files) is permanently lost
     * at uninstall — there is no way to restore it without root. The UI MUST
     * surface this warning before confirmation.
     */
    HARD_UNINSTALL,

    /**
     * **Reminder-only** — the user disables the app themselves via OS Settings
     * (or simply removes it from their workflow). App Manager just records
     * the intent and fires a notification at `restoreAt` so they can decide
     * whether to re-enable.
     *
     * No `apkBackupUri`, no uninstall intent, no data loss. The "restore"
     * action in HARD mode becomes a "remind me" deep-link to OS Settings for
     * the app in SOFT mode.
     */
    SOFT_REMINDER,
}
