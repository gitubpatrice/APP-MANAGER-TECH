package com.filestech.appmanager.domain.usecase

import com.filestech.appmanager.core.result.Outcome
import com.filestech.appmanager.data.system.ActiveRoleApps
import com.filestech.appmanager.data.system.CriticalAppDetector
import com.filestech.appmanager.domain.model.AppCategory
import com.filestech.appmanager.domain.model.AppInfo
import com.filestech.appmanager.domain.model.CriticalCategory
import com.filestech.appmanager.domain.model.CriticalClassification
import com.filestech.appmanager.domain.model.SmartCleanerReport
import com.filestech.appmanager.domain.model.SmartSuggestion
import com.filestech.appmanager.domain.repository.AppInfoRepository
import com.filestech.appmanager.domain.repository.IgnoreListRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * The rules of [GetSmartSuggestionsUseCase].
 *
 * Unless a test says otherwise, every app below is recently used, small and light on cache, so no other
 * rule fires: whatever the report holds comes from the rule under test.
 */
class GetSmartSuggestionsUseCaseTest {

    private val repository: AppInfoRepository = mockk()
    private val ignoreList: IgnoreListRepository = mockk()
    private val activeRoles: ActiveRoleApps = mockk()
    private val criticalDetector: CriticalAppDetector = mockk()
    private val useCase = GetSmartSuggestionsUseCase(
        repository, ignoreList, activeRoles, criticalDetector, Dispatchers.Unconfined,
    )

    private val now = System.currentTimeMillis()
    private val msPerDay = 24L * 60 * 60 * 1000

    @BeforeEach
    fun usageReadableNothingProtected() {
        every { repository.hasUsageStatsAccess() } returns true
        every { activeRoles.packages() } returns emptySet()
        every { criticalDetector.classify(any()) } returns null
        every { ignoreList.observe() } returns flowOf(emptySet())
    }

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
        // A duplicate is a guess about the user's preference: its size is not space to reclaim.
        assertThat(report.totalReclaimableBytes).isEqualTo(0L)
    }

    @Test
    fun `without usage access the report is empty, not a list of never-opened apps`() = runTest {
        every { repository.hasUsageStatsAccess() } returns false
        givenApps(app("com.any", AppCategory.UNDEFINED, lastUsedDaysAgo = null))

        val report = report()

        assertThat(report.suggestions).isEmpty()
        assertThat(report.analyzedAppCount).isEqualTo(0)
    }

    @Test
    fun `the last use is read live, not from a cache left at 0 by a scan made without access`() = runTest {
        givenApps(app("com.used", AppCategory.UNDEFINED, lastUsedDaysAgo = null))
        coEvery { repository.getLastUseTimes() } returns mapOf("com.used" to now - msPerDay)

        assertThat(report().suggestions).isEmpty()
    }

    @Test
    fun `a protected app rarely opened is not proposed for uninstall`() = runTest {
        every { criticalDetector.classify("com.beemdevelopment.aegis") } returns
            CriticalClassification("com.beemdevelopment.aegis", CriticalCategory.AUTHENTICATION)
        givenApps(
            app("com.beemdevelopment.aegis", AppCategory.UNDEFINED, lastUsedDaysAgo = 90),
            app("com.forgotten", AppCategory.UNDEFINED, lastUsedDaysAgo = 90),
        )

        val report = report()

        assertThat(report.suggestions.map { it.appInfo.packageName }).containsExactly("com.forgotten")
    }

    @Test
    fun `the keyboard in use is not proposed for uninstall`() = runTest {
        every { activeRoles.packages() } returns setOf("com.touchtype.swiftkey")
        givenApps(app("com.touchtype.swiftkey", AppCategory.UNDEFINED, lastUsedDaysAgo = null))

        assertThat(report().suggestions).isEmpty()
    }

    @Test
    fun `a large app unopened for half the zombie threshold is to review`() = runTest {
        givenApps(app("com.big.game", AppCategory.UNDEFINED, lastUsedDaysAgo = 20, size = BIG_SIZE))

        val suggestion = report().suggestions.single()

        assertThat(suggestion.category).isEqualTo(SmartSuggestion.Category.OVERSIZED_RARELY_USED)
        assertThat(suggestion.recommendedAction).isEqualTo(SmartSuggestion.RecommendedAction.REVIEW_AND_DECIDE)
    }

    @Test
    fun `days are counted from the last use, as on the Zombies screen`() = runTest {
        // Reinstalled 3 days ago, last opened 200 days ago: unused for 200 days, not 3.
        givenApps(app("com.reinstalled", AppCategory.UNDEFINED, lastUsedDaysAgo = 200, installedDaysAgo = 3))

        val reason = report().suggestions.single().reason

        assertThat(reason).isEqualTo(SmartSuggestion.Reason.UnusedFor(days = 200))
    }

    // ---------------------------------------------------------------------------

    private suspend fun report(): SmartCleanerReport = (useCase() as Outcome.Success).value

    /** The catalogue, and the live usage telling the same last uses (a test may then make them differ). */
    private fun givenApps(vararg apps: AppInfo) {
        every { repository.observeApps(includeSystemApps = false) } returns flowOf(Outcome.Success(apps.toList()))
        coEvery { repository.getLastUseTimes() } returns
            apps.filter { it.lastUsedTime > 0L }.associate { it.packageName to it.lastUsedTime }
    }

    /** [lastUsedDaysAgo] null: never used. */
    private fun app(
        pkg: String,
        category: AppCategory,
        lastUsedDaysAgo: Int?,
        size: Long = APP_SIZE,
        installedDaysAgo: Int = 100,
    ) = AppInfo(
        packageName      = pkg,
        label            = pkg,
        versionName      = "1.0",
        versionCode      = 1L,
        installSizeBytes = size,
        cacheSizeBytes   = 0L,
        dataSizeBytes    = 0L,
        firstInstallTime = now - installedDaysAgo * msPerDay,
        lastUpdateTime   = now - installedDaysAgo * msPerDay,
        lastUsedTime     = lastUsedDaysAgo?.let { now - it * msPerDay } ?: 0L,
        isSystemApp      = false,
        isUninstallable  = true,
        category         = category,
    )

    private companion object {
        // Far below the 100 MB "oversized" and "cache hog" thresholds.
        const val APP_SIZE = 10L * 1024 * 1024

        // Above the 100 MB "oversized" threshold.
        const val BIG_SIZE = 500L * 1024 * 1024
    }
}
