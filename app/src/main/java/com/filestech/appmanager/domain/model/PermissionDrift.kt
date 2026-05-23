package com.filestech.appmanager.domain.model

/**
 * A single observed change in the grant state of one (package, permission)
 * pair between two consecutive captures.
 *
 * Derived in [com.filestech.appmanager.domain.usecase.GetPermissionDriftsUseCase]
 * from raw [PermissionSnapshot] rows — never persisted directly.
 */
data class PermissionDrift(
    val packageName: String,
    /** Human-readable app label at the time of the drift. May be null if the app was uninstalled. */
    val appLabel: String?,
    val permission: String,
    val change: Change,
    /** Epoch ms of the snapshot that captured the change (the "after" side). */
    val whenMs: Long,
) {
    enum class Change {
        /** Permission was previously NOT granted and is now granted. */
        GAINED,

        /** Permission was previously granted and is now revoked. */
        LOST,
    }
}
