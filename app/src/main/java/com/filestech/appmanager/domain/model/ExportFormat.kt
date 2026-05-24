package com.filestech.appmanager.domain.model

/**
 * Preferred export format for the Storage report and v0.2.2 diagnostic
 * report.
 *
 * Lives in `domain/model/` (Phase VIII C5 fix) so the domain-level
 * [ExportReport] no longer has to import from `data/`.
 *
 * **Persistence**: see [ScanInterval] — same rule (do not rename values).
 *
 * v0.2.2: [PDF] added for the diagnostic export. PDF format uses
 * `android.graphics.pdf.PdfDocument` (AOSP, API 19+) — no external
 * dependency, F-Droid-friendly.
 */
enum class ExportFormat { JSON, CSV, PDF }
