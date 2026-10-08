package com.filestech.appmanager.domain.usecase

import android.content.Intent
import com.filestech.appmanager.data.system.ApkBackupManager
import com.filestech.appmanager.data.system.IntentFactory
import com.filestech.appmanager.domain.model.QuarantineEntry
import com.filestech.appmanager.domain.model.QuarantineMode
import com.filestech.appmanager.domain.repository.QuarantineRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * [RestoreFromQuarantineUseCase], HARD mode: the installer reports nothing back, and can be cancelled
 * or refused (Android refused every restore until v0.5.1). The entry must survive the intent and go
 * only once the app is installed again — until v0.5.1 it was dropped with the intent, so a refused
 * install lost the quarantine record.
 */
class RestoreFromQuarantineUseCaseTest {

    private val repository: QuarantineRepository = mockk(relaxed = true)
    private val apkBackup: ApkBackupManager = mockk()
    private val intents: IntentFactory = mockk()
    private val useCase = RestoreFromQuarantineUseCase(repository, apkBackup, intents)

    private fun entry(mode: QuarantineMode) = QuarantineEntry(
        packageName        = PKG,
        label              = "Test",
        mode               = mode,
        quarantinedAt      = 0L,
        restoreAt          = 0L,
        versionName        = "1.0",
        versionCode        = 1L,
        apkBackupUri       = if (mode == QuarantineMode.HARD_UNINSTALL) "content://backup/test.apk" else null,
        autoRestoreEnabled = false,
        notified           = false,
    )

    @Test
    fun `hard restore hands the intent over and keeps the entry`() = runTest {
        coEvery { repository.getByPackage(PKG) } returns entry(QuarantineMode.HARD_UNINSTALL)
        every { apkBackup.restoreIntent(any()) } returns mockk<Intent>()

        val result = useCase(PKG)

        assertThat(result).isInstanceOf(RestoreFromQuarantineUseCase.Result.HardReinstall::class.java)
        coVerify(exactly = 0) { repository.delete(any()) }
    }

    @Test
    fun `confirm drops the hard entry once the app is installed again`() = runTest {
        coEvery { repository.getByPackage(PKG) } returns entry(QuarantineMode.HARD_UNINSTALL)
        every { apkBackup.isInstalled(PKG) } returns true

        assertThat(useCase.confirmHardRestore(PKG)).isTrue()
        coVerify(exactly = 1) { repository.delete(PKG) }
    }

    @Test
    fun `confirm keeps the hard entry when the install was cancelled or refused`() = runTest {
        coEvery { repository.getByPackage(PKG) } returns entry(QuarantineMode.HARD_UNINSTALL)
        every { apkBackup.isInstalled(PKG) } returns false

        assertThat(useCase.confirmHardRestore(PKG)).isFalse()
        coVerify(exactly = 0) { repository.delete(any()) }
    }

    @Test
    fun `confirm never drops a soft entry, even with the app installed`() = runTest {
        coEvery { repository.getByPackage(PKG) } returns entry(QuarantineMode.SOFT_REMINDER)
        every { apkBackup.isInstalled(PKG) } returns true

        assertThat(useCase.confirmHardRestore(PKG)).isFalse()
        coVerify(exactly = 0) { repository.delete(any()) }
    }

    private companion object {
        const val PKG = "com.example.quarantined"
    }
}
