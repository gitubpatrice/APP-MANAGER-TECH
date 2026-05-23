package com.filestech.appmanager.domain.model

/**
 * Aggregated view of the permission-snapshot history. Used by the Drift
 * screen's "Surveillance active" header card.
 *
 * `lastCapturedAt = null` and zero counts → no capture has ever been
 * performed; the header card is hidden in that state and the empty-state
 * onboarding takes over.
 */
data class SnapshotStats(
    val distinctPackages: Int,
    val distinctPermissions: Int,
    val lastCapturedAt: Long?,
) {
    val isEmpty: Boolean get() = lastCapturedAt == null

    companion object {
        val EMPTY = SnapshotStats(0, 0, null)
    }
}
