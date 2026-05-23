package com.filestech.appmanager.data.system

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
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
 *  5. On restore, [restoreIntent] returns the install intent the UI fires.
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
 *    cross-app data access anyway.
 *  - The backup folder belongs to the user — uninstalling App Manager Tech
 *    later does NOT auto-wipe it.
 */
@Singleton
class ApkBackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /**
     * Result of [backupApk]: either a persisted URI string or a failure reason
     * the caller can surface to the UI.
     */
    sealed interface Result {
        data class Success(val documentUri: String) : Result
        data class Failure(val message: String, val cause: Throwable? = null) : Result
    }

    /**
     * Copies the base APK of [packageName] into [treeUri].
     *
     * @param treeUri the persistable URI returned by SAF `ACTION_OPEN_DOCUMENT_TREE`.
     * @param packageName package whose APK to copy.
     * @param versionCode used in the filename for disambiguation.
     */
    suspend fun backupApk(
        treeUri: Uri,
        packageName: String,
        versionCode: Long,
    ): Result = withContext(Dispatchers.IO) {
        try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            val source = appInfo.sourceDir
                ?: return@withContext Result.Failure("No sourceDir for $packageName")
            val sourceFile = File(source)
            if (!sourceFile.exists() || !sourceFile.canRead()) {
                return@withContext Result.Failure("APK file unreadable: $source")
            }

            val tree = DocumentFile.fromTreeUri(context, treeUri)
                ?: return@withContext Result.Failure("SAF tree URI invalid")
            if (!tree.exists() || !tree.canWrite()) {
                return@withContext Result.Failure("SAF tree not writable")
            }

            val fileName = sanitizeFilename("${packageName}_$versionCode.apk")

            // If a previous backup with the same name exists (re-quarantine of
            // same version), delete it first so we don't accumulate duplicates.
            tree.findFile(fileName)?.delete()

            val backupDoc = tree.createFile(MIME_APK, fileName)
                ?: return@withContext Result.Failure("Failed to create backup file")

            val bytes = context.contentResolver.openOutputStream(backupDoc.uri)?.use { out ->
                FileInputStream(sourceFile).use { input -> input.copyTo(out) }
            } ?: return@withContext Result.Failure("Failed to open backup output stream")

            Timber.i("ApkBackupManager: backed up %s (%d bytes) to %s", packageName, bytes, backupDoc.uri)
            Result.Success(backupDoc.uri.toString())
        } catch (e: SecurityException) {
            Timber.w(e, "ApkBackupManager: SAF permission revoked")
            Result.Failure("SAF permission revoked — please re-pick the backup folder", e)
        } catch (e: Exception) {
            Timber.e(e, "ApkBackupManager: backup failed for %s", packageName)
            Result.Failure("Backup failed: ${e.message}", e)
        }
    }

    /**
     * Returns the install intent for a previously-backed-up APK URI, or null
     * if the URI is invalid (user deleted the file, revoked SAF grant, etc.).
     *
     * The intent uses ACTION_VIEW + MIME `application/vnd.android.package-archive`
     * → resolves to PackageInstaller, which shows its own confirmation dialog.
     * Includes `FLAG_GRANT_READ_URI_PERMISSION` so PackageInstaller can read
     * the document; the URI is passed across the binder, so no FileProvider
     * setup is needed for SAF document URIs.
     */
    fun restoreIntent(apkBackupUri: String): Intent? {
        return try {
            val uri = Uri.parse(apkBackupUri)
            val doc = DocumentFile.fromSingleUri(context, uri)
            if (doc == null || !doc.exists()) return null
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, MIME_APK)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } catch (e: Exception) {
            Timber.w(e, "ApkBackupManager: restoreIntent failed for %s", apkBackupUri)
            null
        }
    }

    /**
     * Probe — returns true iff the backup file referenced by [apkBackupUri]
     * still exists and we still have a persistable read grant on it. Used by
     * the UI to disable the "Restaurer" button when the backup is gone.
     */
    fun isBackupAvailable(apkBackupUri: String?): Boolean {
        if (apkBackupUri.isNullOrBlank()) return false
        return try {
            val uri = Uri.parse(apkBackupUri)
            DocumentFile.fromSingleUri(context, uri)?.exists() == true
        } catch (e: Exception) {
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
    }
}
