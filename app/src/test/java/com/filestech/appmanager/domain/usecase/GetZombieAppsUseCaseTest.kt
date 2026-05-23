package com.filestech.appmanager.domain.usecase

import com.filestech.appmanager.core.result.AppError
import com.filestech.appmanager.core.result.Outcome
import com.filestech.appmanager.domain.model.AppCategory
import com.filestech.appmanager.domain.model.AppInfo
import com.filestech.appmanager.domain.model.ZombieApp
import com.filestech.appmanager.domain.repository.AppInfoRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * Tests [GetZombieAppsUseCase] classification:
 * - NEVER_OPENED: lastUsedTime=0 AND firstInstall older than grace window.
 * - UNUSED_SINCE: lastUsedTime non-zero but older than threshold.
 * - System apps (`isUninstallable=false`) excluded — can't act on them anyway.
 * - Brand-new installs (firstInstall within grace window) excluded.
 */
class GetZombieAppsUseCaseTest {

    private val repository: AppInfoRepository = mockk()
    private val useCase = GetZombieAppsUseCase(repository)

    private val now = System.currentTimeMillis()
    private val msPerDay = 24L * 60 * 60 * 1000

    @Test
    fun `validation fails on non-positive threshold`() = runTest {
        val outcome = useCase(unusedThresholdDays = 0)
        assertThat(outcome).isInstanceOf(Outcome.Failure::class.java)
        assertThat((outcome as Outcome.Failure).error).isInstanceOf(AppError.Validation::class.java)
    }

    @Test
    fun `never opened apps are flagged with NEVER_OPENED reason`() = runTest {
        val app = sample(
            pkg = "com.never.opened",
            lastUsed = 0L,
            firstInstall = now - 30 * msPerDay, // 30 days ago — past 7-day grace
        )
        givenAppList(listOf(app))

        val result = (useCase(unusedThresholdDays = 30) as Outcome.Success).value

        assertThat(result).hasSize(1)
        assertThat(result.first().reason).isEqualTo(ZombieApp.Reason.NEVER_OPENED)
    }

    @Test
    fun `brand new never opened apps are NOT flagged (grace window)`() = runTest {
        val app = sample(
            pkg = "com.brand.new",
            lastUsed = 0L,
            firstInstall = now - 2 * msPerDay, // 2 days ago — inside 7-day grace
        )
        givenAppList(listOf(app))

        val result = (useCase(unusedThresholdDays = 30) as Outcome.Success).value
        assertThat(result).isEmpty()
    }

    @Test
    fun `apps unused for longer than threshold are flagged UNUSED_SINCE`() = runTest {
        val app = sample(
            pkg = "com.old.app",
            lastUsed = now - 60 * msPerDay, // unused 60 days
            firstInstall = now - 100 * msPerDay,
        )
        givenAppList(listOf(app))

        val result = (useCase(unusedThresholdDays = 30) as Outcome.Success).value

        assertThat(result).hasSize(1)
        assertThat(result.first().reason).isEqualTo(ZombieApp.Reason.UNUSED_SINCE)
        assertThat(result.first().unusedDays).isAtLeast(60)
    }

    @Test
    fun `recently used apps are NOT flagged`() = runTest {
        val app = sample(
            pkg = "com.recent",
            lastUsed = now - 5 * msPerDay,
            firstInstall = now - 100 * msPerDay,
        )
        givenAppList(listOf(app))

        val result = (useCase(unusedThresholdDays = 30) as Outcome.Success).value
        assertThat(result).isEmpty()
    }

    @Test
    fun `non-uninstallable system apps are filtered out`() = runTest {
        val systemApp = sample(
            pkg = "com.system.bundled",
            lastUsed = 0L,
            firstInstall = now - 100 * msPerDay,
            uninstallable = false, // system app
        )
        givenAppList(listOf(systemApp))

        val result = (useCase(unusedThresholdDays = 30) as Outcome.Success).value
        assertThat(result).isEmpty()
    }

    @Test
    fun `output is sorted by unused days descending`() = runTest {
        val moreUnused = sample(
            pkg = "com.older",
            lastUsed = now - 100 * msPerDay,
            firstInstall = now - 200 * msPerDay,
        )
        val lessUnused = sample(
            pkg = "com.newer",
            lastUsed = now - 40 * msPerDay,
            firstInstall = now - 200 * msPerDay,
        )
        givenAppList(listOf(lessUnused, moreUnused))

        val result = (useCase(unusedThresholdDays = 30) as Outcome.Success).value

        assertThat(result.map { it.info.packageName }).containsExactly(
            "com.older", "com.newer",
        ).inOrder()
    }

    // ---------------------------------------------------------------------------

    private fun givenAppList(apps: List<AppInfo>) {
        every { repository.observeApps(includeSystemApps = false) } returns
            flowOf(Outcome.Success(apps))
    }

    private fun sample(
        pkg: String,
        lastUsed: Long,
        firstInstall: Long,
        uninstallable: Boolean = true,
    ) = AppInfo(
        packageName       = pkg,
        label             = pkg,
        versionName       = "1.0",
        versionCode       = 1L,
        installSizeBytes  = 1L,
        cacheSizeBytes    = 0L,
        dataSizeBytes     = 0L,
        firstInstallTime  = firstInstall,
        lastUpdateTime    = firstInstall,
        lastUsedTime      = lastUsed,
        isSystemApp       = !uninstallable,
        isUninstallable   = uninstallable,
        isEnabled         = true,
        category          = AppCategory.UNDEFINED,
    )
}
