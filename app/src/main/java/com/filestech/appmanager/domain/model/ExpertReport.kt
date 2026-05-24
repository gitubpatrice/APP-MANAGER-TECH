package com.filestech.appmanager.domain.model

/**
 * Pure domain bundle returned by `GetExpertReportUseCase` — exposes the
 * low-level Android components for power users.
 *
 * Built ONLY from public PackageManager APIs — no root, no Shizuku, no ADB
 * shell required. Some fields surface a `null` / empty list when the data is
 * unavailable on the running Android version (see KDoc per field). The UI
 * is expected to render `null` as "—" or "non disponible".
 *
 * Keep this model FREE of `android.*` imports — repository layer is the only
 * place that touches PackageManager.
 */
data class ExpertReport(
    val identity: AppIdentity,
    val sdkInfo: SdkInfo,
    val nativeInfo: NativeInfo,
    val apkPaths: ApkPaths,
    val signature: SignatureInfo,
    val components: ComponentsInfo,
    val permissions: ExpertPermissions,
    val appOps: AppOpsSnapshot,
) {
    /** Identity bits — package name, label, version, install metadata, UID. */
    data class AppIdentity(
        val packageName: String,
        val label: String,
        val versionName: String,
        val versionCode: Long,
        val uid: Int,
        val installerPackage: String?,
        val firstInstallTime: Long,
        val lastUpdateTime: Long,
        val isSystemApp: Boolean,
        val isEnabled: Boolean,
    )

    /** SDK envelope. [minSdk] is `null` on Android < N (API 24) for the target app. */
    data class SdkInfo(
        val minSdk: Int?,
        val targetSdk: Int,
        val compileSdk: Int?,
    )

    /** Native libs / ABI. Both fields can be null when the app ships no native code. */
    data class NativeInfo(
        val primaryAbi: String?,
        val nativeLibraryDir: String?,
    )

    /**
     * APK paths on disk.
     *
     * - [base] = `ApplicationInfo.sourceDir`
     * - [splits] = `ApplicationInfo.splitSourceDirs` (App Bundle splits — empty if monolithic)
     * - [publicSourceDir] = readable variant exposed by Android for non-signature callers
     */
    data class ApkPaths(
        val base: String?,
        val splits: List<String>,
        val publicSourceDir: String?,
    )

    /** Code-signing fingerprint(s). Uppercase hex colon-separated. */
    data class SignatureInfo(
        val sha256: String?,
        /** > 1 = multi-signer (rare). */
        val signerCount: Int,
        /** True if signed with the Android AOSP debug keystore (development build). */
        val isDebugSigned: Boolean,
    )

    /**
     * Declared components from the manifest.
     *
     * Each list is sorted alphabetically by class name. `exported = true` on an
     * activity/service/receiver/provider declares an attack surface from other
     * apps; the UI highlights exported components without a permission gate.
     */
    data class ComponentsInfo(
        val activities: List<ComponentEntry>,
        val services: List<ComponentEntry>,
        val receivers: List<ComponentEntry>,
        val providers: List<ProviderEntry>,
    ) {
        val totalCount: Int
            get() = activities.size + services.size + receivers.size + providers.size

        val exportedCount: Int
            get() = activities.count { it.exported } +
                services.count { it.exported } +
                receivers.count { it.exported } +
                providers.count { it.exported }
    }

    /** Generic activity / service / receiver entry. */
    data class ComponentEntry(
        val className: String,
        val exported: Boolean,
        val enabled: Boolean,
        /** May be null when the component declares no permission gate. */
        val permission: String?,
    )

    /** Content provider entry — adds the declared authority. */
    data class ProviderEntry(
        val className: String,
        val authority: String?,
        val exported: Boolean,
        val enabled: Boolean,
        val readPermission: String?,
        val writePermission: String?,
        val grantUriPermissions: Boolean,
    )

    /** Declared vs granted permission split. */
    data class ExpertPermissions(
        val declared: List<DeclaredPermission>,
        val grantedCount: Int,
        val declaredCount: Int,
    )

    data class DeclaredPermission(
        val name: String,
        val granted: Boolean,
        /** True if the permission is in the OS dangerous list (runtime prompt). */
        val isDangerous: Boolean,
    )

    /**
     * App-ops snapshot — best-effort. Non-root apps can only query a small set
     * via `AppOpsManager.unsafeCheckOpNoThrow` for ops the OS exposes to the
     * caller. We probe a curated list of "interesting for an inspector" ops and
     * surface the result with a clear `available` flag.
     */
    data class AppOpsSnapshot(
        val entries: List<AppOpEntry>,
        /** True when the OS surfaced at least one app-op state to the caller. */
        val isFullyAccessible: Boolean,
    )

    /**
     * @param op constant name (e.g. `OPSTR_FINE_LOCATION`).
     * @param mode value from `AppOpsManager.MODE_*` (-1 = not surfaced to caller).
     * @param modeLabel pre-formatted label for display (`Allowed`, `Ignored`, `Denied`, `Default`, `—`).
     */
    data class AppOpEntry(
        val op: String,
        val mode: Int,
        val modeLabel: String,
    )
}
