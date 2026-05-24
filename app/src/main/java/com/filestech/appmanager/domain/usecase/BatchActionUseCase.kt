package com.filestech.appmanager.domain.usecase

import com.filestech.appmanager.core.ext.isValidPackageName
import com.filestech.appmanager.core.result.AppError
import com.filestech.appmanager.core.result.Outcome
import com.filestech.appmanager.domain.model.AppAction
import com.filestech.appmanager.domain.model.BatchActionResult
import timber.log.Timber
import javax.inject.Inject

/**
 * Applies an [AppAction] to a list of packages sequentially and returns a
 * per-package status report.
 *
 * **Important UX constraint**: actions that require an OS confirmation
 * dialog ([AppAction.Uninstall], [AppAction.ClearCache], and [AppAction.SetEnabled]
 * on non-root devices) cannot be batched silently — the OS shows one dialog
 * per package. Calling this UseCase with such actions returns
 * [Outcome.Failure] with [AppError.Validation]; the UI must iterate at its
 * own level and launch the per-package Intents one by one.
 *
 * The only action that batches silently is [AppAction.ForceStop], because
 * `ActivityManager.killBackgroundProcesses` requires no user confirmation.
 *
 * Sequential by design: parallel would not help (each call is cheap and goes
 * to the same `ActivityManager`); sequential keeps log lines ordered and lets
 * the UI render a progress bar.
 */
class BatchActionUseCase @Inject constructor(
    private val forceStop: ForceStopAppUseCase,
) {

    suspend operator fun invoke(
        packages: List<String>,
        action: AppAction,
    ): Outcome<BatchActionResult> {
        if (packages.isEmpty()) {
            return Outcome.Success(BatchActionResult(0, emptyList(), emptyMap()))
        }
        // Reject batchable-only-via-UI actions upfront.
        if (action !is AppAction.ForceStop) {
            return Outcome.Failure(
                AppError.Validation(
                    "Action ${action::class.simpleName} requires per-package OS confirmation; " +
                        "iterate in the UI and launch the Intent per package instead.",
                ),
            )
        }

        // v0.2.1 audit C6a fix — defence-in-depth: filter invalid package
        // names BEFORE the per-app loop. The downstream ForceStopAppUseCase
        // already validates each pkg, but pre-filtering keeps the batch
        // result clean (invalid pkgs are pre-rejected with a clear reason
        // rather than buried in a per-app failure).
        val validPackages = packages.filter { it.isValidPackageName() }
        val preRejected = packages - validPackages.toSet()
        if (preRejected.isNotEmpty()) {
            Timber.w("BatchActionUseCase: %d invalid package names rejected", preRejected.size)
        }

        val succeeded = mutableListOf<String>()
        val failed = mutableMapOf<String, String>()
        for (pkg in preRejected) {
            failed[pkg] = "Invalid package name"
        }
        for (pkg in validPackages) {
            when (val outcome = forceStop(pkg)) {
                is Outcome.Success -> succeeded.add(pkg)
                is Outcome.Failure -> failed[pkg] = outcome.error.toString()
                Outcome.Loading    -> failed[pkg] = "unexpected Loading from suspend UseCase"
            }
        }
        return Outcome.Success(
            BatchActionResult(
                total     = packages.size,
                succeeded = succeeded,
                failed    = failed,
            ),
        )
    }
}
