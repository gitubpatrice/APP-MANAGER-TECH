package com.filestech.appmanager

import com.filestech.appmanager.data.local.datastore.AppSettings
import com.filestech.appmanager.domain.model.AppSortOrder
import com.filestech.appmanager.domain.model.ThemeMode
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Guard tests for [AppSettings] default values.
 *
 * Doctrine: defaults MUST be the safest / most conservative option.
 *
 * These tests prevent accidental default regressions during refactoring.
 * If a default intentionally changes, update the test AND document the
 * reason in the PR description.
 */
class AppSettingsTest {

    private val defaults = AppSettings()

    // ---------------------------------------------------------------------------
    // Appearance defaults
    // ---------------------------------------------------------------------------

    @Test
    fun `dynamic colour is disabled by default`() {
        // v0.1.2 — flipped from true to false so the app ships with a
        // stable brand palette (BrandBlue light / GitHub dark) regardless
        // of the user's wallpaper. Users who prefer Material You can opt
        // back in via Settings.
        assertThat(defaults.appearance.dynamicColor).isFalse()
    }

    @Test
    fun `theme mode follows system by default`() {
        assertThat(defaults.appearance.themeMode).isEqualTo(ThemeMode.SYSTEM)
    }

    @Test
    fun `app sort order is name ascending by default`() {
        assertThat(defaults.appearance.appSortOrder).isEqualTo(AppSortOrder.NAME_ASC)
    }

    // ---------------------------------------------------------------------------
    // Scanner defaults (conservative = fewer surprises)
    // ---------------------------------------------------------------------------

    @Test
    fun `system apps are excluded by default`() {
        // Conservative: showing system apps by default would overwhelm new users.
        assertThat(defaults.scanner.includeSystemApps).isFalse()
    }

    @Test
    fun `auto scan on launch is disabled by default`() {
        // Conservative: auto-scan on every launch may consume resources without consent.
        assertThat(defaults.scanner.autoScanOnLaunch).isFalse()
    }

    @Test
    fun `file analysis is enabled by default`() {
        assertThat(defaults.scanner.includeFileAnalysis).isTrue()
    }

    // ---------------------------------------------------------------------------
    // Privacy defaults (conservative = opt-in for sensitive features)
    // ---------------------------------------------------------------------------

    @Test
    fun `FLAG_SECURE is disabled by default`() {
        // FLAG_SECURE disabled by default: enabling it breaks screenshot-based
        // support workflows. Users opt in explicitly.
        assertThat(defaults.privacy.flagSecure).isFalse()
    }

    @Test
    fun `confirm before delete is enabled by default`() {
        // Conservative: protect users from accidental cache deletion.
        assertThat(defaults.privacy.confirmBeforeDelete).isTrue()
    }

    // ---------------------------------------------------------------------------
    // Structural invariants
    // ---------------------------------------------------------------------------

    @Test
    fun `AppSettings is a proper data class with structural equality`() {
        val a = AppSettings()
        val b = AppSettings()
        assertThat(a).isEqualTo(b)
        assertThat(a).isNotSameInstanceAs(b)
    }

    @Test
    fun `copy preserves all other fields when one is changed`() {
        val modified = defaults.copy(
            scanner = defaults.scanner.copy(includeSystemApps = true),
        )
        assertThat(modified.scanner.includeSystemApps).isTrue()
        // All other fields unchanged
        assertThat(modified.appearance).isEqualTo(defaults.appearance)
        assertThat(modified.privacy).isEqualTo(defaults.privacy)
        assertThat(modified.scanner.autoScanOnLaunch).isEqualTo(defaults.scanner.autoScanOnLaunch)
    }

    // ---------------------------------------------------------------------------
    // ThemeMode / AppSortOrder enum completeness
    // ---------------------------------------------------------------------------

    @Test
    fun `ThemeMode has exactly 3 values`() {
        assertThat(ThemeMode.entries).hasSize(3)
    }

    @Test
    fun `AppSortOrder has exactly 5 values`() {
        assertThat(AppSortOrder.entries).hasSize(5)
    }
}
