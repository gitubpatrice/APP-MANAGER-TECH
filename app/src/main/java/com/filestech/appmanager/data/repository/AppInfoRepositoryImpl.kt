package com.filestech.appmanager.data.repository

import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import android.os.Process
import android.os.storage.StorageManager
import androidx.core.content.ContextCompat
import androidx.core.content.pm.PackageInfoCompat
import androidx.room.withTransaction
import com.filestech.appmanager.core.ext.MS_PER_DAY
import com.filestech.appmanager.core.ext.isValidPackageName
import com.filestech.appmanager.core.result.AppError
import com.filestech.appmanager.core.result.Outcome
import com.filestech.appmanager.core.result.runCatchingOutcome
import com.filestech.appmanager.data.local.db.AppDatabase
import com.filestech.appmanager.data.local.db.dao.AppInfoDao
import com.filestech.appmanager.data.local.db.entity.AppInfoEntity
import com.filestech.appmanager.di.IoDispatcher
import com.filestech.appmanager.domain.model.AppCategory
import com.filestech.appmanager.domain.model.AppInfo
import com.filestech.appmanager.domain.model.FilterOptions
import com.filestech.appmanager.domain.model.InstallerFilter
import com.filestech.appmanager.domain.model.StorageReport
import com.filestech.appmanager.domain.repository.AppInfoRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for installed-app data.
 *
 * Combines:
 * - [PackageManager] — install metadata (label, version, flags, category, enabled, installer).
 * - [StorageStatsManager] — APK / data / cache sizes (requires PACKAGE_USAGE_STATS).
 * - [UsageStatsManager] — last-used timestamps (30-day lookback; same permission).
 * - [ActivityManager] — `killBackgroundProcesses` for [forceStop].
 * - [AppInfoDao] — Room cache.
 * - [AppOpsManager] — UsageStats permission probe.
 */
@Singleton
class AppInfoRepositoryImpl @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val db: AppDatabase,
    private val dao: AppInfoDao,
    @IoDispatcher private val io: CoroutineDispatcher,
) : AppInfoRepository {

    private val packageManager: PackageManager get() = appContext.packageManager

    private val storageStats: StorageStatsManager? by lazy {
        ContextCompat.getSystemService(appContext, StorageStatsManager::class.java)
    }

    private val usageStats: UsageStatsManager? by lazy {
        ContextCompat.getSystemService(appContext, UsageStatsManager::class.java)
    }

    private val activityManager: ActivityManager? by lazy {
        ContextCompat.getSystemService(appContext, ActivityManager::class.java)
    }

    private val appOps: AppOpsManager? by lazy {
        ContextCompat.getSystemService(appContext, AppOpsManager::class.java)
    }

    // -----------------------------------------------------------------------
    // Catalogue
    // -----------------------------------------------------------------------

    override fun observeApps(includeSystemApps: Boolean): Flow<Outcome<List<AppInfo>>> {
        val source = if (includeSystemApps) dao.observeAll() else dao.observeUserApps()
        return source
            .map<List<AppInfoEntity>, Outcome<List<AppInfo>>> { entities ->
                Outcome.Success(entities.map(AppInfoMapper::toDomain))
            }
            .catch { t -> emit(Outcome.Failure(AppError.DatabaseError(t))) }
            .flowOn(io)
    }

    override fun observeFiltered(filter: FilterOptions): Flow<Outcome<List<AppInfo>>> =
        dao.observeAll()
            .map<List<AppInfoEntity>, Outcome<List<AppInfo>>> { entities ->
                val domain = entities.asSequence()
                    .map(AppInfoMapper::toDomain)
                    .filter { app -> matchesFilter(app, filter) }
                    .toList()
                Outcome.Success(domain)
            }
            .catch { t -> emit(Outcome.Failure(AppError.DatabaseError(t))) }
            .flowOn(io)

    override suspend fun getApp(packageName: String): Outcome<AppInfo> {
        if (!packageName.isValidPackageName()) {
            return Outcome.Failure(AppError.Validation("Invalid package name"))
        }
        return runCatchingOutcome(::mapError) {
            withContext(io) {
                val cached = dao.getByPackage(packageName)
                if (cached != null) {
                    AppInfoMapper.toDomain(cached)
                } else {
                    scanSinglePackage(packageName)
                        ?: throw PackageManager.NameNotFoundException(packageName)
                }
            }
        }
    }

    override suspend fun rescan(): Outcome<Unit> = runCatchingOutcome(::mapError) {
        withContext(io) {
            val now = System.currentTimeMillis()
            val entities = scanInstalledPackages(now)
            db.withTransaction {
                dao.deleteAll()
                dao.upsertAll(entities)
            }
            Timber.i("Rescan: cached %d packages", entities.size)
        }
    }

    // -----------------------------------------------------------------------
    // Actions
    // -----------------------------------------------------------------------

    override suspend fun clearCache(packageName: String): Outcome<Unit> {
        if (!packageName.isValidPackageName()) {
            return Outcome.Failure(AppError.Validation("Invalid package name"))
        }
        return Outcome.Failure(
            AppError.Permission("CLEAR_APP_CACHE_REQUIRES_USER_ACTION"),
        )
    }

    override suspend fun setEnabled(packageName: String, enabled: Boolean): Outcome<Unit> {
        if (!packageName.isValidPackageName()) {
            return Outcome.Failure(AppError.Validation("Invalid package name"))
        }
        return runCatchingOutcome(::mapError) {
            withContext(io) {
                val newState = if (enabled) {
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                } else {
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
                }
                // SecurityException on non-root: CHANGE_COMPONENT_ENABLED_STATE
                // is a signature/system permission. mapError() converts it to
                // AppError.Permission and the UseCase falls back to a Settings intent.
                packageManager.setApplicationEnabledSetting(packageName, newState, 0)
                dao.updateEnabled(packageName, enabled)
            }
        }
    }

    override suspend fun forceStop(packageName: String): Outcome<Unit> {
        if (!packageName.isValidPackageName()) {
            return Outcome.Failure(AppError.Validation("Invalid package name"))
        }
        return runCatchingOutcome(::mapError) {
            withContext(io) {
                val am = activityManager
                    ?: throw IllegalStateException("ActivityManager unavailable")
                am.killBackgroundProcesses(packageName)
                Timber.d("killBackgroundProcesses(%s) requested", packageName)
            }
        }
    }

    // -----------------------------------------------------------------------
    // Reports
    // -----------------------------------------------------------------------

    override suspend fun getStorageReport(
        includeSystemApps: Boolean,
        topN: Int,
    ): Outcome<StorageReport> = runCatchingOutcome(::mapError) {
        require(topN >= 0) { "topN must be >= 0, was $topN" }
        withContext(io) {
            val agg = dao.aggregateSizes(includeSystem = includeSystemApps)
            if (agg.appCount == 0) return@withContext StorageReport.EMPTY

            val entities = if (includeSystemApps) dao.getAll() else dao.getUserApps()
            val perCategory = entities
                .groupBy { AppInfoMapper.toDomain(it).category }
                .map { (cat, group) ->
                    StorageReport.CategoryStats(
                        category   = cat,
                        count      = group.size,
                        totalBytes = group.sumOf {
                            it.installSizeBytes + it.dataSizeBytes + it.cacheSizeBytes
                        },
                    )
                }
                .sortedByDescending { it.totalBytes }

            val byTotal = entities
                .asSequence()
                .map { entity ->
                    StorageReport.AppFootprint(
                        packageName  = entity.packageName,
                        label        = entity.label,
                        installBytes = entity.installSizeBytes,
                        dataBytes    = entity.dataSizeBytes,
                        cacheBytes   = entity.cacheSizeBytes,
                    )
                }
                .sortedByDescending { it.totalBytes }
                .take(topN)
                .toList()

            val byCache = entities
                .asSequence()
                .filter { it.cacheSizeBytes > 0L }
                .map { entity ->
                    StorageReport.AppFootprint(
                        packageName  = entity.packageName,
                        label        = entity.label,
                        installBytes = entity.installSizeBytes,
                        dataBytes    = entity.dataSizeBytes,
                        cacheBytes   = entity.cacheSizeBytes,
                    )
                }
                .sortedByDescending { it.cacheBytes }
                .take(topN)
                .toList()

            StorageReport(
                totalAppCount     = agg.appCount,
                totalInstallBytes = agg.installSum,
                totalDataBytes    = agg.dataSum,
                totalCacheBytes   = agg.cacheSum,
                perCategory       = perCategory,
                topByTotalSize    = byTotal,
                topByCacheSize    = byCache,
            )
        }
    }

    // -----------------------------------------------------------------------
    // On-demand metadata
    // -----------------------------------------------------------------------

    override suspend fun getRequestedPermissions(packageName: String): Outcome<List<String>> {
        if (!packageName.isValidPackageName()) {
            return Outcome.Failure(AppError.Validation("Invalid package name"))
        }
        return runCatchingOutcome(::mapError) {
            withContext(io) {
                val pkg = getPackageInfoWithPermissions(packageName)
                pkg.requestedPermissions?.toList().orEmpty()
            }
        }
    }

    override suspend fun getGrantedPermissions(packageName: String): Outcome<List<String>> {
        if (!packageName.isValidPackageName()) {
            return Outcome.Failure(AppError.Validation("Invalid package name"))
        }
        return runCatchingOutcome(::mapError) {
            withContext(io) {
                val pkg = getPackageInfoWithPermissions(packageName)
                val names = pkg.requestedPermissions ?: return@withContext emptyList()
                val flags = pkg.requestedPermissionsFlags
                    ?: return@withContext emptyList()
                buildList {
                    for (i in names.indices) {
                        val granted = (flags.getOrNull(i) ?: 0) and
                            PackageInfo.REQUESTED_PERMISSION_GRANTED
                        if (granted != 0) add(names[i])
                    }
                }
            }
        }
    }

    override suspend fun getSignatureSha256(packageName: String): Outcome<String> {
        if (!packageName.isValidPackageName()) {
            return Outcome.Failure(AppError.Validation("Invalid package name"))
        }
        return runCatchingOutcome(::mapError) {
            withContext(io) {
                val sig = firstSignature(packageName)
                    ?: throw IllegalStateException("No signature for $packageName")
                hashSha256Hex(sig.toByteArray())
            }
        }
    }

    // -----------------------------------------------------------------------
    // Permission probes
    // -----------------------------------------------------------------------

    override fun hasUsageStatsAccess(): Boolean {
        val ops = appOps ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ops.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                appContext.packageName,
            )
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                appContext.packageName,
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    // -----------------------------------------------------------------------
    // Filtering helpers
    // -----------------------------------------------------------------------

    private fun matchesFilter(app: AppInfo, filter: FilterOptions): Boolean {
        // System / user split
        if (app.isSystemApp && !filter.includeSystemApps) return false
        if (!app.isSystemApp && !filter.includeUserApps) return false
        // Enabled state
        if (!app.isEnabled && !filter.includeDisabled) return false
        // Size envelope
        val total = app.totalSizeBytes
        if (total < filter.minSizeBytes) return false
        if (total > filter.maxSizeBytes) return false
        // Categories
        if (filter.categories.isNotEmpty() && app.category !in filter.categories) return false
        // Installer
        if (filter.installerFilter != InstallerFilter.ANY &&
            classifyInstaller(app.installerPackage) != filter.installerFilter
        ) {
            return false
        }
        // Unused threshold
        filter.unusedSinceDays?.let { days ->
            val cutoff = System.currentTimeMillis() - days * MS_PER_DAY
            if (app.lastUsedTime >= cutoff) return false
            // Apps NEVER used (lastUsedTime == 0) match by definition.
        }
        // Required permissions — requires a live PackageManager probe.
        // Skipped here to keep observeFiltered fast; a dedicated UseCase can
        // post-filter on top by calling getRequestedPermissions per app.
        return true
    }

    private fun classifyInstaller(installer: String?): InstallerFilter = when (installer) {
        null                              -> InstallerFilter.SIDELOAD
        "com.android.vending"             -> InstallerFilter.PLAY_STORE
        "org.fdroid.fdroid",
        "org.fdroid.fdroid.privileged"    -> InstallerFilter.F_DROID
        "com.aurora.store",
        "com.aurora.services"             -> InstallerFilter.AURORA
        else                              -> InstallerFilter.OTHER
    }

    // -----------------------------------------------------------------------
    // Scanning
    // -----------------------------------------------------------------------

    @Suppress("DEPRECATION") // PackageInfoFlags variant is API 33+; Phase VIII migration.
    private fun scanInstalledPackages(now: Long): List<AppInfoEntity> {
        val packages = packageManager.getInstalledPackages(0)
        val usageMap = queryUsageMap()
        return packages.mapNotNull { pkg ->
            val ai = pkg.applicationInfo ?: return@mapNotNull null
            val sizes = queryStats(pkg.packageName, ai.uid)
            AppInfoEntity(
                packageName       = pkg.packageName,
                label             = packageManager.getApplicationLabel(ai).toString(),
                versionName       = pkg.versionName.orEmpty(),
                versionCode       = PackageInfoCompat.getLongVersionCode(pkg),
                installSizeBytes  = sizes.appBytes,
                cacheSizeBytes    = sizes.cacheBytes,
                dataSizeBytes     = sizes.dataBytes,
                firstInstallTime  = pkg.firstInstallTime,
                lastUpdateTime    = pkg.lastUpdateTime,
                lastUsedTime      = usageMap[pkg.packageName] ?: 0L,
                isSystemApp       = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                isUninstallable   = isUninstallable(ai),
                isEnabled         = ai.enabled,
                category          = mapCategory(ai.category).name,
                cachedAt          = now,
                installerPackage  = queryInstallerPackage(pkg.packageName),
                apkSourceDir      = ai.sourceDir,
            )
        }
    }

    private fun scanSinglePackage(packageName: String): AppInfo? = try {
        @Suppress("DEPRECATION")
        val pkg = packageManager.getPackageInfo(packageName, 0)
        val ai = pkg.applicationInfo ?: return null
        val sizes = queryStats(packageName, ai.uid)
        AppInfo(
            packageName       = packageName,
            label             = packageManager.getApplicationLabel(ai).toString(),
            versionName       = pkg.versionName.orEmpty(),
            versionCode       = PackageInfoCompat.getLongVersionCode(pkg),
            installSizeBytes  = sizes.appBytes,
            cacheSizeBytes    = sizes.cacheBytes,
            dataSizeBytes     = sizes.dataBytes,
            firstInstallTime  = pkg.firstInstallTime,
            lastUpdateTime    = pkg.lastUpdateTime,
            lastUsedTime      = queryUsageMap()[packageName] ?: 0L,
            isSystemApp       = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
            isUninstallable   = isUninstallable(ai),
            isEnabled         = ai.enabled,
            category          = mapCategory(ai.category),
            installerPackage  = queryInstallerPackage(packageName),
            apkSourceDir      = ai.sourceDir,
        )
    } catch (e: PackageManager.NameNotFoundException) {
        Timber.d(e, "Package %s not found during single-package scan", packageName)
        null
    }

    private fun isUninstallable(ai: ApplicationInfo): Boolean =
        (ai.flags and ApplicationInfo.FLAG_SYSTEM) == 0 ||
            (ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0

    private fun queryInstallerPackage(packageName: String): String? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            packageManager.getInstallSourceInfo(packageName).installingPackageName
        } else {
            @Suppress("DEPRECATION")
            packageManager.getInstallerPackageName(packageName)
        }
    } catch (e: PackageManager.NameNotFoundException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }

    // -----------------------------------------------------------------------
    // PackageInfo helpers
    // -----------------------------------------------------------------------

    @Suppress("DEPRECATION")
    private fun getPackageInfoWithPermissions(packageName: String): PackageInfo =
        packageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)

    @Suppress("DEPRECATION")
    private fun firstSignature(packageName: String): Signature? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = packageManager.getPackageInfo(
                packageName,
                PackageManager.GET_SIGNING_CERTIFICATES,
            )
            info.signingInfo?.apkContentsSigners?.firstOrNull()
        } else {
            val info = packageManager.getPackageInfo(
                packageName,
                PackageManager.GET_SIGNATURES,
            )
            info.signatures?.firstOrNull()
        }

    private fun hashSha256Hex(bytes: ByteArray): String = try {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        digest.joinToString(separator = ":") { byte -> "%02X".format(byte) }
    } catch (e: NoSuchAlgorithmException) {
        throw IllegalStateException("SHA-256 algorithm unavailable", e)
    }

    // -----------------------------------------------------------------------
    // StorageStats / UsageStats
    // -----------------------------------------------------------------------

    private data class Sizes(val appBytes: Long, val cacheBytes: Long, val dataBytes: Long) {
        companion object {
            val EMPTY = Sizes(0L, 0L, 0L)
        }
    }

    private fun queryStats(packageName: String, uid: Int): Sizes {
        val mgr = storageStats ?: return Sizes.EMPTY
        return try {
            val stats = mgr.queryStatsForUid(StorageManager.UUID_DEFAULT, uid)
            Sizes(
                appBytes   = stats.appBytes,
                cacheBytes = stats.cacheBytes,
                dataBytes  = stats.dataBytes,
            )
        } catch (e: SecurityException) {
            Sizes.EMPTY
        } catch (e: IOException) {
            Timber.w(e, "queryStatsForUid IOException for %s", packageName)
            Sizes.EMPTY
        } catch (e: IllegalStateException) {
            Sizes.EMPTY
        }
    }

    private fun queryUsageMap(): Map<String, Long> {
        val mgr = usageStats ?: return emptyMap()
        return try {
            val end = System.currentTimeMillis()
            val start = end - USAGE_LOOKBACK_MS
            mgr.queryAndAggregateUsageStats(start, end)
                .mapValues { it.value.lastTimeUsed }
        } catch (e: SecurityException) {
            emptyMap()
        }
    }

    // -----------------------------------------------------------------------
    // Category mapping
    // -----------------------------------------------------------------------

    private fun mapCategory(raw: Int): AppCategory = when (raw) {
        ApplicationInfo.CATEGORY_GAME          -> AppCategory.GAMES
        ApplicationInfo.CATEGORY_AUDIO         -> AppCategory.AUDIO
        ApplicationInfo.CATEGORY_VIDEO         -> AppCategory.VIDEO
        ApplicationInfo.CATEGORY_IMAGE         -> AppCategory.IMAGE
        ApplicationInfo.CATEGORY_SOCIAL        -> AppCategory.SOCIAL
        ApplicationInfo.CATEGORY_NEWS          -> AppCategory.NEWS
        ApplicationInfo.CATEGORY_MAPS          -> AppCategory.MAPS
        ApplicationInfo.CATEGORY_PRODUCTIVITY  -> AppCategory.PRODUCTIVITY
        ApplicationInfo.CATEGORY_ACCESSIBILITY -> AppCategory.ACCESSIBILITY
        ApplicationInfo.CATEGORY_UNDEFINED     -> AppCategory.UNDEFINED
        else                                   -> AppCategory.OTHER
    }

    // -----------------------------------------------------------------------
    // Error mapping
    // -----------------------------------------------------------------------

    private fun mapError(t: Throwable): AppError = when (t) {
        is SecurityException                    -> AppError.Permission(t.message ?: "permission required")
        is PackageManager.NameNotFoundException -> AppError.PackageNotFound(t.message ?: "unknown")
        is IOException                          -> AppError.IoError(t)
        is android.database.SQLException        -> AppError.DatabaseError(t)
        else                                    -> AppError.Unknown(t)
    }

    companion object {
        /** Look back 30 days when aggregating UsageStats — enough for "rarely used" detection. */
        private const val USAGE_LOOKBACK_MS: Long = 30L * 24 * 60 * 60 * 1000
        // MS_PER_DAY now imported from core.ext.TimeConstants (VII C1 fix — single source).
    }
}
