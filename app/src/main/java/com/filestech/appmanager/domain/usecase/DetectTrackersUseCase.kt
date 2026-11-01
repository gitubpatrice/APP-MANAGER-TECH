package com.filestech.appmanager.domain.usecase

import android.content.Context
import android.content.pm.ComponentInfo
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
 * Scans an installed app for known tracker SDKs by matching the classes its manifest names
 * against the curated [TrackerDatabase]:
 * - its declared Android components (Activities, Services, BroadcastReceivers, ContentProviders),
 *   disabled ones included — an SDK whose receiver waits for consent is still in the APK;
 * - the classes those components register in their meta-data: Firebase registrars
 *   (`com.google.firebase.components:<class>`, the only trace Crashlytics leaves in a manifest) and
 *   AndroidX Startup initializers (`<class>` = `androidx.startup`).
 *
 * **App Manager Tech innovation**: 100% local. No upload, no network, no external service. The
 * asset `trackers.json` is a list curated for this method, inspired by Exodus Privacy's public
 * tracker database (Exodus reads the classes of the dex; this reads the manifest only).
 *
 * Limitations (be honest with users):
 * - An SDK that leaves no class name in the manifest (AppsFlyer, Branch, Amplitude, Segment… in
 *   their recent versions) is invisible to this method. Reading the dex would catch those, at a
 *   far higher cost.
 * - The asset is a SNAPSHOT shipped with the app — trackers added later are not detected until a
 *   release updates it.
 *
 * Performance: ~1-5 ms per app on a mid-range device (a few hundred names × ~80 signatures, all
 * in-memory `startsWith`).
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
                        PackageManager.GET_PROVIDERS or
                        PackageManager.GET_META_DATA or
                        PackageManager.MATCH_DISABLED_COMPONENTS or
                        PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS,
                )

                val components: List<ComponentInfo> = buildList {
                    pkg.activities?.let { addAll(it) }
                    pkg.services?.let { addAll(it) }
                    pkg.receivers?.let { addAll(it) }
                    pkg.providers?.let { addAll(it) }
                }
                val declared = components.mapNotNull { component ->
                    val name = component.name ?: return@mapNotNull null
                    val meta = component.metaData
                    // get() then a safe cast: getString() logs a warning for every non-string value.
                    @Suppress("DEPRECATION")
                    val values = meta?.keySet()?.associateWith { meta.get(it) as? String }.orEmpty()
                    DeclaredComponent(name = name, metaData = values)
                }

                TrackerReport(
                    packageName       = packageName,
                    detectedTrackers  = matchTrackers(trackerCandidateNames(declared), database.signatureIndex),
                    componentCount    = components.size,
                )
            }
        }
    }
}

/** A manifest component as the matcher sees it: its class name and its meta-data (name → string value). */
internal data class DeclaredComponent(val name: String, val metaData: Map<String, String?>)

private const val FIREBASE_REGISTRAR_PREFIX = "com.google.firebase.components:"
private const val ANDROIDX_STARTUP = "androidx.startup"

/**
 * The class names a tracker signature is matched against: each component's own name, plus the classes
 * it registers in meta-data — a Firebase registrar (key `com.google.firebase.components:<class>`) or an
 * AndroidX Startup initializer (key `<class>`, value `androidx.startup`). Any other meta-data (an API key,
 * a flag) is not a class and is left out.
 */
internal fun trackerCandidateNames(components: List<DeclaredComponent>): List<String> = buildList {
    for (component in components) {
        add(component.name)
        for ((key, value) in component.metaData) {
            when {
                key.startsWith(FIREBASE_REGISTRAR_PREFIX) -> add(key.removePrefix(FIREBASE_REGISTRAR_PREFIX))
                value == ANDROIDX_STARTUP                 -> add(key)
            }
        }
    }
}

/**
 * Distinct trackers whose signature matches one of [names], sorted by name. A signature ending in `.` is
 * a package prefix; any other is one class, matched whole (or one of its nested classes, `Outer$Inner`),
 * so `com.facebook.internal.FacebookInitProvider` does not catch `…FacebookInitProviderProxy`. The first
 * matching signature wins for a name: no signature of the list is a prefix of another tracker's.
 */
internal fun matchTrackers(names: List<String>, signatures: List<Pair<String, Tracker>>): List<Tracker> {
    if (names.isEmpty() || signatures.isEmpty()) return emptyList()
    val matched = linkedSetOf<Tracker>()
    for (name in names) {
        for ((sig, tracker) in signatures) {
            if (signatureMatches(sig, name)) {
                matched += tracker
                break
            }
        }
    }
    return matched.sortedBy { it.name }
}

private fun signatureMatches(signature: String, name: String): Boolean = when {
    !name.startsWith(signature) -> false
    signature.endsWith('.')     -> true
    else                        -> name.length == signature.length || name[signature.length] == '$'
}
