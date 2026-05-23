package com.filestech.appmanager.domain.model

/**
 * User-facing filter applied on top of [AppInfoRepository.observeApps].
 *
 * Pure data class — easy to persist (via DataStore JSON later), easy to test,
 * trivial to combine with [kotlinx.coroutines.flow.combine] in ViewModels.
 *
 * Semantics:
 * - `includeSystemApps = false && includeUserApps = false` → returns nothing.
 * - `categories.isEmpty()` → no category filter (all categories pass).
 * - `requiredPermissions.isEmpty()` → no permission filter.
 * - `unusedSinceDays == null` → no last-used filter.
 * - `installerFilter == ANY` → no provenance filter.
 *
 * Compose-friendly: every field has a sane default so the UI can build
 * partial filters via `currentFilter.copy(installerFilter = F_DROID)`.
 */
data class FilterOptions(
    val includeSystemApps: Boolean = false,
    val includeUserApps: Boolean = true,
    val includeDisabled: Boolean = true,
    val installerFilter: InstallerFilter = InstallerFilter.ANY,
    val categories: Set<AppCategory> = emptySet(),
    /** Apps with `totalSizeBytes` below this threshold are excluded. */
    val minSizeBytes: Long = 0L,
    /** Apps with `totalSizeBytes` above this threshold are excluded. `Long.MAX_VALUE` = no cap. */
    val maxSizeBytes: Long = Long.MAX_VALUE,
    /**
     * Keep only apps with `lastUsedTime` older than now minus this many days.
     * `null` disables this filter. Useful for "rarely used" surfaces.
     */
    val unusedSinceDays: Int? = null,
    /**
     * Keep only apps that declare ALL of these permissions in their manifest.
     * Empty set = no permission filter.
     *
     * NOTE: this is an O(N × M) post-filter — each app needs a live
     * PackageManager probe. Use sparingly.
     */
    val requiredPermissions: Set<String> = emptySet(),
) {

    companion object {
        val DEFAULT = FilterOptions()
    }
}

/**
 * Filter apps by install source (Play Store / F-Droid / sideload / other).
 *
 * Detection rule (applied in the data layer):
 * - `PLAY_STORE`   : installerPackage == "com.android.vending"
 * - `F_DROID`      : installerPackage ∈ { "org.fdroid.fdroid", "org.fdroid.fdroid.privileged" }
 * - `AURORA`       : installerPackage ∈ { "com.aurora.store", "com.aurora.services" }
 * - `SIDELOAD`     : installerPackage == null
 * - `OTHER`        : everything else (Samsung Galaxy Store, Huawei AppGallery, MIUI, etc.)
 * - `ANY`          : no filter
 */
enum class InstallerFilter {
    ANY,
    PLAY_STORE,
    F_DROID,
    AURORA,
    SIDELOAD,
    OTHER,
}
