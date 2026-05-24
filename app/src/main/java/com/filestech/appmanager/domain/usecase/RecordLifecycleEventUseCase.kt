package com.filestech.appmanager.domain.usecase

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import com.filestech.appmanager.core.ext.isValidPackageName
import com.filestech.appmanager.core.result.AppError
import com.filestech.appmanager.core.result.Outcome
import com.filestech.appmanager.core.result.getOrNull
import com.filestech.appmanager.core.result.runCatchingOutcome
import com.filestech.appmanager.data.system.DangerousPermissionInspector
import com.filestech.appmanager.di.IoDispatcher
import com.filestech.appmanager.domain.model.LifecycleEventType
import com.filestech.appmanager.domain.repository.AppInfoRepository
import com.filestech.appmanager.domain.repository.AppLifecycleRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * v0.3.0 — Records ONE lifecycle event for [packageName] of [type].
 *
 * Wire path:
 *  - `PackageMonitor` BroadcastReceiver dispatches OS broadcasts here.
 *  - The baseline scan use case calls this with [LifecycleEventType.BASELINE].
 *
 * Capture strategy (critical):
 *  - For [LifecycleEventType.INSTALLED] / [LifecycleEventType.REPLACED] /
 *    [LifecycleEventType.BASELINE] the package is currently installed →
 *    read live metadata via [PackageManager].
 *  - For [LifecycleEventType.UNINSTALLED] the broadcast fires AFTER the OS
 *    has removed the package; [PackageManager.getPackageInfo] would throw
 *    `NameNotFoundException`. We therefore fall back to the cached
 *    [AppInfoRepository.getApp] entry (Room) so the row is non-empty.
 *  - Worst case (cache miss too — fresh install that we never scanned) the
 *    event still inserts with `versionName = null`, `versionCode = 0`,
 *    `label = null` so the audit trail is never lost; the UI tolerates the
 *    nulls.
 *
 * Returns the inserted event id on success — the caller (uninstall dialog
 * orchestrator) can then [AppLifecycleRepository.setReason] referencing it.
 */
class RecordLifecycleEventUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
    private val repository: AppLifecycleRepository,
    private val appInfoRepository: AppInfoRepository,
    private val dangerousInspector: DangerousPermissionInspector,
) {

    suspend operator fun invoke(
        packageName: String,
        type: LifecycleEventType,
        capturedAt: Long = System.currentTimeMillis(),
    ): Outcome<Long> {
        if (!packageName.isValidPackageName()) {
            return Outcome.Failure(AppError.Validation("Invalid package name"))
        }
        return runCatchingOutcome(mapError = { AppError.Unknown(it) }) {
            withContext(io) {
                val snapshot = collectSnapshot(packageName, type)
                val insertOutcome = repository.insert(
                    packageName                 = packageName,
                    label                       = snapshot.label,
                    type                        = type,
                    capturedAt                  = capturedAt,
                    versionName                 = snapshot.versionName,
                    versionCode                 = snapshot.versionCode,
                    installerPackage            = snapshot.installerPackage,
                    totalSizeBytes              = snapshot.totalSizeBytes,
                    grantedDangerousPermissions = snapshot.grantedDangerousPermissions,
                    apkSha256                   = snapshot.apkSha256,
                )
                when (insertOutcome) {
                    is Outcome.Success -> insertOutcome.value
                    is Outcome.Failure -> throw IllegalStateException(insertOutcome.error.toString())
                    Outcome.Loading    -> throw IllegalStateException("Unexpected Loading")
                }
            }
        }
    }

    /**
     * Pulls a metadata snapshot for [packageName] suitable for the lifecycle
     * row. For UNINSTALLED we read the cache (the OS has already removed the
     * package); for everything else we read live + fall back to cache on
     * `NameNotFoundException` (race conditions with rapid install/uninstall).
     */
    private suspend fun collectSnapshot(
        packageName: String,
        type: LifecycleEventType,
    ): Snapshot {
        val live = if (type == LifecycleEventType.UNINSTALLED) {
            null
        } else {
            readLive(packageName)
        }
        if (live != null) return live

        val cached = appInfoRepository.getApp(packageName).getOrNull()
        return Snapshot(
            label                       = cached?.label,
            versionName                 = cached?.versionName,
            versionCode                 = cached?.versionCode ?: 0L,
            installerPackage            = cached?.installerPackage,
            totalSizeBytes              = cached?.totalSizeBytes ?: 0L,
            // No live PM access on uninstall → leave the perm snapshot empty;
            // the prior INSTALLED / BASELINE row already captured what we knew.
            grantedDangerousPermissions = emptyList(),
            // v0.3.4 — we never hash an uninstalled APK (the file is gone)
            // and we don't backfill from a cache; the prior INSTALLED /
            // REPLACED row already carries the hash for the version that
            // was just removed.
            apkSha256                   = null,
        )
    }

    @Suppress("DEPRECATION")
    private fun readLive(packageName: String): Snapshot? = try {
        val pm = context.packageManager
        val pkg = pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
        val ai = pkg.applicationInfo ?: return null
        val label = pm.getApplicationLabel(ai).toString()
        Snapshot(
            label                       = label,
            versionName                 = pkg.versionName,
            versionCode                 = PackageInfoCompat.getLongVersionCode(pkg),
            installerPackage            = queryInstaller(pm, packageName),
            // Best-effort: live total size requires StorageStatsManager which
            // needs PACKAGE_USAGE_STATS; we keep this off the hot path so the
            // BroadcastReceiver remains fast. The lifecycle row records 0 in
            // that case; the AppDetail screen still shows the true size from
            // the rescan cache, so no UX regression.
            totalSizeBytes              = 0L,
            grantedDangerousPermissions = collectGrantedDangerous(pkg),
            // v0.3.4 — best-effort base-APK hash. Failure (IO, security,
            // file gone between PM resolve and read) is non-fatal: we log
            // and store NULL so the row still records the lifecycle event.
            apkSha256                   = computeApkSha256(ai.sourceDir),
        )
    } catch (e: PackageManager.NameNotFoundException) {
        Timber.d(e, "Lifecycle live snapshot: package %s not found", packageName)
        null
    } catch (e: SecurityException) {
        Timber.w(e, "Lifecycle live snapshot: security denial for %s", packageName)
        null
    }

    /**
     * v0.3.4 — Computes the hex SHA-256 of the base APK pointed to by
     * [sourceDir]. The cost is dominated by reading the APK off disk
     * (typically 10–100 MB on modern apps) — we are already on
     * [Dispatchers.IO] via the surrounding `withContext(io)` so blocking
     * the receiver thread is OK.
     *
     * Defensive on every failure path :
     *  - null/blank path → NULL (legacy ApplicationInfo / split-only APKs),
     *  - missing file → NULL (race with uninstall right after the broadcast),
     *  - oversized APK (> [APK_HASH_MAX_BYTES]) → NULL + log (DoS guard
     *    against pathological apps; tested with 500 MB which covers every
     *    Play app a user is likely to install),
     *  - canonical path outside the allowed install roots → NULL + log
     *    (defence-in-depth against a hostile symlink that points to
     *    `/dev/urandom` or another infinite/large pseudo-file; OS-level
     *    ApplicationInfo is trustworthy on stock AOSP but root/MOD ROMs
     *    can lie),
     *  - IO / SecurityException → NULL + log.
     *
     * Streamed at 64 KB so we don't materialise the full APK in memory.
     */
    private fun computeApkSha256(sourceDir: String?): String? {
        if (sourceDir.isNullOrBlank()) return null
        // Resolve symlinks BEFORE the read-checks so a hostile link is
        // caught by the prefix check instead of being silently followed.
        val file = try {
            File(sourceDir).canonicalFile
        } catch (e: java.io.IOException) {
            Timber.w(e, "computeApkSha256: canonicalFile failed for %s", sourceDir)
            return null
        }
        if (!file.isFile || !file.canRead()) return null
        val canonicalPath = file.path
        if (APK_ALLOWED_ROOTS.none { canonicalPath.startsWith(it) }) {
            Timber.w("computeApkSha256: canonical path outside allowed roots: %s", canonicalPath)
            return null
        }
        val length = file.length()
        if (length > APK_HASH_MAX_BYTES) {
            Timber.w(
                "computeApkSha256: skipping oversized APK (%d bytes) at %s",
                length, canonicalPath,
            )
            return null
        }
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            FileInputStream(file).use { input ->
                val buf = ByteArray(APK_HASH_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buf)
                    if (read <= 0) break
                    digest.update(buf, 0, read)
                }
            }
            digest.digest().joinToString(separator = "") { "%02x".format(it) }
        } catch (e: java.io.IOException) {
            Timber.w(e, "computeApkSha256: IO read failed for %s", canonicalPath)
            null
        } catch (e: SecurityException) {
            Timber.w(e, "computeApkSha256: security denial for %s", canonicalPath)
            null
        }
    }

    @Suppress("DEPRECATION")
    private fun queryInstaller(pm: PackageManager, packageName: String): String? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            pm.getInstallSourceInfo(packageName).installingPackageName
        } else {
            pm.getInstallerPackageName(packageName)
        }
    } catch (e: PackageManager.NameNotFoundException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }

    /**
     * Collects granted dangerous-protection permissions from the live
     * [PackageInfo] (which already carries `requestedPermissions` +
     * `requestedPermissionsFlags`). Reuses the
     * [DangerousPermissionInspector] protection-level cache so we don't
     * re-resolve the protection level once per receive.
     */
    private fun collectGrantedDangerous(pkg: PackageInfo): List<String> {
        val names = pkg.requestedPermissions ?: return emptyList()
        val flags = pkg.requestedPermissionsFlags ?: return emptyList()
        if (flags.size != names.size) return emptyList()
        return buildList {
            for (i in names.indices) {
                val granted = (flags[i] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
                if (!granted) continue
                val name = names[i] ?: continue
                if (dangerousInspector.isDangerous(name)) add(name)
            }
        }.sorted()
    }

    private data class Snapshot(
        val label: String?,
        val versionName: String?,
        val versionCode: Long,
        val installerPackage: String?,
        val totalSizeBytes: Long,
        val grantedDangerousPermissions: List<String>,
        /** v0.3.4 — hex SHA-256 of the base APK at capture time, or NULL. */
        val apkSha256: String?,
    )

    private companion object {
        /** 64 KB streaming buffer for the APK SHA-256 digest. */
        const val APK_HASH_BUFFER_SIZE = 64 * 1024
        /**
         * Hard cap on the APK size we hash. 500 MB easily covers every Play
         * Store app a user is likely to install (Chrome ~250 MB, big
         * games > 200 MB). Above this we log + skip the hash rather than
         * monopolise the IO pool for tens of seconds — the row still
         * inserts with `apkSha256 = null` so the audit trail is preserved.
         */
        const val APK_HASH_MAX_BYTES: Long = 500L * 1024 * 1024
        /**
         * Whitelist of canonical install-root prefixes the APK file must
         * sit under for us to hash it. Symlinks pointing OUTSIDE these
         * roots (rare on stock AOSP, possible on root/MOD ROMs) are
         * silently dropped — defence-in-depth against a malicious link to
         * `/dev/urandom` or another pseudo-infinite file.
         */
        val APK_ALLOWED_ROOTS: List<String> = listOf(
            "/data/app/",
            "/system/app/",
            "/system/priv-app/",
            "/product/app/",
            "/vendor/app/",
        )
    }
}
