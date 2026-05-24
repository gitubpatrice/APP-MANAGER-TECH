package com.filestech.appmanager.domain.model

/**
 * v0.3.3 — Pre-defined user tag categories. v0.3.4 widens the model from
 * "one tag per app" to "a set of tags per app" so a single app can carry
 * several categories at once (e.g. WORK + TOOLS).
 *
 * Fixed set on purpose : keeps DataStore encoding simple (no need for a
 * tag-definition table), keeps the UI a 5-option multi-select, and avoids
 * the "user invents 30 overlapping tags" tagging-system anti-pattern of
 * free-text systems.
 *
 * Storage encoding : enum names as TEXT in the DataStore set, one entry per
 * package : `pkg=TAG1|TAG2|TAG3`. Backward-compatible with the v0.3.3
 * single-tag format `pkg=TAG_NAME` (the decoder wraps a separator-less
 * suffix into a one-element set). Never rename existing constants — would
 * orphan persisted assignments. Add new values at the END.
 */
enum class AppTag {
    WORK,
    FAMILY,
    GAME,
    TOOLS,
    MEDIA,
}
