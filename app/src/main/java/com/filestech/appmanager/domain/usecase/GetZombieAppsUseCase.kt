package com.filestech.appmanager.domain.usecase

import com.filestech.appmanager.core.ext.MS_PER_DAY
import com.filestech.appmanager.core.result.AppError
import com.filestech.appmanager.core.result.Outcome
import com.filestech.appmanager.core.result.runCatchingOutcome
import com.filestech.appmanager.domain.model.AppInfo
import com.filestech.appmanager.domain.model.ZombieApp
import com.filestech.appmanager.domain.repository.AppInfoRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Identifies "zombie" apps in the device's catalogue.
 *
 * Two qualifying patterns:
 * - [ZombieApp.Reason.NEVER_OPENED] — lastUsedTime == 0 AND first install
 *   older than [neverOpenedGraceDays] (avoids flagging just-installed apps
 *   the user simply hasn't launched yet).
 * - [ZombieApp.Reason.UNUSED_SINCE] — lastUsedTime older than
 *   [unusedThresholdDays] ago.
 *
 * The two patterns are NOT mutually exclusive at the data level; NEVER_OPENED
 * always wins in the output (more specific signal).
 */
class GetZombieAppsUseCase @Inject constructor(
    private val repository: AppInfoRepository,
) {

    suspend operator fun invoke(
        unusedThresholdDays: Int = 30,
        neverOpenedGraceDays: Int = 7,
    ): Outcome<List<ZombieApp>> {
        if (unusedThresholdDays <= 0) {
            return Outcome.Failure(AppError.Validation("unusedThresholdDays must be > 0"))
        }
        return runCatchingOutcome(mapError = { AppError.Unknown(it) }) {
            val now = System.currentTimeMillis()
            val unusedCutoff = now - unusedThresholdDays * MS_PER_DAY
            val newInstallCutoff = now - neverOpenedGraceDays * MS_PER_DAY

            // Drain the first non-Loading emission of the user-apps catalogue.
            val first = repository.observeApps(includeSystemApps = false)
                .first { it !is Outcome.Loading }

            val apps = when (first) {
                is Outcome.Success -> first.value
                is Outcome.Failure -> throw IllegalStateException(first.error.toString())
                Outcome.Loading    -> emptyList()
            }

            apps.asSequence()
                .filter { it.isUninstallable } // can't act on system apps anyway
                .mapNotNull { app -> classify(app, now, unusedCutoff, newInstallCutoff) }
                .sortedByDescending { it.unusedDays }
                .toList()
        }
    }

    private fun classify(
        app: AppInfo,
        now: Long,
        unusedCutoff: Long,
        newInstallCutoff: Long,
    ): ZombieApp? {
        // Never opened — only flag if installed long enough ago.
        if (app.lastUsedTime == 0L && app.firstInstallTime <= newInstallCutoff) {
            val days = ((now - app.firstInstallTime) / MS_PER_DAY).toInt()
            return ZombieApp(info = app, reason = ZombieApp.Reason.NEVER_OPENED, unusedDays = days)
        }
        // Used at some point but not recently.
        if (app.lastUsedTime in 1..unusedCutoff) {
            val days = ((now - app.lastUsedTime) / MS_PER_DAY).toInt()
            return ZombieApp(info = app, reason = ZombieApp.Reason.UNUSED_SINCE, unusedDays = days)
        }
        return null
    }

    // MS_PER_DAY now imported from core.ext.TimeConstants (VII C1 fix — single source).
}
