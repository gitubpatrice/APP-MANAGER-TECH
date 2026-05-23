package com.filestech.appmanager.domain.model

// ExportFormat lives in this same package now (Phase VIII C5).

/**
 * Result of an export operation produced by `ExportReportUseCase`.
 *
 * @property format     The export format actually written.
 * @property bytesWritten Size of the written file (for UI feedback).
 * @property appCount   Number of apps written into the file.
 * @property displayPath Human-readable path the user can recognise (e.g. via
 *                       DocumentFile name). Not guaranteed to be a filesystem path.
 */
data class ExportReport(
    val format: ExportFormat,
    val bytesWritten: Long,
    val appCount: Int,
    val displayPath: String,
)

/**
 * Compact JSON-friendly snapshot of one app — used both for export and for
 * backup/restore of the installed-apps list.
 */
data class AppSummary(
    val packageName: String,
    val label: String,
    val versionName: String,
    val versionCode: Long,
    val installerPackage: String?,
    val installSizeBytes: Long,
    val dataSizeBytes: Long,
    val cacheSizeBytes: Long,
    val firstInstallTime: Long,
    val lastUsedTime: Long,
    val isSystemApp: Boolean,
)

/**
 * Backup snapshot of the installed-apps list produced by `BackupAppListUseCase`.
 * Designed to be small enough to share by email or save to cloud storage.
 */
data class BackupSnapshot(
    val createdAt: Long,
    val apps: List<AppSummary>,
)
