package com.filestech.appmanager.data.local.datastore

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

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

/**
 * v0.5.1 — Settings → Privacy → "Confirm before deleting" ("Ask for
 * confirmation before clearing cache"), read by every screen that offers a
 * clear-cache action: App detail, the App list batch, Smart Cleaner. Until
 * v0.5.1 nothing read it: the first two always asked, Smart Cleaner never did.
 */
val SettingsRepository.confirmBeforeDelete: Flow<Boolean>
    get() = flow.map { it.privacy.confirmBeforeDelete }.distinctUntilChanged()
