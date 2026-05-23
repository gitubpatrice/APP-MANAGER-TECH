package com.filestech.appmanager

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.filestech.appmanager.data.local.datastore.SettingsRepository
import com.filestech.appmanager.ui.AppRoot
import com.filestech.appmanager.ui.theme.AppManagerTechTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Single-activity host.
 *
 * - installSplashScreen() FIRST (before super.onCreate) → wires the
 *   androidx.core.splashscreen compat library so the system splash window
 *   coordinates with our `Theme.AppManagerTech.Splash` theme on API 26-30
 *   and uses the platform SplashScreen API on API 31+.
 * - enableEdgeToEdge() → renders behind system bars (AndroidX Activity 1.9+).
 * - FLAG_SECURE observer (Phase VII H-1 fix): reads `privacy.flagSecure` from
 *   the settings flow and applies / clears [WindowManager.LayoutParams.FLAG_SECURE]
 *   on every change. Without this binding, the settings toggle is a dead UI:
 *   the value persists in DataStore but never reaches the OS, leaving the
 *   recents/app-switcher preview unprotected.
 * - Compose content rooted at [AppRoot] which owns the NavHost.
 * - Theme wraps everything via [AppManagerTechTheme] once the splash is
 *   dismissed (`postSplashScreenTheme = Theme.AppManagerTech`).
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settings: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // FLAG_SECURE binding — repeats on STARTED so the flag is set whenever
        // the activity is in the foreground and torn down when it leaves.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                settings.flow
                    .map { it.privacy.flagSecure }
                    .distinctUntilChanged()
                    .collect { secure ->
                        if (secure) {
                            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                        } else {
                            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                        }
                    }
            }
        }

        setContent {
            AppManagerTechTheme {
                AppRoot()
            }
        }
    }
}
