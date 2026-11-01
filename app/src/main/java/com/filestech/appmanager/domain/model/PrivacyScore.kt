package com.filestech.appmanager.domain.model

/**
 * Static privacy assessment of an installed app, computed from its
 * declared permissions, install source, and signing certificate state.
 *
 * Range: 0 (very intrusive) to 100 (minimal footprint).
 *
 * **Novel feature**: most app managers list permissions as a flat array
 * and let the user judge. PrivacyScore collapses the signal into a single
 * number per app, making it possible to sort the catalogue by privacy
 * footprint and surface the most intrusive apps at a glance.
 *
 * The score is **purely heuristic** — it cannot detect malicious intent;
 * it only quantifies surface area. A 30/100 doesn't mean "malware",
 * it means "has a lot of access". A 90/100 doesn't mean "safe",
 * it means "asks for very little".
 *
 * Heuristic (Phase IX innovation):
 * - Start from 100
 * - −5 per [PermissionCapability] group the requested dangerous permissions give (precise and
 *   approximate location are one group)
 * - −5 if MANAGE_EXTERNAL_STORAGE present (access to all files, a special access, not a dangerous one)
 * - −10 if INTERNET present
 * - −10 if FOREGROUND_SERVICE present
 * - −15 if SYSTEM_ALERT_WINDOW present
 * - −20 if the app declares a device-admin receiver (guarded by BIND_DEVICE_ADMIN)
 * - −20 if the app declares an accessibility service (guarded by BIND_ACCESSIBILITY_SERVICE)
 * - −10 if installed outside any store (a package installer, adb, or no installer on a user app)
 * - −15 if the installer is not a known store
 * - +5 if installed by an F-Droid client
 *
 * Clamped to [0, 100]. See [PrivacyTier] for human-friendly buckets.
 */
data class PrivacyScore(
    val packageName: String,
    val value: Int,
    val tier: PrivacyTier,
    /** Human-readable reasons that lowered the score (used in AppDetail tooltip). */
    val deductions: List<String>,
    /** The app declares an accessibility service: it can read the screen and act for the user once enabled. */
    val declaresAccessibilityService: Boolean = false,
    /** The app declares a device-admin receiver: once activated, it can block its own uninstall. */
    val declaresDeviceAdmin: Boolean = false,
) {
    companion object {
        const val MAX_SCORE = 100
        const val MIN_SCORE = 0
    }
}

/**
 * Coarse-grained tier for UI rendering — drives the badge colour:
 * - GREEN (≥ 80) → Excellent / Good — BrandBlue or Material green
 * - YELLOW (50-79) → Average — Material amber
 * - ORANGE (30-49) → Concerning — Material orange
 * - RED (< 30) → Heavy footprint — BrandDanger
 */
enum class PrivacyTier(val minScore: Int) {
    RED(0),
    ORANGE(30),
    YELLOW(50),
    GREEN(80);

    companion object {
        fun of(value: Int): PrivacyTier = entries.lastOrNull { value >= it.minScore } ?: RED
    }
}
