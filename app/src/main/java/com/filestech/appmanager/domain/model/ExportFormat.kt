package com.filestech.appmanager.domain.model

/**
 * Preferred export format for the Storage report.
 *
 * Lives in `domain/model/` (Phase VIII C5 fix) so the domain-level
 * [ExportReport] no longer has to import from `data/`.
 *
 * **Persistence**: see [ScanInterval] — same rule (do not rename values).
 */
enum class ExportFormat { JSON, CSV }
