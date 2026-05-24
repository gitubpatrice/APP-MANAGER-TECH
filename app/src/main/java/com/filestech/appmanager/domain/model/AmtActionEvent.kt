package com.filestech.appmanager.domain.model

/**
 * v0.4.0 — One AMT-initiated action recorded in the in-app journal.
 *
 * **Scope / boundary with [LifecycleEvent]** : the LifecycleEvent table
 * tracks **OS broadcasts** (the user installed/uninstalled an app from
 * anywhere — Play Store, F-Droid, sideload, AMT, others). This table
 * tracks **actions that AMT itself initiated** — the user pressed an
 * Uninstall button inside AMT, or moved an app to the trash from AMT.
 *
 * Both surfaces are complementary :
 *  - "I see a REPLACED in my LifecycleHistory but I do not remember
 *    updating anything" → check the ActionJournal — was it me ?
 *  - "I want a clean log of every destructive action AMT performed on
 *    my behalf" → ActionJournal is the dedicated audit trail.
 *
 * Append-only : rows are inserted by
 * [com.filestech.appmanager.domain.usecase.RecordAmtActionUseCase] from
 * every ViewModel that fires a destructive method. Never updated, never
 * deleted except by the periodic retention purge.
 *
 * Privacy : stays in the app's private Room DB, never leaves the device,
 * the INTERNET permission is not declared.
 */
data class AmtActionEvent(
    val id: Long,
    val packageName: String,
    /**
     * Cached app label at the moment AMT fired the action. Null when the
     * caller could not resolve a label (rare — package gone between
     * dialog confirm and Intent launch). The UI falls back to
     * [packageName] in that case so a row is never blank.
     */
    val labelSnapshot: String?,
    val actionType: AmtActionType,
    val result: AmtActionResult,
    /** Epoch ms — set by the use case, NOT by SQLite default. */
    val timestamp: Long,
)

/**
 * Kind of action AMT performed.
 *
 * Storage encoding : enum name as TEXT — never rename a value (would
 * orphan historical rows). Add new values at the END only.
 *
 * The 10 values correspond to the destructive / state-changing methods
 * exposed by `AppDetailViewModel`, `AppListViewModel` and
 * `TrashViewModel`. They are deliberately decoupled from the underlying
 * Intent or Room call so a future split (e.g. `UNINSTALL_BATCH`) can be
 * added without breaking the wire format.
 */
enum class AmtActionType {
    UNINSTALL,
    FORCE_STOP,
    DISABLE,
    ENABLE,
    CLEAR_CACHE,
    CLEAR_DATA,
    MOVE_TO_TRASH,
    RESTORE_FROM_TRASH,
    QUARANTINE_HARD,
    QUARANTINE_SOFT,
}

/**
 * Outcome of the AMT-side dispatch. Note that for actions whose
 * completion is opaque to AMT (the OS shows a confirm dialog and the
 * user accepts/cancels there), the value is [INTENT_REQUESTED] : AMT
 * fired its intent, the rest is the user + the OS. The LifecycleHistory
 * row that follows (INSTALLED/UNINSTALLED/REPLACED) is the source of
 * truth for what actually happened on disk.
 *
 * Storage encoding : enum name as TEXT — never rename. Add at the END.
 */
enum class AmtActionResult {
    /**
     * AMT launched a system Intent (uninstall confirm, app-info deep
     * link, clear-cache settings page…). The OS handles the user
     * confirmation opaquely.
     */
    INTENT_REQUESTED,
    /**
     * Internal AMT action (move to trash, restore, force-stop, soft
     * quarantine staging) completed successfully — no OS dialog
     * involved, the outcome is deterministic.
     */
    SUCCESS,
    /**
     * The use case returned `Outcome.Failure` or the Intent could not
     * be resolved. The reason lives in Timber logs, not the journal —
     * the journal stays compact.
     */
    FAILED,
}
