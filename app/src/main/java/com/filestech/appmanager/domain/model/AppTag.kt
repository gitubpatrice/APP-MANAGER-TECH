package com.filestech.appmanager.domain.model

/**
 * v0.3.3 — Pre-defined user tag categories.
 *
 * Fixed set on purpose : keeps DataStore encoding simple (no need for a
 * tag-definition table), keeps the UI a 5-option picker, and avoids the
 * "user invents 30 overlapping tags" tagging-system anti-pattern of
 * free-text systems.
 *
 * Storage encoding : enum name as TEXT in the DataStore set
 * (`pkg=TAG_NAME`). Never rename existing constants — would orphan
 * persisted assignments. Add new values at the END.
 */
enum class AppTag {
    WORK,
    FAMILY,
    GAME,
    TOOLS,
    MEDIA,
}
