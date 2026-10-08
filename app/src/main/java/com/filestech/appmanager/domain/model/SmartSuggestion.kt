package com.filestech.appmanager.domain.model

/**
 * One actionable suggestion produced by the Smart Cleaner.
 *
 * **App Manager Tech innovation**: instead of scattering "rarely used" /
 * "cache hogs" / "duplicates" across three different screens, Smart Cleaner
 * groups concrete savings opportunities under one roof, sorted by
 * estimated impact (`reclaimableBytes`).
 *
 * Each suggestion carries:
 * - a [category] for the badge,
 * - the [appInfo] subject,
 * - an estimate of how much space the user could reclaim by acting on it,
 * - a [recommendedAction] hint the UI can wire to an existing UseCase,
 * - the [reason] it was picked, which the screen words in the user's language.
 */
data class SmartSuggestion(
    val appInfo: AppInfo,
    val category: Category,
    val reclaimableBytes: Long,
    val recommendedAction: RecommendedAction,
    val reason: Reason,
) {

    /** Visual badge category — drives the colour and section grouping. */
    enum class Category {
        ZOMBIE_NEVER_OPENED,
        ZOMBIE_UNUSED_LONG,
        CACHE_HOG,
        OVERSIZED_RARELY_USED,
        DUPLICATE_CATEGORY,
    }

    /** Hint for the UI — maps 1:1 to an existing AppAction. */
    enum class RecommendedAction {
        UNINSTALL,
        CLEAR_CACHE,
        REVIEW_AND_DECIDE,
    }

    /** Why the app was suggested — data, not a sentence: the screen words it. */
    sealed interface Reason {
        data class NeverOpened(val daysInstalled: Int) : Reason
        data class UnusedFor(val days: Int) : Reason
        data object LargeCache : Reason
        data object LargeAndRarelyUsed : Reason
        data class DuplicateCategory(val category: AppCategory) : Reason
    }
}

/**
 * Aggregated Smart Cleaner report — produced by `GetSmartSuggestionsUseCase`.
 * Sorted descending by `reclaimableBytes` overall; UI groups by [SmartSuggestion.Category].
 */
data class SmartCleanerReport(
    val suggestions: List<SmartSuggestion>,
    val totalReclaimableBytes: Long,
    val analyzedAppCount: Int,
) {
    companion object {
        val EMPTY = SmartCleanerReport(
            suggestions          = emptyList(),
            totalReclaimableBytes = 0L,
            analyzedAppCount     = 0,
        )
    }
}
