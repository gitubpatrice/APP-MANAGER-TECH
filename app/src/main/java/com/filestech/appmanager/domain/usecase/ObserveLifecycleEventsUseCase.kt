package com.filestech.appmanager.domain.usecase

import com.filestech.appmanager.core.ext.MS_PER_DAY
import com.filestech.appmanager.core.result.Outcome
import com.filestech.appmanager.domain.model.LifecycleEvent
import com.filestech.appmanager.domain.repository.AppLifecycleRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * v0.3.0 — Reactive feed of lifecycle events for the History screen.
 *
 * Carries a [Window] argument so the same UseCase powers the 30j / 90j /
 * "Tout" picker without duplicating data layer code. The 30/90 day windows
 * are computed relative to "now at subscription time" — re-collecting on
 * window change re-bases the lower bound; the upstream Room flow restarts
 * on a new query parameter.
 */
class ObserveLifecycleEventsUseCase @Inject constructor(
    private val repository: AppLifecycleRepository,
) {

    enum class Window(val days: Int?) {
        LAST_30(30),
        LAST_90(90),
        ALL(null),
    }

    operator fun invoke(window: Window): Flow<Outcome<List<LifecycleEvent>>> {
        val sinceMs = window.days?.let {
            System.currentTimeMillis() - it * MS_PER_DAY
        } ?: 0L
        return repository.observeSince(sinceMs)
    }
}
