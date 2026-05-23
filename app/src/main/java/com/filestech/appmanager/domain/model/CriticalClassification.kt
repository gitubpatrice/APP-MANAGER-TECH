package com.filestech.appmanager.domain.model

/**
 * Result of running [com.filestech.appmanager.data.system.CriticalAppDetector]
 * on a single package. `null` returned by the detector when the package is
 * not classified as critical.
 *
 * Carries both the package name (for log / display) and the [CriticalCategory]
 * that triggered the classification, so the warning dialog can pick the right
 * copy.
 */
data class CriticalClassification(
    val packageName: String,
    val category: CriticalCategory,
)
