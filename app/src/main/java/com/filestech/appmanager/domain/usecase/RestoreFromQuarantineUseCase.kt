package com.filestech.appmanager.domain.usecase

import android.content.Intent
import com.filestech.appmanager.core.ext.isValidPackageName
import com.filestech.appmanager.data.system.ApkBackupManager
import com.filestech.appmanager.data.system.IntentFactory
import com.filestech.appmanager.domain.model.QuarantineEntry
import com.filestech.appmanager.domain.model.QuarantineMode
import com.filestech.appmanager.domain.repository.QuarantineRepository
import timber.log.Timber
import javax.inject.Inject

/**
 * Restores a quarantined app — semantics depend on the stored [QuarantineMode]:
 *
 *  - **HARD_UNINSTALL**: produces the install intent for the backup APK URI.
 *    Caller fires it → OS PackageInstaller shows its own confirm dialog. The
 *    row is KEPT: the installer reports nothing back, and the user can cancel
 *    it, or Android can refuse it (it did, for every restore until v0.5.1: the
 *    app lacked REQUEST_INSTALL_PACKAGES). The caller drops it through
 *    [confirmHardRestore] once the app is seen installed again. Until v0.5.1 the
 *    row was dropped when the intent was produced, so a refused install lost the
 *    quarantine record.
 *  - **SOFT_REMINDER**: no install — produces an `appDetailsSettingsIntent`
 *    deep-link so the user can re-enable the app themselves. The quarantine
 *    row is dropped unconditionally.
 *
 * Backup-missing handling: if the backup APK is gone (user deleted from their
 * file manager, SAF grant revoked), HARD restore returns
 * [Result.BackupMissing] so the UI can offer "Drop entry without restore" as
 * a recovery path instead of leaving the user stuck.
 */
class RestoreFromQuarantineUseCase @Inject constructor(
    private val repository: QuarantineRepository,
    private val apkBackup: ApkBackupManager,
    private val intents: IntentFactory,
) {

    sealed interface Result {
        /** HARD mode — caller fires this install intent. Row kept until [confirmHardRestore]. */
        data class HardReinstall(val intent: Intent) : Result

        /** SOFT mode — caller deep-links the user to OS Settings. Row already dropped. */
        data class SoftReenable(val intent: Intent) : Result

        /** HARD mode but backup APK is missing — caller should offer "Drop entry" path. */
        data object BackupMissing : Result

        /** Quarantine entry was not found at all. */
        data object NotFound : Result

        /** Defensive — invalid package name reached the use case. */
        data object InvalidPackage : Result
    }

    suspend operator fun invoke(packageName: String): Result {
        if (!packageName.isValidPackageName()) return Result.InvalidPackage
        val entry = repository.getByPackage(packageName) ?: return Result.NotFound
        return when (entry.mode) {
            QuarantineMode.HARD_UNINSTALL -> hardRestore(entry)
            QuarantineMode.SOFT_REMINDER  -> softRestore(entry)
        }
    }

    private suspend fun hardRestore(entry: QuarantineEntry): Result {
        val intent = entry.apkBackupUri?.let { apkBackup.restoreIntent(it) }
        return if (intent == null) {
            Timber.w("HARD restore: backup missing for %s", entry.packageName)
            Result.BackupMissing
        } else {
            Timber.i("HARD restore: produced install intent for %s, row kept", entry.packageName)
            Result.HardReinstall(intent)
        }
    }

    /**
     * Called when the user comes back from the installer. Drops the HARD entry of [packageName] if,
     * and only if, the app is installed again; returns whether it did. A cancelled or refused
     * install leaves the entry, and its backup, in place.
     */
    suspend fun confirmHardRestore(packageName: String): Boolean {
        if (!packageName.isValidPackageName()) return false
        val entry = repository.getByPackage(packageName) ?: return false
        if (entry.mode != QuarantineMode.HARD_UNINSTALL || !apkBackup.isInstalled(packageName)) return false
        repository.delete(packageName)
        Timber.i("HARD restore: %s installed again, row dropped", packageName)
        return true
    }

    private suspend fun softRestore(entry: QuarantineEntry): Result {
        repository.delete(entry.packageName)
        Timber.i("SOFT restore: row dropped, deep-linked to settings for %s", entry.packageName)
        return Result.SoftReenable(intents.appDetailsSettingsIntent(entry.packageName))
    }
}

/**
 * Bypass — drops a quarantine entry WITHOUT producing any intent. Used by the
 * "Backup missing → drop entry" recovery path and by the UI's swipe-to-dismiss
 * for stale rows.
 */
class DropQuarantineEntryUseCase @Inject constructor(
    private val repository: QuarantineRepository,
) {
    suspend operator fun invoke(packageName: String): Boolean {
        if (!packageName.isValidPackageName()) return false
        return repository.delete(packageName) > 0
    }
}
