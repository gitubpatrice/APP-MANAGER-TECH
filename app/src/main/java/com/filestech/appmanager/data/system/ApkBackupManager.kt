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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
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

    /** One restore at a time: two would race on the installer and on the cache. */
    private val restoreLock = Mutex()

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
            // v0.5.1 — the backup is read back and hashed: a provider may report success on a file it
            // truncated or altered. Checked here, while the app is still installed; found at restore
            // time, the damage would come after the uninstall. Not the size: a provider that does not
            // know it reports 0.
            if (!readsBackAs(backupDoc, sha256)) {
                return@withContext Result.Failure(Reason.FAILED, "Backup did not read back as written ($sha256)")
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

    /**
     * True when [doc] reads back with the SHA-256 [sha256]. A backup that cannot be read back could not
     * be restored either: a mismatch, no stream or a read that throws all delete [doc]; what the read
     * throws propagates.
     */
    private fun readsBackAs(doc: DocumentFile, sha256: String): Boolean {
        var readBack: String? = null
        try {
            readBack = context.contentResolver.openInputStream(doc.uri)?.use { HashUtils.sha256HexLower(it) }
            return readBack == sha256
        } finally {
            if (readBack != sha256) doc.delete()
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
     * went to the installer as is. Now [VerifiedCopy] copies it into this app's private cache — no other
     * app can write there — hashing it, and publishes the copy only if its SHA-256 is [expectedSha256],
     * the fingerprint recorded when the backup was written. The installer is given that verified COPY,
     * never the folder's document: what was checked is what is installed. Restores are serialised
     * ([restoreLock]). Android still asks the user to allow installs from this app and to confirm.
     */
    // SAF providers are third-party code and fail with undocumented runtime exceptions; the user must
    // get a refused restore, not a crash. No suspension point inside the `try`.
    @Suppress("TooGenericExceptionCaught", "ReturnCount")
    suspend fun prepareRestore(
        apkBackupUri: String,
        expectedSha256: String?,
        packageName: String,
    ): Restore = restoreLock.withLock {
        withContext(io) {
            try {
                val uri = Uri.parse(apkBackupUri)
                val doc = DocumentFile.fromSingleUri(context, uri)
                if (doc == null || !doc.exists()) return@withContext Restore.Missing
                if (expectedSha256 == null) return@withContext Restore.Unverifiable

                // Copies older than an installer needs are dropped; recent ones stay, so a restore never
                // deletes a file another installer may still be reading.
                val outDir = File(context.cacheDir, RESTORE_DIR)
                VerifiedCopy.deleteOlderThan(outDir, RESTORE_KEEP_MS, System.currentTimeMillis())
                val stream = context.contentResolver.openInputStream(uri) ?: return@withContext Restore.Missing
                val verified = stream.use { input ->
                    VerifiedCopy.copyIfMatches(input, expectedSha256, File(context.cacheDir, RESTORE_WORK_DIR), outDir)
                } ?: return@withContext Restore.Modified
                Restore.Ready(installIntent(FileProvider.getUriForFile(context, context.packageName + AUTHORITY_SUFFIX, verified)))
            } catch (e: SecurityException) {
                Timber.w(e, "ApkBackupManager: backup folder no longer readable for %s", packageName)
                Restore.Missing
            } catch (e: Exception) {
                Timber.e(e, "ApkBackupManager: restore preparation failed for %s", packageName)
                Restore.Failed
            }
        }
    }

    /**
     * The install intent for a verified copy, pinned to the SYSTEM installer: any app may declare a VIEW
     * filter for APKs, and an implicit intent would hand it this file and its read grant (a third-party
     * handler set as default would even get it without a chooser). Among the system apps that handle it,
     * the one holding INSTALL_PACKAGES is the installer; left implicit only if that still is not a
     * single app, so the pinning never makes a restore impossible.
     */
    private fun installIntent(fileUri: Uri): Intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(fileUri, MIME_APK)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val pm = context.packageManager
        val system = pm.queryIntentActivities(this, PackageManager.MATCH_SYSTEM_ONLY)
            .map { it.activityInfo.packageName }
            .distinct()
        val installer = system.singleOrNull()
            ?: system.filter { pm.checkPermission(INSTALL_PACKAGES, it) == PackageManager.PERMISSION_GRANTED }
                .singleOrNull()
        installer?.let { setPackage(it) }
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

        /** Under `cacheDir`; declared in `res/xml/restore_paths.xml`: the only folder the provider serves. */
        const val RESTORE_DIR = "restore"

        /** Under `cacheDir`, NOT served: where a backup is copied and hashed before it is published. */
        const val RESTORE_WORK_DIR = "restore-work"

        /** A verified copy is kept this long for an installer to stage it, then dropped. */
        const val RESTORE_KEEP_MS = 60L * 60 * 1000

        const val INSTALL_PACKAGES = "android.permission.INSTALL_PACKAGES"

        /** Appended to the package name: the authority declared in the manifest. */
        const val AUTHORITY_SUFFIX = ".restore"
    }
}
