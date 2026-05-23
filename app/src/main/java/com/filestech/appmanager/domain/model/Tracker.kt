package com.filestech.appmanager.domain.model

/**
 * One known tracker / ad / analytics SDK signature, sourced from the
 * Exodus Privacy curated list snapshot embedded as an asset.
 *
 * **App Manager Tech innovation**: Exodus Privacy normally requires
 * uploading the APK to their server for analysis. We do the matching
 * **100% locally** by scanning declared Android components (Activities,
 * Services, Receivers, Providers) of every installed app against the
 * known-tracker signatures. Zero network, zero data leak, F-Droid friendly.
 *
 * @property id stable identifier for the tracker (lowercase slug)
 * @property name human-readable name (e.g. "Google AdMob")
 * @property category Ads / Analytics / Crash reporter / Attribution / Identification / Profiling / Push / Session replay
 * @property signatures FQCN prefixes (e.g. "com.google.android.gms.ads.").
 *                     A match on ANY signature counts as a detection.
 */
data class Tracker(
    val id: String,
    val name: String,
    val category: String,
    val signatures: List<String>,
)

/**
 * Outcome of a tracker scan against one installed app.
 *
 * @property packageName the app scanned
 * @property detectedTrackers trackers whose signature matched at least one
 *                            declared component or permission of the app
 * @property componentCount total Android components (activities + services
 *                          + receivers + providers) considered
 */
data class TrackerReport(
    val packageName: String,
    val detectedTrackers: List<Tracker>,
    val componentCount: Int,
) {
    /** Convenience: distinct categories present (for the UI badge bar). */
    val categories: Set<String>
        get() = detectedTrackers.map { it.category }.toSet()

    /** True if no tracker was detected — the app gets a "clean" badge. */
    val isClean: Boolean
        get() = detectedTrackers.isEmpty()
}
