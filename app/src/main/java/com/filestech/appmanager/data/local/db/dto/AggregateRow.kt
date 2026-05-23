package com.filestech.appmanager.data.local.db.dto

/**
 * Lightweight DTO for the `SELECT COUNT/SUM` aggregate query on `app_info`.
 *
 * Lives in `data/local/db/dto/` (NOT `data/local/db/entity/`) because it is
 * not a Room @Entity — Room maps query columns to this data class by name.
 */
data class AggregateRow(
    val appCount: Int,
    val installSum: Long,
    val dataSum: Long,
    val cacheSum: Long,
)
