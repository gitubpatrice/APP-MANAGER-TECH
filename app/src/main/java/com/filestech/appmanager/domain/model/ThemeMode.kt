package com.filestech.appmanager.domain.model

/**
 * User-facing theme mode for the Material 3 colour scheme.
 *
 * Lives in `domain/model/` (Phase VIII C5 fix) — pure enum, no DataStore dep.
 *
 * **Persistence**: see [AppSortOrder] — same rule (do not rename values).
 */
enum class ThemeMode { LIGHT, DARK, SYSTEM }
