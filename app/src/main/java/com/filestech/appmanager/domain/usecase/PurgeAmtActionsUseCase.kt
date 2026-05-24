package com.filestech.appmanager.domain.usecase

import com.filestech.appmanager.core.ext.MS_PER_DAY
import com.filestech.appmanager.core.result.AppError
import com.filestech.appmanager.core.result.Outcome
import com.filestech.appmanager.core.result.runCatchingOutcome
import com.filestech.appmanager.di.IoDispatcher
import com.filestech.appmanager.domain.repository.AmtActionRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * v0.4.0 — Drops AMT action journal rows older than [retentionDays].
 *
 * Mirror of [PurgeLifecycleEventsUseCase] : best-effort, never throws
 * across the boundary, returns the count of rows deleted for
 * telemetry.
 *
 * Scheduling: invoked by
 * [com.filestech.appmanager.data.system.workers.AmtActionJournalPurgeWorker]
 * on its 24-hour tick. Also invoked manually from Settings when the
 * user lowers the retention slider.
 */
class PurgeAmtActionsUseCase @Inject constructor(
    @IoDispatcher private val io: CoroutineDispatcher,
    private val repository: AmtActionRepository,
) {

    suspend operator fun invoke(retentionDays: Int): Outcome<Int> =
        runCatchingOutcome(mapError = { AppError.Unknown(it) }) {
            withContext(io) {
                require(retentionDays > 0) { "retentionDays must be > 0, was $retentionDays" }
                val cutoff = System.currentTimeMillis() - retentionDays * MS_PER_DAY
                val deleted = when (val r = repository.purgeOlderThan(cutoff)) {
                    is Outcome.Success -> r.value
                    is Outcome.Failure -> throw IllegalStateException(r.error.toString())
                    Outcome.Loading    -> 0
                }
                Timber.i("PurgeAmtActions: deleted %d rows older than %d days", deleted, retentionDays)
                deleted
            }
        }
}
