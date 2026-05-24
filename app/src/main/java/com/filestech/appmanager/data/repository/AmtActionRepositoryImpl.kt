package com.filestech.appmanager.data.repository

import com.filestech.appmanager.core.ext.isValidPackageName
import com.filestech.appmanager.core.result.AppError
import com.filestech.appmanager.core.result.Outcome
import com.filestech.appmanager.core.result.runCatchingOutcome
import com.filestech.appmanager.data.local.db.dao.AmtActionEventDao
import com.filestech.appmanager.data.local.db.entity.AmtActionEventEntity
import com.filestech.appmanager.di.IoDispatcher
import com.filestech.appmanager.domain.model.AmtActionEvent
import com.filestech.appmanager.domain.model.AmtActionResult
import com.filestech.appmanager.domain.model.AmtActionType
import com.filestech.appmanager.domain.repository.AmtActionRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * v0.4.0 — Room-backed implementation of [AmtActionRepository].
 *
 * Mapping layer is private (the domain only sees [AmtActionEvent] /
 * [AmtActionType] / [AmtActionResult]). The `action_type` and `result`
 * enums are stored as their `name` strings (forward-compat decoding
 * drops unknown values from the UI feed).
 *
 * Same single-responsibility split as [AppLifecycleRepositoryImpl] :
 * this class does NOT know what triggers a capture (which destructive
 * call site fires) — it only owns persistence.
 */
@Singleton
class AmtActionRepositoryImpl @Inject constructor(
    private val dao: AmtActionEventDao,
    @IoDispatcher private val io: CoroutineDispatcher,
) : AmtActionRepository {

    override fun observeSince(sinceMs: Long): Flow<Outcome<List<AmtActionEvent>>> =
        dao.observeSince(sinceMs)
            .map<List<AmtActionEventEntity>, Outcome<List<AmtActionEvent>>> { rows ->
                Outcome.Success(rows.mapNotNull { it.toDomainOrNull() })
            }
            .catch { t -> emit(Outcome.Failure(AppError.DatabaseError(t))) }
            .flowOn(io)

    override suspend fun insert(
        packageName: String,
        labelSnapshot: String?,
        actionType: AmtActionType,
        result: AmtActionResult,
        timestamp: Long,
    ): Outcome<Long> {
        if (!packageName.isValidPackageName()) {
            return Outcome.Failure(AppError.Validation("Invalid package name"))
        }
        return runCatchingOutcome(::mapError) {
            withContext(io) {
                dao.insert(
                    AmtActionEventEntity(
                        packageName   = packageName,
                        labelSnapshot = labelSnapshot,
                        actionType    = actionType.name,
                        result        = result.name,
                        timestamp     = timestamp,
                    ),
                )
            }
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

    /**
     * Decodes an entity row to its domain shape. Returns null when
     * either enum field carries a value unknown to the current build
     * (downgrade after we add a new variant). The caller filters those
     * out via `mapNotNull` so the UI never sees an unparseable row.
     * The row stays on disk untouched — a future upgrade can decode it.
     */
    private fun AmtActionEventEntity.toDomainOrNull(): AmtActionEvent? {
        val type = runCatching { AmtActionType.valueOf(actionType) }.getOrNull() ?: return null
        val res  = runCatching { AmtActionResult.valueOf(result) }.getOrNull() ?: return null
        return AmtActionEvent(
            id            = id,
            packageName   = packageName,
            labelSnapshot = labelSnapshot,
            actionType    = type,
            result        = res,
            timestamp     = timestamp,
        )
    }

    private fun mapError(t: Throwable): AppError = when (t) {
        is android.database.SQLException -> AppError.DatabaseError(t)
        else                              -> AppError.Unknown(t)
    }
}
