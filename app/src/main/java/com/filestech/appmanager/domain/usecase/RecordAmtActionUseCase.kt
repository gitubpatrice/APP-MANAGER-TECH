package com.filestech.appmanager.domain.usecase

import com.filestech.appmanager.core.ext.isValidPackageName
import com.filestech.appmanager.core.result.AppError
import com.filestech.appmanager.core.result.Outcome
import com.filestech.appmanager.core.result.runCatchingOutcome
import com.filestech.appmanager.di.IoDispatcher
import com.filestech.appmanager.domain.model.AmtActionResult
import com.filestech.appmanager.domain.model.AmtActionType
import com.filestech.appmanager.domain.repository.AmtActionRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * v0.4.0 — Records one AMT-initiated action in the journal.
 *
 * Wire path :
 *  - Each destructive ViewModel method (`uninstall`, `clearCache`,
 *    `moveToTrash`, …) invokes this use case AFTER its own dispatch so
 *    a record always lives on the same code path as the action it
 *    describes — no risk of recording an action that never fired.
 *  - The result enum is set by the caller :
 *    * `INTENT_REQUESTED` when AMT launched an OS Intent (uninstall
 *      confirm, app-info deep-link, clear-cache settings page…),
 *    * `SUCCESS` when AMT executed an internal action (move to trash,
 *      restore, force-stop, soft quarantine staging),
 *    * `FAILED` when the underlying use case returned `Outcome.Failure`.
 *
 * Validation : invalid package names return `AppError.Validation` and
 * never reach Room.
 *
 * Threading : `withContext(io)` so the Room insert never touches the
 * caller's thread (UI). Fire-and-forget at the UI level is OK — the
 * caller does not await this Outcome to render the destructive action.
 */
class RecordAmtActionUseCase @Inject constructor(
    @IoDispatcher private val io: CoroutineDispatcher,
    private val repository: AmtActionRepository,
) {

    suspend operator fun invoke(
        packageName: String,
        labelSnapshot: String?,
        actionType: AmtActionType,
        result: AmtActionResult,
        timestamp: Long = System.currentTimeMillis(),
    ): Outcome<Long> {
        if (!packageName.isValidPackageName()) {
            return Outcome.Failure(AppError.Validation("Invalid package name"))
        }
        return runCatchingOutcome(mapError = { AppError.Unknown(it) }) {
            withContext(io) {
                when (val out = repository.insert(
                    packageName   = packageName,
                    labelSnapshot = labelSnapshot,
                    actionType    = actionType,
                    result        = result,
                    timestamp     = timestamp,
                )) {
                    is Outcome.Success -> out.value
                    is Outcome.Failure -> throw IllegalStateException(out.error.toString())
                    Outcome.Loading    -> throw IllegalStateException(
                        "AmtActionRepository.insert returned Loading — contract violation",
                    )
                }
            }
        }
    }
}
