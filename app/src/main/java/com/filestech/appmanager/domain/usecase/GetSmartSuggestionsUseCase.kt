package com.filestech.appmanager.domain.usecase

import com.filestech.appmanager.core.ext.MS_PER_DAY
import com.filestech.appmanager.core.result.AppError
import com.filestech.appmanager.core.result.Outcome
import com.filestech.appmanager.core.result.runCatchingOutcome
import com.filestech.appmanager.data.system.ActiveRoleApps
import com.filestech.appmanager.data.system.CriticalAppDetector
import com.filestech.appmanager.di.IoDispatcher
import com.filestech.appmanager.domain.model.AppCategory
import com.filestech.appmanager.domain.model.AppInfo
import com.filestech.appmanager.domain.model.SmartCleanerReport
import com.filestech.appmanager.domain.model.SmartSuggestion
import com.filestech.appmanager.domain.repository.AppInfoRepository
import com.filestech.appmanager.domain.repository.IgnoreListRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Aggregates the device's clean-up opportunities into one prioritised list.
 *
 * **App Manager Tech innovation**: rather than offering a dozen scattered
 * lists ("zombies here, cache hogs there, duplicates somewhere else"), this
 * single use case fuses every signal we already have into one ranked report
 * with concrete one-tap actions and an estimated total reclaimable size.
 *
 * Signals combined:
 * - Zombie (never opened or unused since [unusedThresholdDays]) → recommend UNINSTALL
 * - Cache hog (cacheSizeBytes >= [cacheHogThresholdBytes]) → recommend CLEAR_CACHE
 * - Oversized + rarely used (install + data + cache ≥ 100 MB, not opened for half the zombie threshold)
 *   → recommend REVIEW_AND_DECIDE
 * - Duplicate category — apps in the same `AppCategory` with overlapping role
 *   (e.g. 3 video players) → recommend REVIEW_AND_DECIDE. Never for
 *   `UNDEFINED` or `OTHER`, which name no role. A guess, so its size is not
 *   added to the reclaimable total.
 *
 * Apps in the user's [IgnoreListRepository] are excluded — the user has
 * explicitly opted them out of cleanup suggestions. The usage-based signals (zombies, rarely used,
 * duplicates) also leave out the apps that work without being opened ([ActiveRoleApps]) and the protected
 * apps ([CriticalAppDetector]): an authenticator opened twice a year is not one to uninstall. Without usage
 * access every app reads "never used", so the report is empty rather than false; sizes need that access too.
 */
class GetSmartSuggestionsUseCase @Inject constructor(
    private val repository: AppInfoRepository,
    private val ignoreList: IgnoreListRepository,
    private val activeRoles: ActiveRoleApps,
    private val criticalDetector: CriticalAppDetector,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    // The suggestion rules documented above, applied in one pass over the catalogue: one branch per
    // rule and per category, hence the length and the branch count.
    @Suppress("LongMethod", "CyclomaticComplexMethod")
    suspend operator fun invoke(
        unusedThresholdDays: Int = 30,
        neverOpenedGraceDays: Int = 7,
        cacheHogThresholdBytes: Long = DEFAULT_CACHE_HOG_BYTES,
        oversizedThresholdBytes: Long = DEFAULT_OVERSIZED_BYTES,
        includeSystemApps: Boolean = false,
    ): Outcome<SmartCleanerReport> = runCatchingOutcome(mapError = { AppError.Unknown(it) }) {
        val now = System.currentTimeMillis()
        val unusedCutoff = now - unusedThresholdDays * MS_PER_DAY
        val rarelyUsedCutoff = now - unusedThresholdDays * MS_PER_DAY / 2
        val neverGraceCutoff = now - neverOpenedGraceDays * MS_PER_DAY

        val (usageReadable, inUse) = withContext(io) {
            repository.hasUsageStatsAccess() to activeRoles.packages()
        }
        if (!usageReadable) {
            return@runCatchingOutcome SmartCleanerReport(
                suggestions           = emptyList(),
                totalReclaimableBytes = 0L,
                analyzedAppCount      = 0,
            )
        }

        val ignored = ignoreList.observe().first()

        val first = repository.observeApps(includeSystemApps = includeSystemApps)
            .first { it !is Outcome.Loading }
        val apps = when (first) {
            is Outcome.Success -> first.value
            is Outcome.Failure -> throw IllegalStateException(first.error.toString())
            Outcome.Loading    -> emptyList()
        }
            .filter { it.isUninstallable && it.packageName !in ignored }
        // What the usage-based passes may judge: not an app at work in the background, not a protected one.
        val judgedOnUsage = withContext(io) {
            apps.filter { it.packageName !in inUse && criticalDetector.classify(it.packageName) == null }
        }

        val suggestions = mutableListOf<SmartSuggestion>()
        val seenPackages = mutableSetOf<String>() // dedupe — pick most impactful category per app

        // Pass 1: zombies (oldest unused first). The days are counted as on the Zombies screen.
        judgedOnUsage.forEach { app ->
            val cat = classifyZombie(app, unusedCutoff, neverGraceCutoff) ?: return@forEach
            val reclaim = app.totalSizeBytes // full uninstall reclaims everything
            suggestions += SmartSuggestion(
                appInfo            = app,
                category           = cat,
                reclaimableBytes   = reclaim,
                recommendedAction  = SmartSuggestion.RecommendedAction.UNINSTALL,
                reason             = if (cat == SmartSuggestion.Category.ZOMBIE_NEVER_OPENED) {
                    SmartSuggestion.Reason.NeverOpened(daysInstalled = daysSince(now, app.firstInstallTime))
                } else {
                    SmartSuggestion.Reason.UnusedFor(days = daysSince(now, app.lastUsedTime))
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
                    reason             = SmartSuggestion.Reason.LargeCache,
                )
                seenPackages += app.packageName
            }

        // Pass 3: oversized + rarely used — not opened for half the zombie threshold (zombies are already
        // taken by pass 1). With the zombie cutoff itself, as until 0.5.2, this rule could never fire.
        judgedOnUsage.filter {
            it.packageName !in seenPackages &&
                it.totalSizeBytes >= oversizedThresholdBytes &&
                it.lastUsedTime in 1..rarelyUsedCutoff
        }.forEach { app ->
            suggestions += SmartSuggestion(
                appInfo            = app,
                category           = SmartSuggestion.Category.OVERSIZED_RARELY_USED,
                reclaimableBytes   = app.totalSizeBytes,
                recommendedAction  = SmartSuggestion.RecommendedAction.REVIEW_AND_DECIDE,
                reason             = SmartSuggestion.Reason.LargeAndRarelyUsed,
            )
            seenPackages += app.packageName
        }

        // Pass 4: duplicate category — apps grouped by AppCategory where count > 1.
        // v0.5.1 — the categories that say nothing about an app's role are left out: UNDEFINED (no
        // category declared, as for most sideloaded and F-Droid apps) and OTHER (one this enum does
        // not know). Grouped, they turned unrelated apps into "duplicates" and added their full size
        // to the reclaimable total.
        judgedOnUsage.filter { it.packageName !in seenPackages && it.category !in ROLELESS_CATEGORIES }
            .groupBy { it.category }
            .filterValues { group -> group.size > 1 && group.any { it.lastUsedTime > 0L } }
            .forEach { (_, group) ->
                // Keep the most recently used (likely the one the user prefers). A group where no app
                // was ever used gives no ground to prefer one, so it is left out above.
                val mostRecent = group.maxByOrNull { it.lastUsedTime }
                group.filter { it != mostRecent }.forEach { app ->
                    suggestions += SmartSuggestion(
                        appInfo            = app,
                        category           = SmartSuggestion.Category.DUPLICATE_CATEGORY,
                        reclaimableBytes   = app.totalSizeBytes,
                        recommendedAction  = SmartSuggestion.RecommendedAction.REVIEW_AND_DECIDE,
                        reason             = SmartSuggestion.Reason.DuplicateCategory(app.category),
                    )
                }
            }

        val sorted = suggestions.sortedByDescending { it.reclaimableBytes }
        SmartCleanerReport(
            suggestions          = sorted,
            // A duplicate is a guess about the user's preference, not space to reclaim.
            totalReclaimableBytes = sorted
                .filter { it.category != SmartSuggestion.Category.DUPLICATE_CATEGORY }
                .sumOf { it.reclaimableBytes },
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

    private fun daysSince(now: Long, time: Long): Int = ((now - time) / MS_PER_DAY).toInt()
}

// 100 MB of cache.
private const val DEFAULT_CACHE_HOG_BYTES = 100L * 1024 * 1024

// 100 MB of install + data + cache.
private const val DEFAULT_OVERSIZED_BYTES = 100L * 1024 * 1024

// Categories that do not tell what an app does: two apps sharing one are not duplicates.
private val ROLELESS_CATEGORIES = setOf(AppCategory.UNDEFINED, AppCategory.OTHER)
