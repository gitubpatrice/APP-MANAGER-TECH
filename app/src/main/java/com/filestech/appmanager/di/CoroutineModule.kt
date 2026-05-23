package com.filestech.appmanager.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

/**
 * Provides coroutine primitives as Hilt singletons.
 *
 * - [IoDispatcher]: [Dispatchers.IO] — for Room, DataStore, file I/O.
 * - [ApplicationScope]: process-lifetime scope backed by [SupervisorJob] +
 *   [Dispatchers.Default] so child failures do not cancel the scope, and
 *   coroutines launched without an explicit dispatcher do not hog the IO pool.
 *   IO-bound children must still wrap their work in `withContext(Dispatchers.IO)`.
 */
@Module
@InstallIn(SingletonComponent::class)
object CoroutineModule {

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
