package com.filestech.appmanager.core.ext

/**
 * Time constants used across the codebase.
 *
 * Lives in `core/ext/` so both `data/` (repository scan + worker) and
 * `domain/` (use cases) can share them without crossing layer boundaries.
 *
 * **Calendar-precise vs millisecond-precise**: these are wall-clock
 * approximations — they assume 24-hour days. Sufficient for "N days ago"
 * UX thresholds (zombie detection, usage lookback) which never need
 * DST-aware arithmetic. If a future feature requires DST handling, use
 * `java.time.Duration` / `ZonedDateTime` instead and do not refactor these.
 */
const val MS_PER_DAY: Long = 24L * 60L * 60L * 1000L

/** 30-day window used by UsageStats aggregation and "rarely used" threshold default. */
const val MS_PER_30_DAYS: Long = 30L * MS_PER_DAY

/** Standard `WhileSubscribed` timeout for ViewModel StateFlows (Android convention). */
const val STATEFLOW_STOP_TIMEOUT_MS: Long = 5_000L

/** Max number of packages allowed in the ignore-list DataStore set. */
const val MAX_IGNORED_PACKAGES: Int = 500
