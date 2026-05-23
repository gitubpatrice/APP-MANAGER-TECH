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
 * - −5 per dangerous permission requested
 * - −10 if INTERNET present
 * - −10 if FOREGROUND_SERVICE present
 * - −15 if SYSTEM_ALERT_WINDOW present
 * - −20 if BIND_DEVICE_ADMIN active
 * - −20 if BIND_ACCESSIBILITY_SERVICE active
 * - −10 if installer is "sideload" (unknown source, but legitimate use case → mild penalty)
 * - −15 if installer source is "other" (unknown store)
 * - +5 if installer is F-Droid (vetted source bonus)
 *
 * Clamped to [0, 100]. See [PrivacyTier] for human-friendly buckets.
 */
data class PrivacyScore(
    val packageName: String,
    val value: Int,
    val tier: PrivacyTier,
    /** Human-readable reasons that lowered the score (used in AppDetail tooltip). */
    val deductions: List<String>,
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
