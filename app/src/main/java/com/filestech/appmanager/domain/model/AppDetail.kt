package com.filestech.appmanager.domain.model

/**
 * Aggregate detail bundle returned by `GetAppDetailUseCase`.
 *
 * Combines the cached [AppInfo] with live-from-PackageManager permission data.
 * Suitable for binding to a single "App detail" screen without making the UI
 * orchestrate multiple suspend calls itself.
 */
data class AppDetail(
    val info: AppInfo,
    /** All permissions declared in the app's manifest, granted or not. */
    val requestedPermissions: List<String>,
    /** Subset of [requestedPermissions] actually granted at the OS level. */
    val grantedPermissions: List<String>,
) {
    /** Permissions declared but not granted (revoked / never granted). */
    val notGrantedPermissions: List<String>
        get() = requestedPermissions - grantedPermissions.toSet()
}
