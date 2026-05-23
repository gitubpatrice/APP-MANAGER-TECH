package com.filestech.appmanager.domain.usecase

import com.filestech.appmanager.core.ext.isValidPackageName
import com.filestech.appmanager.core.result.AppError
import com.filestech.appmanager.core.result.Outcome
import com.filestech.appmanager.domain.repository.TrashRepository
import javax.inject.Inject

/**
 * Removes one package from the Corbeille / Trash. The app stays installed —
 * "restore" only means "no longer staged for uninstall".
 */
class RestoreFromTrashUseCase @Inject constructor(
    private val trash: TrashRepository,
) {

    suspend operator fun invoke(packageName: String): Outcome<Unit> {
        if (!packageName.isValidPackageName()) {
            return Outcome.Failure(AppError.Validation("Invalid package name"))
        }
        trash.restore(packageName)
        return Outcome.Success(Unit)
    }
}
