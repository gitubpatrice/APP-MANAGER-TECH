package com.filestech.appmanager.di

import javax.inject.Qualifier

/**
 * Hilt qualifier for the application-level [kotlinx.coroutines.CoroutineScope].
 *
 * This scope is backed by [SupervisorJob] and lives as long as the process.
 * Use it for work that must outlive individual ViewModels (e.g., writing to
 * DataStore from a WorkManager worker, or fire-and-forget DB writes initiated
 * by the system layer).
 *
 * Do NOT inject this scope into ViewModels; use `viewModelScope` there instead.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
