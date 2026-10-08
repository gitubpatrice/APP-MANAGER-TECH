package com.filestech.appmanager.ui.text

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.filestech.appmanager.R
import com.filestech.appmanager.domain.model.AppCategory

/** The user-facing name of an [AppCategory]. */
@Composable
fun categoryLabel(category: AppCategory): String = stringResource(
    when (category) {
        AppCategory.GAMES         -> R.string.category_games
        AppCategory.AUDIO         -> R.string.category_audio
        AppCategory.VIDEO         -> R.string.category_video
        AppCategory.IMAGE         -> R.string.category_image
        AppCategory.SOCIAL        -> R.string.category_social
        AppCategory.NEWS          -> R.string.category_news
        AppCategory.MAPS          -> R.string.category_maps
        AppCategory.PRODUCTIVITY  -> R.string.category_productivity
        AppCategory.ACCESSIBILITY -> R.string.category_accessibility
        AppCategory.OTHER         -> R.string.category_other
        AppCategory.UNDEFINED     -> R.string.category_undefined
    }
)

/**
 * The user-facing name of a tracker category from `assets/trackers.json`. The file stores English
 * keys; a category added to it later and not mapped here is shown as stored.
 */
@Composable
fun trackerCategoryLabel(category: String): String = when (category) {
    "Ads"            -> stringResource(R.string.tracker_category_ads)
    "Analytics"      -> stringResource(R.string.tracker_category_analytics)
    "Attribution"    -> stringResource(R.string.tracker_category_attribution)
    "Crash reporter" -> stringResource(R.string.tracker_category_crash_reporter)
    "Identification" -> stringResource(R.string.tracker_category_identification)
    "Profiling"      -> stringResource(R.string.tracker_category_profiling)
    "Push"           -> stringResource(R.string.tracker_category_push)
    "Session replay" -> stringResource(R.string.tracker_category_session_replay)
    else             -> category
}
