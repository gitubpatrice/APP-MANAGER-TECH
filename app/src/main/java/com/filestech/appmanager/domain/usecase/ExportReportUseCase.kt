package com.filestech.appmanager.domain.usecase

import android.content.Context
import android.net.Uri
import com.filestech.appmanager.core.result.AppError
import com.filestech.appmanager.core.result.Outcome
import com.filestech.appmanager.core.result.runCatchingOutcome
import com.filestech.appmanager.data.system.PdfDocumentBuilder
import com.filestech.appmanager.domain.model.ExportFormat
import com.filestech.appmanager.di.IoDispatcher
import com.filestech.appmanager.domain.model.AppSummary
import com.filestech.appmanager.domain.model.BackupSnapshot
import com.filestech.appmanager.domain.model.ExportReport
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Writes a [BackupSnapshot] to a user-chosen [Uri] (Storage Access Framework
 * Document) in either CSV or JSON.
 *
 * The UI is expected to obtain [destination] via `ActivityResultContracts.CreateDocument`
 * with MIME type `application/json` or `text/csv` — that gives the user
 * full control over storage location without needing the dangerous
 * MANAGE_EXTERNAL_STORAGE permission.
 *
 * Format choices are encoded as enum (no `when` strings flying around).
 *
 * No third-party JSON dep — we hand-write the small JSON / CSV here. Keeps
 * APK small and avoids reflection-based serialisation surprises.
 */
class ExportReportUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
    private val backup: BackupAppListUseCase,
    private val buildDiagnostic: BuildDiagnosticReportUseCase,
    private val pdfBuilder: PdfDocumentBuilder,
) {

    suspend operator fun invoke(
        destination: Uri,
        format: ExportFormat,
        includeSystemApps: Boolean = false,
    ): Outcome<ExportReport> = runCatchingOutcome(mapError = { AppError.Unknown(it) }) {
        when (format) {
            ExportFormat.JSON,
            ExportFormat.CSV -> exportBackup(destination, format, includeSystemApps)
            ExportFormat.PDF -> exportDiagnosticPdf(destination)
        }
    }

    // -----------------------------------------------------------------------
    // JSON / CSV — lightweight backup snapshot
    // -----------------------------------------------------------------------

    private suspend fun exportBackup(
        destination: Uri,
        format: ExportFormat,
        includeSystemApps: Boolean,
    ): ExportReport {
        val snapshot = when (val r = backup(includeSystemApps)) {
            is Outcome.Success -> r.value
            is Outcome.Failure -> throw IllegalStateException(r.error.toString())
            Outcome.Loading    -> throw IllegalStateException("Unexpected Loading")
        }
        val payload = when (format) {
            ExportFormat.JSON -> renderJson(snapshot).toByteArray(Charsets.UTF_8)
            ExportFormat.CSV  -> renderCsv(snapshot).toByteArray(Charsets.UTF_8)
            ExportFormat.PDF  -> error("PDF dispatched separately")
        }
        val displayPath = destination.lastPathSegment ?: destination.toString()
        withContext(io) {
            context.contentResolver.openOutputStream(destination, "wt")
                ?.use { it.write(payload) }
                ?: throw java.io.IOException("Cannot open output stream for $destination")
        }
        return ExportReport(
            format       = format,
            bytesWritten = payload.size.toLong(),
            appCount     = snapshot.apps.size,
            displayPath  = displayPath,
        )
    }

    // -----------------------------------------------------------------------
    // PDF — diagnostic report
    // -----------------------------------------------------------------------

    /**
     * Streams the PDF directly to the SAF Uri — keeps memory bounded for large
     * inventories (every page is written incrementally by PdfDocument.writeTo).
     *
     * The caller's `ActivityResultContracts.CreateDocument("application/pdf")`
     * gives us the right MIME on disk; no need to override it here.
     */
    private suspend fun exportDiagnosticPdf(destination: Uri): ExportReport {
        val report = when (val r = buildDiagnostic()) {
            is Outcome.Success -> r.value
            is Outcome.Failure -> throw IllegalStateException(r.error.toString())
            Outcome.Loading    -> throw IllegalStateException("Unexpected Loading")
        }
        val displayPath = destination.lastPathSegment ?: destination.toString()
        val written = withContext(io) {
            context.contentResolver.openOutputStream(destination, "wt")
                ?.use { stream -> pdfBuilder.render(report, stream) }
                ?: throw java.io.IOException("Cannot open output stream for $destination")
        }
        return ExportReport(
            format       = ExportFormat.PDF,
            bytesWritten = written,
            appCount     = report.inventory.size,
            displayPath  = displayPath,
        )
    }

    // -----------------------------------------------------------------------
    // Renderers — minimal hand-written serialisers, no reflection dependency.
    // -----------------------------------------------------------------------

    private fun renderJson(snapshot: BackupSnapshot): String = buildString {
        append("{\n")
        append("  \"createdAt\": ").append(snapshot.createdAt).append(",\n")
        append("  \"appCount\": ").append(snapshot.apps.size).append(",\n")
        append("  \"apps\": [\n")
        snapshot.apps.forEachIndexed { i, app ->
            append("    ").append(app.toJsonLine())
            if (i < snapshot.apps.lastIndex) append(",")
            append("\n")
        }
        append("  ]\n")
        append("}\n")
    }

    private fun AppSummary.toJsonLine(): String = buildString {
        append("{")
        append("\"package\":\"").append(esc(packageName)).append("\",")
        append("\"label\":\"").append(esc(label)).append("\",")
        append("\"versionName\":\"").append(esc(versionName)).append("\",")
        append("\"versionCode\":").append(versionCode).append(",")
        append("\"installer\":").append(installerPackage?.let { "\"${esc(it)}\"" } ?: "null").append(",")
        append("\"installBytes\":").append(installSizeBytes).append(",")
        append("\"dataBytes\":").append(dataSizeBytes).append(",")
        append("\"cacheBytes\":").append(cacheSizeBytes).append(",")
        append("\"firstInstall\":").append(firstInstallTime).append(",")
        append("\"lastUsed\":").append(lastUsedTime).append(",")
        append("\"system\":").append(isSystemApp)
        append("}")
    }

    /**
     * VII M-7 fix: handle every control char from U+0000 to U+001F (tab,
     * carriage return, etc.), not just `\n`. A package label or installer
     * package picked up by an OEM-shipped app could legitimately contain a
     * tab or other control char and produce invalid JSON otherwise.
     */
    private fun esc(s: String): String = buildString(capacity = s.length + 8) {
        for (c in s) when (c) {
            '\\' -> append("\\\\")
            '"'  -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            '\b' -> append("\\b")
            else -> if (c.code < 0x20) {
                append("\\u").append("%04X".format(c.code))
            } else {
                append(c)
            }
        }
    }

    private fun renderCsv(snapshot: BackupSnapshot): String = buildString {
        append("package,label,versionName,versionCode,installer,installBytes,dataBytes,cacheBytes,firstInstall,lastUsed,system\n")
        snapshot.apps.forEach { app ->
            append(csvField(app.packageName)).append(',')
            append(csvField(app.label)).append(',')
            append(csvField(app.versionName)).append(',')
            append(app.versionCode).append(',')
            append(csvField(app.installerPackage.orEmpty())).append(',')
            append(app.installSizeBytes).append(',')
            append(app.dataSizeBytes).append(',')
            append(app.cacheSizeBytes).append(',')
            append(app.firstInstallTime).append(',')
            append(app.lastUsedTime).append(',')
            append(app.isSystemApp).append('\n')
        }
    }

    private fun csvField(s: String): String =
        if (s.any { it == ',' || it == '"' || it == '\n' }) {
            "\"" + s.replace("\"", "\"\"") + "\""
        } else {
            s
        }
}
