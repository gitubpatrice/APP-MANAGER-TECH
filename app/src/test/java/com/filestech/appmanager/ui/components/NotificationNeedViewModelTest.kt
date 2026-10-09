package com.filestech.appmanager.ui.components

import com.filestech.appmanager.data.local.datastore.AppSettings
import com.filestech.appmanager.data.local.datastore.SettingsRepository
import com.filestech.appmanager.domain.model.ScanInterval
import com.filestech.appmanager.domain.repository.QuarantineRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * [NotificationNeedViewModel] — decides, once per launch, whether to ask for the notification
 * permission (v0.5.1): only when one of the app's notifications is on and could fire.
 */
class NotificationNeedViewModelTest {

    private val defaults = AppSettings()

    @Test
    fun `defaults with no quarantine need no notification`() {
        assertThat(NotificationNeedViewModel.notificationsNeeded(defaults, quarantineCount = 0)).isFalse()
    }

    @Test
    fun `a quarantine with its default reminder needs notifications`() {
        assertThat(NotificationNeedViewModel.notificationsNeeded(defaults, quarantineCount = 1)).isTrue()
    }

    @Test
    fun `a cache threshold needs notifications only with the automatic scan running`() {
        val threshold = defaults.copy(scanner = defaults.scanner.copy(cacheThresholdMb = 500))
        val scanning = threshold.copy(scanner = threshold.scanner.copy(autoScanInterval = ScanInterval.DAILY))

        assertThat(NotificationNeedViewModel.notificationsNeeded(threshold, quarantineCount = 0)).isFalse()
        assertThat(NotificationNeedViewModel.notificationsNeeded(scanning, quarantineCount = 0)).isTrue()
    }

    @Test
    fun `permission-change alerts need notifications only when they notify`() {
        val drift = defaults.copy(privacyMonitor = defaults.privacyMonitor.copy(permissionDriftEnabled = true))
        val silent = drift.copy(privacyMonitor = drift.privacyMonitor.copy(permissionDriftNotify = false))

        assertThat(NotificationNeedViewModel.notificationsNeeded(drift, quarantineCount = 0)).isTrue()
        assertThat(NotificationNeedViewModel.notificationsNeeded(silent, quarantineCount = 0)).isFalse()
    }

    @Test
    fun `an unreadable quarantine count means no request, not a crash`() = runTest {
        val settings: SettingsRepository = mockk { every { flow } returns flowOf(defaults) }
        val quarantine: QuarantineRepository = mockk {
            every { observeCount() } returns flow { throw IllegalStateException("Cannot open database") }
        }

        assertThat(NotificationNeedViewModel(settings, quarantine).notificationsNeeded()).isFalse()
    }
}
