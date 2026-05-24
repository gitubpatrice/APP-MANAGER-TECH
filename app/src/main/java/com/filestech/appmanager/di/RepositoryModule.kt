package com.filestech.appmanager.di

import com.filestech.appmanager.data.local.datastore.SettingsRepository
import com.filestech.appmanager.data.local.datastore.SettingsRepositoryImpl
import com.filestech.appmanager.data.repository.AppInfoRepositoryImpl
import com.filestech.appmanager.data.repository.AppLifecycleRepositoryImpl
import com.filestech.appmanager.data.repository.IgnoreListRepositoryImpl
import com.filestech.appmanager.data.repository.PermissionSnapshotRepositoryImpl
import com.filestech.appmanager.data.repository.QuarantineRepositoryImpl
import com.filestech.appmanager.data.repository.TrashRepositoryImpl
import com.filestech.appmanager.domain.repository.AppInfoRepository
import com.filestech.appmanager.domain.repository.AppLifecycleRepository
import com.filestech.appmanager.domain.repository.IgnoreListRepository
import com.filestech.appmanager.domain.repository.PermissionSnapshotRepository
import com.filestech.appmanager.domain.repository.QuarantineRepository
import com.filestech.appmanager.domain.repository.TrashRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds domain repository interfaces to their concrete implementations.
 *
 * @Binds (vs @Provides) generates less bytecode and lets R8 inline the
 * binding in release builds. The cost is that the impl must be `@Inject`-able
 * directly (no factory pattern) — which is the case for all our repositories.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(
        impl: SettingsRepositoryImpl,
    ): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindAppInfoRepository(
        impl: AppInfoRepositoryImpl,
    ): AppInfoRepository

    @Binds
    @Singleton
    abstract fun bindIgnoreListRepository(
        impl: IgnoreListRepositoryImpl,
    ): IgnoreListRepository

    @Binds
    @Singleton
    abstract fun bindTrashRepository(
        impl: TrashRepositoryImpl,
    ): TrashRepository

    @Binds
    @Singleton
    abstract fun bindPermissionSnapshotRepository(
        impl: PermissionSnapshotRepositoryImpl,
    ): PermissionSnapshotRepository

    @Binds
    @Singleton
    abstract fun bindQuarantineRepository(
        impl: QuarantineRepositoryImpl,
    ): QuarantineRepository

    @Binds
    @Singleton
    abstract fun bindAppLifecycleRepository(
        impl: AppLifecycleRepositoryImpl,
    ): AppLifecycleRepository
}
