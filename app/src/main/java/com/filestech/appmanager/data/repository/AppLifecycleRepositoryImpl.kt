package com.filestech.appmanager.data.repository

import com.filestech.appmanager.core.ext.isValidPackageName
import com.filestech.appmanager.core.result.AppError
import com.filestech.appmanager.core.result.Outcome
import com.filestech.appmanager.core.result.runCatchingOutcome
import com.filestech.appmanager.data.local.db.dao.AppLifecycleEventDao
import com.filestech.appmanager.data.local.db.entity.AppLifecycleEventEntity
import com.filestech.appmanager.di.IoDispatcher
import com.filestech.appmanager.domain.model.LifecycleEvent
import com.filestech.appmanager.domain.model.LifecycleEventType
import com.filestech.appmanager.domain.model.UninstallReason
import com.filestech.appmanager.domain.repository.AppLifecycleRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * v0.3.0 — Room-backed implementation of [AppLifecycleRepository].
 *
 * Mapping layer is private (the domain only sees [LifecycleEvent] /
 * [LifecycleEventType] / [UninstallReason]). The dangerous-perms list is
 * stored as a `|`-separated TEXT for compactness; decoding tolerates NULL +
 * empty string + trailing separators.
 *
 * Same single-responsibility split as [AppInfoRepositoryImpl] /
 * [PermissionSnapshotRepositoryImpl]: this class does NOT know what triggers
 * a capture (broadcast vs baseline scan) — it only owns persistence.
 */
@Singleton
class AppLifecycleRepositoryImpl @Inject constructor(
    private val dao: AppLifecycleEventDao,
    @IoDispatcher private val io: CoroutineDispatcher,
) : AppLifecycleRepository {

    override fun observeSince(sinceMs: Long): Flow<Outcome<List<LifecycleEvent>>> =
        dao.observeSince(sinceMs)
            .map<List<AppLifecycleEventEntity>, Outcome<List<LifecycleEvent>>> { rows ->
                Outcome.Success(rows.map { it.toDomain() })
            }
            .catch { t -> emit(Outcome.Failure(AppError.DatabaseError(t))) }
            .flowOn(io)

    override fun observeByPackage(packageName: String): Flow<Outcome<List<LifecycleEvent>>> =
        dao.observeByPackage(packageName)
            .map<List<AppLifecycleEventEntity>, Outcome<List<LifecycleEvent>>> { rows ->
                Outcome.Success(rows.map { it.toDomain() })
            }
            .catch { t -> emit(Outcome.Failure(AppError.DatabaseError(t))) }
            .flowOn(io)

    override suspend fun insert(
        packageName: String,
        label: String?,
        type: LifecycleEventType,
        capturedAt: Long,
        versionName: String?,
        versionCode: Long,
        installerPackage: String?,
        totalSizeBytes: Long,
        grantedDangerousPermissions: List<String>,
        apkSha256: String?,
    ): Outcome<Long> {
        if (!packageName.isValidPackageName()) {
            return Outcome.Failure(AppError.Validation("Invalid package name"))
        }
        return runCatchingOutcome(::mapError) {
            withContext(io) {
                dao.insert(
                    AppLifecycleEventEntity(
                        packageName            = packageName,
                        label                  = label,
                        type                   = type.name,
                        capturedAt             = capturedAt,
                        versionName            = versionName,
                        versionCode            = versionCode,
                        installerPackage       = installerPackage,
                        totalSizeBytes         = totalSizeBytes,
                        grantedDangerousPerms  = encodePerms(grantedDangerousPermissions),
                        userReason             = null,
                        apkSha256              = apkSha256,
                    ),
                )
            }
        }
    }

    override suspend fun setReason(eventId: Long, reason: UninstallReason?): Outcome<Boolean> =
        runCatchingOutcome(::mapError) {
            withContext(io) {
                dao.setReasonForId(eventId, reason?.name) > 0
            }
        }

    override suspend fun hasBaseline(packageName: String): Outcome<Boolean> {
        if (!packageName.isValidPackageName()) {
            return Outcome.Failure(AppError.Validation("Invalid package name"))
        }
        return runCatchingOutcome(::mapError) {
            withContext(io) { dao.hasBaseline(packageName) }
        }
    }

    override suspend fun latestByPackage(packageName: String): Outcome<LifecycleEvent?> {
        if (!packageName.isValidPackageName()) {
            return Outcome.Failure(AppError.Validation("Invalid package name"))
        }
        return runCatchingOutcome(::mapError) {
            withContext(io) { dao.getLatestByPackage(packageName)?.toDomain() }
        }
    }

    override suspend fun latestUninstallByPackage(packageName: String): Outcome<LifecycleEvent?> {
        if (!packageName.isValidPackageName()) {
            return Outcome.Failure(AppError.Validation("Invalid package name"))
        }
        return runCatchingOutcome(::mapError) {
            withContext(io) { dao.getLatestUninstallByPackage(packageName)?.toDomain() }
        }
    }

    override suspend fun purgeOlderThan(cutoffMs: Long): Outcome<Int> =
        runCatchingOutcome(::mapError) {
            withContext(io) { dao.purgeOlderThan(cutoffMs) }
        }

    override suspend fun deleteAll(): Outcome<Int> =
        runCatchingOutcome(::mapError) {
            withContext(io) { dao.deleteAll() }
        }

    override suspend fun count(): Outcome<Int> =
        runCatchingOutcome(::mapError) {
            withContext(io) { dao.count() }
        }

    // -----------------------------------------------------------------------
    // Mapping
    // -----------------------------------------------------------------------

    private fun AppLifecycleEventEntity.toDomain(): LifecycleEvent = LifecycleEvent(
        id                          = id,
        packageName                 = packageName,
        label                       = label,
        type                        = parseType(type),
        capturedAt                  = capturedAt,
        versionName                 = versionName,
        versionCode                 = versionCode,
        installerPackage            = installerPackage,
        totalSizeBytes              = totalSizeBytes,
        grantedDangerousPermissions = decodePerms(grantedDangerousPerms),
        userReason                  = userReason?.let { parseReason(it) },
        apkSha256                   = apkSha256,
    )

    /**
     * Forward-compat decoder: unknown enum names (downgrade after we add a
     * new variant) collapse to BASELINE so a stale row doesn't crash the
     * observe flow. The UI shows it as "Baseline" which is harmless.
     */
    private fun parseType(raw: String): LifecycleEventType =
        runCatching { LifecycleEventType.valueOf(raw) }.getOrDefault(LifecycleEventType.BASELINE)

    private fun parseReason(raw: String): UninstallReason? =
        runCatching { UninstallReason.valueOf(raw) }.getOrNull()

    /**
     * Encodes a list of permission constants into a single `|`-separated
     * TEXT. The `|` character is invalid inside a permission constant
     * (Android requires `[A-Za-z0-9._]`) so the encoding is unambiguous.
     * Empty list → empty string (NOT null). The repository decodes both
     * empty string and null to `emptyList()` for forward-compat.
     */
    private fun encodePerms(perms: List<String>): String =
        if (perms.isEmpty()) "" else perms.joinToString(separator = "|")

    private fun decodePerms(raw: String?): List<String> {
        if (raw.isNullOrEmpty()) return emptyList()
        return raw.split('|').filter { it.isNotBlank() }
    }

    private fun mapError(t: Throwable): AppError = when (t) {
        is android.database.SQLException -> AppError.DatabaseError(t)
        else                              -> AppError.Unknown(t)
    }
}
