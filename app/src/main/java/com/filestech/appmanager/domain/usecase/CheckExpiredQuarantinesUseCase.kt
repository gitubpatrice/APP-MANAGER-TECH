package com.filestech.appmanager.domain.usecase

import com.filestech.appmanager.core.result.AppError
import com.filestech.appmanager.core.result.Outcome
import com.filestech.appmanager.core.result.runCatchingOutcome
import com.filestech.appmanager.domain.model.QuarantineEntry
import com.filestech.appmanager.domain.repository.QuarantineRepository
import timber.log.Timber
import javax.inject.Inject

/**
 * Returns every quarantine entry whose [QuarantineEntry.restoreAt] has passed
 * and which has NOT yet been notified. Used by the periodic
 * [com.filestech.appmanager.data.system.workers.QuarantineRestoreWorker] to
 * decide which entries to surface as "expired — please decide".
 *
 * Doesn't auto-mark the entries as notified — the worker does that after the
 * notification fires successfully, so a notif failure leads to a retry next
 * tick rather than losing the expiry signal.
 */
class CheckExpiredQuarantinesUseCase @Inject constructor(
    private val repository: QuarantineRepository,
) {
    suspend operator fun invoke(
        nowMs: Long = System.currentTimeMillis(),
    ): Outcome<List<QuarantineEntry>> = runCatchingOutcome(mapError = { AppError.Unknown(it) }) {
        val expired = repository.getExpiredUnnotified(nowMs)
        if (expired.isNotEmpty()) {
            Timber.i("CheckExpiredQuarantines: %d entries expired", expired.size)
        }
        expired
    }
}

/**
 * Acknowledges that the expiry notification fired for [packageName] — flips
 * `notified = true`. Idempotent.
 */
class MarkQuarantineNotifiedUseCase @Inject constructor(
    private val repository: QuarantineRepository,
) {
    suspend operator fun invoke(packageName: String) {
        repository.markNotified(packageName)
    }
}
