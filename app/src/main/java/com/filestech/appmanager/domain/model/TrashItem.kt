package com.filestech.appmanager.domain.model

/**
 * Domain representation of one entry in the Corbeille / Trash staging area.
 *
 * Snapshots `label` and `totalSizeBytes` at insert time so the UI can list
 * trashed apps even if PackageManager no longer knows about them (already
 * uninstalled out-of-band, or scanned away on a different device profile).
 */
data class TrashItem(
    val packageName: String,
    val label: String,
    val totalSizeBytes: Long,
    val addedAt: Long,
)
