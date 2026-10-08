package com.filestech.appmanager.ui.screens.securityaudit

import android.content.Intent
import com.filestech.appmanager.core.result.Outcome
import com.filestech.appmanager.data.system.AmtActionLogger
import com.filestech.appmanager.data.system.CriticalAppDetector
import com.filestech.appmanager.domain.model.CriticalCategory
import com.filestech.appmanager.domain.model.CriticalClassification
import com.filestech.appmanager.domain.usecase.GetAccessibilityServiceAppsUseCase
import com.filestech.appmanager.domain.usecase.GetDeviceAdminAppsUseCase
import com.filestech.appmanager.domain.usecase.UninstallAppUseCase
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * [SecurityAuditViewModel.uninstall], v0.5.1: a protected app stops on the hold-3s warning, as on
 * every other uninstall path (this one went straight to the system dialog).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SecurityAuditViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val uninstallApp: UninstallAppUseCase = mockk()
    private val actionLogger: AmtActionLogger = mockk(relaxed = true)
    private val criticalDetector: CriticalAppDetector = mockk()
    private val getDeviceAdmins: GetDeviceAdminAppsUseCase = mockk()
    private val getAccessibility: GetAccessibilityServiceAppsUseCase = mockk()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        coEvery { getDeviceAdmins() } returns Outcome.Success(emptyList())
        coEvery { getAccessibility() } returns Outcome.Success(emptyList())
        every { uninstallApp(any()) } answers { Outcome.Success(mockk<Intent>()) }
        every { criticalDetector.classify(any()) } returns null
        every { criticalDetector.classify(PROTECTED) } returns
            CriticalClassification(PROTECTED, CriticalCategory.PASSWORD_MANAGERS)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = SecurityAuditViewModel(
        getDeviceAdmins  = getDeviceAdmins,
        getAccessibility = getAccessibility,
        uninstallApp     = uninstallApp,
        intents          = mockk(relaxed = true),
        actionLogger     = actionLogger,
        criticalDetector = criticalDetector,
    )

    /** The init refresh posts a RefreshDone first: skip it. */
    private suspend fun SecurityAuditViewModel.nextActionEvent() =
        events.filterNot { it is SecurityAuditViewModel.Event.RefreshDone }.first()

    @Test
    fun `a protected app waits for the hold-3s confirmation`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.uninstall(PROTECTED)

        assertThat(vm.nextActionEvent()).isEqualTo(
            SecurityAuditViewModel.Event.RequiresCriticalConfirmation(
                CriticalClassification(PROTECTED, CriticalCategory.PASSWORD_MANAGERS),
            ),
        )
        verify(exactly = 0) { uninstallApp(any()) }
        verify(exactly = 0) { actionLogger.log(any(), any(), any(), any()) }

        vm.uninstall(PROTECTED, bypassCriticalCheck = true)

        assertThat(vm.nextActionEvent()).isInstanceOf(SecurityAuditViewModel.Event.LaunchIntent::class.java)
        verify(exactly = 1) { uninstallApp(PROTECTED) }
    }

    @Test
    fun `an unprotected app goes straight to the system dialog`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.uninstall(PLAIN)

        assertThat(vm.nextActionEvent()).isInstanceOf(SecurityAuditViewModel.Event.LaunchIntent::class.java)
    }

    private companion object {
        const val PLAIN = "com.example.plain"
        const val PROTECTED = "com.x8bit.bitwarden"
    }
}
