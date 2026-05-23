package com.filestech.appmanager.domain.usecase

import com.filestech.appmanager.core.ext.MAX_IGNORED_PACKAGES
import com.filestech.appmanager.core.ext.isValidPackageName
import com.filestech.appmanager.core.result.AppError
import com.filestech.appmanager.core.result.Outcome
import com.filestech.appmanager.core.result.runCatchingOutcome
import com.filestech.appmanager.domain.repository.IgnoreListRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Adds [packageName] to the ignore list. Idempotent.
 *
 * VII M-4 fix: rejects the add when the ignore list has already reached
 * [MAX_IGNORED_PACKAGES] entries. DataStore reloads the whole `Set<String>`
 * blob on every write — an unbounded set would cause perceptible latency on
 * the toggle and eventually ANR on the IO thread.
 */
class IgnoreAppUseCase @Inject constructor(
    private val repository: IgnoreListRepository,
) {
    suspend operator fun invoke(packageName: String): Outcome<Unit> {
        if (!packageName.isValidPackageName()) {
            return Outcome.Failure(AppError.Validation("Invalid package name"))
        }
        val current = repository.observe().first()
        if (packageName !in current && current.size >= MAX_IGNORED_PACKAGES) {
            return Outcome.Failure(
                AppError.Validation("Ignore list is full (max $MAX_IGNORED_PACKAGES entries)"),
            )
        }
        return runCatchingOutcome(mapError = { AppError.Unknown(it) }) {
            repository.add(packageName)
        }
    }
}

/** Removes [packageName] from the ignore list. Idempotent. */
class UnignoreAppUseCase @Inject constructor(
    private val repository: IgnoreListRepository,
) {
    suspend operator fun invoke(packageName: String): Outcome<Unit> {
        if (!packageName.isValidPackageName()) {
            return Outcome.Failure(AppError.Validation("Invalid package name"))
        }
        return runCatchingOutcome(mapError = { AppError.Unknown(it) }) {
            repository.remove(packageName)
        }
    }
}

/** One-shot probe. */
class IsAppIgnoredUseCase @Inject constructor(
    private val repository: IgnoreListRepository,
) {
    suspend operator fun invoke(packageName: String): Boolean =
        repository.isIgnored(packageName)
}
