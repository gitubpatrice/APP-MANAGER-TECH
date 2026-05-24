package com.filestech.appmanager.di

import android.content.Context
import androidx.room.Room
import com.filestech.appmanager.data.local.db.AppDatabase
import com.filestech.appmanager.data.local.db.Migrations
import com.filestech.appmanager.data.local.db.dao.AmtActionEventDao
import com.filestech.appmanager.data.local.db.dao.AppInfoDao
import com.filestech.appmanager.data.local.db.dao.AppLifecycleEventDao
import com.filestech.appmanager.data.local.db.dao.PermissionSnapshotDao
import com.filestech.appmanager.data.local.db.dao.QuarantineEntryDao
import com.filestech.appmanager.data.local.db.dao.TrashItemDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for Room database and DAO bindings.
 *
 * No `fallbackToDestructiveMigration` — every schema bump must ship an
 * additive Migration registered in [Migrations.ALL_MIGRATIONS].
 *
 * The database file is stored under the app's private files dir and is
 * excluded from cloud backup by `res/xml/backup_rules.xml`.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME,
        )
            .addMigrations(*Migrations.ALL_MIGRATIONS)
            .build()

    @Provides
    fun provideAppInfoDao(db: AppDatabase): AppInfoDao = db.appInfoDao()

    @Provides
    fun provideTrashItemDao(db: AppDatabase): TrashItemDao = db.trashItemDao()

    @Provides
    fun providePermissionSnapshotDao(db: AppDatabase): PermissionSnapshotDao =
        db.permissionSnapshotDao()

    @Provides
    fun provideQuarantineEntryDao(db: AppDatabase): QuarantineEntryDao =
        db.quarantineEntryDao()

    @Provides
    fun provideAppLifecycleEventDao(db: AppDatabase): AppLifecycleEventDao =
        db.appLifecycleEventDao()

    @Provides
    fun provideAmtActionEventDao(db: AppDatabase): AmtActionEventDao =
        db.amtActionEventDao()
}
