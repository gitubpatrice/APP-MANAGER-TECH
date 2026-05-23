package com.filestech.appmanager.domain.model

/**
 * Application category, mirrored from `ApplicationInfo.CATEGORY_*` constants
 * (introduced in API 26 — matches our minSdk).
 *
 * Mapping rule:
 * - PackageManager reports an `Int` category (or `CATEGORY_UNDEFINED = -1`).
 * - We map to this enum at the data layer; UI never sees the raw int.
 * - Unknown / future Android categories collapse to [OTHER].
 *
 * **PERSISTENCE WARNING** — values are serialised to Room (`app_info.category`
 * TEXT column) via [Enum.name]. Renaming a value SILENTLY breaks rows cached
 * by older app versions: they fall back to [UNDEFINED] on read.
 * Safe operations: ADD a new value (additive). Forbidden: rename, remove,
 * reorder semantically. To rename, ship a Room migration that rewrites the
 * column.
 */
enum class AppCategory {
    GAMES,
    AUDIO,
    VIDEO,
    IMAGE,
    SOCIAL,
    NEWS,
    MAPS,
    PRODUCTIVITY,
    ACCESSIBILITY,
    OTHER,
    UNDEFINED,
}
