package com.filestech.appmanager.domain.usecase

import android.content.Context
import android.content.pm.PackageManager
import com.filestech.appmanager.core.ext.isValidPackageName
import com.filestech.appmanager.core.result.AppError
import com.filestech.appmanager.core.result.Outcome
import com.filestech.appmanager.core.result.runCatchingOutcome
import com.filestech.appmanager.data.system.TrackerDatabase
import com.filestech.appmanager.di.IoDispatcher
import com.filestech.appmanager.domain.model.Tracker
import com.filestech.appmanager.domain.model.TrackerReport
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Scans an installed app for known tracker SDKs by matching its declared
 * Android components (Activities, Services, BroadcastReceivers, ContentProviders)
 * against the curated [TrackerDatabase].
 *
 * **App Manager Tech innovation**: 100% local. No upload, no network, no
 * external service. The asset `trackers.json` ships a snapshot of the
 * Exodus Privacy curated list. Detection is a longest-prefix match on FQCN.
 *
 * Limitations (be honest with users):
 * - A library compiled into the APK WITHOUT declaring any Android component
 *   (e.g. a pure-Java JAR doing pure HTTP) is invisible to this method.
 *   Approach 2 (dex class enumeration) would catch those but is 100× slower.
 * - False positives are possible if an app declares a class in the same
 *   package as a tracker without actually using it (very rare).
 * - The asset is a SNAPSHOT — recently-added trackers won't be detected
 *   until the next app release refreshes the asset.
 *
 * Performance: ~1-5 ms per app on a mid-range device (200 components × ~50
 * signatures ≈ 10k startsWith comparisons, all in-memory).
 */
class DetectTrackersUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
    private val database: TrackerDatabase,
) {

    suspend operator fun invoke(packageName: String): Outcome<TrackerReport> {
        if (!packageName.isValidPackageName()) {
            return Outcome.Failure(AppError.Validation("Invalid package name"))
        }
        return runCatchingOutcome(mapError = { AppError.Unknown(it) }) {
            withContext(io) {
                val pm = context.packageManager
                @Suppress("DEPRECATION")
                val pkg = pm.getPackageInfo(
                    packageName,
                    PackageManager.GET_ACTIVITIES or
                        PackageManager.GET_SERVICES or
                        PackageManager.GET_RECEIVERS or
                        PackageManager.GET_PROVIDERS,
                )

                val componentNames = buildList {
                    pkg.activities?.mapNotNullTo(this) { it.name }
                    pkg.services?.mapNotNullTo(this) { it.name }
                    pkg.receivers?.mapNotNullTo(this) { it.name }
                    pkg.providers?.mapNotNullTo(this) { it.name }
                }

                val detected = matchComponents(componentNames, database.signatureIndex)
                TrackerReport(
                    packageName       = packageName,
                    detectedTrackers  = detected,
                    componentCount    = componentNames.size,
                )
            }
        }
    }

    /**
     * For each component FQCN, check whether it `startsWith` any tracker
     * signature. Collect distinct trackers (a single tracker matching N
     * components counts once).
     */
    private fun matchComponents(
        components: List<String>,
        signatures: List<Pair<String, Tracker>>,
    ): List<Tracker> {
        if (components.isEmpty() || signatures.isEmpty()) return emptyList()
        val matched = linkedSetOf<Tracker>()
        for (component in components) {
            for ((sig, tracker) in signatures) {
                if (component.startsWith(sig)) {
                    matched += tracker
                    break // one match per component is enough
                }
            }
        }
        return matched.sortedBy { it.name }
    }
}
