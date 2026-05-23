package com.filestech.appmanager

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.filestech.appmanager.data.local.datastore.SettingsRepository
import com.filestech.appmanager.domain.model.ThemeMode
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
        // v0.1.2 — force a ~700ms minimum splash duration so the launcher
        // logo is visibly perceptible on fast cold starts. Without this,
        // the home screen Compose tree paints within ~150ms on modern
        // devices and the splash flashes too quickly to register.
        val splash = installSplashScreen()
        val splashStart = System.currentTimeMillis()
        splash.setKeepOnScreenCondition {
            System.currentTimeMillis() - splashStart < SPLASH_MIN_DURATION_MS
        }
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
            // v0.1.1 fix: collect the live settings so the theme toggle in
            // Settings actually takes effect (previously the picker wrote to
            // DataStore but the value never flowed back into MaterialTheme,
            // leaving the dark-mode switch dead).
            // collectAsStateWithLifecycle (vs raw collectAsState) so the
            // DataStore subscription is paused while the Activity is in
            // background — mirrors the FLAG_SECURE observer above and avoids
            // a background-process battery drain on Android 14+.
            val appearance by settings.flow
                .map { it.appearance }
                .distinctUntilChanged()
                .collectAsStateWithLifecycle(initialValue = null)

            val resolvedDarkTheme = when (appearance?.themeMode) {
                ThemeMode.LIGHT  -> false
                ThemeMode.DARK   -> true
                ThemeMode.SYSTEM,
                null             -> isSystemInDarkTheme()
            }
            // v0.1.2 — default to OFF so the first frame matches the
             // post-DataStore frame (avoid the brand → dynamic flash on
             // Android 12+ devices with non-neutral wallpapers).
            val resolvedDynamicColor = appearance?.dynamicColor ?: false

            AppManagerTechTheme(
                darkTheme    = resolvedDarkTheme,
                dynamicColor = resolvedDynamicColor,
            ) {
                AppRoot()
            }
        }
    }

    companion object {
        /**
         * v0.1.2 — minimum splash duration in ms. 700ms is long enough for the
         * splash logo to be perceptible (typical human reaction time floor)
         * but short enough that fast cold starts don't feel sluggish.
         */
        private const val SPLASH_MIN_DURATION_MS: Long = 700L
    }
}
