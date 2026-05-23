package com.filestech.appmanager.data.repository

import com.filestech.appmanager.data.local.datastore.SettingsRepository
import com.filestech.appmanager.domain.repository.IgnoreListRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DataStore-backed [IgnoreListRepository] — delegates to [SettingsRepository]
 * so the ignore set is persisted in the same transactional store as every
 * other user preference (single .dataStore file, atomic edits).
 */
@Singleton
class IgnoreListRepositoryImpl @Inject constructor(
    private val settings: SettingsRepository,
) : IgnoreListRepository {

    override fun observe(): Flow<Set<String>> =
        settings.flow.map { it.ignoredPackages }

    override suspend fun isIgnored(packageName: String): Boolean =
        packageName in settings.flow.first().ignoredPackages

    override suspend fun add(packageName: String) {
        settings.update { copy(ignoredPackages = ignoredPackages + packageName) }
    }

    override suspend fun remove(packageName: String) {
        settings.update { copy(ignoredPackages = ignoredPackages - packageName) }
    }

    override suspend fun toggle(packageName: String) {
        settings.update {
            val next = if (packageName in ignoredPackages) {
                ignoredPackages - packageName
            } else {
                ignoredPackages + packageName
            }
            copy(ignoredPackages = next)
        }
    }
}
