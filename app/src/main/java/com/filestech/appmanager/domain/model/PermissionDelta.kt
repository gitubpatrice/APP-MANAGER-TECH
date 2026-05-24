package com.filestech.appmanager.domain.model

/**
 * v0.3.2 — Permission gain observed between a REPLACED lifecycle event and
 * the closest preceding baseline (INSTALLED / BASELINE / earlier REPLACED).
 *
 * Computed by `DetectPermissionDeltaUseCase` — never persisted. The UI uses
 * this to surface "this update added Camera + Microphone" as a hint on the
 * Lifecycle History row.
 *
 * Note: we only carry [gained], not [lost]. Losing a dangerous permission on
 * an update is uncommon and uninteresting from a privacy / safety standpoint
 * (the OS strips revoked perms but the user has to grant them again anyway);
 * the gain side is the actionable signal.
 */
data class PermissionDelta(
    val packageName: String,
    /** Identifies the REPLACED [LifecycleEvent] this delta was computed for. */
    val eventId: Long,
    /** Full permission constants (e.g. `android.permission.CAMERA`). Sorted. */
    val gained: List<String>,
) {
    val hasChanges: Boolean get() = gained.isNotEmpty()
}
