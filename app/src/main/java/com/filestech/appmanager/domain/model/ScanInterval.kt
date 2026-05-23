package com.filestech.appmanager.domain.model

/**
 * Cadence at which the background scan worker runs.
 *
 * Lives in `domain/model/` (Phase VIII C5 fix) so the use case
 * `ScheduleBackgroundScanUseCase` doesn't have to import from `data/`.
 *
 * Values map to WorkManager `PeriodicWorkRequest` intervals.
 * `OFF` disables the worker (cancels any scheduled instance).
 *
 * **Persistence**: stored as enum name (TEXT) in DataStore. Never rename
 * values without shipping a DataStore migration (silently falls back to OFF
 * if unknown).
 */
enum class ScanInterval { OFF, DAILY, WEEKLY }
