package com.filestech.appmanager.ui.screens.applist

import android.content.Intent
import com.filestech.appmanager.core.result.Outcome
import com.filestech.appmanager.data.local.datastore.AppSettings
import com.filestech.appmanager.data.local.datastore.SettingsRepository
import com.filestech.appmanager.data.system.AmtActionLogger
import com.filestech.appmanager.data.system.CriticalAppDetector
import com.filestech.appmanager.domain.model.AppSortOrder
import com.filestech.appmanager.domain.model.CriticalCategory
import com.filestech.appmanager.domain.model.CriticalClassification
import com.filestech.appmanager.domain.model.FilterOptions
import com.filestech.appmanager.domain.model.InstallerFilter
import com.filestech.appmanager.domain.usecase.GetInstalledAppsUseCase
import com.filestech.appmanager.domain.usecase.UninstallAppUseCase
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * [AppListViewModel], v0.5.1:
 *  - the list opens on the saved "Default sort order" and "Include system apps" (it always opened on
 *    NAME_ASC, user apps only: only the Settings screen read them), follows a later change made in
 *    Settings, and keeps an in-screen choice until then;
 *  - "Uninstall selected" stops on a protected app for the hold-3s warning, as every single-app
 *    uninstall path does (it went straight to the system dialogs).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppListViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val getInstalledApps: GetInstalledAppsUseCase = mockk()
    private val uninstallApp: UninstallAppUseCase = mockk()
    private val actionLogger: AmtActionLogger = mockk(relaxed = true)
    private val criticalDetector: CriticalAppDetector = mockk()
    private val settingsFlow = MutableStateFlow(AppSettings())
    private val settings: SettingsRepository = mockk {
        every { flow } returns settingsFlow
    }

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { getInstalledApps(any(), any()) } returns flowOf(Outcome.Success(emptyList()))
        every { uninstallApp(any()) } answers { Outcome.Success(mockk<Intent>()) }
        every { criticalDetector.classify(any()) } returns null
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = AppListViewModel(
        getInstalledApps = getInstalledApps,
        uninstallApp     = uninstallApp,
        clearAppCache    = mockk(relaxed = true),
        batchAction      = mockk(relaxed = true),
        rescanApps       = mockk(relaxed = true),
        appInfoRepo      = mockk(relaxed = true),
        settings         = settings,
        intents          = mockk(relaxed = true),
        actionLogger     = actionLogger,
        criticalDetector = criticalDetector,
        io               = dispatcher,
    )

    /** `state` is shared WhileSubscribed: keep a subscriber, as the screen does. */
    private fun TestScope.subscribe(vm: AppListViewModel) {
        backgroundScope.launch(dispatcher) { vm.state.collect {} }
    }

    @Test
    fun `the list opens on the saved defaults, with no query on a hard-coded order first`() = runTest(dispatcher) {
        settingsFlow.value = AppSettings(
            appearance = AppSettings.Appearance(appSortOrder = AppSortOrder.SIZE_DESC),
            scanner    = AppSettings.Scanner(includeSystemApps = true),
        )
        val vm = viewModel()
        subscribe(vm)

        verify(exactly = 1) { getInstalledApps(any(), any()) }
        verify { getInstalledApps(match { it.includeSystemApps }, AppSortOrder.SIZE_DESC) }
        assertThat(vm.state.value.sortOrder).isEqualTo(AppSortOrder.SIZE_DESC)
        assertThat(vm.state.value.filterOptions.includeSystemApps).isTrue()
    }

    @Test
    fun `an in-screen choice holds until the matching setting changes`() = runTest(dispatcher) {
        val vm = viewModel()
        subscribe(vm)
        vm.onSortOrderChanged(AppSortOrder.NAME_DESC)
        vm.onToggleSystemApps(true)

        // An unrelated DataStore write undoes neither in-screen choice.
        settingsFlow.value = settingsFlow.value.copy(ignoredPackages = setOf("com.example.other"))
        assertThat(vm.state.value.sortOrder).isEqualTo(AppSortOrder.NAME_DESC)
        assertThat(vm.state.value.filterOptions.includeSystemApps).isTrue()

        // The default sort changed in Settings applies on return, and leaves the system-apps choice alone.
        settingsFlow.value = settingsFlow.value.copy(
            appearance = AppSettings.Appearance(appSortOrder = AppSortOrder.LAST_USED_DESC),
        )
        assertThat(vm.state.value.sortOrder).isEqualTo(AppSortOrder.LAST_USED_DESC)
        assertThat(vm.state.value.filterOptions.includeSystemApps).isTrue()

        // And the reverse: the system-apps setting applies, and leaves the in-screen sort alone.
        vm.onSortOrderChanged(AppSortOrder.NAME_DESC)
        vm.onToggleSystemApps(false)
        settingsFlow.value = settingsFlow.value.copy(scanner = AppSettings.Scanner(includeSystemApps = true))
        assertThat(vm.state.value.filterOptions.includeSystemApps).isTrue()
        assertThat(vm.state.value.sortOrder).isEqualTo(AppSortOrder.NAME_DESC)
    }

    @Test
    fun `seeding the system-apps default keeps every other filter field`() {
        val custom = FilterOptions(installerFilter = InstallerFilter.F_DROID, includeDisabled = false)

        assertThat(custom.withSystemApps(true)).isEqualTo(custom.copy(includeSystemApps = true))
        assertThat(null.withSystemApps(true)).isEqualTo(FilterOptions.DEFAULT.copy(includeSystemApps = true))
    }

    @Test
    fun `a batch holding a protected app waits for the hold-3s confirmation`() = runTest(dispatcher) {
        every { criticalDetector.classify(PROTECTED) } returns
            CriticalClassification(PROTECTED, CriticalCategory.AUTHENTICATION)
        val vm = viewModel()
        subscribe(vm)
        vm.toggleSelection(PLAIN)
        vm.toggleSelection(PROTECTED)

        vm.batchUninstall()

        val event = vm.events.first()
        assertThat(event).isEqualTo(
            AppListViewModel.Event.RequiresCriticalConfirmation(
                classification = CriticalClassification(PROTECTED, CriticalCategory.AUTHENTICATION),
                criticalCount  = 1,
            ),
        )
        verify(exactly = 0) { uninstallApp(any()) }
        verify(exactly = 0) { actionLogger.log(any(), any(), any(), any()) }
        assertThat(vm.state.value.selectedPackages).containsExactly(PLAIN, PROTECTED)

        vm.batchUninstall(bypassCriticalCheck = true)

        val launched = vm.events.first() as AppListViewModel.Event.LaunchIntentsSequentially
        assertThat(launched.intents).hasSize(2)
        verify(exactly = 2) { uninstallApp(any()) }
        assertThat(vm.state.value.selectedPackages).isEmpty()
    }

    @Test
    fun `a batch without a protected app goes straight to the system dialogs`() = runTest(dispatcher) {
        val vm = viewModel()
        subscribe(vm)
        vm.toggleSelection(PLAIN)

        vm.batchUninstall()

        val launched = vm.events.first() as AppListViewModel.Event.LaunchIntentsSequentially
        assertThat(launched.intents).hasSize(1)
    }

    private companion object {
        const val PLAIN = "com.example.plain"
        const val PROTECTED = "com.beemdevelopment.aegis"
    }
}
