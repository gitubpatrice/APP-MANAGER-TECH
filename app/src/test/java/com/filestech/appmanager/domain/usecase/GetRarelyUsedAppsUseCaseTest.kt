package com.filestech.appmanager.domain.usecase

import com.filestech.appmanager.core.result.AppError
import com.filestech.appmanager.core.result.Outcome
import com.filestech.appmanager.data.system.ActiveRoleApps
import com.filestech.appmanager.domain.model.AppCategory
import com.filestech.appmanager.domain.model.AppInfo
import com.filestech.appmanager.domain.model.FilterOptions
import com.filestech.appmanager.domain.repository.AppInfoRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * [GetRarelyUsedAppsUseCase]: the "unused since" threshold applies to the last use read live, with the
 * grace period for apps never opened; without usage access the list is empty, a failure stays a failure.
 */
class GetRarelyUsedAppsUseCaseTest {

    private val repository: AppInfoRepository = mockk()
    private val activeRoles: ActiveRoleApps = mockk()
    private val useCase = GetRarelyUsedAppsUseCase(repository, activeRoles, Dispatchers.Unconfined)

    private val now = System.currentTimeMillis()
    private val msPerDay = 24L * 60 * 60 * 1000

    @BeforeEach
    fun usageReadableNoRoles() {
        every { repository.hasUsageStatsAccess() } returns true
        every { activeRoles.packages() } returns emptySet()
    }

    @Test
    fun `the threshold applies to the live last use, oldest first`() = runTest {
        givenCatalogue(
            app("com.recent", installedDaysAgo = 400),
            app("com.old", installedDaysAgo = 400),
            app("com.older", installedDaysAgo = 400),
        )
        // The cache knows nothing (all 0); live usage does.
        coEvery { repository.getLastUseTimes() } returns mapOf(
            "com.recent" to now - 2 * msPerDay,
            "com.old" to now - 40 * msPerDay,
            "com.older" to now - 90 * msPerDay,
        )

        val apps = (useCase(thresholdDays = 30).first() as Outcome.Success).value

        assertThat(apps.map { it.packageName }).containsExactly("com.older", "com.old").inOrder()
    }

    @Test
    fun `an app never opened is unused only if installed before the threshold`() = runTest {
        givenCatalogue(app("com.yesterday", installedDaysAgo = 1), app("com.forgotten", installedDaysAgo = 100))
        coEvery { repository.getLastUseTimes() } returns emptyMap()

        val apps = (useCase(thresholdDays = 30).first() as Outcome.Success).value

        assertThat(apps.map { it.packageName }).containsExactly("com.forgotten")
    }

    @Test
    fun `an app at work without being opened is left out`() = runTest {
        every { activeRoles.packages() } returns setOf("com.touchtype.swiftkey")
        givenCatalogue(app("com.touchtype.swiftkey", installedDaysAgo = 400))
        coEvery { repository.getLastUseTimes() } returns emptyMap()

        assertThat((useCase(thresholdDays = 30).first() as Outcome.Success).value).isEmpty()
    }

    @Test
    fun `without usage access the list is empty, and a failure stays a failure`() = runTest {
        every { repository.hasUsageStatsAccess() } returns false
        givenCatalogue(app("com.any", installedDaysAgo = 400))
        assertThat((useCase(thresholdDays = 30).first() as Outcome.Success).value).isEmpty()

        every { repository.observeFiltered(FilterOptions(includeSystemApps = false)) } returns
            flowOf(Outcome.Failure(AppError.Unknown(IllegalStateException("db"))))
        assertThat(useCase(thresholdDays = 30).first()).isInstanceOf(Outcome.Failure::class.java)
    }

    private fun givenCatalogue(vararg apps: AppInfo) {
        every { repository.observeFiltered(FilterOptions(includeSystemApps = false)) } returns
            flowOf(Outcome.Success(apps.toList()))
    }

    /** Cached last use left at 0, as a scan made without usage access writes it. */
    private fun app(pkg: String, installedDaysAgo: Int) = AppInfo(
        packageName      = pkg,
        label            = pkg,
        versionName      = "1.0",
        versionCode      = 1L,
        installSizeBytes = 1L,
        cacheSizeBytes   = 0L,
        dataSizeBytes    = 0L,
        firstInstallTime = now - installedDaysAgo * msPerDay,
        lastUpdateTime   = now - installedDaysAgo * msPerDay,
        lastUsedTime     = 0L,
        isSystemApp      = false,
        isUninstallable  = true,
        category         = AppCategory.UNDEFINED,
    )
}
