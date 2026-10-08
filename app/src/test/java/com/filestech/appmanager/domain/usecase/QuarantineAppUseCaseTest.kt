package com.filestech.appmanager.domain.usecase

import android.content.Intent
import android.net.Uri
import com.filestech.appmanager.core.result.Outcome
import com.filestech.appmanager.data.system.ApkBackupManager
import com.filestech.appmanager.data.system.IntentFactory
import com.filestech.appmanager.domain.model.AppInfo
import com.filestech.appmanager.domain.model.QuarantineMode
import com.filestech.appmanager.domain.repository.AppInfoRepository
import com.filestech.appmanager.domain.repository.QuarantineRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * [QuarantineAppUseCase], HARD mode: it uninstalls the app and its data on the promise of a restore
 * from the backup. v0.5.1 — refused, before anything is copied, saved or uninstalled, for the apps
 * that backup could never restore: a system app (its uninstall only removes updates) and an app
 * installed as split APKs (the backup holds the base APK only).
 */
class QuarantineAppUseCaseTest {

    private val repository: QuarantineRepository = mockk(relaxed = true)
    private val appInfo: AppInfoRepository = mockk()
    private val apkBackup: ApkBackupManager = mockk()
    private val intents: IntentFactory = mockk()
    private val useCase = QuarantineAppUseCase(repository, appInfo, apkBackup, intents)
    private val backupFolder: Uri = mockk()

    private fun app(isSystemApp: Boolean = false) = AppInfo(
        packageName      = PKG,
        label            = LABEL,
        versionName      = "1.0",
        versionCode      = 1L,
        installSizeBytes = 0L,
        cacheSizeBytes   = 0L,
        dataSizeBytes    = 0L,
        firstInstallTime = 0L,
        lastUpdateTime   = 0L,
        isSystemApp      = isSystemApp,
        isUninstallable  = !isSystemApp,
    )

    private suspend fun quarantineHard() = useCase(PKG, QuarantineMode.HARD_UNINSTALL, DAYS, backupFolder)

    @Test
    fun `hard quarantine of a system app is refused before any backup, save or uninstall`() = runTest {
        coEvery { appInfo.getApp(PKG) } returns Outcome.Success(app(isSystemApp = true))
        coEvery { apkBackup.hasSplitApks(PKG) } returns false

        val result = quarantineHard()

        assertThat(result).isEqualTo(
            QuarantineAppUseCase.Result.Failure(
                reason   = QuarantineAppUseCase.FailureReason.SYSTEM_APP,
                detail   = "HARD mode refused for system app $PKG",
                appLabel = LABEL,
            ),
        )
        assertNothingDone()
    }

    @Test
    fun `hard quarantine of a split-APK app is refused before any backup, save or uninstall`() = runTest {
        coEvery { appInfo.getApp(PKG) } returns Outcome.Success(app())
        coEvery { apkBackup.hasSplitApks(PKG) } returns true

        val result = quarantineHard()

        assertThat((result as QuarantineAppUseCase.Result.Failure).reason)
            .isEqualTo(QuarantineAppUseCase.FailureReason.SPLIT_APKS)
        assertThat(result.appLabel).isEqualTo(LABEL)
        assertNothingDone()
    }

    @Test
    fun `hard quarantine of a single-APK user app still goes through`() = runTest {
        // Control: the refusal must not catch the apps the backup can restore.
        coEvery { appInfo.getApp(PKG) } returns Outcome.Success(app())
        coEvery { apkBackup.hasSplitApks(PKG) } returns false
        coEvery { apkBackup.backupApk(backupFolder, PKG, 1L) } returns ApkBackupManager.Result.Success("content://backup/x.apk")
        every { intents.uninstallIntent(PKG) } returns mockk<Intent>()

        val result = quarantineHard()

        assertThat(result).isInstanceOf(QuarantineAppUseCase.Result.HardReady::class.java)
        coVerify(exactly = 1) { repository.upsert(any()) }
    }

    @Test
    fun `a reminder is still allowed for an app hard mode refuses`() = runTest {
        coEvery { appInfo.getApp(PKG) } returns Outcome.Success(app(isSystemApp = true))
        coEvery { apkBackup.hasSplitApks(PKG) } returns true
        every { intents.appDetailsSettingsIntent(PKG) } returns mockk<Intent>()

        val result = useCase(PKG, QuarantineMode.SOFT_REMINDER, DAYS, backupTreeUri = null)

        assertThat(result).isInstanceOf(QuarantineAppUseCase.Result.SoftReady::class.java)
        coVerify(exactly = 1) { repository.upsert(any()) }
    }

    private fun assertNothingDone() {
        coVerify(exactly = 0) { apkBackup.backupApk(any(), any(), any()) }
        coVerify(exactly = 0) { repository.upsert(any()) }
        verify(exactly = 0) { intents.uninstallIntent(any()) }
    }

    private companion object {
        const val PKG = "com.example.target"
        const val LABEL = "Target"
        const val DAYS = 30
    }
}
