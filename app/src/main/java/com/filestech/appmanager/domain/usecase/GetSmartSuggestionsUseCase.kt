package com.filestech.appmanager.domain.usecase

import com.filestech.appmanager.core.ext.MS_PER_DAY
import com.filestech.appmanager.core.result.AppError
import com.filestech.appmanager.core.result.Outcome
import com.filestech.appmanager.core.result.runCatchingOutcome
import com.filestech.appmanager.domain.model.AppInfo
import com.filestech.appmanager.domain.model.SmartCleanerReport
import com.filestech.appmanager.domain.model.SmartSuggestion
import com.filestech.appmanager.domain.repository.AppInfoRepository
import com.filestech.appmanager.domain.repository.IgnoreListRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import kotlin.math.max

/**
 * Aggregates the device's clean-up opportunities into one prioritised list.
 *
 * **App Manager Tech innovation**: rather than offering a dozen scattered
 * lists ("zombies here, cache hogs there, duplicates somewhere else"), this
 * single use case fuses every signal we already have into one ranked report
 * with concrete one-tap actions and an estimated total reclaimable size.
 *
 * Signals combined:
 * - Zombie (never opened or unused since X days) → recommend UNINSTALL
 * - Cache hog (cacheSizeBytes >= [cacheHogThresholdBytes]) → recommend CLEAR_CACHE
 * - Oversized + rarely used (install + data + cache > 100 MB and unused > 30 days)
 *   → recommend REVIEW_AND_DECIDE
 * - Duplicate category — apps in the same `AppCategory` with overlapping role
 *   (e.g. 3 video players) → recommend REVIEW_AND_DECIDE
 *
 * Apps in the user's [IgnoreListRepository] are excluded — the user has
 * explicitly opted them out of cleanup suggestions.
 */
class GetSmartSuggestionsUseCase @Inject constructor(
    private val repository: AppInfoRepository,
    private val ignoreList: IgnoreListRepository,
) {

    suspend operator fun invoke(
        unusedThresholdDays: Int = 30,
        neverOpenedGraceDays: Int = 7,
        cacheHogThresholdBytes: Long = 100L * 1024 * 1024, // 100 MB cache
        oversizedThresholdBytes: Long = 100L * 1024 * 1024, // 100 MB install + data + cache
        includeSystemApps: Boolean = false,
    ): Outcome<SmartCleanerReport> = runCatchingOutcome(mapError = { AppError.Unknown(it) }) {
        val now = System.currentTimeMillis()
        val unusedCutoff = now - unusedThresholdDays * MS_PER_DAY
        val neverGraceCutoff = now - neverOpenedGraceDays * MS_PER_DAY

        val ignored = ignoreList.observe().first()

        val first = repository.observeApps(includeSystemApps = includeSystemApps)
            .first { it !is Outcome.Loading }
        val apps = when (first) {
            is Outcome.Success -> first.value
            is Outcome.Failure -> throw IllegalStateException(first.error.toString())
            Outcome.Loading    -> emptyList()
        }
            .filter { it.isUninstallable && it.packageName !in ignored }

        val suggestions = mutableListOf<SmartSuggestion>()
        val seenPackages = mutableSetOf<String>() // dedupe — pick most impactful category per app

        // Pass 1: zombies (oldest unused first)
        apps.forEach { app ->
            val cat = classifyZombie(app, unusedCutoff, neverGraceCutoff) ?: return@forEach
            val reclaim = app.totalSizeBytes // full uninstall reclaims everything
            val days = ((now - max(app.lastUsedTime, app.firstInstallTime)) / MS_PER_DAY).toInt()
            suggestions += SmartSuggestion(
                appInfo            = app,
                category           = cat,
                reclaimableBytes   = reclaim,
                recommendedAction  = SmartSuggestion.RecommendedAction.UNINSTALL,
                reasonShort        = if (cat == SmartSuggestion.Category.ZOMBIE_NEVER_OPENED) {
                    "Never opened ($days days installed)"
                } else {
                    "Unused for $days days"
                },
            )
            seenPackages += app.packageName
        }

        // Pass 2: cache hogs (apps NOT already flagged as zombie)
        apps.filter { it.packageName !in seenPackages && it.cacheSizeBytes >= cacheHogThresholdBytes }
            .forEach { app ->
                suggestions += SmartSuggestion(
                    appInfo            = app,
                    category           = SmartSuggestion.Category.CACHE_HOG,
                    reclaimableBytes   = app.cacheSizeBytes,
                    recommendedAction  = SmartSuggestion.RecommendedAction.CLEAR_CACHE,
                    reasonShort        = "Large cache",
                )
                seenPackages += app.packageName
            }

        // Pass 3: oversized + rarely used (not zombie, not cache hog already flagged)
        apps.filter {
            it.packageName !in seenPackages &&
                it.totalSizeBytes >= oversizedThresholdBytes &&
                it.lastUsedTime in 1..unusedCutoff
        }.forEach { app ->
            suggestions += SmartSuggestion(
                appInfo            = app,
                category           = SmartSuggestion.Category.OVERSIZED_RARELY_USED,
                reclaimableBytes   = app.totalSizeBytes,
                recommendedAction  = SmartSuggestion.RecommendedAction.REVIEW_AND_DECIDE,
                reasonShort        = "Large + rarely used",
            )
            seenPackages += app.packageName
        }

        // Pass 4: duplicate category — apps grouped by AppCategory where count > 1
        apps.filter { it.packageName !in seenPackages }
            .groupBy { it.category }
            .filterValues { it.size > 1 }
            .forEach { (_, group) ->
                // Keep all except the most recently used (likely the one the user prefers).
                val mostRecent = group.maxByOrNull { it.lastUsedTime }
                group.filter { it != mostRecent }.forEach { app ->
                    suggestions += SmartSuggestion(
                        appInfo            = app,
                        category           = SmartSuggestion.Category.DUPLICATE_CATEGORY,
                        reclaimableBytes   = app.totalSizeBytes,
                        recommendedAction  = SmartSuggestion.RecommendedAction.REVIEW_AND_DECIDE,
                        reasonShort        = "Duplicate ${app.category.name.lowercase()}",
                    )
                }
            }

        val sorted = suggestions.sortedByDescending { it.reclaimableBytes }
        SmartCleanerReport(
            suggestions          = sorted,
            totalReclaimableBytes = sorted.sumOf { it.reclaimableBytes },
            analyzedAppCount     = apps.size,
        )
    }

    private fun classifyZombie(
        app: AppInfo,
        unusedCutoff: Long,
        neverGraceCutoff: Long,
    ): SmartSuggestion.Category? = when {
        app.lastUsedTime == 0L && app.firstInstallTime <= neverGraceCutoff ->
            SmartSuggestion.Category.ZOMBIE_NEVER_OPENED
        app.lastUsedTime in 1..unusedCutoff ->
            SmartSuggestion.Category.ZOMBIE_UNUSED_LONG
        else -> null
    }
}
