package com.filestech.appmanager

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.filestech.appmanager.data.system.NotificationChannels
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import javax.inject.Inject

/**
 * Application entry point.
 *
 * Responsibilities:
 * - Hilt DI graph initialisation (@HiltAndroidApp)
 * - WorkManager configuration with HiltWorkerFactory (on-demand init —
 *   AndroidManifest removes androidx.startup.InitializationProvider for
 *   WorkManagerInitializer; cf. Phase VII C release fix).
 * - Timber logging setup (DebugTree in DEBUG builds only; NoOp in release)
 * - Notification channels registration (Phase VIII D3 fix — moved from
 *   lazy init in `NotificationHelper` to startup so WorkManager-resurrected
 *   processes never post a notif before the channel exists).
 */
@HiltAndroidApp
class MainApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var notificationChannels: NotificationChannels

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        } else {
            Timber.plant(NoOpReleaseTree())
        }
        notificationChannels.ensureRegistered()
    }
}

/**
 * Silent Timber tree for release builds.
 * Never logs to Logcat; silently discards all entries.
 * Crash reporting (if added later) would go here, NOT in DebugTree.
 */
private class NoOpReleaseTree : Timber.Tree() {
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) = Unit
}
