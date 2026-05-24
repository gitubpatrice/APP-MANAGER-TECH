package com.filestech.appmanager.domain.model

/**
 * v0.2.2 — Pure domain payload for the Diagnostic PDF export.
 *
 * Built by `BuildDiagnosticReportUseCase`, consumed by the PDF renderer.
 * Deliberately split from [ExportReport] (which describes the OUTCOME of an
 * export call) — `DiagnosticReport` is the SUBJECT that gets serialised.
 *
 * The renderer can lay this out without any Android framework imports beyond
 * the PDF API itself. Strings come pre-localised from the UseCase so the
 * renderer never reaches for resources.
 */
data class DiagnosticReport(
    val generatedAtMs: Long,
    val device: DeviceProfile,
    val app: AppManagerProfile,
    val inventory: List<InventoryRow>,
    val dangerousPermsApps: List<DangerousPermsRow>,
    val sideloadedApps: List<InventoryRow>,
    val sensitiveAccessApps: List<SensitiveAccessRow>,
    val issues: IssuesSummary,
    /**
     * v0.3.2 — Lifecycle journal section (newest first). Empty when the
     * Lifecycle History feature is disabled or no events have been recorded
     * yet — the PDF renderer hides the section header in that case.
     */
    val lifecycleJournal: List<LifecycleJournalRow> = emptyList(),
) {
    data class DeviceProfile(
        val manufacturer: String,
        val model: String,
        val androidRelease: String,
        val sdkInt: Int,
        val totalStorageBytes: Long,
        val freeStorageBytes: Long,
    )

    /**
     * App Manager Tech's own profile in the report — version, our own signing
     * SHA-256, number of apps tracked. Lets a support technician confirm
     * "this report was produced by AMT vX.Y.Z" without rereading the header.
     */
    data class AppManagerProfile(
        val versionName: String,
        val versionCode: Long,
        val signatureSha256: String?,
        val appsTracked: Int,
    )

    /**
     * One row per user-installed app in the inventory table.
     * Pre-formatted size string keeps the renderer free of locale concerns.
     */
    data class InventoryRow(
        val label: String,
        val packageName: String,
        val versionName: String,
        val installerLabel: String,
        val totalBytes: Long,
    )

    data class DangerousPermsRow(
        val label: String,
        val packageName: String,
        /** Sorted list of permission *short names* (after the last dot). */
        val grantedDangerous: List<String>,
    )

    enum class SensitiveAccessKind { DEVICE_ADMIN, ACCESSIBILITY }

    data class SensitiveAccessRow(
        val label: String,
        val packageName: String,
        val kind: SensitiveAccessKind,
    )

    data class IssuesSummary(
        val zombiesCount: Int,
        val rarelyUsedCount: Int,
        val oversizedCount: Int,
        val sideloadedCount: Int,
    ) {
        val hasAny: Boolean get() = zombiesCount + rarelyUsedCount + oversizedCount + sideloadedCount > 0
    }

    /**
     * v0.3.2 — One row per lifecycle event (BASELINE / INSTALLED /
     * UNINSTALLED / REPLACED). Pre-formatted strings keep the renderer
     * free of locale concerns.
     */
    data class LifecycleJournalRow(
        val capturedAtMs: Long,
        val packageName: String,
        val label: String?,
        val typeLabel: String,
        val versionLabel: String,
        val reasonLabel: String?,
    )
}
