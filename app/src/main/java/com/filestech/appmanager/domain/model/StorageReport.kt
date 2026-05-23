package com.filestech.appmanager.domain.model

/**
 * Aggregate storage statistics across the cached app catalogue.
 *
 * Produced by `AnalyzeStorageUseCase` and consumed by the Storage screen
 * (pie chart + top-N list + per-category breakdown).
 *
 * All `*Bytes` fields are summed from the latest Room cache snapshot — they
 * reflect what was true at the last `rescan()`, not necessarily what is true
 * on disk right this second. Refresh strategy is the ViewModel's concern.
 */
data class StorageReport(
    /** Number of apps considered (after the system/user filter). */
    val totalAppCount: Int,
    /** Sum of `installSizeBytes` across the considered apps. */
    val totalInstallBytes: Long,
    /** Sum of `dataSizeBytes`. */
    val totalDataBytes: Long,
    /** Sum of `cacheSizeBytes`. */
    val totalCacheBytes: Long,
    /** Per-category breakdown. Sorted by [CategoryStats.totalBytes] descending. */
    val perCategory: List<CategoryStats>,
    /** Top-N apps by `totalSizeBytes`, descending. N is whatever the use case asks for. */
    val topByTotalSize: List<AppFootprint>,
    /** Top-N apps by `cacheSizeBytes`, descending — surface for cache cleaning. */
    val topByCacheSize: List<AppFootprint>,
) {

    /** Install + data + cache, summed. */
    val grandTotalBytes: Long
        get() = totalInstallBytes + totalDataBytes + totalCacheBytes

    data class CategoryStats(
        val category: AppCategory,
        val count: Int,
        val totalBytes: Long,
    )

    data class AppFootprint(
        val packageName: String,
        val label: String,
        val installBytes: Long,
        val dataBytes: Long,
        val cacheBytes: Long,
    ) {
        val totalBytes: Long get() = installBytes + dataBytes + cacheBytes
    }

    companion object {
        val EMPTY = StorageReport(
            totalAppCount     = 0,
            totalInstallBytes = 0L,
            totalDataBytes    = 0L,
            totalCacheBytes   = 0L,
            perCategory       = emptyList(),
            topByTotalSize    = emptyList(),
            topByCacheSize    = emptyList(),
        )
    }
}
