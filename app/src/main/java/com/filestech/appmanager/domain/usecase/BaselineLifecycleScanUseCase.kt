package com.filestech.appmanager.domain.usecase

import com.filestech.appmanager.core.result.AppError
import com.filestech.appmanager.core.result.Outcome
import com.filestech.appmanager.core.result.getOrNull
import com.filestech.appmanager.core.result.runCatchingOutcome
import com.filestech.appmanager.di.IoDispatcher
import com.filestech.appmanager.domain.model.LifecycleEventType
import com.filestech.appmanager.domain.repository.AppInfoRepository
import com.filestech.appmanager.domain.repository.AppLifecycleRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * v0.3.0 — One-shot baseline scan executed at most once per package.
 *
 * Inserts a [LifecycleEventType.BASELINE] row for every user-installed app
 * that does not already have one in the history. Idempotent — re-running on
 * subsequent launches is a no-op for packages we already baselined.
 *
 * Trigger:
 *  - `MainApplication.onCreate` (gated by `settings.lifecycle.enabled`).
 *  - Settings → enable Lifecycle tracking → user flips toggle → re-run.
 *
 * Cost: O(n) Room reads to check `hasBaseline` per package + O(missing) inserts.
 * Cap : returns early if the cache has 0 apps (no rescan yet — there will be
 * nothing useful to baseline).
 */
class BaselineLifecycleScanUseCase @Inject constructor(
    @IoDispatcher private val io: CoroutineDispatcher,
    private val appInfoRepository: AppInfoRepository,
    private val lifecycleRepository: AppLifecycleRepository,
    private val recordEvent: RecordLifecycleEventUseCase,
) {

    /**
     * @return number of BASELINE rows newly inserted (0 = everything was
     *   already baselined or the cache is empty).
     */
    suspend operator fun invoke(): Outcome<Int> =
        runCatchingOutcome(mapError = { AppError.Unknown(it) }) {
            withContext(io) {
                val apps = appInfoRepository.observeApps(includeSystemApps = false)
                    .first()
                    .getOrNull()
                    .orEmpty()
                if (apps.isEmpty()) {
                    Timber.i("BaselineLifecycleScan: empty cache, skipping")
                    return@withContext 0
                }
                var inserted = 0
                val now = System.currentTimeMillis()
                for (app in apps) {
                    val already = lifecycleRepository.hasBaseline(app.packageName).getOrNull()
                        ?: false
                    if (already) continue
                    val result = recordEvent(
                        packageName = app.packageName,
                        type        = LifecycleEventType.BASELINE,
                        capturedAt  = now,
                    )
                    if (result is Outcome.Success) inserted++
                    else if (result is Outcome.Failure) {
                        Timber.w("BaselineLifecycleScan: insert failed for %s: %s",
                            app.packageName, result.error)
                    }
                }
                Timber.i("BaselineLifecycleScan: %d new BASELINE rows", inserted)
                inserted
            }
        }
}
