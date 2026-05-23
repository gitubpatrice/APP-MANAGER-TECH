package com.filestech.appmanager.domain.model

/**
 * Domain-layer mirror of the persisted permission-snapshot row.
 *
 * Pure Kotlin — no androidx.room import — so use cases and ViewModels can
 * reference it without dragging the data layer in.
 */
data class PermissionSnapshot(
    val packageName: String,
    /** Android permission constant, e.g. `android.permission.CAMERA`. */
    val permission: String,
    val granted: Boolean,
    val capturedAt: Long,
)
