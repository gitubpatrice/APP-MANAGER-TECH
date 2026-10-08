package com.filestech.appmanager.domain.usecase

import android.content.Intent
import android.net.Uri
import com.filestech.appmanager.core.ext.MS_PER_DAY
import com.filestech.appmanager.core.ext.isValidPackageName
import com.filestech.appmanager.core.result.Outcome
import com.filestech.appmanager.data.system.ApkBackupManager
import com.filestech.appmanager.data.system.IntentFactory
import com.filestech.appmanager.domain.model.AppInfo
import com.filestech.appmanager.domain.model.QuarantineEntry
import com.filestech.appmanager.domain.model.QuarantineMode
import com.filestech.appmanager.domain.repository.AppInfoRepository
import com.filestech.appmanager.domain.repository.QuarantineRepository
import kotlinx.coroutines.CancellationException
import timber.log.Timber
import javax.inject.Inject

/**
 * Quarantines an app for [durationDays] days.
 *
 * Two modes (see [QuarantineMode]):
 *  - HARD_UNINSTALL: requires a SAF [Uri] pointing at the user-picked backup
 *    folder. Copies the APK + persists the entry + returns the uninstall
 *    intent for the UI to launch. The OS shows its own uninstall confirm.
 *    Refused upfront for an app the backup could never restore (v0.5.1, see
 *    [FailureReason.SYSTEM_APP] and [FailureReason.SPLIT_APKS]).
 *  - SOFT_REMINDER: just persists the entry — no APK copy, no uninstall.
 *
 * The UI is responsible for the destructive-data warning before HARD mode
 * (see [Result.dataLossWarning] which exposes the constant the dialog should
 * surface).
 *
 * Returns a [Result] carrying either the action intent to launch (HARD mode)
 * or just a success marker (SOFT mode), so the UI knows what to do next.
 */
class QuarantineAppUseCase @Inject constructor(
    private val repository: QuarantineRepository,
    private val appInfo: AppInfoRepository,
    private val apkBackup: ApkBackupManager,
    private val intents: IntentFactory,
) {

    sealed interface Result {
        /** HARD mode — APK backed up, [uninstallIntent] must be launched to complete. */
        data class HardReady(val uninstallIntent: Intent) : Result

        /** SOFT mode — entry persisted, [appDetailsIntent] points to OS Settings for manual disable. */
        data class SoftReady(val appDetailsIntent: Intent) : Result

        /**
         * Validation, backup or persist failed. [reason] decides the message the user sees; [detail]
         * is the technical cause, in English, for the logs only. [appLabel] names the app in the
         * messages that refuse this app in particular ([FailureReason.SPLIT_APKS],
         * [FailureReason.SYSTEM_APP]).
         */
        data class Failure(
            val reason: FailureReason,
            val detail: String,
            val appLabel: String? = null,
        ) : Result
    }

    enum class FailureReason {
        INVALID_PACKAGE,
        INVALID_DURATION,
        APP_NOT_FOUND,
        APP_LIST_LOADING,
        NEEDS_BACKUP_FOLDER,
        APK_UNREADABLE,
        BACKUP_FOLDER_UNAVAILABLE,
        BACKUP_FOLDER_ACCESS_REVOKED,
        BACKUP_FAILED,
        SAVE_FAILED,

        /** v0.5.1 — HARD refused: split APKs (App Bundle), which the base-APK backup cannot reinstall. */
        SPLIT_APKS,

        /** v0.5.1 — HARD refused: a system app, whose uninstall only removes its updates. */
        SYSTEM_APP,
    }

    suspend operator fun invoke(
        packageName: String,
        mode: QuarantineMode,
        durationDays: Int,
        backupTreeUri: Uri?,
        nowMs: Long = System.currentTimeMillis(),
    ): Result {
        if (!packageName.isValidPackageName()) return Result.Failure(FailureReason.INVALID_PACKAGE, "Invalid package name: $packageName")
        if (durationDays !in MIN_DAYS..MAX_DAYS) {
            return Result.Failure(FailureReason.INVALID_DURATION, "Duration $durationDays not in [$MIN_DAYS, $MAX_DAYS]")
        }

        // Resolve label + version snapshot from the cache.
        val info = when (val r = appInfo.getApp(packageName)) {
            is Outcome.Success -> r.value
            is Outcome.Failure -> return Result.Failure(FailureReason.APP_NOT_FOUND, "App not found in cache: $packageName")
            Outcome.Loading    -> return Result.Failure(FailureReason.APP_LIST_LOADING, "Cache not ready")
        }

        val restoreAt = nowMs + durationDays.toLong() * MS_PER_DAY

        return when (mode) {
            QuarantineMode.HARD_UNINSTALL -> refuseUnrestorable(info) ?: doHard(
                packageName = packageName,
                label       = info.label,
                versionName = info.versionName,
                versionCode = info.versionCode,
                quarantinedAt = nowMs,
                restoreAt   = restoreAt,
                backupTreeUri = backupTreeUri,
            )
            QuarantineMode.SOFT_REMINDER -> doSoft(
                packageName = packageName,
                label       = info.label,
                versionName = info.versionName,
                versionCode = info.versionCode,
                quarantinedAt = nowMs,
                restoreAt   = restoreAt,
            )
        }
    }

    /**
     * v0.5.1 — HARD mode uninstalls the app, and its data with it, on the promise of a later restore
     * from the backup. Refused, before anything is copied, persisted or uninstalled, when that restore
     * could never happen:
     *  - a system app: Android's uninstall only removes its updates, the app itself stays;
     *  - an app installed as split APKs (App Bundle): the backup holds the base APK only, which the
     *    installer cannot reinstall the app from.
     * Until v0.5.1 both went through: entry saved, backup written, uninstall launched, on a promise
     * that backup could not keep. Returns null when HARD mode may proceed.
     */
    private suspend fun refuseUnrestorable(info: AppInfo): Result.Failure? = when {
        info.isSystemApp -> Result.Failure(
            reason   = FailureReason.SYSTEM_APP,
            detail   = "HARD mode refused for system app ${info.packageName}",
            appLabel = info.label,
        )
        apkBackup.hasSplitApks(info.packageName) -> Result.Failure(
            reason   = FailureReason.SPLIT_APKS,
            detail   = "HARD mode refused for split-APK app ${info.packageName}",
            appLabel = info.label,
        )
        else -> null
    }

    // doHard and doSoft catch everything on purpose: Room reports a failed write through several
    // unrelated exception types, and the user must get a message, not a crash. Cancellation is
    // rethrown first — `repository.upsert` suspends, and swallowing it would report a cancelled
    // coroutine as a failed quarantine. LongParameterList: the entry's fields, passed through from
    // invoke one by one.
    @Suppress("TooGenericExceptionCaught", "LongParameterList")
    private suspend fun doHard(
        packageName: String,
        label: String,
        versionName: String?,
        versionCode: Long,
        quarantinedAt: Long,
        restoreAt: Long,
        backupTreeUri: Uri?,
    ): Result {
        if (backupTreeUri == null) {
            return Result.Failure(FailureReason.NEEDS_BACKUP_FOLDER, "HARD mode without a backup folder")
        }
        val backupResult = apkBackup.backupApk(backupTreeUri, packageName, versionCode)
        val backupUri = when (backupResult) {
            is ApkBackupManager.Result.Success -> backupResult.documentUri
            is ApkBackupManager.Result.Failure -> return Result.Failure(
                reason = when (backupResult.reason) {
                    ApkBackupManager.Reason.APK_UNREADABLE -> FailureReason.APK_UNREADABLE
                    ApkBackupManager.Reason.FOLDER_UNAVAILABLE -> FailureReason.BACKUP_FOLDER_UNAVAILABLE
                    ApkBackupManager.Reason.FOLDER_ACCESS_REVOKED -> FailureReason.BACKUP_FOLDER_ACCESS_REVOKED
                    ApkBackupManager.Reason.FAILED -> FailureReason.BACKUP_FAILED
                },
                detail = backupResult.message,
            )
        }
        return try {
            repository.upsert(
                QuarantineEntry(
                    packageName        = packageName,
                    label              = label,
                    mode               = QuarantineMode.HARD_UNINSTALL,
                    quarantinedAt      = quarantinedAt,
                    restoreAt          = restoreAt,
                    versionName        = versionName,
                    versionCode        = versionCode,
                    apkBackupUri       = backupUri,
                    autoRestoreEnabled = true,
                    notified           = false,
                ),
            )
            Timber.i("Quarantine HARD: %s persisted, backup at %s", packageName, backupUri)
            Result.HardReady(uninstallIntent = intents.uninstallIntent(packageName))
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Timber.e(e, "Quarantine HARD: persist failed for %s", packageName)
            Result.Failure(FailureReason.SAVE_FAILED, "Failed to persist quarantine entry: ${e.message}")
        }
    }

    @Suppress("TooGenericExceptionCaught", "LongParameterList") // Same reasons as doHard.
    private suspend fun doSoft(
        packageName: String,
        label: String,
        versionName: String?,
        versionCode: Long,
        quarantinedAt: Long,
        restoreAt: Long,
    ): Result {
        return try {
            repository.upsert(
                QuarantineEntry(
                    packageName        = packageName,
                    label              = label,
                    mode               = QuarantineMode.SOFT_REMINDER,
                    quarantinedAt      = quarantinedAt,
                    restoreAt          = restoreAt,
                    versionName        = versionName,
                    versionCode        = versionCode,
                    apkBackupUri       = null,
                    autoRestoreEnabled = true,
                    notified           = false,
                ),
            )
            Timber.i("Quarantine SOFT: %s persisted, restoreAt=%d", packageName, restoreAt)
            Result.SoftReady(appDetailsIntent = intents.appDetailsSettingsIntent(packageName))
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Timber.e(e, "Quarantine SOFT: persist failed for %s", packageName)
            Result.Failure(FailureReason.SAVE_FAILED, "Failed to persist quarantine entry: ${e.message}")
        }
    }

    companion object {
        const val MIN_DAYS = 1
        const val MAX_DAYS = 365
    }
}
