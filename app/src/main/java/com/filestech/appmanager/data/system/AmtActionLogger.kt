package com.filestech.appmanager.data.system

import com.filestech.appmanager.data.local.datastore.SettingsRepository
import com.filestech.appmanager.di.ApplicationScope
import com.filestech.appmanager.domain.model.AmtActionResult
import com.filestech.appmanager.domain.model.AmtActionType
import com.filestech.appmanager.domain.usecase.RecordAmtActionUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * v0.4.0 — Tiny call-site shim that lets every destructive ViewModel
 * record an AMT action with a single non-suspend, fire-and-forget call.
 *
 * Why a separate class (not direct [RecordAmtActionUseCase] injection
 * in every VM) ?
 *  - **Single-line wiring** — call sites just need
 *    `actionLogger.log(pkg, label, type, result)` without juggling a
 *    suspend boundary or a Hilt-injected dispatcher.
 *  - **Zero-cost feature gate** — the
 *    [com.filestech.appmanager.data.local.datastore.AppSettings.ActionJournal.enabled]
 *    flag is cached in an [AtomicBoolean] that a hot collector keeps
 *    fresh. When the journal is OFF, `log(...)` is a single atomic
 *    read + early return — no DataStore IPC, no coroutine launch.
 *  - **Process-scope lifetime** — every record runs on
 *    [ApplicationScope] so a VM that gets garbage-collected mid-launch
 *    (the user navigates away the moment they tap Uninstall) still
 *    persists its journal row.
 *  - **Never blocks the caller** — every dispatch is `launch {}` on
 *    the app scope ; the destructive Intent / Room call returns
 *    immediately.
 *
 * Failure model : the underlying use case logs into Timber on Failure
 * and swallows the Outcome — recording is best-effort, never load-
 * bearing for the destructive action itself.
 */
@Singleton
class AmtActionLogger @Inject constructor(
    @ApplicationScope private val appScope: CoroutineScope,
    settings: SettingsRepository,
    private val record: RecordAmtActionUseCase,
) {

    private val enabledFlag = AtomicBoolean(false)

    init {
        // Hot observer keeps the cached flag in sync with DataStore writes.
        // `distinctUntilChanged` shields against re-emits on unrelated
        // settings changes.
        appScope.launch {
            settings.flow
                .map { it.actionJournal.enabled }
                .distinctUntilChanged()
                .collect { enabledFlag.set(it) }
        }
    }

    /**
     * Records one action. Non-suspend — safe to call from any thread,
     * including the UI thread. Returns immediately ; the actual Room
     * insert runs on the IO dispatcher inside the use case.
     */
    fun log(
        packageName: String,
        labelSnapshot: String?,
        actionType: AmtActionType,
        result: AmtActionResult,
    ) {
        if (!enabledFlag.get()) return
        appScope.launch {
            when (val outcome = record(packageName, labelSnapshot, actionType, result)) {
                is com.filestech.appmanager.core.result.Outcome.Failure ->
                    Timber.w("AmtActionLogger: record failed for %s: %s", packageName, outcome.error)
                else -> Unit
            }
        }
    }
}
