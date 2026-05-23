package com.filestech.appmanager.domain.usecase

import com.filestech.appmanager.core.result.AppError
import com.filestech.appmanager.core.result.Outcome
import com.filestech.appmanager.core.result.runCatchingOutcome
import com.filestech.appmanager.di.IoDispatcher
import com.filestech.appmanager.domain.model.AppInfo
import com.filestech.appmanager.domain.model.TrackerReport
import com.filestech.appmanager.domain.repository.AppInfoRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Bulk-scans every installed user app for tracker SDKs.
 *
 * Parallelism: each per-app scan is a few-ms PackageManager probe; we fan
 * them out via `async` with the IO dispatcher pool. On a typical phone with
 * ~150 user apps the scan finishes in 1-2 seconds.
 *
 * Result is the full list of [TrackerReport]s, sorted by tracker count
 * descending (the most contaminated apps surface first).
 */
class ScanAllTrackersUseCase @Inject constructor(
    private val repository: AppInfoRepository,
    private val detectTrackers: DetectTrackersUseCase,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    suspend operator fun invoke(
        includeSystemApps: Boolean = false,
    ): Outcome<Result> = runCatchingOutcome(mapError = { AppError.Unknown(it) }) {
        val source = repository.observeApps(includeSystemApps)
        val first = source.first { it !is Outcome.Loading }
        val apps = when (first) {
            is Outcome.Success -> first.value
            is Outcome.Failure -> throw IllegalStateException(first.error.toString())
            Outcome.Loading    -> emptyList()
        }

        // Fan out across IO dispatcher.
        val reports: List<TrackerReport> = withContext(io) {
            coroutineScope {
                apps.map { app ->
                    async {
                        when (val r = detectTrackers(app.packageName)) {
                            is Outcome.Success -> r.value
                            else               -> TrackerReport(app.packageName, emptyList(), 0)
                        }
                    }
                }.awaitAll()
            }
        }

        val byPackage = reports.associateBy { it.packageName }
        val appsByPackage = apps.associateBy { it.packageName }

        // Aggregate stats for the Trackers screen header.
        val withTrackers = reports.count { !it.isClean }
        val totalDetections = reports.sumOf { it.detectedTrackers.size }
        val byCategory = reports.flatMap { it.detectedTrackers }
            .groupingBy { it.category }
            .eachCount()
            .toList()
            .sortedByDescending { it.second }

        Result(
            reports          = reports.sortedByDescending { it.detectedTrackers.size },
            byPackage        = byPackage,
            apps             = appsByPackage,
            totalAppCount    = apps.size,
            appsWithTrackers = withTrackers,
            totalDetections  = totalDetections,
            categoryHistogram = byCategory,
        )
    }

    data class Result(
        val reports: List<TrackerReport>,
        val byPackage: Map<String, TrackerReport>,
        val apps: Map<String, AppInfo>,
        val totalAppCount: Int,
        val appsWithTrackers: Int,
        val totalDetections: Int,
        /** Pairs of (category name, occurrence count) sorted desc. */
        val categoryHistogram: List<Pair<String, Int>>,
    ) {
        /** % of scanned apps that have at least one tracker. */
        val contaminationPercent: Int
            get() = if (totalAppCount == 0) 0 else (appsWithTrackers * 100 / totalAppCount)
    }
}
