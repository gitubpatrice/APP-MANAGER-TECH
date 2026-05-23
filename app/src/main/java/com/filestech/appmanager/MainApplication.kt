package com.filestech.appmanager

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.filestech.appmanager.core.result.Outcome
import com.filestech.appmanager.data.local.datastore.SettingsRepository
import com.filestech.appmanager.data.system.NotificationChannels
import com.filestech.appmanager.di.ApplicationScope
import com.filestech.appmanager.domain.repository.AppInfoRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
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
 * - **v0.1.1 fix** — auto-rescan at first launch (empty cache) OR when the user
 *   opted in via `Settings → autoScanOnLaunch`. Without this trigger, the home
 *   screen stayed empty on a fresh install: `dao.observeAll()` returned no
 *   rows because no scan had ever run.
 */
@HiltAndroidApp
class MainApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var notificationChannels: NotificationChannels
    @Inject lateinit var appInfoRepository: AppInfoRepository
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

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
        triggerInitialScanIfNeeded()
    }

    /**
     * Fire-and-forget rescan when EITHER the cache is empty (first launch on
     * this device) OR the user has opted in via `autoScanOnLaunch`. Runs on
     * [appScope] so it survives Activity rotation / process-level work but
     * does NOT block onCreate. Failures land in Timber only — the UI will
     * still render the (empty) loading state and the user can hit the
     * Refresh button to retry.
     */
    private fun triggerInitialScanIfNeeded() {
        appScope.launch {
            try {
                // v0.1.1 audit S3 fix — 5s ceiling so a corrupt DataStore proto
                // file cannot block the initial-scan coroutine forever.
                val settings = withTimeout(SETTINGS_LOAD_TIMEOUT_MS) {
                    settingsRepository.flow.first()
                }
                val shouldScan = if (settings.scanner.autoScanOnLaunch) {
                    true
                } else {
                    appInfoRepository.getStorageReport(includeSystemApps = false, topN = 0)
                        .let { it is Outcome.Success && it.value.totalAppCount == 0 }
                }
                if (!shouldScan) return@launch
                when (val r = appInfoRepository.rescan()) {
                    is Outcome.Success -> Timber.i("Initial rescan complete")
                    is Outcome.Failure -> Timber.w("Initial rescan failed: %s", r.error)
                    Outcome.Loading    -> Unit
                }
            } catch (e: TimeoutCancellationException) {
                Timber.w(e, "Initial scan trigger timed out — DataStore unreachable")
            }
        }
    }

    companion object {
        private const val SETTINGS_LOAD_TIMEOUT_MS: Long = 5_000L
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
