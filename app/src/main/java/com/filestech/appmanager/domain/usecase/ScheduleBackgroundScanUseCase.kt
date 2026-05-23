package com.filestech.appmanager.domain.usecase

import com.filestech.appmanager.core.result.AppError
import com.filestech.appmanager.core.result.Outcome
import com.filestech.appmanager.core.result.runCatchingOutcome
import com.filestech.appmanager.data.local.datastore.SettingsRepository
import com.filestech.appmanager.data.system.WorkScheduler
import com.filestech.appmanager.domain.model.ScanInterval
import javax.inject.Inject

/**
 * Persists the user-chosen [ScanInterval] AND applies it to WorkManager in
 * the same call — so the UI never has to remember to do both.
 *
 * Idempotent: applying the same interval twice is a no-op (WorkScheduler
 * uses `ExistingPeriodicWorkPolicy.UPDATE`).
 *
 * **Architectural exception (Phase VIII C6 documentation)**: this UseCase
 * imports `data.local.datastore.SettingsRepository` and `data.system.WorkScheduler`
 * directly — both are infrastructure concerns living in the `data/` layer.
 * The pragmatic choice (vs introducing a `domain/service/WorkScheduler`
 * interface and a binding) keeps the wiring shallow for a single call site.
 * If a second UseCase ever needs to drive WorkManager, lift `WorkScheduler`
 * to a `domain/service/` interface at that point.
 */
class ScheduleBackgroundScanUseCase @Inject constructor(
    private val settings: SettingsRepository,
    private val scheduler: WorkScheduler,
) {
    suspend operator fun invoke(interval: ScanInterval): Outcome<Unit> =
        runCatchingOutcome(mapError = { AppError.Unknown(it) }) {
            settings.update { copy(scanner = scanner.copy(autoScanInterval = interval)) }
            scheduler.apply(interval)
        }
}
