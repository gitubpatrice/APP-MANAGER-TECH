package com.filestech.appmanager.ui.screens.quarantine

import com.filestech.appmanager.core.result.Outcome
import com.filestech.appmanager.data.local.datastore.SettingsRepository
import com.filestech.appmanager.data.system.AmtActionLogger
import com.filestech.appmanager.data.system.CriticalAppDetector
import com.filestech.appmanager.domain.model.CriticalCategory
import com.filestech.appmanager.domain.model.CriticalClassification
import com.filestech.appmanager.domain.model.QuarantineMode
import com.filestech.appmanager.domain.repository.AppInfoRepository
import com.filestech.appmanager.domain.repository.QuarantineRepository
import com.filestech.appmanager.domain.usecase.QuarantineAppUseCase
import com.google.common.truth.Truth.assertThat
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * [QuarantinePickerViewModel.quarantine]: a protected app stops on the hold-3s warning in both modes —
 * the reminder ("soft") mode leads the user to disable the app, which for an authenticator is losing it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class QuarantinePickerViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val appInfo: AppInfoRepository = mockk()
    private val quarantineRepo: QuarantineRepository = mockk()
    private val quarantineUseCase: QuarantineAppUseCase = mockk()
    private val criticalDetector: CriticalAppDetector = mockk()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { appInfo.observeApps(includeSystemApps = false) } returns flowOf(Outcome.Success(emptyList()))
        every { quarantineRepo.observeAll() } returns flowOf(emptyList())
        every { criticalDetector.classify(PROTECTED) } returns
            CriticalClassification(PROTECTED, CriticalCategory.AUTHENTICATION)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = QuarantinePickerViewModel(
        appInfo           = appInfo,
        quarantineRepo    = quarantineRepo,
        quarantineUseCase = quarantineUseCase,
        settings          = mockk<SettingsRepository>(relaxed = true),
        criticalDetector  = criticalDetector,
        actionLogger      = mockk<AmtActionLogger>(relaxed = true),
    )

    @Test
    fun `a protected app put in reminder quarantine waits for the hold-3s confirmation`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.quarantine(PROTECTED, QuarantineMode.SOFT_REMINDER, durationDays = 7)

        val event = vm.events.first()
        assertThat(event).isInstanceOf(QuarantinePickerViewModel.Event.RequiresCriticalConfirmation::class.java)
        assertThat((event as QuarantinePickerViewModel.Event.RequiresCriticalConfirmation).mode)
            .isEqualTo(QuarantineMode.SOFT_REMINDER)
        // Five matchers: invoke's fifth parameter (nowMs) has a default, which a fourth-only call would
        // pin to a concrete time and so never match — making "exactly = 0" true whatever happened.
        coVerify(exactly = 0) { quarantineUseCase(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `the confirmation keeps the mode it was asked for`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.quarantine(PROTECTED, QuarantineMode.HARD_UNINSTALL, durationDays = 30)

        val event = vm.events.first() as QuarantinePickerViewModel.Event.RequiresCriticalConfirmation
        assertThat(event.mode).isEqualTo(QuarantineMode.HARD_UNINSTALL)
        assertThat(event.durationDays).isEqualTo(30)
    }

    private companion object {
        const val PROTECTED = "com.beemdevelopment.aegis"
    }
}
