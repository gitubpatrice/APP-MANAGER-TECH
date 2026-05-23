package com.filestech.appmanager.domain.model

/**
 * Actions that can be applied to one or many apps.
 *
 * Used by `BatchActionUseCase` to iterate over a selection and apply a single
 * operation. Each variant corresponds to exactly one UseCase invocation per
 * package, so callers do not need to know which UseCase to dispatch on.
 *
 * Why a sealed interface (not enum): variants may carry payload (e.g.
 * [SetEnabled] carries the new state). Enums can't.
 */
sealed interface AppAction {

    /** Triggers the system uninstall confirmation dialog. */
    data object Uninstall : AppAction

    /** Opens the OS Settings → App info screen so the user can clear the cache. */
    data object ClearCache : AppAction

    /** Stops background processes of the app via `ActivityManager.killBackgroundProcesses`. */
    data object ForceStop : AppAction

    /** Toggles the enabled state. Requires privileged permission on non-root devices. */
    data class SetEnabled(val enabled: Boolean) : AppAction
}

/**
 * Result of a [BatchActionUseCase] run.
 *
 * Indexed by packageName so the UI can render a per-row status. Counts are
 * derived for convenience.
 */
data class BatchActionResult(
    val total: Int,
    val succeeded: List<String>,
    val failed: Map<String, String>,
) {
    val successCount: Int get() = succeeded.size
    val failureCount: Int get() = failed.size
    val allSucceeded: Boolean get() = total > 0 && failureCount == 0
}
