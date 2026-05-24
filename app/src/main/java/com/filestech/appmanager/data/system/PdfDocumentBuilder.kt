package com.filestech.appmanager.data.system

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.format.Formatter
import com.filestech.appmanager.R
import com.filestech.appmanager.domain.model.DiagnosticReport
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.OutputStream
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * v0.2.2 — Renders a [DiagnosticReport] into a PDF written to [output] via
 * `android.graphics.pdf.PdfDocument` — native AOSP API (API 19+), zero
 * external dependency, F-Droid friendly.
 *
 * Layout choices:
 * - A4 portrait (595 × 842 pt at 72 dpi).
 * - 36 pt margin on every side.
 * - Title page header on page 1 only; subsequent pages start straight at the
 *   section header.
 * - Footer with `Page N / M` re-rendered after the full page count is known.
 * - Sections that overflow a page wrap to the next; the renderer never
 *   silently truncates content.
 * - Monospace font for technical fields (package names, signatures, paths,
 *   permissions).
 *
 * **Thread safety**: each call to [render] builds a fresh [PdfDocument]. The
 * function is non-suspending; callers must wrap in `withContext(io)`.
 */
@Singleton
class PdfDocumentBuilder @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textSize = 16f
        color    = Color.BLACK
    }
    private val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textSize = 12f
        color    = Color.rgb(0x24, 0x60, 0xAB) // BrandBlue
    }
    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT
        textSize = 10f
        color    = Color.BLACK
    }
    private val monoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        textSize = 9f
        color    = Color.DKGRAY
    }
    private val mutedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT
        textSize = 9f
        color    = Color.DKGRAY
    }
    private val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT
        textSize = 8f
        color    = Color.GRAY
    }

    /**
     * Renders [report] into [output]. Closes the underlying PdfDocument but
     * NOT [output] — the caller owns the stream lifecycle (SAF requires the
     * caller to close it via `use {}` semantics).
     *
     * @return number of bytes written to [output].
     */
    fun render(report: DiagnosticReport, output: OutputStream): Long {
        val doc = PdfDocument()
        try {
            val pageMaker = PageMaker(doc, headerOnFirstPage = { canvas -> drawHeader(canvas, report) })
            drawDeviceSection(pageMaker, report.device)
            drawAppManagerSection(pageMaker, report.app)
            drawIssuesSection(pageMaker, report.issues)
            drawInventorySection(pageMaker, report.inventory)
            drawDangerousPermsSection(pageMaker, report.dangerousPermsApps)
            drawSideloadedSection(pageMaker, report.sideloadedApps)
            drawSensitiveAccessSection(pageMaker, report.sensitiveAccessApps)
            // v0.3.2 — Lifecycle journal section (last). Hidden when empty so
            // users who never enabled the Lifecycle feature don't see a
            // confusing "Aucune entrée" line in their diagnostic.
            drawLifecycleJournalSection(pageMaker, report.lifecycleJournal)
            pageMaker.finalizeLastPage()

            val counting = CountingOutputStream(output)
            doc.writeTo(counting)
            return counting.bytesWritten
        } finally {
            doc.close()
        }
    }

    // -----------------------------------------------------------------------
    // Sections
    // -----------------------------------------------------------------------

    private fun drawHeader(canvas: Canvas, report: DiagnosticReport) {
        var y = MARGIN_TOP + titlePaint.textSize
        canvas.drawText(context.getString(R.string.pdf_title), MARGIN_LEFT, y, titlePaint)
        y += 16f
        val ts = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            .format(Date(report.generatedAtMs))
        canvas.drawText(
            context.getString(R.string.pdf_generated_at, ts),
            MARGIN_LEFT, y, mutedPaint,
        )
    }

    private fun drawDeviceSection(p: PageMaker, device: DiagnosticReport.DeviceProfile) {
        p.drawSection(context.getString(R.string.pdf_section_device))
        p.drawKv(context.getString(R.string.pdf_label_manufacturer), device.manufacturer)
        p.drawKv(context.getString(R.string.pdf_label_model),        device.model)
        p.drawKv(
            context.getString(R.string.pdf_label_android),
            context.getString(R.string.pdf_label_android) + " " + device.androidRelease +
                "  (" + context.getString(R.string.pdf_label_sdk) + " " + device.sdkInt + ")",
        )
        p.drawKv(
            context.getString(R.string.pdf_label_total_storage),
            Formatter.formatShortFileSize(context, device.totalStorageBytes),
        )
        p.drawKv(
            context.getString(R.string.pdf_label_free_storage),
            Formatter.formatShortFileSize(context, device.freeStorageBytes),
        )
    }

    private fun drawAppManagerSection(p: PageMaker, app: DiagnosticReport.AppManagerProfile) {
        p.drawSection(context.getString(R.string.pdf_section_amt))
        p.drawKv(
            context.getString(R.string.pdf_label_amt_version),
            app.versionName + " (" + app.versionCode + ")",
        )
        p.drawKv(context.getString(R.string.pdf_label_amt_apps_tracked), app.appsTracked.toString())
        if (app.signatureSha256 != null) {
            p.drawKvMono(context.getString(R.string.pdf_label_amt_signature), app.signatureSha256)
        }
    }

    private fun drawIssuesSection(p: PageMaker, issues: DiagnosticReport.IssuesSummary) {
        p.drawSection(context.getString(R.string.pdf_section_issues))
        if (!issues.hasAny) {
            p.drawBody(context.getString(R.string.pdf_issue_none))
            return
        }
        if (issues.zombiesCount > 0) {
            p.drawBody(context.getString(R.string.pdf_issue_zombies, issues.zombiesCount))
        }
        if (issues.rarelyUsedCount > 0) {
            p.drawBody(context.getString(R.string.pdf_issue_rarely_used, issues.rarelyUsedCount))
        }
        if (issues.oversizedCount > 0) {
            p.drawBody(context.getString(R.string.pdf_issue_oversized, issues.oversizedCount))
        }
        if (issues.sideloadedCount > 0) {
            p.drawBody(context.getString(R.string.pdf_issue_sideloaded, issues.sideloadedCount))
        }
    }

    private fun drawInventorySection(
        p: PageMaker,
        inventory: List<DiagnosticReport.InventoryRow>,
    ) {
        p.drawSection(context.getString(R.string.pdf_section_inventory))
        if (inventory.isEmpty()) {
            p.drawBody(context.getString(R.string.pdf_no_apps))
            return
        }
        p.drawBody(context.getString(R.string.pdf_inventory_header))
        inventory.forEach { row ->
            p.drawBody(row.label + "  —  " + row.versionName + "  —  " + row.installerLabel +
                "  —  " + Formatter.formatShortFileSize(context, row.totalBytes))
            p.drawMono(row.packageName)
        }
    }

    private fun drawDangerousPermsSection(
        p: PageMaker,
        rows: List<DiagnosticReport.DangerousPermsRow>,
    ) {
        p.drawSection(context.getString(R.string.pdf_section_dangerous_perms))
        if (rows.isEmpty()) {
            p.drawBody(context.getString(R.string.pdf_no_apps))
            return
        }
        rows.forEach { row ->
            p.drawBody(row.label)
            p.drawMono(row.packageName)
            p.drawMuted(row.grantedDangerous.joinToString(", "))
        }
    }

    private fun drawSideloadedSection(
        p: PageMaker,
        rows: List<DiagnosticReport.InventoryRow>,
    ) {
        p.drawSection(context.getString(R.string.pdf_section_sideloaded))
        if (rows.isEmpty()) {
            p.drawBody(context.getString(R.string.pdf_no_apps))
            return
        }
        rows.forEach { row ->
            p.drawBody(row.label + "  —  " + row.installerLabel)
            p.drawMono(row.packageName)
        }
    }

    private fun drawSensitiveAccessSection(
        p: PageMaker,
        rows: List<DiagnosticReport.SensitiveAccessRow>,
    ) {
        p.drawSection(context.getString(R.string.pdf_section_sensitive_access))
        if (rows.isEmpty()) {
            p.drawBody(context.getString(R.string.pdf_no_apps))
            return
        }
        rows.forEach { row ->
            val kind = when (row.kind) {
                DiagnosticReport.SensitiveAccessKind.DEVICE_ADMIN  -> "DEVICE_ADMIN"
                DiagnosticReport.SensitiveAccessKind.ACCESSIBILITY -> "ACCESSIBILITY"
            }
            p.drawBody(row.label + "  —  " + kind)
            p.drawMono(row.packageName)
        }
    }

    /**
     * v0.3.2 — Lifecycle journal section. Hidden when the journal is empty
     * (no events recorded yet — usually means the user hasn't enabled the
     * feature). Pre-formatted strings come from
     * [BuildDiagnosticReportUseCase] so the renderer doesn't reach for
     * Android resources.
     */
    private fun drawLifecycleJournalSection(
        p: PageMaker,
        rows: List<DiagnosticReport.LifecycleJournalRow>,
    ) {
        if (rows.isEmpty()) return
        p.drawSection(context.getString(R.string.pdf_section_lifecycle))
        val dateFormat = java.text.DateFormat.getDateTimeInstance(
            java.text.DateFormat.MEDIUM,
            java.text.DateFormat.SHORT,
        )
        rows.forEach { row ->
            val label = row.label ?: row.packageName
            p.drawBody(
                dateFormat.format(java.util.Date(row.capturedAtMs)) +
                    "  ·  " + row.typeLabel +
                    "  ·  " + label,
            )
            p.drawMono(row.packageName + "  ·  " + row.versionLabel)
            if (row.reasonLabel != null) {
                p.drawMuted("→ " + row.reasonLabel)
            }
        }
    }

    // -----------------------------------------------------------------------
    // Page maker — handles A4 layout + line wrapping + auto page-break
    // -----------------------------------------------------------------------

    /**
     * Encapsulates the cursor-style "draw + advance Y, break to next page if
     * needed" loop. Keeps the section renderers above free of pagination
     * bookkeeping.
     */
    private inner class PageMaker(
        private val doc: PdfDocument,
        private val headerOnFirstPage: (Canvas) -> Unit,
    ) {
        private var pageNumber = 0
        private var currentPage: PdfDocument.Page? = null
        private var currentCanvas: Canvas? = null
        private var y: Float = 0f

        private fun newPage() {
            currentPage?.let { doc.finishPage(it) }
            pageNumber += 1
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            val page = doc.startPage(pageInfo)
            currentPage = page
            currentCanvas = page.canvas
            if (pageNumber == 1) {
                headerOnFirstPage(page.canvas)
                y = HEADER_Y_AFTER_FIRST_PAGE
            } else {
                y = MARGIN_TOP + bodyPaint.textSize
            }
        }

        fun finalizeLastPage() {
            // Stamp the footer on the page that's still open before we close
            // it — without this the last page would ship without page number.
            if (currentPage != null) {
                drawFooter()
            }
            currentPage?.let { doc.finishPage(it); currentPage = null }
        }

        private fun ensureSpace(neededHeight: Float) {
            if (currentPage == null || y + neededHeight > PAGE_HEIGHT - FOOTER_HEIGHT) {
                drawFooter()
                newPage()
            }
        }

        /**
         * Footer is drawn when a page is about to be finished (because
         * PdfDocument disallows re-opening a finished page for editing). We
         * therefore show "Page N" only — the total is unknown until all
         * sections have been laid out, and a second pass would double the
         * rendering cost for negligible UX value.
         */
        private fun drawFooter() {
            val canvas = currentCanvas ?: return
            val text = context.getString(R.string.pdf_page_footer, pageNumber)
            canvas.drawText(
                text,
                (PAGE_WIDTH - footerPaint.measureText(text)) / 2f,
                PAGE_HEIGHT - 18f,
                footerPaint,
            )
        }

        fun drawSection(title: String) {
            ensureSpace(28f)
            val canvas = currentCanvas ?: return
            y += 8f
            canvas.drawText(title, MARGIN_LEFT, y, sectionPaint)
            y += 6f
            // underline
            canvas.drawLine(
                MARGIN_LEFT, y, PAGE_WIDTH - MARGIN_LEFT, y, sectionPaint,
            )
            y += 10f
        }

        fun drawKv(key: String, value: String) {
            drawTwoColumn(key, value, bodyPaint)
        }

        fun drawKvMono(key: String, value: String) {
            drawTwoColumn(key, value, monoPaint)
        }

        private fun drawTwoColumn(key: String, value: String, valuePaint: Paint) {
            ensureSpace(14f)
            val canvas = currentCanvas ?: return
            canvas.drawText(key, MARGIN_LEFT, y, bodyPaint)
            // Wrap the value if it exceeds the right column width.
            val valueX = MARGIN_LEFT + KEY_COLUMN_WIDTH
            val available = PAGE_WIDTH - valueX - MARGIN_LEFT
            wrap(value, available, valuePaint).forEachIndexed { idx, line ->
                if (idx > 0) {
                    ensureSpace(valuePaint.textSize + 2f)
                    y += valuePaint.textSize + 2f
                }
                currentCanvas?.drawText(line, valueX, y, valuePaint)
            }
            y += 12f
        }

        fun drawBody(text: String) {
            drawWrapped(text, bodyPaint, leading = 12f)
        }

        fun drawMono(text: String) {
            drawWrapped(text, monoPaint, leading = 11f)
        }

        fun drawMuted(text: String) {
            drawWrapped(text, mutedPaint, leading = 11f)
        }

        private fun drawWrapped(text: String, paint: Paint, leading: Float) {
            ensureSpace(leading)
            val available = PAGE_WIDTH - 2 * MARGIN_LEFT
            wrap(text, available, paint).forEach { line ->
                ensureSpace(leading)
                currentCanvas?.drawText(line, MARGIN_LEFT, y, paint)
                y += leading
            }
        }

        /**
         * Naive greedy word-wrap. Splits on spaces; long unbreakable tokens
         * (paths, signatures) fall through as a single line that may bleed
         * slightly past the right margin. The 80-char ABI / SHA-256 strings
         * we handle fit comfortably within A4 width at 9pt mono.
         */
        private fun wrap(text: String, maxWidth: Float, paint: Paint): List<String> {
            if (paint.measureText(text) <= maxWidth) return listOf(text)
            val words = text.split(' ')
            val lines = mutableListOf<StringBuilder>()
            var current = StringBuilder()
            for (word in words) {
                val candidate = if (current.isEmpty()) word else current.toString() + " " + word
                if (paint.measureText(candidate) > maxWidth && current.isNotEmpty()) {
                    lines.add(current)
                    current = StringBuilder(word)
                } else {
                    if (current.isNotEmpty()) current.append(' ')
                    current.append(word)
                }
            }
            if (current.isNotEmpty()) lines.add(current)
            return lines.map { it.toString() }
        }

        init {
            newPage()
        }
    }

    /** Tracks bytes flushed to the underlying stream for the return value. */
    private class CountingOutputStream(private val delegate: OutputStream) : OutputStream() {
        var bytesWritten: Long = 0L
            private set

        override fun write(b: Int) {
            delegate.write(b)
            bytesWritten += 1
        }
        override fun write(b: ByteArray, off: Int, len: Int) {
            delegate.write(b, off, len)
            bytesWritten += len
        }
        override fun flush() { delegate.flush() }
    }

    private companion object {
        // A4 portrait @ 72 dpi.
        const val PAGE_WIDTH: Int = 595
        const val PAGE_HEIGHT: Int = 842
        const val MARGIN_LEFT: Float = 36f
        const val MARGIN_TOP: Float = 36f
        const val FOOTER_HEIGHT: Float = 28f

        /** Y to start drawing on page 1 after the title header. */
        const val HEADER_Y_AFTER_FIRST_PAGE: Float = 90f

        /** Left column width for Key/Value rows. */
        const val KEY_COLUMN_WIDTH: Float = 140f
    }
}
