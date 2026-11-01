package com.filestech.appmanager.domain.usecase

import com.filestech.appmanager.core.result.AppError
import com.filestech.appmanager.core.result.Outcome
import com.filestech.appmanager.domain.model.AppInfo
import com.filestech.appmanager.domain.model.PrivacyScore
import com.filestech.appmanager.domain.model.PrivacyTier
import com.filestech.appmanager.domain.repository.AppInfoRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * Pins the privacy score heuristic: every weight, the F-Droid bonus, each installer family and the
 * clamp, with the exact deduction labels. The permission groups come from PermissionCapability; the
 * device-admin and accessibility penalties from the permissions guarding the app's components, where a
 * real app declares them (never among its requested permissions).
 */
class GetPrivacyScoreUseCaseTest {

    private val repository: AppInfoRepository = mockk()
    private val useCase = GetPrivacyScoreUseCase(repository)

    private fun score(
        installer: String?,
        vararg permissions: String,
        componentPerms: Set<String> = emptySet(),
        systemApp: Boolean = false,
    ): PrivacyScore = useCase.score(app(installer, systemApp), permissions.toList(), componentPerms)

    @Test
    fun `an app from Play with no permission keeps the maximum score`() {
        val s = score("com.android.vending")
        assertThat(s.value).isEqualTo(100)
        assertThat(s.deductions).isEmpty()
        assertThat(s.tier).isEqualTo(PrivacyTier.GREEN)
    }

    @Test
    fun `Aurora and the phone makers' stores are mainstream - no adjustment`() {
        assertThat(score("com.aurora.store").value).isEqualTo(100)
        assertThat(score("com.aurora.services").deductions).isEmpty()
        assertThat(score("com.sec.android.app.samsungapps").deductions).isEmpty()
        assertThat(score("com.huawei.appmarket").deductions).isEmpty()
    }

    @Test
    fun `every F-Droid client earns the bonus, clamped at the maximum`() {
        for (client in listOf("org.fdroid.fdroid", "org.fdroid.basic", "com.looker.droidify", "com.machiav3lli.fdroid")) {
            val s = score(client)
            assertThat(s.value).isEqualTo(100)
            assertThat(s.deductions).containsExactly("+5 : F-Droid client")
        }
    }

    @Test
    fun `the F-Droid bonus offsets one dangerous permission group`() {
        val s = score("org.fdroid.fdroid.privileged", "android.permission.CAMERA")
        assertThat(s.value).isEqualTo(100)
        assertThat(s.deductions)
            .containsExactly("−5 : 1 dangerous permission group(s)", "+5 : F-Droid client")
            .inOrder()
    }

    @Test
    fun `a non-standard installer and INTERNET`() {
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
    fun `precise and approximate location are one group, READ and WRITE_CONTACTS another`() {
        val s = score(
            "com.android.vending",
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_COARSE_LOCATION",
            "android.permission.READ_CONTACTS",
            "android.permission.WRITE_CONTACTS",
        )
        assertThat(s.value).isEqualTo(90)
        assertThat(s.deductions).containsExactly("−10 : 2 dangerous permission group(s)")
    }

    @Test
    fun `permissions the old list missed are counted, health data among them`() {
        val s = score(
            "com.android.vending",
            "android.permission.GET_ACCOUNTS",
            "android.permission.RECEIVE_MMS",
            "android.permission.health.READ_HEART_RATE",
            "android.permission.health.READ_SLEEP",
        )
        // contacts, SMS, health: three groups.
        assertThat(s.deductions).containsExactly("−15 : 3 dangerous permission group(s)")
    }

    @Test
    fun `USE_BIOMETRIC is a normal permission and costs nothing`() {
        assertThat(score("com.android.vending", "android.permission.USE_BIOMETRIC").value).isEqualTo(100)
    }

    @Test
    fun `access to all files is its own deduction, not a dangerous group`() {
        val s = score("com.android.vending", "android.permission.MANAGE_EXTERNAL_STORAGE")
        assertThat(s.value).isEqualTo(95)
        assertThat(s.deductions).containsExactly("−5 : access to all files")
    }

    @Test
    fun `accessibility and device admin are read from the component guards`() {
        val s = score(
            "com.android.vending",
            componentPerms = setOf(
                "android.permission.BIND_ACCESSIBILITY_SERVICE",
                "android.permission.BIND_DEVICE_ADMIN",
            ),
        )
        assertThat(s.value).isEqualTo(60)
        assertThat(s.deductions).containsExactly("−20 : device admin", "−20 : accessibility service").inOrder()
        assertThat(s.declaresAccessibilityService).isTrue()
        assertThat(s.declaresDeviceAdmin).isTrue()
    }

    @Test
    fun `the same names among requested permissions count for nothing`() {
        // No real app requests them; the old rule looked only there and never fired.
        val s = score(
            "com.android.vending",
            "android.permission.BIND_ACCESSIBILITY_SERVICE",
            "android.permission.BIND_DEVICE_ADMIN",
        )
        assertThat(s.value).isEqualTo(100)
        assertThat(s.declaresAccessibilityService).isFalse()
    }

    @Test
    fun `no installer is sideloaded for a user app, preinstalled for a system app`() {
        assertThat(score(null).deductions).containsExactly("−10 : sideloaded (unknown origin)")
        assertThat(score(null, systemApp = true).deductions).isEmpty()
    }

    @Test
    fun `the package installer and adb count as sideloading`() {
        assertThat(score("com.google.android.packageinstaller").deductions)
            .containsExactly("−10 : sideloaded (unknown origin)")
        assertThat(score("com.android.shell").deductions).containsExactly("−10 : sideloaded (unknown origin)")
    }

    @Test
    fun `every weight at once lands exactly on the minimum`() {
        val s = score(
            null,
            "android.permission.CAMERA",
            "android.permission.RECORD_AUDIO",
            "android.permission.READ_SMS",
            "android.permission.INTERNET",
            "android.permission.FOREGROUND_SERVICE",
            "android.permission.SYSTEM_ALERT_WINDOW",
            componentPerms = setOf(
                "android.permission.BIND_DEVICE_ADMIN",
                "android.permission.BIND_ACCESSIBILITY_SERVICE",
            ),
        )
        // 100 - 3*5 - 10 - 10 - 15 - 20 - 20 - 10 = 0
        assertThat(s.value).isEqualTo(0)
        assertThat(s.tier).isEqualTo(PrivacyTier.RED)
        assertThat(s.deductions)
            .containsExactly(
                "−15 : 3 dangerous permission group(s)",
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
    fun `the score never goes below the minimum`() {
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
            "android.permission.MANAGE_EXTERNAL_STORAGE",
            "android.permission.SYSTEM_ALERT_WINDOW",
            componentPerms = setOf(
                "android.permission.BIND_DEVICE_ADMIN",
                "android.permission.BIND_ACCESSIBILITY_SERVICE",
            ),
        )
        // 100 - 7*5 - 5 - 15 - 20 - 20 - 10 = -5: below the floor, so the clamp is what gives 0.
        assertThat(s.value).isEqualTo(0)
        assertThat(s.tier).isEqualTo(PrivacyTier.RED)
        assertThat(s.deductions.first()).isEqualTo("−35 : 7 dangerous permission group(s)")
    }

    @Test
    fun `a permission list that cannot be read gives no score, not a perfect one`() = runTest {
        coEvery { repository.getApp("com.example.app") } returns Outcome.Success(app("com.android.vending", false))
        coEvery { repository.getRequestedPermissions("com.example.app") } returns
            Outcome.Failure(AppError.Unknown(IllegalStateException("package manager")))
        coEvery { repository.getComponentPermissions("com.example.app") } returns Outcome.Success(emptySet())

        assertThat(useCase("com.example.app")).isInstanceOf(Outcome.Failure::class.java)
    }

    @Test
    fun `component guards that cannot be read give no score either`() = runTest {
        coEvery { repository.getApp("com.example.app") } returns Outcome.Success(app("com.android.vending", false))
        coEvery { repository.getRequestedPermissions("com.example.app") } returns Outcome.Success(emptyList())
        coEvery { repository.getComponentPermissions("com.example.app") } returns
            Outcome.Failure(AppError.Unknown(IllegalStateException("package manager")))

        assertThat(useCase("com.example.app")).isInstanceOf(Outcome.Failure::class.java)
    }

    private fun app(installer: String?, systemApp: Boolean) = AppInfo(
        packageName      = "com.example.app",
        label            = "Example",
        versionName      = "1.0",
        versionCode      = 1L,
        installSizeBytes = 0L,
        cacheSizeBytes   = 0L,
        dataSizeBytes    = 0L,
        firstInstallTime = 0L,
        lastUpdateTime   = 0L,
        isSystemApp      = systemApp,
        isUninstallable  = true,
        installerPackage = installer,
    )
}
