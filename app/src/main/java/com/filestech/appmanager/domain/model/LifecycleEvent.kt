package com.filestech.appmanager.domain.model

/**
 * v0.3.0 — One observed entry in an app's lifecycle on this device.
 *
 * Append-only by design (same invariant as [PermissionSnapshot]): rows are
 * inserted by [com.filestech.appmanager.domain.usecase.RecordLifecycleEventUseCase]
 * in reaction to OS broadcasts (`PACKAGE_ADDED`, `PACKAGE_REMOVED`,
 * `PACKAGE_REPLACED`) plus a one-shot baseline scan on first launch post-
 * upgrade. Rows are NEVER updated — `userReason` is captured at insert time
 * via the optional uninstall-reason dialog and a follow-up "edit reason" UX
 * is intentionally out of scope (would muddy the audit trail).
 *
 * Privacy: stays in the app's private Room DB only — never leaves the device,
 * no INTERNET permission declared.
 */
data class LifecycleEvent(
    val id: Long,
    val packageName: String,
    /**
     * Cached app label at the moment of capture. Null when the OS could not
     * resolve a label (rare — sharedUserId proxies). The UI falls back to
     * [packageName] in that case so a row is never blank.
     */
    val label: String?,
    val type: LifecycleEventType,
    /** Epoch ms — set by the use case, NOT by SQLite default. */
    val capturedAt: Long,
    val versionName: String?,
    val versionCode: Long,
    /**
     * Installer package at the moment of capture. Useful for forensics
     * ("this app was sideloaded last Tuesday").
     */
    val installerPackage: String?,
    /** Total footprint at capture time (install + data + cache, bytes). */
    val totalSizeBytes: Long,
    /**
     * Sorted list of granted dangerous permissions at capture time. Empty when
     * the app declared no dangerous permission (or none was granted). Encoded
     * as a comma-separated string in storage; this list is the post-decode form.
     */
    val grantedDangerousPermissions: List<String>,
    /** Free-text reason captured via the optional uninstall dialog, or null. */
    val userReason: UninstallReason?,
)

/**
 * Kind of lifecycle observation.
 *
 * Storage encoding: enum name as TEXT — never rename a value (would orphan
 * historical rows). Add new values at the END only.
 */
enum class LifecycleEventType {
    /**
     * Synthetic event inserted on first launch post-upgrade so the history
     * isn't empty for apps that were already installed before App Manager Tech
     * v0.3.0. One BASELINE row per package, per upgrade — guarded by the
     * companion [com.filestech.appmanager.domain.usecase.BaselineLifecycleScanUseCase]
     * `hasBaseline()` check.
     */
    BASELINE,

    /** Fresh install detected via `ACTION_PACKAGE_ADDED` (no replacing flag). */
    INSTALLED,

    /** Uninstall detected via `ACTION_PACKAGE_REMOVED` (no replacing flag). */
    UNINSTALLED,

    /**
     * Update detected via `ACTION_PACKAGE_REPLACED` OR
     * `ACTION_PACKAGE_ADDED/REMOVED` carrying `EXTRA_REPLACING = true`.
     */
    REPLACED,
}

/**
 * v0.3.0 — Reason category captured from the optional uninstall dialog.
 *
 * Persisted as enum name (TEXT). Add new values at the END.
 *
 * `null` on a [LifecycleEvent] = no dialog answer (user dismissed / dialog
 * disabled in Settings). The [OTHER] variant carries a free-text label in a
 * future revision; for v0.3.0 we keep the picker to fixed categories to avoid
 * shipping a text input that nobody types into.
 */
enum class UninstallReason {
    UNUSED,
    REPLACED_BY_ANOTHER,
    TOO_HEAVY,
    PRIVACY_TRACKER,
    OTHER,
}
