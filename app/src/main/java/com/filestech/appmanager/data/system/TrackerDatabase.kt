package com.filestech.appmanager.data.system

import android.content.Context
import com.filestech.appmanager.domain.model.Tracker
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loads and exposes the curated tracker signature database (asset
 * `trackers.json`).
 *
 * Lazy-init at first call to [signatures] (i.e. when the user opens the
 * Trackers screen or AppDetail) — keeps startup latency at zero for users
 * who never look at this feature.
 *
 * Why a local asset and not a remote API: app is F-Droid only, no INTERNET
 * permission. The asset ships a snapshot of the Exodus Privacy curated list
 * — refreshed by the developer at each release, not at runtime.
 *
 * Lookup is structured as `signatures: Map<String, Tracker>` where keys are
 * the FQCN prefixes. Matching is a longest-prefix scan over the keys
 * (O(N×M) on N components × M signatures, but both stay < 1000 so this is
 * a few milliseconds per app).
 */
@Singleton
class TrackerDatabase @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /** Parsed once, kept in memory for the process lifetime. */
    val all: List<Tracker> by lazy { load() }

    /** Pre-indexed by signature prefix for O(1) prefix-startsWith scan. */
    val signatureIndex: List<Pair<String, Tracker>> by lazy {
        all.flatMap { tracker -> tracker.signatures.map { sig -> sig to tracker } }
    }

    private fun load(): List<Tracker> = try {
        val text = context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
        val array = JSONArray(text)
        buildList(array.length()) {
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val sigs = obj.getJSONArray("signatures")
                add(
                    Tracker(
                        id         = obj.getString("id"),
                        name       = obj.getString("name"),
                        category   = obj.getString("category"),
                        signatures = List(sigs.length()) { sigs.getString(it) },
                    ),
                )
            }
        }
    } catch (e: Exception) {
        Timber.e(e, "Failed to load tracker asset")
        emptyList()
    }

    private companion object {
        const val ASSET_NAME = "trackers.json"
    }
}
