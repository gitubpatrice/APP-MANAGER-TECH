package com.filestech.appmanager.domain.usecase

import android.content.Intent
import android.net.Uri
import com.filestech.appmanager.core.ext.MS_PER_DAY
import com.filestech.appmanager.core.ext.isValidPackageName
import com.filestech.appmanager.core.result.Outcome
import com.filestech.appmanager.data.system.ApkBackupManager
import com.filestech.appmanager.data.system.IntentFactory
import com.filestech.appmanager.domain.model.QuarantineEntry
import com.filestech.appmanager.domain.model.QuarantineMode
import com.filestech.appmanager.domain.repository.AppInfoRepository
import com.filestech.appmanager.domain.repository.QuarantineRepository
import timber.log.Timber
import javax.inject.Inject

/**
 * Quarantines an app for [durationDays] days.
 *
 * Two modes (see [QuarantineMode]):
 *  - HARD_UNINSTALL: requires a SAF [Uri] pointing at the user-picked backup
 *    folder. Copies the APK + persists the entry + returns the uninstall
 *    intent for the UI to launch. The OS shows its own uninstall confirm.
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

        /** Backup or persist failed; [message] is human-readable English. */
        data class Failure(val message: String) : Result
    }

    suspend operator fun invoke(
        packageName: String,
        mode: QuarantineMode,
        durationDays: Int,
        backupTreeUri: Uri?,
        nowMs: Long = System.currentTimeMillis(),
    ): Result {
        if (!packageName.isValidPackageName()) return Result.Failure("Invalid package name")
        if (durationDays !in MIN_DAYS..MAX_DAYS) {
            return Result.Failure("Duration must be in [$MIN_DAYS, $MAX_DAYS] days")
        }

        // Resolve label + version snapshot from the cache.
        val info = when (val r = appInfo.getApp(packageName)) {
            is Outcome.Success -> r.value
            is Outcome.Failure -> return Result.Failure("App not found in cache: $packageName")
            Outcome.Loading    -> return Result.Failure("Cache not ready")
        }

        val restoreAt = nowMs + durationDays.toLong() * MS_PER_DAY

        return when (mode) {
            QuarantineMode.HARD_UNINSTALL -> doHard(
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
            return Result.Failure("HARD mode requires a backup folder — please pick one in Settings")
        }
        val backupResult = apkBackup.backupApk(backupTreeUri, packageName, versionCode)
        val backupUri = when (backupResult) {
            is ApkBackupManager.Result.Success -> backupResult.documentUri
            is ApkBackupManager.Result.Failure -> return Result.Failure(backupResult.message)
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
        } catch (e: Exception) {
            Timber.e(e, "Quarantine HARD: persist failed for %s", packageName)
            Result.Failure("Failed to persist quarantine entry: ${e.message}")
        }
    }

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
        } catch (e: Exception) {
            Timber.e(e, "Quarantine SOFT: persist failed for %s", packageName)
            Result.Failure("Failed to persist quarantine entry: ${e.message}")
        }
    }

    companion object {
        const val MIN_DAYS = 1
        const val MAX_DAYS = 365
    }
}
