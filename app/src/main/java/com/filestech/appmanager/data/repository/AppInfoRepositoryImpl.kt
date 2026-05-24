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
import com.filestech.appmanager.domain.model.ExpertReport
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
import com.filestech.appmanager.core.ext.HashUtils
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException
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
    // Expert Mode (v0.2.2)
    // -----------------------------------------------------------------------

    override suspend fun getExpertReport(packageName: String): Outcome<ExpertReport> {
        if (!packageName.isValidPackageName()) {
            return Outcome.Failure(AppError.Validation("Invalid package name"))
        }
        return runCatchingOutcome(::mapError) {
            withContext(io) {
                buildExpertReport(packageName)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun buildExpertReport(packageName: String): ExpertReport {
        val flags = (PackageManager.GET_ACTIVITIES
            or PackageManager.GET_SERVICES
            or PackageManager.GET_RECEIVERS
            or PackageManager.GET_PROVIDERS
            or PackageManager.GET_PERMISSIONS)
        val pkg = packageManager.getPackageInfo(packageName, flags)
        val ai = pkg.applicationInfo
            ?: throw PackageManager.NameNotFoundException(packageName)
        val label = packageManager.getApplicationLabel(ai).toString()
        val identity = ExpertReport.AppIdentity(
            packageName      = pkg.packageName,
            label            = label,
            versionName      = pkg.versionName.orEmpty(),
            versionCode      = PackageInfoCompat.getLongVersionCode(pkg),
            uid              = ai.uid,
            installerPackage = queryInstallerPackage(pkg.packageName),
            firstInstallTime = pkg.firstInstallTime,
            lastUpdateTime   = pkg.lastUpdateTime,
            isSystemApp      = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
            isEnabled        = ai.enabled,
        )
        val sdkInfo = ExpertReport.SdkInfo(
            // minSdkVersion field is exposed since API 24; older runtimes report 0
            // for ai.minSdkVersion which we coerce to null for "non disponible".
            minSdk     = ai.minSdkVersion.takeIf { it > 0 },
            targetSdk  = ai.targetSdkVersion,
            compileSdk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ai.compileSdkVersion.takeIf { it > 0 }
            } else null,
        )
        val nativeInfo = ExpertReport.NativeInfo(
            primaryAbi       = readPrimaryAbi(ai),
            nativeLibraryDir = ai.nativeLibraryDir,
        )
        val apkPaths = ExpertReport.ApkPaths(
            base            = ai.sourceDir,
            splits          = ai.splitSourceDirs?.toList().orEmpty(),
            publicSourceDir = ai.publicSourceDir,
        )
        val signature = collectSignature(packageName)
        val components = ExpertReport.ComponentsInfo(
            activities = pkg.activities?.map { componentEntry(it) }?.sortedBy { it.className }.orEmpty(),
            services   = pkg.services?.map { componentEntry(it) }?.sortedBy { it.className }.orEmpty(),
            receivers  = pkg.receivers?.map { componentEntry(it) }?.sortedBy { it.className }.orEmpty(),
            providers  = pkg.providers?.map { providerEntry(it) }?.sortedBy { it.className }.orEmpty(),
        )
        val permissions = collectExpertPermissions(pkg)
        val appOps = collectAppOpsSnapshot(packageName, ai.uid)
        return ExpertReport(
            identity   = identity,
            sdkInfo    = sdkInfo,
            nativeInfo = nativeInfo,
            apkPaths   = apkPaths,
            signature  = signature,
            components = components,
            permissions = permissions,
            appOps     = appOps,
        )
    }

    private fun componentEntry(info: android.content.pm.ComponentInfo): ExpertReport.ComponentEntry {
        // ActivityInfo / ServiceInfo / ProviderInfo all inherit `permission` field;
        // ActivityInfo + ServiceInfo expose it directly. For ProviderInfo we use
        // the dedicated providerEntry() helper.
        val permission: String? = when (info) {
            is android.content.pm.ActivityInfo -> info.permission
            is android.content.pm.ServiceInfo  -> info.permission
            else                               -> null
        }
        return ExpertReport.ComponentEntry(
            className  = info.name.orEmpty(),
            exported   = info.exported,
            enabled    = info.enabled,
            permission = permission,
        )
    }

    private fun providerEntry(info: android.content.pm.ProviderInfo): ExpertReport.ProviderEntry =
        ExpertReport.ProviderEntry(
            className           = info.name.orEmpty(),
            authority           = info.authority,
            exported            = info.exported,
            enabled             = info.enabled,
            readPermission      = info.readPermission,
            writePermission     = info.writePermission,
            grantUriPermissions = info.grantUriPermissions,
        )

    private fun collectExpertPermissions(pkg: PackageInfo): ExpertReport.ExpertPermissions {
        val names = pkg.requestedPermissions ?: return ExpertReport.ExpertPermissions(
            declared = emptyList(),
            grantedCount = 0,
            declaredCount = 0,
        )
        val flags = pkg.requestedPermissionsFlags
        val list = names.mapIndexed { i, name ->
            val granted = ((flags?.getOrNull(i) ?: 0) and
                PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
            ExpertReport.DeclaredPermission(
                name        = name,
                granted     = granted,
                isDangerous = isDangerousPermission(name),
            )
        }.sortedBy { it.name }
        return ExpertReport.ExpertPermissions(
            declared      = list,
            grantedCount  = list.count { it.granted },
            declaredCount = list.size,
        )
    }

    private fun isDangerousPermission(name: String): Boolean = try {
        val info = packageManager.getPermissionInfo(name, 0)
        val level = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.protection
        } else {
            @Suppress("DEPRECATION")
            info.protectionLevel and android.content.pm.PermissionInfo.PROTECTION_MASK_BASE
        }
        level == android.content.pm.PermissionInfo.PROTECTION_DANGEROUS
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    /**
     * App-ops probe — best-effort. The list of ops below is curated for an
     * inspector audience: location, mic, camera, contacts/SMS, body sensors,
     * draw-over-other-apps, modify settings, usage stats, schedule exact alarm.
     *
     * `unsafeCheckOpNoThrow` returns `MODE_DEFAULT` when the caller is not
     * allowed to peek at the op for that UID; we surface that as "—" so the user
     * understands the OS hides it, rather than mistakenly thinking it's denied.
     */
    private fun collectAppOpsSnapshot(packageName: String, uid: Int): ExpertReport.AppOpsSnapshot {
        val ops = appOps ?: return ExpertReport.AppOpsSnapshot(emptyList(), isFullyAccessible = false)
        val curated = listOf(
            AppOpsManager.OPSTR_FINE_LOCATION,
            AppOpsManager.OPSTR_COARSE_LOCATION,
            AppOpsManager.OPSTR_CAMERA,
            AppOpsManager.OPSTR_RECORD_AUDIO,
            AppOpsManager.OPSTR_READ_CONTACTS,
            AppOpsManager.OPSTR_WRITE_CONTACTS,
            AppOpsManager.OPSTR_READ_SMS,
            AppOpsManager.OPSTR_SEND_SMS,
            AppOpsManager.OPSTR_BODY_SENSORS,
            AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW,
            AppOpsManager.OPSTR_WRITE_SETTINGS,
            AppOpsManager.OPSTR_GET_USAGE_STATS,
        )
        var anyAccessible = false
        val entries = curated.map { op ->
            val mode = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ops.unsafeCheckOpNoThrow(op, uid, packageName)
                } else {
                    @Suppress("DEPRECATION")
                    ops.checkOpNoThrow(op, uid, packageName)
                }
            } catch (e: SecurityException) {
                APP_OP_MODE_UNAVAILABLE
            } catch (e: IllegalArgumentException) {
                APP_OP_MODE_UNAVAILABLE
            }
            if (mode != APP_OP_MODE_UNAVAILABLE && mode != AppOpsManager.MODE_DEFAULT) {
                anyAccessible = true
            }
            ExpertReport.AppOpEntry(
                op        = op,
                mode      = mode,
                modeLabel = formatAppOpMode(mode),
            )
        }
        return ExpertReport.AppOpsSnapshot(entries = entries, isFullyAccessible = anyAccessible)
    }

    private fun formatAppOpMode(mode: Int): String = when (mode) {
        AppOpsManager.MODE_ALLOWED  -> "Allowed"
        AppOpsManager.MODE_IGNORED  -> "Ignored"
        AppOpsManager.MODE_ERRORED  -> "Denied"
        AppOpsManager.MODE_DEFAULT  -> "Default"
        else                        -> "—"
    }

    /**
     * Reads `ApplicationInfo.primaryCpuAbi` via reflection — it's a system-API
     * field exposed since API 21 but never added to the public SDK. Returns null
     * when the field is missing on the running OS or when the app ships no
     * native code (then `nativeLibraryDir` is also typically absent).
     */
    private fun readPrimaryAbi(ai: ApplicationInfo): String? = try {
        val field = ApplicationInfo::class.java.getDeclaredField("primaryCpuAbi")
        field.isAccessible = true
        (field.get(ai) as? String)?.takeIf { it.isNotBlank() }
    } catch (e: NoSuchFieldException) {
        null
    } catch (e: SecurityException) {
        null
    } catch (e: IllegalAccessException) {
        null
    }

    /**
     * Collects all signers, hashes the first signer to SHA-256, and detects
     * debug-signed APKs by parsing the certificate Subject DN. Apps signed
     * with the AOSP debug keystore (Android SDK) always carry
     * `CN=Android Debug,O=Android,C=US` — a useful red flag when an inspector
     * spots one shipped to a production device.
     */
    @Suppress("DEPRECATION")
    private fun collectSignature(packageName: String): ExpertReport.SignatureInfo {
        val signers: Array<Signature>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = packageManager.getPackageInfo(
                packageName,
                PackageManager.GET_SIGNING_CERTIFICATES,
            )
            info.signingInfo?.apkContentsSigners
        } else {
            packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES).signatures
        }
        if (signers.isNullOrEmpty()) {
            return ExpertReport.SignatureInfo(sha256 = null, signerCount = 0, isDebugSigned = false)
        }
        val first = signers.first()
        val sha = hashSha256Hex(first.toByteArray())
        return ExpertReport.SignatureInfo(
            sha256        = sha,
            signerCount   = signers.size,
            isDebugSigned = isDebugSignerCert(first),
        )
    }

    private fun isDebugSignerCert(signer: Signature): Boolean = try {
        val cf = java.security.cert.CertificateFactory.getInstance("X.509")
        val cert = cf.generateCertificate(signer.toByteArray().inputStream())
            as? java.security.cert.X509Certificate
            ?: return false
        cert.subjectX500Principal.name.contains(DEBUG_SIGNER_DN_SUBSTRING, ignoreCase = true)
    } catch (e: java.security.cert.CertificateException) {
        false
    } catch (e: IllegalArgumentException) {
        false
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
                isHibernated      = queryIsHibernated(pkg.packageName),
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
            isHibernated      = queryIsHibernated(packageName),
        )
    } catch (e: PackageManager.NameNotFoundException) {
        Timber.d(e, "Package %s not found during single-package scan", packageName)
        null
    }

    /**
     * v0.3.2 — probe the OS for an "inactive / hibernated" classification.
     *
     * `UsageStatsManager.isAppInactive(pkg)` (API 23+) returns true when
     * Android's adaptive battery decided the app should stop receiving
     * alarms / jobs / network. Requires PACKAGE_USAGE_STATS — without it,
     * the call returns false silently (which matches our "unknown → false"
     * default).
     *
     * Defensive against SecurityException + IllegalArgumentException — both
     * fall back to false rather than poisoning the scan.
     */
    private fun queryIsHibernated(packageName: String): Boolean = try {
        usageStats?.isAppInactive(packageName) ?: false
    } catch (e: SecurityException) {
        false
    } catch (e: IllegalArgumentException) {
        false
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

    /**
     * v0.4.0 — APK signing certificate fingerprint formatter. Delegates
     * to the shared [HashUtils.sha256ToColonHexUpper] helper so the
     * single representation (`AA:BB:CC:…`) lives in one place. The
     * private wrapper kept for source-compatibility with the existing
     * 2 call sites in this class.
     */
    private fun hashSha256Hex(bytes: ByteArray): String =
        HashUtils.sha256ToColonHexUpper(bytes)

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

        /**
         * Sentinel value for [collectAppOpsSnapshot] when the OS refused or
         * threw on a probe. Distinct from `AppOpsManager.MODE_*` constants
         * (0..4) so the UI can render "—" instead of misreporting a denial.
         */
        private const val APP_OP_MODE_UNAVAILABLE: Int = -1

        /**
         * Heuristic to detect dev / debug-signed APKs by their certificate
         * Subject DN. The AOSP debug keystore (Android SDK, `debug.keystore`)
         * always signs with `CN=Android Debug,O=Android,C=US`. The SHA-256 of
         * that certificate varies across JVMs / SDK versions, so a hardcoded
         * fingerprint would be unreliable — the subject DN does not.
         */
        private const val DEBUG_SIGNER_DN_SUBSTRING: String = "CN=Android Debug"
    }
}
