package com.filestech.appmanager.domain.usecase

import com.filestech.appmanager.domain.model.QuarantineEntry
import com.filestech.appmanager.domain.repository.QuarantineRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Hot stream of every quarantined app, sorted by [QuarantineEntry.restoreAt]
 * ascending (next-to-expire first).
 *
 * Used by [com.filestech.appmanager.ui.screens.quarantine.QuarantineViewModel]
 * to drive the Quarantine list screen.
 */
class ObserveQuarantinesUseCase @Inject constructor(
    private val repository: QuarantineRepository,
) {
    operator fun invoke(): Flow<List<QuarantineEntry>> = repository.observeAll()
}

/**
 * Hot count for badge UIs (Tool card subtitle, etc.).
 */
class ObserveQuarantineCountUseCase @Inject constructor(
    private val repository: QuarantineRepository,
) {
    operator fun invoke(): Flow<Int> = repository.observeCount()
}
