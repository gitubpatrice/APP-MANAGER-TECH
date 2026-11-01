package com.filestech.appmanager.domain.usecase

import com.filestech.appmanager.core.result.AppError
import com.filestech.appmanager.core.result.Outcome
import com.filestech.appmanager.domain.model.LifecycleEvent
import com.filestech.appmanager.domain.model.LifecycleEventType
import com.filestech.appmanager.domain.repository.AppLifecycleRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/** Tests [DetectPermissionDeltaUseCase]: which earlier event is the baseline, and an unreadable history. */
class DetectPermissionDeltaUseCaseTest {

    private val repository: AppLifecycleRepository = mockk()
    private val useCase = DetectPermissionDeltaUseCase(Dispatchers.Unconfined, repository)

    @Test
    fun `the update gained what its snapshot has and the previous one lacks`() = runTest {
        val installed = event(id = 1, type = LifecycleEventType.INSTALLED, at = 1_000, perms = listOf(CAMERA))
        val replaced = event(id = 2, type = LifecycleEventType.REPLACED, at = 2_000, perms = listOf(CAMERA, MIC))
        givenHistory(replaced, installed)

        val delta = (useCase(replaced) as Outcome.Success).value

        assertThat(delta?.gained).containsExactly(MIC)
    }

    @Test
    fun `the baseline is the event recorded before, even when the clock went back`() = runTest {
        // Clock set back a day between the two: the baseline reads as "later" than the update.
        val installed = event(id = 1, type = LifecycleEventType.INSTALLED, at = 90_000_000, perms = listOf(CAMERA))
        val replaced = event(id = 2, type = LifecycleEventType.REPLACED, at = 3_600_000, perms = listOf(CAMERA, MIC))
        givenHistory(installed, replaced)

        val delta = (useCase(replaced) as Outcome.Success).value

        assertThat(delta?.gained).containsExactly(MIC)
    }

    @Test
    fun `an uninstall in between is no baseline`() = runTest {
        val installed = event(id = 1, type = LifecycleEventType.INSTALLED, at = 1_000, perms = emptyList())
        val uninstalled = event(id = 2, type = LifecycleEventType.UNINSTALLED, at = 2_000, perms = listOf(MIC))
        val replaced = event(id = 3, type = LifecycleEventType.REPLACED, at = 3_000, perms = listOf(MIC))
        givenHistory(replaced, uninstalled, installed)

        val delta = (useCase(replaced) as Outcome.Success).value

        assertThat(delta?.gained).containsExactly(MIC)
    }

    @Test
    fun `a history that cannot be read is a failure, not an absence of comparison`() = runTest {
        every { repository.observeByPackage(PKG) } returns
            flowOf(Outcome.Failure(AppError.DatabaseError(IllegalStateException("db"))))
        val replaced = event(id = 2, type = LifecycleEventType.REPLACED, at = 2_000, perms = listOf(MIC))

        assertThat(useCase(replaced)).isInstanceOf(Outcome.Failure::class.java)
    }

    private fun givenHistory(vararg events: LifecycleEvent) {
        every { repository.observeByPackage(PKG) } returns flowOf(Outcome.Success(events.toList()))
    }

    private fun event(id: Long, type: LifecycleEventType, at: Long, perms: List<String>) = LifecycleEvent(
        id                          = id,
        packageName                 = PKG,
        label                       = "Example",
        type                        = type,
        capturedAt                  = at,
        versionName                 = "1.0",
        versionCode                 = id,
        installerPackage            = null,
        totalSizeBytes              = 0L,
        grantedDangerousPermissions = perms,
        userReason                  = null,
        apkSha256                   = null,
    )

    private companion object {
        const val PKG = "com.example.app"
        const val CAMERA = "android.permission.CAMERA"
        const val MIC = "android.permission.RECORD_AUDIO"
    }
}
