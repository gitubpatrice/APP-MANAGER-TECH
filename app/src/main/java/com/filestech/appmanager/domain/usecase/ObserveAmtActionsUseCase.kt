package com.filestech.appmanager.domain.usecase

import com.filestech.appmanager.core.ext.MS_PER_DAY
import com.filestech.appmanager.core.result.Outcome
import com.filestech.appmanager.domain.model.AmtActionEvent
import com.filestech.appmanager.domain.repository.AmtActionRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * v0.4.0 — Reactive feed of AMT-initiated actions for the journal
 * screen. Mirrors [ObserveLifecycleEventsUseCase] — same 3-segment
 * window vocabulary (30j / 90j / Tout) so the user does not have to
 * learn a second mental model.
 *
 * The 30 / 90-day windows are computed relative to "now at subscription
 * time" — re-collecting on window change re-bases the lower bound; the
 * upstream Room flow restarts on a new query parameter.
 */
class ObserveAmtActionsUseCase @Inject constructor(
    private val repository: AmtActionRepository,
) {

    enum class Window(val days: Int?) {
        LAST_30(30),
        LAST_90(90),
        ALL(null),
    }

    operator fun invoke(window: Window): Flow<Outcome<List<AmtActionEvent>>> {
        val sinceMs = window.days?.let {
            System.currentTimeMillis() - it * MS_PER_DAY
        } ?: 0L
        return repository.observeSince(sinceMs)
    }
}
