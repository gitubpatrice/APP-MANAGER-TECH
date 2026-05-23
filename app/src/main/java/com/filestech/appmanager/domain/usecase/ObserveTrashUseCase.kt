package com.filestech.appmanager.domain.usecase

import com.filestech.appmanager.domain.model.TrashItem
import com.filestech.appmanager.domain.repository.TrashRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Hot stream of every app currently in the Corbeille / Trash, sorted by
 * added-at desc (most-recent first).
 */
class ObserveTrashUseCase @Inject constructor(
    private val trash: TrashRepository,
) {
    operator fun invoke(): Flow<List<TrashItem>> = trash.observe()
}
