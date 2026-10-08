package com.filestech.appmanager.domain.usecase

import com.filestech.appmanager.core.result.Outcome
import com.filestech.appmanager.domain.model.AppCategory
import com.filestech.appmanager.domain.model.AppInfo
import com.filestech.appmanager.domain.model.SmartCleanerReport
import com.filestech.appmanager.domain.model.SmartSuggestion
import com.filestech.appmanager.domain.repository.AppInfoRepository
import com.filestech.appmanager.domain.repository.IgnoreListRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * v0.5.1 — the "duplicate category" rule of [GetSmartSuggestionsUseCase].
 *
 * Every app below is recently used, small and light on cache, so no other rule fires: whatever
 * the report holds comes from the duplicate rule alone.
 */
class GetSmartSuggestionsUseCaseTest {

    private val repository: AppInfoRepository = mockk()
    private val ignoreList: IgnoreListRepository = mockk()
    private val useCase = GetSmartSuggestionsUseCase(repository, ignoreList)

    private val now = System.currentTimeMillis()
    private val msPerDay = 24L * 60 * 60 * 1000

    @ParameterizedTest
    @EnumSource(value = AppCategory::class, names = ["UNDEFINED", "OTHER"])
    fun `apps without a known role are never duplicates of each other`(category: AppCategory) = runTest {
        givenApps(
            app("org.fdroid.one", category, lastUsedDaysAgo = 1),
            app("org.fdroid.two", category, lastUsedDaysAgo = 2),
            app("org.fdroid.three", category, lastUsedDaysAgo = 3),
        )

        val report = report()

        assertThat(report.suggestions).isEmpty()
        assertThat(report.totalReclaimableBytes).isEqualTo(0L)
    }

    @Test
    fun `apps sharing a real category are flagged, except the most recently used`() = runTest {
        givenApps(
            app("com.player.recent", AppCategory.VIDEO, lastUsedDaysAgo = 1),
            app("com.player.older", AppCategory.VIDEO, lastUsedDaysAgo = 5),
            app("org.fdroid.one", AppCategory.UNDEFINED, lastUsedDaysAgo = 2),
            app("org.fdroid.two", AppCategory.UNDEFINED, lastUsedDaysAgo = 3),
        )

        val report = report()

        assertThat(report.suggestions.map { it.appInfo.packageName }).containsExactly("com.player.older")
        val suggestion = report.suggestions.single()
        assertThat(suggestion.category).isEqualTo(SmartSuggestion.Category.DUPLICATE_CATEGORY)
        assertThat(suggestion.reason).isEqualTo(SmartSuggestion.Reason.DuplicateCategory(AppCategory.VIDEO))
        assertThat(report.totalReclaimableBytes).isEqualTo(APP_SIZE)
    }

    // ---------------------------------------------------------------------------

    private suspend fun report(): SmartCleanerReport = (useCase() as Outcome.Success).value

    private fun givenApps(vararg apps: AppInfo) {
        every { ignoreList.observe() } returns flowOf(emptySet())
        every { repository.observeApps(includeSystemApps = false) } returns flowOf(Outcome.Success(apps.toList()))
    }

    private fun app(pkg: String, category: AppCategory, lastUsedDaysAgo: Int) = AppInfo(
        packageName      = pkg,
        label            = pkg,
        versionName      = "1.0",
        versionCode      = 1L,
        installSizeBytes = APP_SIZE,
        cacheSizeBytes   = 0L,
        dataSizeBytes    = 0L,
        firstInstallTime = now - 100 * msPerDay,
        lastUpdateTime   = now - 100 * msPerDay,
        lastUsedTime     = now - lastUsedDaysAgo * msPerDay,
        isSystemApp      = false,
        isUninstallable  = true,
        category         = category,
    )

    private companion object {
        // Far below the 100 MB "oversized" and "cache hog" thresholds.
        const val APP_SIZE = 10L * 1024 * 1024
    }
}
