package com.filestech.appmanager.domain.usecase

import com.filestech.appmanager.core.result.AppError
import com.filestech.appmanager.core.result.Outcome
import com.filestech.appmanager.domain.model.AppInfo
import com.filestech.appmanager.domain.model.Tracker
import com.filestech.appmanager.domain.model.TrackerReport
import com.filestech.appmanager.domain.repository.AppInfoRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/** Tests [ScanAllTrackersUseCase]: an app whose scan fails is neither clean nor counted in the figures. */
class ScanAllTrackersUseCaseTest {

    private val repository: AppInfoRepository = mockk()
    private val detect: DetectTrackersUseCase = mockk()
    private val useCase = ScanAllTrackersUseCase(repository, detect, Dispatchers.Unconfined)

    private val admob = Tracker("admob", "Google AdMob", "Ads", listOf("com.google.android.gms.ads."))

    @Test
    fun `a failed scan is counted apart, not as a clean app`() = runTest {
        every { repository.observeApps(false) } returns
            flowOf(Outcome.Success(listOf(app("com.clean"), app("com.ads"), app("com.gone"))))
        coEvery { detect("com.clean") } returns Outcome.Success(TrackerReport("com.clean", emptyList(), 3))
        coEvery { detect("com.ads") } returns Outcome.Success(TrackerReport("com.ads", listOf(admob), 5))
        coEvery { detect("com.gone") } returns Outcome.Failure(AppError.PackageNotFound("com.gone"))

        val result = (useCase() as Outcome.Success).value

        assertThat(result.totalAppCount).isEqualTo(2)
        assertThat(result.failedAppCount).isEqualTo(1)
        assertThat(result.appsWithTrackers).isEqualTo(1)
        assertThat(result.contaminationPercent).isEqualTo(50)
        assertThat(result.reports.map { it.packageName }).containsExactly("com.ads", "com.clean").inOrder()
    }

    @Test
    fun `when every scan fails, nothing is analysed and everything is counted as failed`() = runTest {
        every { repository.observeApps(false) } returns flowOf(Outcome.Success(listOf(app("com.a"), app("com.b"))))
        coEvery { detect(any()) } returns Outcome.Failure(AppError.PackageNotFound("x"))

        val result = (useCase() as Outcome.Success).value

        assertThat(result.totalAppCount).isEqualTo(0)
        assertThat(result.failedAppCount).isEqualTo(2)
        assertThat(result.reports).isEmpty()
    }

    private fun app(pkg: String) = AppInfo(
        packageName      = pkg,
        label            = pkg,
        versionName      = "1.0",
        versionCode      = 1L,
        installSizeBytes = 0L,
        cacheSizeBytes   = 0L,
        dataSizeBytes    = 0L,
        firstInstallTime = 0L,
        lastUpdateTime   = 0L,
        isSystemApp      = false,
        isUninstallable  = true,
    )
}
