package com.filestech.appmanager.domain.usecase

import com.filestech.appmanager.domain.model.AppInfo
import com.filestech.appmanager.domain.model.PrivacyScore
import com.filestech.appmanager.domain.model.PrivacyTier
import com.filestech.appmanager.domain.repository.AppInfoRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * Pins the privacy score heuristic: every weight, the F-Droid bonus, each installer family and the
 * clamp, with the exact deduction labels the AppDetail screen shows. Written against the heuristic as
 * it stood before its weights were named, so that naming them provably changed no score.
 */
class GetPrivacyScoreUseCaseTest {

    private val useCase = GetPrivacyScoreUseCase(mockk<AppInfoRepository>())

    private suspend fun score(installer: String?, vararg permissions: String): PrivacyScore =
        useCase.scoreMany(listOf(app(installer))) { permissions.toList() }.single()

    @Test
    fun `an app from Play with no permission keeps the maximum score`() = runTest {
        val s = score("com.android.vending")
        assertThat(s.value).isEqualTo(100)
        assertThat(s.deductions).isEmpty()
        assertThat(s.tier).isEqualTo(PrivacyTier.GREEN)
    }

    @Test
    fun `Aurora is a mainstream store - no adjustment`() = runTest {
        assertThat(score("com.aurora.store").value).isEqualTo(100)
        assertThat(score("com.aurora.services").deductions).isEmpty()
    }

    @Test
    fun `the F-Droid bonus is clamped at the maximum`() = runTest {
        val s = score("org.fdroid.fdroid")
        assertThat(s.value).isEqualTo(100)
        assertThat(s.deductions).containsExactly("+5 : F-Droid (vetted)")
    }

    @Test
    fun `the F-Droid bonus offsets one dangerous permission`() = runTest {
        val s = score("org.fdroid.fdroid.privileged", "android.permission.CAMERA")
        assertThat(s.value).isEqualTo(100)
        assertThat(s.deductions)
            .containsExactly("−5 : 1 dangerous permission(s)", "+5 : F-Droid (vetted)")
            .inOrder()
    }

    @Test
    fun `a non-standard installer and INTERNET`() = runTest {
        val s = score("com.example.store", "android.permission.INTERNET")
        assertThat(s.value).isEqualTo(75)
        assertThat(s.deductions)
            .containsExactly(
                "−10 : declares INTERNET",
                "−15 : non-standard installer (com.example.store)",
            )
            .inOrder()
    }

    @Test
    fun `every weight at once lands exactly on the minimum`() = runTest {
        val s = score(
            null,
            "android.permission.CAMERA",
            "android.permission.RECORD_AUDIO",
            "android.permission.READ_SMS",
            "android.permission.INTERNET",
            "android.permission.FOREGROUND_SERVICE",
            "android.permission.SYSTEM_ALERT_WINDOW",
            "android.permission.BIND_DEVICE_ADMIN",
            "android.permission.BIND_ACCESSIBILITY_SERVICE",
        )
        // 100 - 3*5 - 10 - 10 - 15 - 20 - 20 - 10 = 0
        assertThat(s.value).isEqualTo(0)
        assertThat(s.tier).isEqualTo(PrivacyTier.RED)
        assertThat(s.deductions)
            .containsExactly(
                "−15 : 3 dangerous permission(s)",
                "−10 : declares INTERNET",
                "−10 : runs foreground services",
                "−15 : can draw over other apps",
                "−20 : device admin",
                "−20 : accessibility service",
                "−10 : sideloaded (unknown origin)",
            )
            .inOrder()
    }

    @Test
    fun `the score never goes below the minimum`() = runTest {
        val s = score(
            null,
            "android.permission.CAMERA",
            "android.permission.RECORD_AUDIO",
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_COARSE_LOCATION",
            "android.permission.READ_CONTACTS",
            "android.permission.READ_SMS",
            "android.permission.READ_CALL_LOG",
            "android.permission.READ_CALENDAR",
            "android.permission.SYSTEM_ALERT_WINDOW",
            "android.permission.BIND_DEVICE_ADMIN",
            "android.permission.BIND_ACCESSIBILITY_SERVICE",
        )
        // 100 - 8*5 - 15 - 20 - 20 - 10 = -5: below the floor, so the clamp is what gives 0.
        assertThat(s.value).isEqualTo(0)
        assertThat(s.tier).isEqualTo(PrivacyTier.RED)
        assertThat(s.deductions.first()).isEqualTo("−40 : 8 dangerous permission(s)")
    }

    private fun app(installer: String?) = AppInfo(
        packageName      = "com.example.app",
        label            = "Example",
        versionName      = "1.0",
        versionCode      = 1L,
        installSizeBytes = 0L,
        cacheSizeBytes   = 0L,
        dataSizeBytes    = 0L,
        firstInstallTime = 0L,
        lastUpdateTime   = 0L,
        isSystemApp      = false,
        isUninstallable  = true,
        installerPackage = installer,
    )
}
