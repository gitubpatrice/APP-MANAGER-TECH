package com.filestech.appmanager.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Whitelist of package names the user has opted out of batch actions for.
 *
 * Backed by [com.filestech.appmanager.data.local.datastore.SettingsRepository]
 * — the underlying storage is a DataStore `stringSet` — but exposed through
 * this domain-level façade so UseCases stay decoupled from DataStore plumbing.
 *
 * Phase VI feature. Future Phase VIII screen: `IgnoreListScreen`.
 */
interface IgnoreListRepository {

    /** Hot flow of the current ignore set. */
    fun observe(): Flow<Set<String>>

    /** One-shot probe. */
    suspend fun isIgnored(packageName: String): Boolean

    suspend fun add(packageName: String)
    suspend fun remove(packageName: String)
    suspend fun toggle(packageName: String)
}
