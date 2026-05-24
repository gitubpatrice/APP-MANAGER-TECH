package com.filestech.appmanager.domain.usecase

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import com.filestech.appmanager.BuildConfig
import com.filestech.appmanager.R
import com.filestech.appmanager.core.ext.MS_PER_DAY
import com.filestech.appmanager.core.result.AppError
import com.filestech.appmanager.core.result.Outcome
import com.filestech.appmanager.core.result.getOrNull
import com.filestech.appmanager.core.result.runCatchingOutcome
import com.filestech.appmanager.data.system.DangerousPermissionInspector
import com.filestech.appmanager.di.IoDispatcher
import com.filestech.appmanager.domain.model.AppInfo
import com.filestech.appmanager.domain.model.DiagnosticReport
import com.filestech.appmanager.domain.model.FilterOptions
import com.filestech.appmanager.domain.model.LifecycleEvent
import com.filestech.appmanager.domain.model.LifecycleEventType
import com.filestech.appmanager.domain.model.UninstallReason
import com.filestech.appmanager.domain.repository.AppInfoRepository
import com.filestech.appmanager.domain.repository.AppLifecycleRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * v0.2.2 — Collects all sections needed for the Diagnostic PDF report into a
 * single in-memory [DiagnosticReport] payload.
 *
 * Why a dedicated UseCase (instead of stuffing this into ExportReportUseCase):
 * - The PDF renderer is dumb on purpose; it consumes a pre-built model.
 * - JSON/CSV exports use [BackupAppListUseCase] (lightweight). The diagnostic
 *   needs MORE data (device profile, dangerous perms by app, device admins,
 *   accessibility services, sideloaded apps) — keeping that work isolated
 *   prevents the simple JSON/CSV path from getting slower.
 * - This UseCase wires together 4 sub-data-sources (repo + dangerous-perm
 *   inspector + device-admin UC + accessibility-service UC). Composition
 *   belongs in the domain layer.
 *
 * **Performance**: invokes [DangerousPermissionInspector.observeAll] once per
 * build (PackageManager IPC O(n_packages)) + reads `Environment` storage stats
 * (cheap). Runs on `@IoDispatcher`. Typical < 500 ms for ~200 apps.
 */
class BuildDiagnosticReportUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
    private val appInfoRepository: AppInfoRepository,
    private val dangerousInspector: DangerousPermissionInspector,
    private val getDeviceAdminApps: GetDeviceAdminAppsUseCase,
    private val getAccessibilityServiceApps: GetAccessibilityServiceAppsUseCase,
    private val lifecycleRepository: AppLifecycleRepository,
) {

    suspend operator fun invoke(): Outcome<DiagnosticReport> =
        runCatchingOutcome(mapError = { AppError.Unknown(it) }) {
            withContext(io) {
                val apps = collectUserApps()
                val deviceAdmins = getDeviceAdminApps().getOrNull().orEmpty().toSet()
                val accessibility = getAccessibilityServiceApps().getOrNull().orEmpty().toSet()
                val ourSignature = appInfoRepository
                    .getSignatureSha256(context.packageName)
                    .getOrNull()
                val lifecycle = collectLifecycleJournal(apps)

                DiagnosticReport(
                    generatedAtMs       = System.currentTimeMillis(),
                    device              = buildDeviceProfile(),
                    app                 = buildAppManagerProfile(ourSignature, apps.size),
                    inventory           = buildInventory(apps),
                    dangerousPermsApps  = buildDangerousPermsRows(apps),
                    sideloadedApps      = buildSideloadedRows(apps),
                    sensitiveAccessApps = buildSensitiveAccessRows(apps, deviceAdmins, accessibility),
                    issues              = buildIssuesSummary(apps),
                    lifecycleJournal    = lifecycle,
                )
            }
        }

    // -----------------------------------------------------------------------
    // Sections
    // -----------------------------------------------------------------------

    /**
     * Returns the cached user-installed app list. Empty when the Room cache
     * has not been populated yet — the caller's UX should rescan first or the
     * report will look empty.
     */
    private suspend fun collectUserApps(): List<AppInfo> = appInfoRepository
        .observeApps(includeSystemApps = false)
        .first()
        .getOrNull()
        .orEmpty()

    private fun buildDeviceProfile(): DiagnosticReport.DeviceProfile {
        val (total, free) = readInternalStorage()
        return DiagnosticReport.DeviceProfile(
            manufacturer      = Build.MANUFACTURER.orEmpty(),
            model             = Build.MODEL.orEmpty(),
            androidRelease    = Build.VERSION.RELEASE.orEmpty(),
            sdkInt            = Build.VERSION.SDK_INT,
            totalStorageBytes = total,
            freeStorageBytes  = free,
        )
    }

    /**
     * Uses `Environment.getDataDirectory()` (internal /data) — same partition
     * the apps live on. Catches generic exceptions because StatFs throws
     * `IllegalArgumentException` on missing mountpoint (rare emulator edge case).
     */
    private fun readInternalStorage(): Pair<Long, Long> = try {
        val stat = StatFs(Environment.getDataDirectory().path)
        val block = stat.blockSizeLong
        val total = stat.blockCountLong * block
        val free  = stat.availableBlocksLong * block
        total to free
    } catch (e: IllegalArgumentException) {
        0L to 0L
    }

    private fun buildAppManagerProfile(
        ourSignature: String?,
        appsTracked: Int,
    ): DiagnosticReport.AppManagerProfile = DiagnosticReport.AppManagerProfile(
        versionName     = BuildConfig.VERSION_NAME,
        versionCode     = BuildConfig.VERSION_CODE.toLong(),
        signatureSha256 = ourSignature,
        appsTracked     = appsTracked,
    )

    private fun buildInventory(apps: List<AppInfo>): List<DiagnosticReport.InventoryRow> =
        apps.asSequence()
            .sortedBy { it.label.lowercase() }
            .map { it.toInventoryRow() }
            .toList()

    private fun buildSideloadedRows(apps: List<AppInfo>): List<DiagnosticReport.InventoryRow> =
        apps.asSequence()
            .filter { isSideloaded(it.installerPackage) }
            .sortedBy { it.label.lowercase() }
            .map { it.toInventoryRow() }
            .toList()

    private fun AppInfo.toInventoryRow(): DiagnosticReport.InventoryRow =
        DiagnosticReport.InventoryRow(
            label          = label,
            packageName    = packageName,
            versionName    = versionName,
            installerLabel = installerHuman(installerPackage),
            totalBytes     = totalSizeBytes,
        )

    /**
     * Folds the [DangerousPermissionInspector] observations into per-app rows
     * keyed by package name, then enriches with the cached label so the PDF
     * shows "Telegram" not "org.telegram.messenger".
     */
    private fun buildDangerousPermsRows(
        apps: List<AppInfo>,
    ): List<DiagnosticReport.DangerousPermsRow> {
        val labels = apps.associateBy({ it.packageName }, { it.label })
        return dangerousInspector
            .observeAll(includeSystemApps = false)
            .filter { it.granted }
            .groupBy { it.packageName }
            .mapNotNull { (pkg, observations) ->
                val label = labels[pkg] ?: return@mapNotNull null
                DiagnosticReport.DangerousPermsRow(
                    label            = label,
                    packageName      = pkg,
                    grantedDangerous = observations
                        .map { it.permission.substringAfterLast('.') }
                        .distinct()
                        .sorted(),
                )
            }
            .sortedByDescending { it.grantedDangerous.size }
    }

    private fun buildSensitiveAccessRows(
        apps: List<AppInfo>,
        deviceAdmins: Set<String>,
        accessibility: Set<String>,
    ): List<DiagnosticReport.SensitiveAccessRow> {
        val labels = apps.associateBy({ it.packageName }, { it.label })
        val rows = ArrayList<DiagnosticReport.SensitiveAccessRow>()
        deviceAdmins.forEach { pkg ->
            rows.add(
                DiagnosticReport.SensitiveAccessRow(
                    label       = labels[pkg] ?: pkg,
                    packageName = pkg,
                    kind        = DiagnosticReport.SensitiveAccessKind.DEVICE_ADMIN,
                ),
            )
        }
        accessibility.forEach { pkg ->
            rows.add(
                DiagnosticReport.SensitiveAccessRow(
                    label       = labels[pkg] ?: pkg,
                    packageName = pkg,
                    kind        = DiagnosticReport.SensitiveAccessKind.ACCESSIBILITY,
                ),
            )
        }
        return rows.sortedBy { it.label.lowercase() }
    }

    /**
     * v0.3.2 — Pulls a flat snapshot of the lifecycle log (newest first) +
     * formats it into [DiagnosticReport.LifecycleJournalRow] rows with
     * pre-localised type / reason labels so the PDF renderer doesn't have
     * to reach for resources.
     *
     * Capped to [LIFECYCLE_JOURNAL_CAP] to keep the PDF size bounded — the
     * full history stays accessible in-app via LifecycleHistoryScreen.
     */
    private suspend fun collectLifecycleJournal(
        apps: List<AppInfo>,
    ): List<DiagnosticReport.LifecycleJournalRow> {
        val labels = apps.associateBy({ it.packageName }, { it.label })
        val events = lifecycleRepository.observeSince(sinceMs = 0L).first()
            .getOrNull()
            .orEmpty()
            .take(LIFECYCLE_JOURNAL_CAP)
        return events.map { ev ->
            DiagnosticReport.LifecycleJournalRow(
                capturedAtMs = ev.capturedAt,
                packageName  = ev.packageName,
                label        = ev.label ?: labels[ev.packageName],
                typeLabel    = formatType(ev.type),
                versionLabel = formatVersion(ev),
                reasonLabel  = ev.userReason?.let { formatReason(it) },
            )
        }
    }

    private fun formatType(type: LifecycleEventType): String = context.getString(
        when (type) {
            LifecycleEventType.BASELINE    -> R.string.lifecycle_type_baseline
            LifecycleEventType.INSTALLED   -> R.string.lifecycle_type_installed
            LifecycleEventType.UNINSTALLED -> R.string.lifecycle_type_uninstalled
            LifecycleEventType.REPLACED    -> R.string.lifecycle_type_replaced
        },
    )

    private fun formatReason(reason: UninstallReason): String = context.getString(
        when (reason) {
            UninstallReason.UNUSED              -> R.string.lifecycle_reason_unused
            UninstallReason.REPLACED_BY_ANOTHER -> R.string.lifecycle_reason_replaced_by_another
            UninstallReason.TOO_HEAVY           -> R.string.lifecycle_reason_too_heavy
            UninstallReason.PRIVACY_TRACKER     -> R.string.lifecycle_reason_privacy_tracker
            UninstallReason.OTHER               -> R.string.lifecycle_reason_other
        },
    )

    private fun formatVersion(ev: LifecycleEvent): String =
        ev.versionName?.let { "v$it (${ev.versionCode})" } ?: "—"

    private fun buildIssuesSummary(apps: List<AppInfo>): DiagnosticReport.IssuesSummary {
        val now = System.currentTimeMillis()
        val rarelyUsedThresholdMs = RARELY_USED_THRESHOLD_DAYS * MS_PER_DAY
        return DiagnosticReport.IssuesSummary(
            zombiesCount    = apps.count { !it.isEnabled },
            rarelyUsedCount = apps.count { app ->
                // `lastUsedTime == 0L` means "never used" → also rarely-used.
                val lu = app.lastUsedTime
                lu == 0L || (now - lu) >= rarelyUsedThresholdMs
            },
            oversizedCount  = apps.count { it.totalSizeBytes >= OVERSIZED_THRESHOLD_BYTES },
            sideloadedCount = apps.count { isSideloaded(it.installerPackage) },
        )
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /** Same classification as [installerHuman] but returns the boolean only. */
    private fun isSideloaded(installer: String?): Boolean = when (installer) {
        null,
        "" -> true
        in KNOWN_STORE_INSTALLERS -> false
        else -> true
    }

    private fun installerHuman(installer: String?): String = when (installer) {
        null, ""                          -> context.getString(R.string.installer_sideload)
        "com.android.vending"             -> context.getString(R.string.installer_play_store)
        "org.fdroid.fdroid",
        "org.fdroid.fdroid.privileged"    -> context.getString(R.string.installer_f_droid)
        "com.aurora.store",
        "com.aurora.services"             -> context.getString(R.string.installer_aurora)
        else                              -> installer
    }

    private companion object {
        // MS_PER_DAY imported from core.ext.TimeConstants (VII C1 single source).
        const val RARELY_USED_THRESHOLD_DAYS = 60L
        const val OVERSIZED_THRESHOLD_BYTES: Long = 200L * 1024 * 1024 // 200 MB

        /**
         * Cap on the lifecycle journal section to keep PDF size bounded.
         * 200 entries fits comfortably (~5-10 pages). Full history stays
         * available in-app via LifecycleHistoryScreen.
         */
        const val LIFECYCLE_JOURNAL_CAP: Int = 200

        /**
         * Known well-behaved installers — anything else is treated as
         * "sideload" in the report (file manager, ADB, third-party store).
         * Mirror of `AppInfoRepositoryImpl.classifyInstaller` choices.
         */
        val KNOWN_STORE_INSTALLERS = setOf(
            "com.android.vending",
            "org.fdroid.fdroid",
            "org.fdroid.fdroid.privileged",
            "com.aurora.store",
            "com.aurora.services",
        )
    }
}
