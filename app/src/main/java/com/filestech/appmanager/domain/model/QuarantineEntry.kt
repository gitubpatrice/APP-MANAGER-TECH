package com.filestech.appmanager.domain.model

/**
 * Domain mirror of [com.filestech.appmanager.data.local.db.entity.QuarantineEntryEntity].
 *
 * Pure Kotlin — referenced by use cases / ViewModels without dragging Room.
 */
data class QuarantineEntry(
    val packageName: String,
    val label: String,
    val mode: QuarantineMode,
    val quarantinedAt: Long,
    val restoreAt: Long,
    val versionName: String?,
    val versionCode: Long,
    /** SAF document URI string — null for [QuarantineMode.SOFT_REMINDER]. */
    val apkBackupUri: String?,
    val autoRestoreEnabled: Boolean,
    val notified: Boolean,
    /**
     * v0.5.1 — SHA-256 of the backup APK when it was written; null for SOFT and pre-0.5.1 entries. No
     * default on purpose: an entry rebuilt field by field must state it, or its backup would turn
     * unverifiable — never restorable — without a compile error.
     */
    val apkSha256: String?,
) {
    fun isExpired(nowMs: Long = System.currentTimeMillis()): Boolean = nowMs >= restoreAt
}
