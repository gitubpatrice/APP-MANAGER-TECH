package com.filestech.appmanager.data.local.datastore

import kotlinx.coroutines.flow.Flow

/**
 * Domain-layer contract for user settings persistence.
 *
 * Kept in the data/datastore package (rather than domain/repository) because
 * settings are tightly coupled to the DataStore implementation and have no
 * alternative implementation planned. If that changes, move to domain/repository/.
 */
interface SettingsRepository {

    /** Hot flow emitting the current [AppSettings] on every change. */
    val flow: Flow<AppSettings>

    /**
     * Atomically updates settings via [transform].
     * Thread-safe; DataStore serialises writes internally.
     */
    suspend fun update(transform: AppSettings.() -> AppSettings)
}
