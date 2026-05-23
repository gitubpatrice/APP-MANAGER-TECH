package com.filestech.appmanager.core.ext

/**
 * String extension utilities for App Manager Tech.
 *
 * Anti-ReDoS note: any regex used here must be bounded / anchored, and
 * compiled once at top-level — never instantiated inside hot paths.
 */

/**
 * Package-name validator pattern. Compiled once for reuse across all calls.
 *
 * Anchored with `^...$` and bounded — no `+?` / `(a+)+` constructs, so the
 * matching cost is linear in input length. Length is also pre-checked to
 * cap pathological inputs before the regex engine runs.
 */
private val PACKAGE_NAME_REGEX =
    Regex("""^[a-zA-Z_][a-zA-Z0-9_]*(\.[a-zA-Z_][a-zA-Z0-9_]*)+$""")

/** Returns null if the string is blank; the string itself otherwise. */
fun String.nullIfBlank(): String? = ifBlank { null }

/** Truncates to [maxLength] characters, appending [ellipsis] if truncated. */
fun String.truncate(maxLength: Int, ellipsis: String = "…"): String {
    require(maxLength > 0) { "maxLength must be > 0, was $maxLength" }
    return if (length <= maxLength) this else take(maxLength) + ellipsis
}

/**
 * Converts a package name (e.g. "com.filestech.appmanager") to a display label
 * by taking the last segment and capitalising it.
 *
 * Example: "com.example.myapp" → "Myapp"
 */
fun String.packageNameToLabel(): String =
    substringAfterLast('.').replaceFirstChar { it.uppercaseChar() }

/** Returns true if this string is a syntactically plausible Android package name. */
fun String.isValidPackageName(): Boolean {
    if (length > 255 || isBlank()) return false
    return PACKAGE_NAME_REGEX.matches(this)
}
