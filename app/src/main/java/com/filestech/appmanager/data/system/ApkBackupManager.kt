package com.filestech.appmanager.data.system

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import com.filestech.appmanager.core.ext.HashUtils
import com.filestech.appmanager.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Copies the base APK of an installed app into a user-picked SAF tree URI,
 * and produces a re-installable [Intent.ACTION_VIEW] for the resulting backup
 * document.
 *
 * **HARD quarantine flow**:
 *  1. UI prompts the user via SAF `ACTION_OPEN_DOCUMENT_TREE` (the first time)
 *     to pick a folder + grants `FLAG_GRANT_PERSISTABLE_URI_PERMISSION`.
 *  2. Settings persists the chosen tree URI string.
 *  3. [backupApk] copies `ApplicationInfo.sourceDir` into that tree as
 *     `<package>_<versionCode>.apk` (via [DocumentFile.createFile]). Returns
 *     the resulting document URI string.
 *  4. Caller fires [com.filestech.appmanager.data.system.IntentFactory.uninstallIntent].
 *  5. On restore, [prepareRestore] copies the backup into the app's private cache, checks the copy's
 *     SHA-256 against the one recorded in step 3, and returns the install intent for that verified
 *     copy (v0.5.1; until then the backup document itself went to the installer, unchecked).
 *
 * Why SAF and not direct File access?
 *  - F-Droid-friendly: no MANAGE_EXTERNAL_STORAGE permission required.
 *  - Survives uninstall + reinstall of App Manager Tech itself (persistable
 *    grant stays valid).
 *  - User keeps explicit visibility over WHERE the backups live (their
 *    Documents folder by default, or a dedicated SD-card folder).
 *
 * Privacy:
 *  - We only copy the BASE APK, never the user's app data — Android prevents
 *    cross-app data access anyway. A base APK alone cannot reinstall an app
 *    split into several APKs, so HARD mode is refused for those upfront
 *    ([hasSplitApks]).
 *  - The backup folder belongs to the user — uninstalling App Manager Tech
 *    later does NOT auto-wipe it.
 */
@Singleton
class ApkBackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    /**
     * v0.4.0 audit SECU-H1 fix — IO dispatcher injected so `backupApk`
     * can be unit-tested with a substitute dispatcher (previously
     * `Dispatchers.IO` hardcoded — untestable).
     */
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    /**
     * Result of [backupApk]: either a persisted URI string or a failure. The caller turns [Reason]
     * into the message the user sees; [Failure.message] is the technical detail, for the logs.
     */
    sealed interface Result {
        /** [sha256]: the fingerprint of the bytes written, which [prepareRestore] checks later. */
        data class Success(val documentUri: String, val sha256: String) : Result
        data class Failure(val reason: Reason, val message: String, val cause: Throwable? = null) : Result
    }

    enum class Reason {
        /** The installed APK cannot be located or read. */
        APK_UNREADABLE,

        /** The backup folder is invalid, read-only, or refuses the new file. */
        FOLDER_UNAVAILABLE,

        /** The persisted SAF permission on the backup folder was revoked. */
        FOLDER_ACCESS_REVOKED,

        /** Anything else; the cause is in the logs. */
        FAILED,
    }

    /**
     * Copies the base APK of [packageName] into [treeUri].
     *
     * @param treeUri the persistable URI returned by SAF `ACTION_OPEN_DOCUMENT_TREE`.
     * @param packageName package whose APK to copy.
     * @param versionCode used in the filename for disambiguation.
     */
    // SAF providers are third-party code and fail with undocumented runtime exceptions; the user must
    // get a failed backup, not a crash. No suspension point inside the `try` (the copy is blocking).
    @Suppress("TooGenericExceptionCaught")
    suspend fun backupApk(
        treeUri: Uri,
        packageName: String,
        versionCode: Long,
    ): Result = withContext(io) {
        try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            val source = appInfo.sourceDir
                ?: return@withContext Result.Failure(Reason.APK_UNREADABLE, "No sourceDir for $packageName")
            val sourceFile = File(source)
            if (!sourceFile.exists() || !sourceFile.canRead()) {
                return@withContext Result.Failure(Reason.APK_UNREADABLE, "APK file unreadable: $source")
            }

            // fromTreeUri throws IllegalArgumentException for a document URI that is not a tree.
            val tree = runCatching { DocumentFile.fromTreeUri(context, treeUri) }.getOrNull()
                ?: return@withContext Result.Failure(Reason.FOLDER_UNAVAILABLE, "SAF tree URI invalid")
            if (!tree.exists() || !tree.canWrite()) {
                return@withContext Result.Failure(Reason.FOLDER_UNAVAILABLE, "SAF tree not writable")
            }

            val fileName = sanitizeFilename("${packageName}_$versionCode.apk")

            // If a previous backup with the same name exists (re-quarantine of
            // same version), delete it first so we don't accumulate duplicates.
            tree.findFile(fileName)?.delete()

            val backupDoc = tree.createFile(MIME_APK, fileName)
                ?: return@withContext Result.Failure(Reason.FOLDER_UNAVAILABLE, "Failed to create backup file")

            val sha256 = copyInto(backupDoc, sourceFile)
                ?: return@withContext Result.Failure(Reason.FOLDER_UNAVAILABLE, "Failed to open backup output stream")
            // v0.5.1 — a provider may report success on a file it truncated. Checked here, while the app
            // is still installed: found at restore time, the corruption would come after the uninstall.
            if (backupDoc.length() != sourceFile.length()) {
                backupDoc.delete()
                return@withContext Result.Failure(
                    Reason.FAILED,
                    "Backup size ${backupDoc.length()} != APK size ${sourceFile.length()}",
                )
            }

            Timber.i("ApkBackupManager: backed up %s to %s (sha256 %s)", packageName, backupDoc.uri, sha256)
            Result.Success(backupDoc.uri.toString(), sha256)
        } catch (e: PackageManager.NameNotFoundException) {
            // Uninstalled between the catalogue read and the backup: there is no APK to copy.
            Timber.w(e, "ApkBackupManager: %s is no longer installed", packageName)
            Result.Failure(Reason.APK_UNREADABLE, "Package not found: $packageName", e)
        } catch (e: SecurityException) {
            Timber.w(e, "ApkBackupManager: SAF permission revoked")
            Result.Failure(Reason.FOLDER_ACCESS_REVOKED, "SAF permission revoked", e)
        } catch (e: Exception) {
            Timber.e(e, "ApkBackupManager: backup failed for %s", packageName)
            Result.Failure(Reason.FAILED, "Backup failed: ${e.message}", e)
        }
    }

    /**
     * Copies [source] into [target] and returns the SHA-256 of what was written; null when the
     * provider gives no output stream. Whatever stops the copy — no stream, an IOException, a
     * provider's undocumented runtime exception — deletes [target]: an empty or half-written APK must
     * not stay in the folder looking like a backup.
     */
    private fun copyInto(target: DocumentFile, source: File): String? {
        var sha256: String? = null
        try {
            sha256 = context.contentResolver.openOutputStream(target.uri)?.use { out ->
                FileInputStream(source).use { input -> HashUtils.copyWithSha256HexLower(input, out) }
            }
            return sha256
        } finally {
            if (sha256 == null) target.delete()
        }
    }

    /** Outcome of [prepareRestore]. */
    sealed interface Restore {
        /** The backup is the very file that was saved: [intent] installs its verified private copy. */
        data class Ready(val intent: Intent) : Restore

        /** The backup document is gone, or its folder is no longer readable. */
        data object Missing : Restore

        /** Saved before v0.5.1, without a fingerprint: it cannot be checked, so it is not installed. */
        data object Unverifiable : Restore

        /** The backup no longer has the fingerprint recorded when it was saved: it was changed. */
        data object Modified : Restore

        /** The copy failed; the cause is in the logs. */
        data object Failed : Restore
    }

    /**
     * v0.5.1 — checks a HARD quarantine's backup and prepares its install.
     *
     * The backup document lives in a folder other apps may be able to write to, and until v0.5.1 it
     * went to the installer as is. Now it is first copied into this app's private cache — no other app
     * can write there — and the SHA-256 of the bytes copied is compared with [expectedSha256], the
     * fingerprint recorded when the backup was written. Only an identical file is installed, and the
     * installer is given the verified COPY, never the folder's document: what was checked is what is
     * installed. Android still asks the user to allow installs from this app and to confirm.
     *
     * The intent is pinned to the SYSTEM installer: any app may declare a VIEW filter for APKs, and an
     * implicit intent would offer it this file and its read grant. Pinned only when exactly one system
     * package handles it (resolveActivity returns the system chooser, package "android", when there
     * are several); otherwise left implicit, so the pinning never makes a restore impossible.
     */
    // SAF providers are third-party code and fail with undocumented runtime exceptions; the user must
    // get a refused restore, not a crash. No suspension point inside the `try`.
    @Suppress("TooGenericExceptionCaught", "ReturnCount")
    suspend fun prepareRestore(
        apkBackupUri: String,
        expectedSha256: String?,
        packageName: String,
    ): Restore = withContext(io) {
        try {
            val uri = Uri.parse(apkBackupUri)
            val doc = DocumentFile.fromSingleUri(context, uri)
            if (doc == null || !doc.exists()) return@withContext Restore.Missing
            if (expectedSha256 == null) return@withContext Restore.Unverifiable

            // The previous restore's verified copy goes here, and only here: deleting it when the screen
            // resumes could pull it from under an installer the user left open (review M1). At most one
            // APK is kept, in the app-private cache, which Android may also evict.
            val dir = File(context.cacheDir, RESTORE_DIR)
            dir.deleteRecursively()
            dir.mkdirs()
            val copy = File(dir, sanitizeFilename("$packageName.apk"))
            val actual = context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(copy).use { output -> HashUtils.copyWithSha256HexLower(input, output) }
            } ?: return@withContext Restore.Missing
            if (actual != expectedSha256) {
                copy.delete()
                Timber.w("ApkBackupManager: backup of %s changed (sha256 %s, expected %s)", packageName, actual, expectedSha256)
                return@withContext Restore.Modified
            }

            val copyUri = FileProvider.getUriForFile(context, context.packageName + AUTHORITY_SUFFIX, copy)
            Restore.Ready(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(copyUri, MIME_APK)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    context.packageManager
                        .queryIntentActivities(this, PackageManager.MATCH_SYSTEM_ONLY)
                        .map { it.activityInfo.packageName }
                        .distinct()
                        .singleOrNull()
                        ?.let { setPackage(it) }
                },
            )
        } catch (e: SecurityException) {
            Timber.w(e, "ApkBackupManager: backup folder no longer readable for %s", packageName)
            Restore.Missing
        } catch (e: Exception) {
            Timber.e(e, "ApkBackupManager: restore preparation failed for %s", packageName)
            Restore.Failed
        }
    }

    /**
     * v0.5.1 — true iff [packageName] is installed as split APKs (an App Bundle, as most Play Store
     * apps are: `ApplicationInfo.splitSourceDirs` not empty). [backupApk] copies the base APK only,
     * which the installer cannot reinstall such an app from, so a HARD quarantine is refused for it
     * before anything is copied or uninstalled. False when the package is not installed: [backupApk]
     * reports that case itself, as [Reason.APK_UNREADABLE].
     */
    suspend fun hasSplitApks(packageName: String): Boolean = withContext(io) {
        try {
            !context.packageManager.getApplicationInfo(packageName, 0).splitSourceDirs.isNullOrEmpty()
        } catch (ignored: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * v0.5.1 — Live probe: when [packageName] was last installed or updated (`PackageInfo.lastUpdateTime`,
     * epoch ms), or null if it is not installed right now. Asks PackageManager, not the Room cache,
     * which still lists an app for a while after it is uninstalled. The time matters as much as the
     * presence: an uninstall the user cancelled leaves the app installed, last updated before its
     * quarantine — see [com.filestech.appmanager.domain.usecase.RestoreFromQuarantineUseCase.reconcileHardRestores].
     */
    suspend fun lastUpdateTime(packageName: String): Long? = withContext(io) {
        try {
            context.packageManager.getPackageInfo(packageName, 0).lastUpdateTime
        } catch (ignored: PackageManager.NameNotFoundException) {
            null
        }
    }

    /**
     * Probe — returns true iff the backup file referenced by [apkBackupUri]
     * still exists and we still have a persistable read grant on it. Used by
     * the UI to disable the "Restaurer" button when the backup is gone.
     */
    @Suppress("TooGenericExceptionCaught") // Same SAF boundary: any failure means "not available".
    fun isBackupAvailable(apkBackupUri: String?): Boolean {
        if (apkBackupUri.isNullOrBlank()) return false
        return try {
            val uri = Uri.parse(apkBackupUri)
            DocumentFile.fromSingleUri(context, uri)?.exists() == true
        } catch (ignored: Exception) {
            false
        }
    }

    /**
     * Whitelist filename to a safe SAF subset: [a-zA-Z0-9._-]. Avoids
     * `..`/path-traversal style filenames the OS would interpret as directory
     * components. We control the input (it's a package name + version code),
     * but defensive-by-default — never trust caller-supplied strings.
     */
    private fun sanitizeFilename(name: String): String =
        name.replace(Regex("[^a-zA-Z0-9._-]"), "_")

    private companion object {
        const val MIME_APK = "application/vnd.android.package-archive"

        /** Under `cacheDir`; declared in `res/xml/restore_paths.xml` for the FileProvider. */
        const val RESTORE_DIR = "restore"

        /** Appended to the package name: the authority declared in the manifest. */
        const val AUTHORITY_SUFFIX = ".restore"
    }
}
