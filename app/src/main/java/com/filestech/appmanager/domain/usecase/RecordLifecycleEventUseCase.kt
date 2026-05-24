package com.filestech.appmanager.domain.usecase

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
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
        )
    } catch (e: PackageManager.NameNotFoundException) {
        Timber.d(e, "Lifecycle live snapshot: package %s not found", packageName)
        null
    } catch (e: SecurityException) {
        Timber.w(e, "Lifecycle live snapshot: security denial for %s", packageName)
        null
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
    )
}
